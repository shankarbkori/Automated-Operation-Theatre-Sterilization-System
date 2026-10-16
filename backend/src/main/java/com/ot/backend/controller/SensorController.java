package com.ot.backend.controller;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ot.backend.dto.ApiResponse;
import com.ot.backend.dto.SensorDataDTO;
import com.ot.backend.entity.SensorData;
import com.ot.backend.mapper.SensorMapper;
import com.ot.backend.service.DeviceService;
import com.ot.backend.service.NotificationService;
import com.ot.backend.service.SensorService;
import com.ot.backend.service.SterilizationStateMachineService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/sensors")
@CrossOrigin(origins = { "http://localhost:8081", "http://127.0.0.1:8081" })
public class SensorController {

    private static final String DEVICE_ID = "ESP32_OT_NODE";

    private final SensorService sensorService;
    private final NotificationService notificationService;
    private final DeviceService deviceService;
    private final SterilizationStateMachineService stateMachine;

    public SensorController(SensorService sensorService,
            NotificationService notificationService,
            DeviceService deviceService,
            SterilizationStateMachineService stateMachine) {
        this.sensorService = sensorService;
        this.notificationService = notificationService;
        this.deviceService = deviceService;
        this.stateMachine = stateMachine;
    }

    @PostMapping
    public ApiResponse<SensorDataDTO> save(@Valid @RequestBody SensorDataDTO dto) {
        SensorData previous = sensorService.getLatestSensorData();
        String oldState = (previous != null && previous.getProcessState() != null) ? previous.getProcessState()
                : "IDLE";
        String newState = dto.getProcessState() != null ? dto.getProcessState() : "IDLE";

        // WhatsApp Alert Transitions
        if (!"READY".equals(oldState) && "READY".equals(newState)) {
            String msg = String.format(
                    "✅ *Operation Theatre Sterilization Completed*\nTemp: %.1f C\nHumidity: %.1f %%\nAir Quality: %.0f PPM\nRoom Ready Time: %s",
                    dto.getTemperature(), dto.getHumidity(), dto.getCo2(), LocalDateTime.now().toString());
            notificationService.sendWhatsAppAlert(msg);
        }

        if (!"EMERGENCY".equals(oldState) && "EMERGENCY".equals(newState)) {
            String msg = "🚨 *CRITICAL ALERT: Operation Theatre Sterilization ABORTED*\nAnomaly Detected during cycle! System has severed power to actuators.";
            notificationService.sendWhatsAppAlert(msg);
        }

        // Every sensor packet from ESP32 counts as a heartbeat — stamp lastSeenAt
        deviceService.processHeartbeat(DEVICE_ID, null);

        // Safety interrupt: if UV is running and door opens or motion detected, halt
        // immediately
        stateMachine.onSensorUpdate(dto.isMotionDetected(), dto.isDoorClosed());

        SensorData saved = sensorService.saveSensorData(SensorMapper.toEntity(dto));
        SensorDataDTO responseDto = SensorMapper.toDto(saved);
        responseDto.setDeviceOnline(true);
        return new ApiResponse<>(true, "Sensor data saved", responseDto);
    }

    @GetMapping
    public ApiResponse<List<SensorDataDTO>> getAll() {
        List<SensorDataDTO> list = sensorService.getAllSensorData().stream()
                .map(SensorMapper::toDto)
                .collect(Collectors.toList());
        return new ApiResponse<>(true, "Sensor data retrieved", list);
    }

    @GetMapping("/latest")
    public ApiResponse<SensorDataDTO> latest() {
        SensorData latest = sensorService.getLatestSensorData();
        if (latest == null) {
            // No sensor data yet (empty DB) — return safe defaults
            SensorDataDTO empty = new SensorDataDTO();
            empty.setDeviceOnline(false);
            return new ApiResponse<>(true, "No sensor data yet", empty);
        }
        SensorDataDTO dto = SensorMapper.toDto(latest);
        // Server-authoritative: the device is ONLY online if its heartbeat was
        // received within the last 30 seconds. No fallback — if the heartbeat
        // window expires, the device is OFFLINE, period.
        boolean online = deviceService.isDeviceOnline(DEVICE_ID);

        if (!online) {
            // Zero out ALL live sensor values so the dashboard never displays
            // stale readings as if they were current. The historical data
            // remains in the DB for reports, but the "latest" API must only
            // return live values when the hardware is actually connected.
            dto.setTemperature(0);
            dto.setHumidity(0);
            dto.setPm25(0);
            dto.setPm10(0);
            dto.setCo2(0);
            dto.setTvoc(0);
            dto.setCo(0);
            dto.setLight(0);
            dto.setMotionDetected(false);
            dto.setDoorClosed(true); // Default to safe state when offline, not false
            dto.setProcessState("IDLE");
            dto.setAirQualityLabel("UNKNOWN");
        }
        dto.setDeviceOnline(online);
        return new ApiResponse<>(true, "Latest sensor data retrieved", dto);
    }

}
