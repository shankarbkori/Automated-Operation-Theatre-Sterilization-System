package com.ot.backend.controller;

import com.ot.backend.entity.SystemLog;
import com.ot.backend.service.LogService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/logs")
@CrossOrigin(origins = { "http://localhost:8081", "http://127.0.0.1:8081" })
public class LogController {

    private final LogService logService;

    public LogController(LogService logService) {
        this.logService = logService;
    }

    @PostMapping
    public SystemLog save(@RequestBody SystemLog log) {
        return logService.saveLog(log);
    }

    @GetMapping
    public List<SystemLog> getAll() {
        return logService.getAllLogs();
    }

    @GetMapping("/latest")
    public List<SystemLog> getLatest(@RequestParam(defaultValue = "10") int count) {
        return logService.getLatestLogs(count);
    }

}
