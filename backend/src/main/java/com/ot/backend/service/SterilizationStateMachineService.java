package com.ot.backend.service;

import com.ot.backend.dto.SterilizationStatusDTO;
import com.ot.backend.entity.SterilizationSession;

/**
 * Core state machine interface for the OT sterilization cycle.
 * All state transitions are persisted to the database immediately.
 */
public interface SterilizationStateMachineService {

    /**
     * Start a new sterilization cycle.
     * Fails if a cycle is already in progress.
     *
     * @param operatorUsername the user triggering the cycle
     * @return the created session with PREPARING state
     */
    SterilizationSession startCycle(String operatorUsername);

    /**
     * Abort the current cycle immediately.
     * Cuts all relays, triggers alarm, transitions to ERROR.
     *
     * @param reason human-readable reason for abort
     */
    void abortCycle(String reason);

    /**
     * Pause the current cycle manually.
     * Cuts all relays and sets the state to PAUSED.
     *
     * @param reason human-readable reason for pausing
     */
    void pauseCycle(String reason);

    /**
     * Resume a PAUSED cycle (after admin review of a safety event).
     * Resumes from the pausedAtState — never restarts from the beginning.
     */
    void resumeCycle();

    /**
     * Called on every sensor data packet.
     * Checks if a safety interrupt is needed during UV_RUNNING.
     *
     * @param motionDetected true if PIR sensor detects presence
     * @param doorClosed     true if reed switch shows door is closed
     */
    void onSensorUpdate(boolean motionDetected, boolean doorClosed);

    /**
     * Returns the current status of the sterilization system.
     * Safe to call at any time — returns IDLE if no active session.
     */
    SterilizationStatusDTO getStatus();

    /**
     * Called at application startup to resume any in-progress cycle.
     * If no incomplete session exists, no action is taken.
     */
    void resumeOnStartup();

}
