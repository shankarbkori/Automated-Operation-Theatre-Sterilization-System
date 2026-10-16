package com.ot.backend.controller;

import com.ot.backend.dto.ApiResponse;
import com.ot.backend.dto.SterilizationStatusDTO;
import com.ot.backend.entity.SterilizationSession;
import com.ot.backend.service.SterilizationStateMachineService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST API for the sterilization state machine.
 *
 * Endpoints:
 * POST /api/sterilization/start — Start a new cycle
 * POST /api/sterilization/abort — Emergency abort
 * POST /api/sterilization/resume — Resume a PAUSED cycle
 * GET /api/sterilization/status — Current state (polled by dashboard every 2s)
 */
@RestController
@RequestMapping("/api/sterilization")
@CrossOrigin(origins = { "http://localhost:8081", "http://127.0.0.1:8081" })
public class SterilizationController {

    private final SterilizationStateMachineService stateMachine;

    public SterilizationController(SterilizationStateMachineService stateMachine) {
        this.stateMachine = stateMachine;
    }

    /**
     * Start a new sterilization cycle.
     * The cycle runs asynchronously — this call returns immediately.
     */
    @PostMapping("/start")
    public ResponseEntity<ApiResponse<SterilizationSession>> start(Authentication auth) {
        String username = (auth != null) ? auth.getName() : "system";
        try {
            SterilizationSession session = stateMachine.startCycle(username);
            return ResponseEntity.ok(new ApiResponse<>(true, "Sterilization cycle started.", session));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest()
                    .body(new ApiResponse<>(false, e.getMessage(), null));
        }
    }

    /**
     * Emergency abort — cuts all relays immediately and transitions to ERROR.
     */
    @PostMapping("/abort")
    public ResponseEntity<ApiResponse<Void>> abort(@RequestBody(required = false) Map<String, String> body) {
        String reason = (body != null) ? body.getOrDefault("reason", "Manual abort by operator") : "Manual abort";
        stateMachine.abortCycle(reason);
        return ResponseEntity.ok(new ApiResponse<>(true, "Cycle aborted. All relays OFF.", null));
    }

    /**
     * Pause the current cycle.
     * Cuts all relays and sets state to PAUSED.
     */
    @PostMapping("/pause")
    public ResponseEntity<ApiResponse<Void>> pause(@RequestBody(required = false) Map<String, String> body) {
        String reason = (body != null) ? body.getOrDefault("reason", "Manual pause by operator") : "Manual pause";
        stateMachine.pauseCycle(reason);
        return ResponseEntity.ok(new ApiResponse<>(true, "Cycle paused. All relays OFF.", null));
    }

    /**
     * Resume a cycle that was PAUSED due to a safety event.
     * Requires admin approval (ROLE_ADMIN or ROLE_SUPER_ADMIN).
     */
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    @PostMapping("/resume")
    public ResponseEntity<ApiResponse<Void>> resume() {
        try {
            stateMachine.resumeCycle();
            return ResponseEntity.ok(new ApiResponse<>(true, "Cycle resumed.", null));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest()
                    .body(new ApiResponse<>(false, e.getMessage(), null));
        }
    }

    /**
     * Current sterilization state — polled by the dashboard every 2 seconds.
     * Returns IDLE if no active cycle.
     */
    @GetMapping("/status")
    public ResponseEntity<ApiResponse<SterilizationStatusDTO>> status() {
        SterilizationStatusDTO dto = stateMachine.getStatus();
        return ResponseEntity.ok(new ApiResponse<>(true, "Status retrieved.", dto));
    }
}