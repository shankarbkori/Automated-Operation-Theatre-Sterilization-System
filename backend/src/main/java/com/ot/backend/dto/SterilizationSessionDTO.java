package com.ot.backend.dto;

import lombok.Data;

@Data
public class SterilizationSessionDTO {

    private double predictedUvTime;
    private double predictedFogTime;
    private double predictedFanTime;

    private double actualUvTime;
    private double actualFogTime;
    private double actualFanTime;

    private String status;

}