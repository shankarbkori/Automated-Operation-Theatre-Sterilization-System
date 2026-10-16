package com.ot.backend.entity;

import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
public class SystemLog extends BaseEntity {

    private String level;

    private String message;

}