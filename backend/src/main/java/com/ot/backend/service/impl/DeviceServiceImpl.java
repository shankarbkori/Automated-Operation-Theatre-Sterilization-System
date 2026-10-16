package com.ot.backend.service.impl;

import com.ot.backend.entity.DeviceStatus;
import com.ot.backend.repository.DeviceRepository;
import com.ot.backend.service.DeviceService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class DeviceServiceImpl implements DeviceService {

    private final DeviceRepository repository;

    public DeviceServiceImpl(DeviceRepository repository) {
        this.repository = repository;
    }

    @Override
    public DeviceStatus saveDevice(DeviceStatus device) {
        return repository.save(device);
    }

    @Override
    public List<DeviceStatus> getAllDevices() {
        return repository.findAll();
    }

    @Override
    public DeviceStatus processHeartbeat(String deviceId, String ipAddress) {
        DeviceStatus device = repository.findByDeviceName(deviceId)
                .orElseGet(() -> {
                    DeviceStatus d = new DeviceStatus();
                    d.setDeviceName(deviceId);
                    return d;
                });
        device.setLastSeenAt(LocalDateTime.now());
        if (ipAddress != null && !ipAddress.isBlank()) {
            device.setIpAddress(ipAddress);
        }
        return repository.save(device);
    }

    @Override
    public boolean isDeviceOnline(String deviceId) {
        return repository.findByDeviceName(deviceId)
                .map(DeviceStatus::isDeviceOnline)
                .orElse(false);
    }

    @Override
    public DeviceStatus updateRelay(String deviceName, String relayType, boolean state) {
        DeviceStatus device = repository.findByDeviceName(deviceName)
                .orElseGet(() -> {
                    DeviceStatus newDevice = new DeviceStatus();
                    newDevice.setDeviceName(deviceName);
                    return newDevice;
                });

        switch (relayType.toLowerCase()) {
            case "relay":
                device.setRelayActive(state);
                break;
            case "uvlamp":
                device.setUvLampActive(state);
                break;
            case "mistpump":
            case "humidifier":
                device.setHumidifierActive(state);
                break;
            case "exhaustfan":
            case "fan":
                device.setExhaustFanActive(state);
                break;
            default:
                throw new IllegalArgumentException("Unknown relay type: " + relayType);
        }

        return repository.save(device);
    }

    @Override
    public DeviceStatus pushCyclePlan(String deviceName, int uvSeconds, int fogSeconds, int exhaustSeconds) {
        DeviceStatus device = repository.findByDeviceName(deviceName)
                .orElseGet(() -> { DeviceStatus d = new DeviceStatus(); d.setDeviceName(deviceName); return d; });
        device.setPlannedUvSeconds(uvSeconds);
        device.setPlannedFoggingSeconds(fogSeconds);
        device.setPlannedExhaustSeconds(exhaustSeconds);
        device.setCycleSequence(device.getCycleSequence() + 1);
        device.setRelayActive(true);
        return repository.save(device);
    }

    @Override
    public DeviceStatus clearCyclePlan(String deviceName) {
        DeviceStatus device = repository.findByDeviceName(deviceName)
                .orElseGet(() -> { DeviceStatus d = new DeviceStatus(); d.setDeviceName(deviceName); return d; });
        device.setRelayActive(false);
        device.setUvLampActive(false);
        device.setHumidifierActive(false);
        device.setExhaustFanActive(false);
        device.setPlannedUvSeconds(0);
        device.setPlannedFoggingSeconds(0);
        device.setPlannedExhaustSeconds(0);
        return repository.save(device);
    }
}
