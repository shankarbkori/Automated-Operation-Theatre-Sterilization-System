package com.ot.backend.entity;

/**
 * All possible states in the OT sterilization cycle.
 * The state machine transitions through these states sequentially.
 * ERROR and PAUSED can occur at any point.
 */
public enum SterilizationState {

    /** Waiting for a sterilization cycle to be triggered. */
    IDLE,

    /** Initial checks — verifying device is online, session can start. */
    PREPARING,

    /** Verifying door is closed and no motion is detected. */
    SAFETY_CHECK,

    /** Sending sensor data to AI engine to get UV/fog/fan durations. */
    AI_ANALYSIS,

    /**
     * UV-C lamp is running. Safety-critical — any door/motion event halts
     * immediately.
     */
    UV_RUNNING,

    /** UV phase completed successfully. */
    UV_COMPLETED,

    /** Disinfectant fogger is running. */
    FOGGING_RUNNING,

    /** Fogging phase completed. */
    FOGGING_COMPLETED,

    /** Exhaust fan is running to purge the room. */
    EXHAUST_RUNNING,

    /** Fan phase completed. Room is clear. */
    EXHAUST_COMPLETED,

    /** Generating the sterilization report. */
    REPORT_GENERATION,

    /** Cycle finished successfully. */
    COMPLETED,

    /**
     * Cycle was paused due to a safety event. Waiting for admin approval to resume.
     */
    PAUSED,

    /** Cycle was aborted due to an unrecoverable error or safety override. */
    ERROR
}
