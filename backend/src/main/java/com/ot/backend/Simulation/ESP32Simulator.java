package com.ot.backend.Simulation;

import java.time.LocalDateTime;

import com.ot.backend.entity.DeviceStatus;
import com.ot.backend.service.DeviceService;

import jakarta.annotation.PostConstruct;

// @Component
public class ESP32Simulator {

    private final DeviceService deviceService;

    public ESP32Simulator(DeviceService deviceService) {
        this.deviceService = deviceService;
    }

    @PostConstruct
    public void initDevice() {
        DeviceStatus defaultDevice = new DeviceStatus();
        defaultDevice.setDeviceName("ESP32_OT_NODE");
        defaultDevice.setIpAddress("192.168.1.100 (Simulated)");
        defaultDevice.setFirmwareVersion("v2.1.0-SIM");
        defaultDevice.setLastSeenAt(LocalDateTime.now());
        defaultDevice.setRelayActive(false);
        defaultDevice.setUvLampActive(false);
        defaultDevice.setHumidifierActive(false);
        defaultDevice.setExhaustFanActive(false);
        deviceService.saveDevice(defaultDevice);
    }

    // @Scheduled(fixedRate = 5000)
    public void simulateSensorPing() {
        // Keeps the simulated tracker updating (simulating an ESP32 sending a health
        // ping structure)
        System.out.println("[ESP32 Mock] Pinging heartbeat to core systems.");
        deviceService.updateRelay("ESP32_OT_NODE", "relay", false); // Just a dummy trace if we wanted to enforce state
    }
}
