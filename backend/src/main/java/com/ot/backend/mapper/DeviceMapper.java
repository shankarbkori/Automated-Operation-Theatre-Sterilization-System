package com.ot.backend.mapper;

import com.ot.backend.dto.DeviceStatusDTO;
import com.ot.backend.entity.DeviceStatus;

public class DeviceMapper {

    public static DeviceStatusDTO toDto(DeviceStatus entity) {
        if (entity == null)
            return null;
        DeviceStatusDTO dto = new DeviceStatusDTO();
        dto.setDeviceName(entity.getDeviceName());
        dto.setOnline(entity.isDeviceOnline());
        dto.setIpAddress(entity.getIpAddress());
        dto.setFirmwareVersion(entity.getFirmwareVersion());
        dto.setRelayActive(entity.isRelayActive());
        dto.setUvLampActive(entity.isUvLampActive());
        dto.setHumidifierActive(entity.isHumidifierActive());
        dto.setExhaustFanActive(entity.isExhaustFanActive());
        return dto;
    }

    public static DeviceStatus toEntity(DeviceStatusDTO dto) {
        if (dto == null)
            return null;
        DeviceStatus entity = new DeviceStatus();
        entity.setDeviceName(dto.getDeviceName());
        entity.setIpAddress(dto.getIpAddress());
        entity.setFirmwareVersion(dto.getFirmwareVersion());
        entity.setRelayActive(dto.isRelayActive());
        entity.setUvLampActive(dto.isUvLampActive());
        entity.setHumidifierActive(dto.isHumidifierActive());
        entity.setExhaustFanActive(dto.isExhaustFanActive());
        return entity;
    }
}
