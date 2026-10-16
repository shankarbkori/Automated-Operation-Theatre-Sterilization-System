package com.ot.backend.entity;

import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
public class DeviceStatus extends BaseEntity {

    private String deviceName;

    private String ipAddress;

    private int plannedUvSeconds;
    private int plannedFoggingSeconds;
    private int plannedExhaustSeconds;
    private long cycleSequence;

    private String firmwareVersion;

    private boolean relayActive;

    private boolean uvLampActive;

    private boolean humidifierActive;

    private boolean exhaustFanActive;

    /** Updated every time the ESP32 sends a heartbeat or sensor packet. */
    private LocalDateTime lastSeenAt;

    /**
     * Returns true if a heartbeat/sensor packet was received within the last 30
     * seconds. (30s window — ESP32 may transmit every 10–15s; 10s was too strict
     * and caused mid-cycle false offline detection.)
     */
    public boolean isDeviceOnline() {
        if (lastSeenAt == null)
            return false;
        return lastSeenAt.isAfter(LocalDateTime.now().minusSeconds(30));
    }

}
