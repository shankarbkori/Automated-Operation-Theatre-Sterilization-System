package com.ot.backend.repository;

import com.ot.backend.entity.SensorData;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SensorRepository extends JpaRepository<SensorData, Long> {

    Optional<SensorData> findTopByOrderByCreatedAtDesc();

}