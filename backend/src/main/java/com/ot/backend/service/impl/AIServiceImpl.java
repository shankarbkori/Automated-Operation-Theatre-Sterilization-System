package com.ot.backend.service.impl;

import com.ot.backend.entity.AIRecommendation;
import com.ot.backend.repository.RecommendationRepository;
import com.ot.backend.service.AIService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AIServiceImpl implements AIService {

    private final RecommendationRepository repository;

    public AIServiceImpl(RecommendationRepository repository) {
        this.repository = repository;
    }

    @Override
    public AIRecommendation saveRecommendation(AIRecommendation recommendation) {
        return repository.save(recommendation);
    }

    @Override
    public List<AIRecommendation> getRecommendations() {
        return repository.findAll();
    }

    @Override
    public void clearRecommendations() {
        repository.deleteAll();
    }

}