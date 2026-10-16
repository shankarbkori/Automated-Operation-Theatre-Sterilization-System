package com.ot.backend.service;

import com.ot.backend.entity.SterilizationSession;
import java.util.List;

public interface SterilizationService {

    SterilizationSession saveSession(SterilizationSession session);

    List<SterilizationSession> getAllSessions();

}