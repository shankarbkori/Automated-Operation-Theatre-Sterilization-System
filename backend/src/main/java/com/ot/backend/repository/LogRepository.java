package com.ot.backend.repository;

import com.ot.backend.entity.SystemLog;
import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.domain.Pageable;
import java.util.List;

public interface LogRepository extends JpaRepository<SystemLog, Long> {

    List<SystemLog> findAllByOrderByCreatedAtDesc(Pageable pageable);

}