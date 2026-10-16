package com.ot.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

/**
 * Persists the full state of a sterilization cycle.
 * On server restart, the last INCOMPLETE session is resumed from its stored
 * state —
 * the cycle NEVER restarts from IDLE.
 */
@Getter
@Setter
@Entity
@Table(name = "sterilization_session")
public class SterilizationSession extends BaseEntity {

    // ── State Machine ─────────────────────────────────────────────────────────

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SterilizationState currentState = SterilizationState.IDLE;

    // ── Cycle Timestamps ──────────────────────────────────────────────────────

    private LocalDateTime cycleStartedAt;
    private LocalDateTime uvStartedAt;
    private LocalDateTime fogStartedAt;
    private LocalDateTime fanStartedAt;
    private LocalDateTime completedAt;

    // ── AI Prediction Results ─────────────────────────────────────────────────

    /** Risk level: 0.0 (clean) to 1.0 (critical) */
    private double aiRiskLevel;

    /** AI-predicted UV duration in seconds */
    private int aiUvSeconds;

    /** AI-predicted fogging duration in seconds */
    private int aiFogSeconds;

    /** AI-predicted exhaust fan duration in seconds */
    private int aiFanSeconds;

    /** Human-readable risk label: CLEAN / MODERATE / HIGH / CRITICAL */
    private String aiRiskLabel;

    // ── Actual Relay Times (recorded after each phase completes) ──────────────

    private double actualUvTime;
    private double actualFogTime;
    private double actualFanTime;

    // ── Safety & Error Tracking ───────────────────────────────────────────────

    /** Log of all safety events during this session (newline-delimited). */
    @Column(columnDefinition = "TEXT")
    private String safetyEventLog;

    /** Error message if the session ended in ERROR state. */
    private String errorMessage;

    /**
     * State the machine was in when it was PAUSED (so it can resume from there).
     */
    @Enumerated(EnumType.STRING)
    private SterilizationState pausedAtState;

    // ── Identity ──────────────────────────────────────────────────────────────

    /** Username of the operator who triggered the cycle. */
    private String operatorUsername;

    /** Whether a complete report has been generated for this session. */
    private boolean reportGenerated = false;

    /** Number of safety interruptions during this cycle. */
    private int interruptionCount = 0;

    // ── Initial Environment Snapshot (captured before cycle starts) ───────

    private double initialTemp;
    private double initialHumidity;
    private double initialCo2;

    // ── Final Environment Snapshot (captured after cycle completes) ───────

    private double finalTemp;
    private double finalHumidity;
    private double finalCo2;

    /** Whether AI prediction was used (true) or fallback defaults (false). */
    private boolean aiPredictionUsed = false;

}