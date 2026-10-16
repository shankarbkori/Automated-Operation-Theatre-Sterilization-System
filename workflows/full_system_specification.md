---
description: Full A‑Z functional workflow and logic specification for the Automated OT Sterilization System
---

# Workflow: Complete System Specification

## 1. System Startup
1. Power on ESP32.
2. Initialize all GPIO pins, sensors, relays, buzzer, LEDs.
3. Connect to Wi‑Fi.
4. Verify backend availability (ping Spring Boot API).
5. Send initial heartbeat to backend.
6. Ensure all actuators are OFF.
7. Set system state to `SYSTEM_STARTING`.

## 2. Self‑Check
1. Perform hardware self‑diagnostics for each sensor and actuator.
2. Validate Wi‑Fi and backend connectivity.
3. Backend validates database and AI service connectivity.
4. If any check fails → log error, set state `SYSTEM_ERROR`, keep all actuators OFF, display error on dashboard.
5. If all pass → set state `SYSTEM_HEALTHY` and proceed.

## 3. Device Online Heartbeat
- ESP32 sends heartbeat JSON every few seconds containing:
  - deviceId, timestamp, firmware status, sensor health, actuator status, current sterilization stage, remaining time.
- Backend stores `lastSeenAt` and updates `deviceOnline` flag (30‑second window).

## 4. Offline Logic
- If no heartbeat within 30 s, backend marks device OFFLINE.
- Dashboard replaces live sensor values with `--` / `UNKNOWN` and disables control actions.

## 5. Safety Preconditions (Room Ready)
1. Verify `deviceOnline`.
2. Check door sensor = CLOSED.
3. Check PIR = NO_MOTION.
4. Ensure all required sensors are healthy.
5. Ensure no active alarm or ongoing cycle.
6. Ensure AI service is reachable.
7. If all true → state `SAFE_FOR_STERILIZATION`.

## 6. Environmental Capture & Validation
- Collect stable readings (average over 3 samples) for temperature, humidity, air‑quality, light.
- Backend validates ranges; marks any out‑of‑range as `UNKNOWN`.

## 7. AI/ML Prediction
- Send validated environmental data to Python AI service.
- Receive predicted durations: `uvDuration`, `foggingDuration`, `exhaustDuration`.
- Compute `predictedTotal = sum(durations)`.
- If `predictedTotal > 120 s` → flag `PREDICTION_OUTSIDE_PROTOTYPE_LIMIT` and apply fallback policy (e.g., cap at 120 s or abort).
- Store prediction and status in DB.

## 8. Final Safety Check before Start
- Re‑verify all safety conditions (device online, door closed, no motion, sensors healthy, no alarm).
- If any fail → abort start.

## 9. Create Sterilization Session
- Insert session record with:
  - sessionId, deviceId, startTimestamp, predicted durations, initial sensor snapshot, AI prediction status.
- Set session state `UV_RUNNING`.

## 10. UV Stage
1. Backend sends `UV_ON` command to ESP32.
2. ESP32 acknowledges and turns UV relay ON, reports `uvRelay = ON`.
3. Backend records `stageStartTime`.
4. Continuously monitor safety sensors (door, motion, device connection, sensor health).
5. If interruption → send `UV_OFF`, log event, store remaining time, set state `UV_PAUSED`.
6. On completion → turn UV OFF, record actual duration, set state `FOGGING_RUNNING`.

## 11. Fogging Stage (similar to UV)
- Commands `FOG_ON` / `FOG_OFF`.
- Handle interruptions, pause/resume, record actual duration.
- Transition to `EXHAUST_RUNNING` after completion.

## 12. Exhaust / Ventilation Stage
- Activate exhaust fan for predicted duration.
- After initial period, evaluate air‑quality reading.
- If `AIR_QUALITY_SAFE` → finish stage, else continue ventilation up to a configured maximum timeout.
- Record actual exhaust duration.

## 13. Final Air‑Quality Verification
- Take final sensor snapshot.
- Compare with initial snapshot.
- Determine overall safety result.

## 14. Session Completion
- Update session record with actual stage durations, final sensor values, interruptions count, safety events, final air‑quality status, `status = COMPLETED`.
- Dashboard displays full report and predicted vs actual timings.

## 15. Reporting & Notification
- Generate PDF/CSV report containing all session data.
- Optionally send notification (e.g., email) if configured.

## 16. Return to Idle Monitoring
- Set system state `IDLE`.
- ESP32 continues sensor polling and heartbeat.
- Await next safe‑room request.

## 17. State Machine Overview (Backend)
```
SYSTEM_STARTING → SYSTEM_HEALTHY → IDLE
IDLE → PRECHECK → SAFE_FOR_STERILIZATION → AI_ANALYSIS → READY_TO_START
READY_TO_START → UV_RUNNING → UV_PAUSED → FOGGING_RUNNING → FOGGING_PAUSED → EXHAUST_RUNNING → EXHAUST_PAUSED → AIR_QUALITY_VERIFICATION → EXTENDED_VENTILATION → COMPLETED
```
Each transition lists entry conditions, allowed actions, safety checks, and exit conditions.

## 18. Safety Priority Rules
- Emergency/Alarm overrides all.
- Device offline forces all actuators OFF.
- Door open or motion detected forces pause/stop.
- AI prediction never overrides safety conditions.

## 19. Prototype Time Constraint
- Enforce `predictedTotal ≤ 120 s`.
- If exceeded, either cap durations proportionally or abort with user notification.

---
*This workflow captures the complete A‑Z functional specification and can be used as a reference for implementation, testing, and documentation.*
