package com.ot.backend.controller;

import com.ot.backend.entity.DeviceStatus;
import com.ot.backend.service.DeviceService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/device")
@CrossOrigin(origins = { "http://localhost:8081", "http://127.0.0.1:8081" })
public class DeviceController {

    private final DeviceService service;

    public DeviceController(DeviceService service) {
        this.service = service;
    }

    @PostMapping
    public DeviceStatus save(@RequestBody DeviceStatus device) {
        return service.saveDevice(device);
    }

    @GetMapping
    public List<DeviceStatus> all() {
        return service.getAllDevices();
    }

    /**
     * Heartbeat endpoint — called by ESP32 every 5 seconds.
     * Marks the device as ONLINE and stamps lastSeenAt.
     * No JWT required (ESP32 cannot hold tokens).
     */
    @PostMapping("/heartbeat")
    public ResponseEntity<Map<String, Object>> heartbeat(@RequestBody Map<String, String> payload) {
        String deviceId = payload.getOrDefault("deviceId", "ESP32_OT_NODE");
        String ip = payload.getOrDefault("ip", "");
        service.processHeartbeat(deviceId, ip);
        return ResponseEntity.ok(Map.of("status", "ok", "deviceId", deviceId));
    }

    /**
     * Returns current online status for a device — used by the dashboard for
     * real-time checks.
     */
    @GetMapping("/{deviceName}/status")
    public ResponseEntity<Map<String, Object>> deviceStatus(@PathVariable String deviceName) {
        boolean online = service.isDeviceOnline(deviceName);
        return ResponseEntity.ok(Map.of("deviceId", deviceName, "online", online));
    }

    @PostMapping("/{deviceName}/command/{relayType}/{state}")
    public DeviceStatus commandRelay(@PathVariable String deviceName,
            @PathVariable String relayType,
            @PathVariable boolean state) {
        return service.updateRelay(deviceName, relayType, state);
    }

    /** Focused endpoint the firmware polls to get its AI-calculated cycle plan. */
    @GetMapping("/{deviceName}/plan")
    public ResponseEntity<Map<String, Object>> plan(@PathVariable String deviceName) {
        DeviceStatus d = service.getAllDevices().stream()
                .filter(x -> x.getDeviceName().equals(deviceName)).findFirst().orElse(null);
        if (d == null) {
            return ResponseEntity.ok(Map.of("relayActive", false, "cycleSequence", 0,
                    "plannedUvSeconds", 0, "plannedFoggingSeconds", 0, "plannedExhaustSeconds", 0));
        }
        return ResponseEntity.ok(Map.of(
                "relayActive", d.isRelayActive(), "cycleSequence", d.getCycleSequence(),
                "plannedUvSeconds", d.getPlannedUvSeconds(), "plannedFoggingSeconds", d.getPlannedFoggingSeconds(),
                "plannedExhaustSeconds", d.getPlannedExhaustSeconds(),
                "uvLampActive", d.isUvLampActive(), "humidifierActive", d.isHumidifierActive(),
                "exhaustFanActive", d.isExhaustFanActive()));
    }
}
