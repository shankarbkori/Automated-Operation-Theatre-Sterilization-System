package com.ot.backend.service;

import com.ot.backend.entity.SensorData;
import java.util.List;

public interface SensorService {

    SensorData saveSensorData(SensorData sensorData);

    List<SensorData> getAllSensorData();

    SensorData getLatestSensorData();

}