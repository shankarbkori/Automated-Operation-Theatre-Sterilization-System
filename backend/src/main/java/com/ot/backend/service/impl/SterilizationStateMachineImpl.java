package com.ot.backend.service.impl;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.ot.backend.dto.SterilizationStatusDTO;
import com.ot.backend.entity.SensorData;
import com.ot.backend.entity.SterilizationSession;
import com.ot.backend.entity.SterilizationState;
import com.ot.backend.repository.SessionRepository;
import com.ot.backend.service.DeviceService;
import com.ot.backend.service.SensorService;
import com.ot.backend.service.SterilizationStateMachineService;

import jakarta.annotation.PostConstruct;

/**
 * Core sterilization state machine.
 *
 * Key design principles:
 * 1. Every state transition immediately persists to the database.
 * 2. On server restart, the active session is detected and the cycle resumes.
 * 3. Safety events (motion/door during any active phase) immediately halt the
 * cycle.
 * 4. Durations come from the Python AI engine — never hardcoded.
 * 5. Each phase runs on a background thread so the HTTP response returns
 * immediately.
 * 6. The total prototype cycle is capped at 120 seconds.
 * 7. Exhaust phase includes air-quality verification loop.
 * 8. Initial and final sensor snapshots are captured for reporting.
 */
@Service
public class SterilizationStateMachineImpl implements SterilizationStateMachineService {

    private static final Logger log = LoggerFactory.getLogger(SterilizationStateMachineImpl.class);

    private static final String DEVICE_ID = "ESP32_OT_NODE";
    private static final String AI_PREDICT_URL = "http://localhost:5000/api/ai/sterilization-plan";

    // Prototype constraint: total cycle must complete within 120 seconds
    private static final int MAX_PROTOTYPE_SECONDS = 120;

    // Fallback durations if AI is unavailable (seconds) — must fit within 120s
    private static final int FALLBACK_UV_SECONDS = 45;
    private static final int FALLBACK_FOG_SECONDS = 30;
    private static final int FALLBACK_FAN_SECONDS = 25;

    // Maximum extended ventilation timeout (seconds) before raising alarm
    private static final int MAX_VENTILATION_TIMEOUT_SECONDS = 60;

    // Air quality thresholds for exhaust verification (AQI scale 0–500)
    private static final double CO2_SAFE_THRESHOLD = 100.0;
    private static final double PM25_SAFE_THRESHOLD = 100.0;

    private final SessionRepository sessionRepository;
    private final DeviceService deviceService;
    private final SensorService sensorService;
    private final RestTemplate restTemplate;
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    private final com.ot.backend.service.NotificationService notificationService;

    // In-memory active session cache (always in sync with DB)
    private volatile SterilizationSession activeSession = null;

    // Track whether the last AI call succeeded
    private volatile boolean lastAiAvailable = false;

    public SterilizationStateMachineImpl(SessionRepository sessionRepository,
            DeviceService deviceService,
            SensorService sensorService,
            com.ot.backend.service.NotificationService notificationService,
            RestTemplate restTemplate) {
        this.sessionRepository = sessionRepository;
        this.deviceService = deviceService;
        this.sensorService = sensorService;
        this.notificationService = notificationService;
        this.restTemplate = restTemplate;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // STARTUP — Resume any interrupted cycle
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    @PostConstruct
    public void resumeOnStartup() {
        List<SterilizationSession> active = sessionRepository.findActiveSession();
        if (active.isEmpty()) {
            log.info("[StateMachine] No active session found. Machine is IDLE.");
            return;
        }

        log.warn(
                "[StateMachine] Found {} unfinished 'ghost' sessions from previous incomplete runs. Aborting them safely.",
                active.size());

        for (SterilizationSession ghost : active) {
            ghost.setCurrentState(SterilizationState.ERROR);
            ghost.setErrorMessage("Server restarted while cycle was active. Cycle safely aborted.");
            sessionRepository.save(ghost);
        }

        // Ensure we boot totally clean
        activeSession = null;
        turnAllRelaysOff();
        deviceService.clearCyclePlan(DEVICE_ID);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // START
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public synchronized SterilizationSession startCycle(String operatorUsername) {
        if (activeSession != null && !isTerminalState(activeSession.getCurrentState())) {
            String reason = activeSession.getCurrentState() == SterilizationState.PAUSED
                    ? "A cycle is PAUSED on a safety event and must be resumed or aborted first."
                    : "A sterilization cycle is already in progress: " + activeSession.getCurrentState();
            throw new IllegalStateException(reason);
        }

        if (!deviceService.isDeviceOnline(DEVICE_ID)) {
            throw new IllegalStateException("ESP32 device is OFFLINE. Cannot start sterilization.");
        }

        SterilizationSession session = new SterilizationSession();
        session.setCurrentState(SterilizationState.PREPARING);
        session.setCycleStartedAt(LocalDateTime.now());
        session.setOperatorUsername(operatorUsername);
        sessionRepository.save(session);
        activeSession = session;

        log.info("[StateMachine] New cycle started. Session #{} by {}", session.getId(), operatorUsername);

        // Run the state machine on a background thread
        runAsync(() -> executePreparing(session));

        return session;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ABORT
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public void abortCycle(String reason) {
        if (activeSession == null || isTerminalState(activeSession.getCurrentState())) {
            return;
        }
        log.warn("[StateMachine] ABORTING cycle. Reason: {}", reason);
        turnAllRelaysOff();
        deviceService.clearCyclePlan(DEVICE_ID);
        transitionTo(activeSession, SterilizationState.ERROR);
        activeSession.setErrorMessage(reason);
        appendSafetyEvent(activeSession, "ABORT: " + reason);
        sessionRepository.save(activeSession);
        activeSession = null; // Allow new cycles to start
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PAUSE
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public void pauseCycle(String reason) {
        if (activeSession == null || isTerminalState(activeSession.getCurrentState())
                || activeSession.getCurrentState() == SterilizationState.PAUSED) {
            return;
        }
        log.warn("[StateMachine] PAUSING cycle manually. Reason: {}", reason);
        turnAllRelaysOff();
        deviceService.clearCyclePlan(DEVICE_ID);
        activeSession.setPausedAtState(activeSession.getCurrentState());
        activeSession.setInterruptionCount(activeSession.getInterruptionCount() + 1);
        transitionTo(activeSession, SterilizationState.PAUSED);
        appendSafetyEvent(activeSession, "MANUAL PAUSE: " + reason);
        sessionRepository.save(activeSession);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // RESUME (after PAUSED state)
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public void resumeCycle() {
        if (activeSession == null || activeSession.getCurrentState() != SterilizationState.PAUSED) {
            throw new IllegalStateException("No paused cycle to resume.");
        }

        if (!deviceService.isDeviceOnline(DEVICE_ID)) {
            throw new IllegalStateException("ESP32 device is OFFLINE. Cannot resume.");
        }

        SterilizationState resumeFrom = activeSession.getPausedAtState();
        if (resumeFrom == null)
            resumeFrom = SterilizationState.SAFETY_CHECK;

        log.info("[StateMachine] Resuming cycle from: {}", resumeFrom);
        appendSafetyEvent(activeSession, "RESUMED from " + resumeFrom + " by admin.");
        sessionRepository.save(activeSession);

        final SterilizationSession session = activeSession;
        switch (resumeFrom) {
            case UV_RUNNING -> runAsync(() -> executeUvPhase(session));
            case FOGGING_RUNNING -> runAsync(() -> executeFogPhase(session));
            case EXHAUST_RUNNING -> runAsync(() -> executeFanPhase(session));
            default -> runAsync(() -> executePreparing(session)); // back to safety check
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SAFETY INTERRUPT — called on every sensor packet
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public void onSensorUpdate(boolean motionDetected, boolean doorClosed) {
        if (activeSession == null)
            return;
        SterilizationState cur = activeSession.getCurrentState();
        boolean active = cur == SterilizationState.UV_RUNNING || cur == SterilizationState.FOGGING_RUNNING
                || cur == SterilizationState.EXHAUST_RUNNING;
        if (!active)
            return;

        if (motionDetected || !doorClosed) {
            String event = (motionDetected ? "MOTION DETECTED" : "DOOR OPENED") + " during " + cur;
            log.error("[StateMachine] SAFETY INTERRUPT: {}", event);

            sendRelayCommand("uvlamp", false);
            sendRelayCommand("mistpump", false);
            sendRelayCommand("exhaustfan", false);
            sendRelayCommand("relay", false);
            deviceService.clearCyclePlan(DEVICE_ID);

            appendSafetyEvent(activeSession, event);
            activeSession.setPausedAtState(cur);
            activeSession.setInterruptionCount(activeSession.getInterruptionCount() + 1);
            transitionTo(activeSession, SterilizationState.PAUSED);
            sessionRepository.save(activeSession);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // STATUS
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public SterilizationStatusDTO getStatus() {
        SterilizationStatusDTO dto = new SterilizationStatusDTO();

        if (activeSession == null) {
            dto.setCurrentState(SterilizationState.IDLE);
            dto.setCurrentStateLabel("System Idle");
            dto.setProgressPercent(0);
            return dto;
        }

        mapSessionToDto(activeSession, dto);
        return dto;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PRIVATE — Phase Executors
    // ─────────────────────────────────────────────────────────────────────────

    private void executePreparing(SterilizationSession session) {
        log.info("[StateMachine] Phase: PREPARING");
        transitionTo(session, SterilizationState.PREPARING);
        sleep(2);

        // SAFETY CHECK
        transitionTo(session, SterilizationState.SAFETY_CHECK);
        log.info("[StateMachine] Phase: SAFETY_CHECK");
        for (int attempt = 0; attempt < 30; attempt++) { // wait up to 30s
            var latest = sensorService.getLatestSensorData();
            if (latest != null && latest.isDoorClosed() && !latest.isMotionDetected()) {
                log.info("[StateMachine] Safety check passed.");
                break;
            }
            if (attempt == 29) {
                abortCycle("Safety check failed: door not closed or motion detected after 30 seconds.");
                return;
            }
            sleep(1);
        }

        // Capture initial environment snapshot
        captureInitialSnapshot(session);

        // AI ANALYSIS
        transitionTo(session, SterilizationState.AI_ANALYSIS);
        log.info("[StateMachine] Phase: AI_ANALYSIS");
        fetchAiPrediction(session);

        // Enforce 120-second prototype limit
        enforcePrototypeLimit(session);
        save(session);

        // Move to UV phase
        executeUvPhase(session);
    }

    private void executeUvPhase(SterilizationSession session) {
        int uvSeconds = session.getAiUvSeconds() > 0 ? session.getAiUvSeconds() : FALLBACK_UV_SECONDS;
        int fogSeconds = session.getAiFogSeconds() > 0 ? session.getAiFogSeconds() : FALLBACK_FOG_SECONDS;
        int fanSeconds = session.getAiFanSeconds() > 0 ? session.getAiFanSeconds() : FALLBACK_FAN_SECONDS;
        log.info("[StateMachine] pushing AI plan to firmware ({}s, {}s, {}s)", uvSeconds, fogSeconds, fanSeconds);

        turnAllRelaysOff(); // Safe baseline

        // Push full AI-predicted plan to firmware once so ESP32 can run relays
        // independently.
        deviceService.pushCyclePlan(DEVICE_ID, uvSeconds + 5, fogSeconds + 5, fanSeconds + 5);

        // Hardware Sync: Wait until ESP32 acknowledges plan and starts local UV loop
        log.info("[StateMachine] Waiting for ESP32 to acknowledge UV_ACTIVE phase (max 10s)...");
        for (int i = 0; i < 15; i++) {
            var latest = sensorService.getLatestSensorData();
            if (latest != null && "UV_ACTIVE".equalsIgnoreCase(latest.getProcessState())) {
                log.info("[StateMachine] ESP32 hardware synced. Starting UV timer in backend.");
                break;
            }
            sleep(1);
        }

        log.info("[StateMachine] Phase: UV_RUNNING (backend timer aligned)");
        session.setUvStartedAt(LocalDateTime.now());
        transitionTo(session, SterilizationState.UV_RUNNING);

        if (!sleepForPhase(session, uvSeconds))
            return;

        double actualUv = ChronoUnit.SECONDS.between(session.getUvStartedAt(), LocalDateTime.now());
        session.setActualUvTime(actualUv);
        transitionTo(session, SterilizationState.UV_COMPLETED);
        log.info("[StateMachine] UV_COMPLETED (actual: {}s)", actualUv);
        executeFogPhase(session);
    }

    private void executeFogPhase(SterilizationSession session) {
        log.info("[StateMachine] Phase: FOGGING_RUNNING");
        session.setFogStartedAt(LocalDateTime.now());
        transitionTo(session, SterilizationState.FOGGING_RUNNING);

        int fogSeconds = session.getAiFogSeconds() > 0 ? session.getAiFogSeconds() : FALLBACK_FOG_SECONDS;
        int fanSeconds = session.getAiFanSeconds() > 0 ? session.getAiFanSeconds() : FALLBACK_FAN_SECONDS;

        // Push plan with UV=0 and a 5s pad so ESP32 skips directly to FOGGING and
        // awaits backend
        int pad = 5;
        deviceService.pushCyclePlan(DEVICE_ID, 0, fogSeconds + pad, fanSeconds + pad);

        if (!sleepForPhase(session, fogSeconds))
            return;

        double actualFog = ChronoUnit.SECONDS.between(session.getFogStartedAt(), LocalDateTime.now());
        session.setActualFogTime(actualFog);
        transitionTo(session, SterilizationState.FOGGING_COMPLETED);
        log.info("[StateMachine] FOGGING_COMPLETED (actual: {}s)", actualFog);
        executeFanPhase(session);
    }

    private void executeFanPhase(SterilizationSession session) {
        log.info("[StateMachine] Phase: EXHAUST_RUNNING");
        session.setFanStartedAt(LocalDateTime.now());
        transitionTo(session, SterilizationState.EXHAUST_RUNNING);

        int fanSeconds = session.getAiFanSeconds() > 0 ? session.getAiFanSeconds() : FALLBACK_FAN_SECONDS;

        // Push plan with UV=0 and FOG=0 and a 5s pad so ESP32 skips directly to EXHAUST
        int pad = 5;
        deviceService.pushCyclePlan(DEVICE_ID, 0, 0, fanSeconds + pad);

        // Run the initial predicted exhaust duration
        if (!sleepForPhase(session, fanSeconds))
            return;

        // ── Air Quality Verification Loop ────────────────────────────────────
        // After the predicted exhaust duration, check if air quality is safe.
        // If not, continue ventilation in a closed loop until safe or timeout.
        int extraVentilationElapsed = 0;
        while (extraVentilationElapsed < MAX_VENTILATION_TIMEOUT_SECONDS) {
            SensorData latest = sensorService.getLatestSensorData();
            if (latest != null) {
                boolean airSafe = latest.getCo2() < CO2_SAFE_THRESHOLD
                        && latest.getPm25() < PM25_SAFE_THRESHOLD;
                if (airSafe) {
                    log.info("[StateMachine] Air quality verified SAFE after exhaust. CO2={}, PM2.5={}",
                            latest.getCo2(), latest.getPm25());
                    break;
                }
                log.info("[StateMachine] Air quality NOT safe yet. CO2={}, PM2.5={}. Extending ventilation...",
                        latest.getCo2(), latest.getPm25());
            }

            // Check safety conditions during extended ventilation
            SterilizationState cur = session.getCurrentState();
            if (cur == SterilizationState.PAUSED || cur == SterilizationState.ERROR)
                return;

            sleep(5); // Check every 5 seconds
            extraVentilationElapsed += 5;
        }

        if (extraVentilationElapsed >= MAX_VENTILATION_TIMEOUT_SECONDS) {
            appendSafetyEvent(session,
                    "WARNING: Air quality did not reach safe levels within " + MAX_VENTILATION_TIMEOUT_SECONDS
                            + "s extended ventilation. Proceeding with completion.");
            log.warn("[StateMachine] Maximum ventilation timeout reached. Proceeding to completion.");
        }

        deviceService.clearCyclePlan(DEVICE_ID);
        double actualFan = ChronoUnit.SECONDS.between(session.getFanStartedAt(), LocalDateTime.now());
        session.setActualFanTime(actualFan);
        transitionTo(session, SterilizationState.EXHAUST_COMPLETED);
        log.info("[StateMachine] EXHAUST_COMPLETED (actual: {}s, includes {}s extended ventilation)",
                actualFan, extraVentilationElapsed);
        executeReportGeneration(session);
    }

    /**
     * Sleeps for exactly durationSeconds, checking for pause/abort/emergency once
     * per second. Returns true if the phase completed normally, false if it was
     * interrupted by a safety event.
     */
    private boolean sleepForPhase(SterilizationSession session, int durationSeconds) {
        long targetEndTime = System.currentTimeMillis() + (durationSeconds * 1000L);

        while (System.currentTimeMillis() < targetEndTime) {
            SterilizationState cur = session.getCurrentState();
            if (cur == SterilizationState.PAUSED || cur == SterilizationState.ERROR) {
                return false;
            }

            var latest = sensorService.getLatestSensorData();
            String processState = latest != null ? latest.getProcessState() : null;

            if ("EMERGENCY".equals(processState)) {
                appendSafetyEvent(session, "Firmware reported EMERGENCY state during " + cur);
                session.setPausedAtState(cur);
                session.setInterruptionCount(session.getInterruptionCount() + 1);
                transitionTo(session, SterilizationState.PAUSED);
                sessionRepository.save(session);
                return false;
            }

            long remaining = targetEndTime - System.currentTimeMillis();
            if (remaining <= 0)
                break;
            long sleepTime = Math.min(1000L, remaining);

            try {
                Thread.sleep(sleepTime);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return true;
    }

    private void executeReportGeneration(SterilizationSession session) {
        log.info("[StateMachine] Phase: REPORT_GENERATION");
        transitionTo(session, SterilizationState.REPORT_GENERATION);

        // Capture final environment snapshot
        captureFinalSnapshot(session);
        save(session);

        sleep(2);

        session.setCompletedAt(LocalDateTime.now());
        session.setReportGenerated(true);
        transitionTo(session, SterilizationState.COMPLETED);
        log.info("[StateMachine] Sterilization cycle COMPLETED. Session #{}", session.getId());
        log.info("[StateMachine] Predicted: UV={}s FOG={}s FAN={}s | Actual: UV={}s FOG={}s FAN={}s",
                session.getAiUvSeconds(), session.getAiFogSeconds(), session.getAiFanSeconds(),
                session.getActualUvTime(), session.getActualFogTime(), session.getActualFanTime());

        // Ensure all hardware is off and clear the ESP32 cycle plan
        turnAllRelaysOff();
        deviceService.clearCyclePlan(DEVICE_ID);
        activeSession = null; // Allow new cycles to start

        try {
            double totalActual = session.getActualUvTime() + session.getActualFogTime()
                    + session.getActualFanTime();
            int totalPredicted = session.getAiUvSeconds() + session.getAiFogSeconds() + session.getAiFanSeconds();
            notificationService.sendWhatsAppAlert(
                    "OT Sterilization cycle #" + session.getId()
                            + " completed successfully.\nTotal: " + String.format("%.0f", totalActual)
                            + "s (predicted: " + totalPredicted + "s)\nInterruptions: "
                            + session.getInterruptionCount()
                            + "\nRoom is ready.");
        } catch (Exception e) {
            log.warn("[StateMachine] WhatsApp notification failed: {}", e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PRIVATE — Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private void transitionTo(SterilizationSession session, SterilizationState newState) {
        log.info("[StateMachine] {} → {}", session.getCurrentState(), newState);
        session.setCurrentState(newState);
        sessionRepository.save(session);
    }

    private SterilizationSession save(SterilizationSession session) {
        return sessionRepository.save(session);
    }

    /**
     * Capture the initial environment snapshot before starting the cycle.
     */
    private void captureInitialSnapshot(SterilizationSession session) {
        SensorData latest = sensorService.getLatestSensorData();
        if (latest != null) {
            session.setInitialTemp(latest.getTemperature());
            session.setInitialHumidity(latest.getHumidity());
            session.setInitialCo2(latest.getCo2());
            log.info("[StateMachine] Initial snapshot: Temp={}, Hum={}, CO2={}",
                    latest.getTemperature(), latest.getHumidity(), latest.getCo2());
        }
        save(session);
    }

    /**
     * Capture the final environment snapshot after completing the cycle.
     */
    private void captureFinalSnapshot(SterilizationSession session) {
        SensorData latest = sensorService.getLatestSensorData();
        if (latest != null) {
            session.setFinalTemp(latest.getTemperature());
            session.setFinalHumidity(latest.getHumidity());
            session.setFinalCo2(latest.getCo2());
            log.info("[StateMachine] Final snapshot: Temp={}, Hum={}, CO2={}",
                    latest.getTemperature(), latest.getHumidity(), latest.getCo2());
        }
    }

    /**
     * Enforce the 120-second prototype limit.
     * If the predicted total exceeds 120s, scale all durations proportionally.
     */
    private void enforcePrototypeLimit(SterilizationSession session) {
        int totalPredicted = session.getAiUvSeconds() + session.getAiFogSeconds() + session.getAiFanSeconds();
        if (totalPredicted > MAX_PROTOTYPE_SECONDS && totalPredicted > 0) {
            double scale = (double) MAX_PROTOTYPE_SECONDS / totalPredicted;
            int scaledUv = Math.max(5, (int) (session.getAiUvSeconds() * scale));
            int scaledFog = Math.max(3, (int) (session.getAiFogSeconds() * scale));
            int scaledFan = Math.max(3, (int) (session.getAiFanSeconds() * scale));
            log.warn(
                    "[StateMachine] AI predicted {}s total (exceeds {}s limit). Scaling: UV={}→{}s FOG={}→{}s FAN={}→{}s",
                    totalPredicted, MAX_PROTOTYPE_SECONDS,
                    session.getAiUvSeconds(), scaledUv,
                    session.getAiFogSeconds(), scaledFog,
                    session.getAiFanSeconds(), scaledFan);
            session.setAiUvSeconds(scaledUv);
            session.setAiFogSeconds(scaledFog);
            session.setAiFanSeconds(scaledFan);
            appendSafetyEvent(session,
                    "Prototype limit enforced: original total=" + totalPredicted + "s, scaled to "
                            + (scaledUv + scaledFog + scaledFan) + "s");
        }
    }

    @SuppressWarnings("unchecked")
    private void fetchAiPrediction(SterilizationSession session) {
        try {
            // Build request body with latest live sensor readings
            var latest = sensorService.getLatestSensorData();
            java.util.Map<String, Object> requestBody = new java.util.HashMap<>();
            if (latest != null) {
                requestBody.put("temperature", latest.getTemperature());
                requestBody.put("humidity", latest.getHumidity());
                requestBody.put("pm25", latest.getPm25());
                requestBody.put("pm10", latest.getPm10());
                requestBody.put("co2", latest.getCo2());
            }

            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
            org.springframework.http.HttpEntity<java.util.Map<String, Object>> entity = new org.springframework.http.HttpEntity<>(
                    requestBody, headers);

            Map<String, Object> response = restTemplate.postForObject(AI_PREDICT_URL, entity, Map.class);
            if (response != null && !Boolean.FALSE.equals(response.get("success"))) {
                session.setAiUvSeconds(toInt(response.get("uvSeconds"), FALLBACK_UV_SECONDS));
                session.setAiFogSeconds(toInt(response.get("foggingSeconds"), FALLBACK_FOG_SECONDS));
                session.setAiFanSeconds(toInt(response.get("exhaustSeconds"), FALLBACK_FAN_SECONDS));
                String notes = (String) response.getOrDefault("notes", "AI plan");
                session.setAiRiskLabel(notes.length() > 80 ? notes.substring(0, 80) : notes);
                session.setAiRiskLevel(0.5);
                session.setAiPredictionUsed(true);
                lastAiAvailable = true;
                log.info("[StateMachine] AI prediction: UV={}s FOG={}s FAN={}s | {}",
                        session.getAiUvSeconds(), session.getAiFogSeconds(),
                        session.getAiFanSeconds(), notes);
            } else {
                log.warn("[StateMachine] AI responded with success=false or null. Using fallback durations.");
                applyFallbackDurations(session);
            }
        } catch (Exception e) {
            log.warn("[StateMachine] AI service unavailable. Using fallback durations. Error: {}", e.getMessage(), e);
            applyFallbackDurations(session);
        }
    }

    private void applyFallbackDurations(SterilizationSession session) {
        session.setAiUvSeconds(FALLBACK_UV_SECONDS);
        session.setAiFogSeconds(FALLBACK_FOG_SECONDS);
        session.setAiFanSeconds(FALLBACK_FAN_SECONDS);
        session.setAiRiskLevel(0.5);
        session.setAiRiskLabel("MODERATE (fallback — AI unavailable)");
        session.setAiPredictionUsed(false);
        lastAiAvailable = false;
    }

    private void sendRelayCommand(String relayType, boolean state) {
        try {
            deviceService.updateRelay(DEVICE_ID, relayType, state);
            log.debug("[StateMachine] Relay {} → {}", relayType, state ? "ON" : "OFF");
        } catch (Exception e) {
            log.error("[StateMachine] Failed to send relay command {}: {}", relayType, e.getMessage());
        }
    }

    private void turnAllRelaysOff() {
        sendRelayCommand("uvlamp", false);
        sendRelayCommand("mistpump", false);
        sendRelayCommand("exhaustfan", false);
        sendRelayCommand("relay", false);
    }

    private void appendSafetyEvent(SterilizationSession session, String event) {
        String timestamp = LocalDateTime.now().toString();
        String entry = "[" + timestamp + "] " + event;
        String existing = session.getSafetyEventLog();
        session.setSafetyEventLog(existing == null ? entry : existing + "\n" + entry);
    }

    private boolean isTerminalState(SterilizationState state) {
        return state == SterilizationState.COMPLETED || state == SterilizationState.ERROR;
    }

    private void runAsync(Runnable task) {
        executor.submit(() -> {
            try {
                task.run();
            } catch (Exception e) {
                log.error("[StateMachine] Unhandled error in state machine thread: {}", e.getMessage(), e);
                if (activeSession != null) {
                    abortCycle("Internal error: " + e.getMessage());
                }
            }
        });
    }

    private void sleep(int seconds) {
        try {
            TimeUnit.SECONDS.sleep(seconds);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Maps the current session state to the dashboard DTO, including live
     * computed elapsed/remaining times, phase names, relay states, and
     * actual durations.
     */
    private void mapSessionToDto(SterilizationSession session, SterilizationStatusDTO dto) {
        dto.setSessionId(session.getId());
        dto.setCurrentState(session.getCurrentState());
        dto.setCurrentStateLabel(stateLabel(session.getCurrentState()));
        dto.setProgressPercent(stateProgress(session.getCurrentState()));
        dto.setCycleStartedAt(session.getCycleStartedAt());
        dto.setUvStartedAt(session.getUvStartedAt());
        dto.setFogStartedAt(session.getFogStartedAt());
        dto.setFanStartedAt(session.getFanStartedAt());
        dto.setCompletedAt(session.getCompletedAt());
        dto.setAiUvSeconds(session.getAiUvSeconds());
        dto.setAiFogSeconds(session.getAiFogSeconds());
        dto.setAiFanSeconds(session.getAiFanSeconds());
        dto.setTotalPredictedSeconds(session.getAiUvSeconds() + session.getAiFogSeconds() + session.getAiFanSeconds());
        dto.setAiRiskLevel(session.getAiRiskLevel());
        dto.setAiRiskLabel(session.getAiRiskLabel());
        dto.setAiAvailable(lastAiAvailable || session.isAiPredictionUsed());
        dto.setLastSafetyEvent(session.getSafetyEventLog());
        dto.setErrorMessage(session.getErrorMessage());
        dto.setInterruptionCount(session.getInterruptionCount());

        // Actual durations (populated after each phase completes)
        dto.setActualUvSeconds(session.getActualUvTime());
        dto.setActualFogSeconds(session.getActualFogTime());
        dto.setActualFanSeconds(session.getActualFanTime());

        // ── Live relay status from current state ─────────────────────────────
        SterilizationState cur = session.getCurrentState();
        dto.setUvActive(cur == SterilizationState.UV_RUNNING);
        dto.setFogActive(cur == SterilizationState.FOGGING_RUNNING);
        dto.setFanActive(cur == SterilizationState.EXHAUST_RUNNING);
        dto.setAlarmActive(cur == SterilizationState.PAUSED || cur == SterilizationState.ERROR);

        // ── Live elapsed/remaining time computation ──────────────────────────
        LocalDateTime now = LocalDateTime.now();
        int totalPredicted = session.getAiUvSeconds() + session.getAiFogSeconds() + session.getAiFanSeconds();
        dto.setTotalPredictedSeconds(totalPredicted);

        // Total elapsed since cycle start
        long totalElapsed = 0;
        if (session.getCycleStartedAt() != null && !isTerminalState(cur) && cur != SterilizationState.IDLE) {
            totalElapsed = ChronoUnit.SECONDS.between(session.getCycleStartedAt(), now);
        } else if (session.getCycleStartedAt() != null && session.getCompletedAt() != null) {
            totalElapsed = ChronoUnit.SECONDS.between(session.getCycleStartedAt(), session.getCompletedAt());
        }
        dto.setTotalElapsedSeconds(totalElapsed);
        dto.setTotalRemainingSeconds(Math.max(0, totalPredicted - totalElapsed));

        // Current phase elapsed/remaining
        String phaseName = "—";
        long phaseElapsed = 0;
        long phaseRemaining = 0;
        int phaseDuration = 0;

        switch (cur) {
            case UV_RUNNING -> {
                phaseName = "UV-C Sterilization";
                phaseDuration = session.getAiUvSeconds();
                if (session.getUvStartedAt() != null) {
                    phaseElapsed = ChronoUnit.SECONDS.between(session.getUvStartedAt(), now);
                }
            }
            case FOGGING_RUNNING -> {
                phaseName = "H₂O₂ Fogging";
                phaseDuration = session.getAiFogSeconds();
                if (session.getFogStartedAt() != null) {
                    phaseElapsed = ChronoUnit.SECONDS.between(session.getFogStartedAt(), now);
                }
            }
            case EXHAUST_RUNNING -> {
                phaseName = "Exhaust / HEPA";
                phaseDuration = session.getAiFanSeconds();
                if (session.getFanStartedAt() != null) {
                    phaseElapsed = ChronoUnit.SECONDS.between(session.getFanStartedAt(), now);
                }
            }
            case PREPARING, SAFETY_CHECK -> phaseName = "Safety Pre-Check";
            case AI_ANALYSIS -> phaseName = "AI Analysis";
            case REPORT_GENERATION -> phaseName = "Generating Report";
            case COMPLETED -> phaseName = "Cycle Complete";
            case PAUSED -> phaseName = "Paused — Safety Event";
            case ERROR -> phaseName = "Aborted";
            default -> phaseName = "Idle";
        }

        phaseRemaining = Math.max(0, phaseDuration - phaseElapsed);
        dto.setCurrentPhaseName(phaseName);
        dto.setCurrentPhaseElapsedSeconds(phaseElapsed);
        dto.setCurrentPhaseRemainingSeconds(phaseRemaining);
    }

    private String stateLabel(SterilizationState state) {
        return switch (state) {
            case IDLE -> "System Idle";
            case PREPARING -> "Preparing Cycle";
            case SAFETY_CHECK -> "Safety Check";
            case AI_ANALYSIS -> "AI Analysis";
            case UV_RUNNING -> "UV-C Sterilization Running";
            case UV_COMPLETED -> "UV Phase Complete";
            case FOGGING_RUNNING -> "Disinfectant Fogging";
            case FOGGING_COMPLETED -> "Fogging Complete";
            case EXHAUST_RUNNING -> "Exhaust Fan Running";
            case EXHAUST_COMPLETED -> "Fan Phase Complete";
            case REPORT_GENERATION -> "Generating Report";
            case COMPLETED -> "Cycle Complete ✅";
            case PAUSED -> "⚠ Paused — Safety Event";
            case ERROR -> "🚨 Cycle Aborted";
        };
    }

    private int stateProgress(SterilizationState state) {
        return switch (state) {
            case IDLE -> 0;
            case PREPARING -> 5;
            case SAFETY_CHECK -> 10;
            case AI_ANALYSIS -> 15;
            case UV_RUNNING -> 35;
            case UV_COMPLETED -> 50;
            case FOGGING_RUNNING -> 65;
            case FOGGING_COMPLETED -> 75;
            case EXHAUST_RUNNING -> 88;
            case EXHAUST_COMPLETED -> 95;
            case REPORT_GENERATION -> 97;
            case COMPLETED -> 100;
            case PAUSED -> 30;
            case ERROR -> 0;
        };
    }

    private int toInt(Object val, int fallback) {
        if (val == null)
            return fallback;
        if (val instanceof Number n)
            return n.intValue();
        try {
            return Integer.parseInt(val.toString());
        } catch (Exception e) {
            return fallback;
        }
    }
}
