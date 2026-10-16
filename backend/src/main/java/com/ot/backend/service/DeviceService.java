package com.ot.backend.service;

import com.ot.backend.entity.DeviceStatus;
import java.util.List;

public interface DeviceService {

    DeviceStatus saveDevice(DeviceStatus device);

    List<DeviceStatus> getAllDevices();

    DeviceStatus updateRelay(String deviceName, String relayType, boolean state);

    /**
     * Called when ESP32 sends a heartbeat ping. Updates lastSeenAt and marks device
     * online.
     */
    DeviceStatus processHeartbeat(String deviceId, String ipAddress);

    /**
     * Returns the online status of a device — true if heard from within 10 seconds.
     */
    boolean isDeviceOnline(String deviceId);

    DeviceStatus pushCyclePlan(String deviceName, int uvSeconds, int fogSeconds, int exhaustSeconds);

    DeviceStatus clearCyclePlan(String deviceName);

}
