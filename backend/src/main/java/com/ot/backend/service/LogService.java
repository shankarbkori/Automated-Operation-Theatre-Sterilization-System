package com.ot.backend.service;

import com.ot.backend.entity.SystemLog;
import java.util.List;

public interface LogService {
    SystemLog saveLog(SystemLog log);

    List<SystemLog> getAllLogs();

    List<SystemLog> getLatestLogs(int count);
}
