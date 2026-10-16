package com.ot.backend.dto;

import lombok.Data;

@Data
public class DeviceStatusDTO {

    private String deviceName;
    private boolean online;
    private String ipAddress;
    private String firmwareVersion;
    private boolean relayActive;
    private boolean uvLampActive;
    private boolean humidifierActive;
    private boolean exhaustFanActive;

}