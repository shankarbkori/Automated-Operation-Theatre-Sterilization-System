package com.ot.backend.controller;

import com.ot.backend.dto.ApiResponse;
import com.ot.backend.entity.SterilizationSession;
import com.ot.backend.repository.SessionRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Session history endpoints consumed by history.html and
 * sterilization-control.html.
 *
 * GET /api/session — list all sessions (most recent first)
 * GET /api/session/{id}/status — single session status
 */
@RestController
@RequestMapping("/api/session")
@CrossOrigin(origins = { "http://localhost:8081", "http://127.0.0.1:8081" })
public class SessionController {

    private final SessionRepository sessionRepository;

    public SessionController(SessionRepository sessionRepository) {
        this.sessionRepository = sessionRepository;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<SterilizationSession>>> listSessions() {
        List<SterilizationSession> sessions = sessionRepository.findAll()
                .stream()
                .sorted((a, b) -> {
                    if (b.getCreatedAt() == null)
                        return -1;
                    if (a.getCreatedAt() == null)
                        return 1;
                    return b.getCreatedAt().compareTo(a.getCreatedAt());
                })
                .toList();
        return ResponseEntity.ok(new ApiResponse<>(true, "Sessions retrieved.", sessions));
    }

    @GetMapping("/{id}/status")
    public ResponseEntity<ApiResponse<SterilizationSession>> sessionStatus(@PathVariable Long id) {
        return sessionRepository.findById(id)
                .map(session -> ResponseEntity.ok(new ApiResponse<>(true, "Session found.", session)))
                .orElse(ResponseEntity.notFound().build());
    }
}
