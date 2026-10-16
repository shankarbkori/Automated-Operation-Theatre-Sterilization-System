package com.ot.backend.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class SensorDataDTO {

    @Min(-10)
    @Max(100)
    private double temperature;

    @Min(0)
    @Max(100)
    private double humidity;

    @Min(0)
    private double pm25;

    @Min(0)
    private double pm10;

    @Min(0)
    private double co2;

    @Min(0)
    private double tvoc;

    @Min(0)
    private double co;

    @Min(0)
    private double light;

    private String airQualityLabel;

    private boolean motionDetected;

    private boolean doorClosed;

    private String processState;

    private LocalDateTime createdAt;

    /** Server-computed: true if ESP32 sent data within the last 10 seconds. */
    private boolean deviceOnline;

}
