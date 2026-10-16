package com.ot.backend.service;

import com.ot.backend.entity.AIRecommendation;
import java.util.List;

public interface AIService {

    AIRecommendation saveRecommendation(AIRecommendation recommendation);

    List<AIRecommendation> getRecommendations();

    void clearRecommendations();

}