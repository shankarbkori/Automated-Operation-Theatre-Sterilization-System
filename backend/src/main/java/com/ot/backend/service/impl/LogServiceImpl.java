package com.ot.backend.service.impl;

import com.ot.backend.entity.SystemLog;
import com.ot.backend.repository.LogRepository;
import com.ot.backend.service.LogService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class LogServiceImpl implements LogService {

    private final LogRepository logRepository;

    public LogServiceImpl(LogRepository logRepository) {
        this.logRepository = logRepository;
    }

    @Override
    public SystemLog saveLog(SystemLog log) {
        return logRepository.save(log);
    }

    @Override
    public List<SystemLog> getAllLogs() {
        return logRepository.findAll();
    }

    @Override
    public List<SystemLog> getLatestLogs(int count) {
        // Find top 'count' logs ordered by date (null-safe to avoid NPE)
        return logRepository.findAll().stream()
                .sorted(java.util.Comparator.comparing(SystemLog::getCreatedAt,
                        java.util.Comparator.nullsLast(java.util.Comparator.reverseOrder())))
                .limit(count)
                .toList();
    }
}
