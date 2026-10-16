package com.ot.backend.dto;

public class DeviceRegistrationRequest {

    private String device;
    private String status;

    public DeviceRegistrationRequest() {
    }

    public DeviceRegistrationRequest(String device, String status) {
        this.device = device;
        this.status = status;
    }

    public String getDevice() {
        return device;
    }

    public void setDevice(String device) {
        this.device = device;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}