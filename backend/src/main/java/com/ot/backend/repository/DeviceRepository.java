package com.ot.backend.repository;

import com.ot.backend.entity.DeviceStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DeviceRepository extends JpaRepository<DeviceStatus, Long> {

    Optional<DeviceStatus> findByDeviceName(String deviceName);

}