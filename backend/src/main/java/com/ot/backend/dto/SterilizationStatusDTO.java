package com.ot.backend.dto;

import com.ot.backend.entity.SterilizationState;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * DTO returned by GET /api/sterilization/status.
 * Contains everything the dashboard needs to render the current cycle state.
 */
@Data
public class SterilizationStatusDTO {

    private Long sessionId;
    private SterilizationState currentState;
    private String currentStateLabel;
    private int progressPercent;

    // Timestamps
    private LocalDateTime cycleStartedAt;
    private LocalDateTime uvStartedAt;
    private LocalDateTime fogStartedAt;
    private LocalDateTime fanStartedAt;
    private LocalDateTime completedAt;

    // AI-predicted durations (seconds)
    private int aiUvSeconds;
    private int aiFogSeconds;
    private int aiFanSeconds;
    private int totalPredictedSeconds;
    private double aiRiskLevel;
    private String aiRiskLabel;
    private boolean aiAvailable;

    // Relay states
    private boolean uvActive;
    private boolean fogActive;
    private boolean fanActive;
    private boolean alarmActive;

    // Time tracking (seconds) — computed live from timestamps
    private long totalElapsedSeconds;
    private long totalRemainingSeconds;
    private long currentPhaseElapsedSeconds;
    private long currentPhaseRemainingSeconds;
    private String currentPhaseName;

    // Safety
    private int interruptionCount;
    private String lastSafetyEvent;
    private String errorMessage;

    // Actual durations (recorded after each phase completes)
    private double actualUvSeconds;
    private double actualFogSeconds;
    private double actualFanSeconds;

}
