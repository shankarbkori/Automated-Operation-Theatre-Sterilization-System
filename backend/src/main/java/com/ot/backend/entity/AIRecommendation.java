package com.ot.backend.entity;

import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
public class AIRecommendation extends BaseEntity {

    private String recommendation;

    private double confidence;

}