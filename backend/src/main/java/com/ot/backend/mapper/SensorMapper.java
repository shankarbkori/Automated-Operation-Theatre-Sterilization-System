package com.ot.backend.mapper;

import com.ot.backend.dto.SensorDataDTO;
import com.ot.backend.entity.SensorData;

public class SensorMapper {

    public static SensorData toEntity(SensorDataDTO dto) {
        if (dto == null)
            return null;

        SensorData entity = new SensorData();

        entity.setTemperature(dto.getTemperature());
        entity.setHumidity(dto.getHumidity());
        entity.setPm25(dto.getPm25());
        entity.setPm10(dto.getPm10());
        entity.setCo2(dto.getCo2());
        entity.setTvoc(dto.getTvoc());
        entity.setCo(dto.getCo());
        entity.setLight(dto.getLight());
        entity.setAirQualityLabel(dto.getAirQualityLabel());
        entity.setMotionDetected(dto.isMotionDetected());
        entity.setDoorClosed(dto.isDoorClosed());
        entity.setProcessState(dto.getProcessState());

        return entity;
    }

    public static SensorDataDTO toDto(SensorData entity) {
        if (entity == null)
            return null;

        SensorDataDTO dto = new SensorDataDTO();

        dto.setTemperature(entity.getTemperature());
        dto.setHumidity(entity.getHumidity());
        dto.setPm25(entity.getPm25());
        dto.setPm10(entity.getPm10());
        dto.setCo2(entity.getCo2());
        dto.setTvoc(entity.getTvoc());
        dto.setCo(entity.getCo());
        dto.setLight(entity.getLight());
        dto.setAirQualityLabel(entity.getAirQualityLabel());
        dto.setMotionDetected(entity.isMotionDetected());
        dto.setDoorClosed(entity.isDoorClosed());
        dto.setProcessState(entity.getProcessState());
        dto.setCreatedAt(entity.getCreatedAt());

        return dto;
    }

}