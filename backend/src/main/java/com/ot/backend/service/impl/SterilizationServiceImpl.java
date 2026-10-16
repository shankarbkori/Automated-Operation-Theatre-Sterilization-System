package com.ot.backend.service.impl;

import com.ot.backend.entity.SterilizationSession;
import com.ot.backend.repository.SessionRepository;
import com.ot.backend.service.SterilizationService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SterilizationServiceImpl implements SterilizationService {

    private final SessionRepository repository;

    public SterilizationServiceImpl(SessionRepository repository) {
        this.repository = repository;
    }

    @Override
    public SterilizationSession saveSession(SterilizationSession session) {
        return repository.save(session);
    }

    @Override
    public List<SterilizationSession> getAllSessions() {
        return repository.findAll();
    }

}