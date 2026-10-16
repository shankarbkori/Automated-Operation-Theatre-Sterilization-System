package com.ot.backend.repository;

import com.ot.backend.entity.SterilizationSession;
import com.ot.backend.entity.SterilizationState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface SessionRepository extends JpaRepository<SterilizationSession, Long> {

    /**
     * Find the most recently active (non-IDLE, non-COMPLETED, non-ERROR) session.
     * Used on server restart to resume an in-progress cycle.
     */
    @Query("SELECT s FROM SterilizationSession s WHERE s.currentState NOT IN " +
            "('IDLE', 'COMPLETED', 'ERROR') ORDER BY s.updatedAt DESC")
    List<SterilizationSession> findActiveSession();

    /** Returns the latest session regardless of state — used for reports/status. */
    Optional<SterilizationSession> findTopByOrderByCreatedAtDesc();

    /** Find all completed sessions for report generation. */
    List<SterilizationSession> findByCurrentState(SterilizationState state);

}