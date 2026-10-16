package com.ot.backend.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
public class User extends BaseEntity {

    private String fullname;
    private String username;
    private String email;
    private String password;

    @Enumerated(EnumType.STRING)
    private Role role;

    private String department;
    private String phone;
    private boolean active = true;

    private int failedAttempts;
    private LocalDateTime lockTime;
    private LocalDateTime lastLogin;

}