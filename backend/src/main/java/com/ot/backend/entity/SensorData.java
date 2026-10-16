package com.ot.backend.entity;

import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
public class SensorData extends BaseEntity {

    private double temperature;

    private double humidity;

    private double pm25;

    private double pm10;

    private double co2;

    private double tvoc;

    private double co;

    private double light;

    private String airQualityLabel;

    private boolean motionDetected;

    private boolean doorClosed;

    private String processState;

}