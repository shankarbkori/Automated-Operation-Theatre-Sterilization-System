package com.ot.backend.service.impl;

import com.ot.backend.entity.SensorData;
import com.ot.backend.repository.SensorRepository;
import com.ot.backend.service.SensorService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SensorServiceImpl implements SensorService {

    private final SensorRepository sensorRepository;
    private final com.ot.backend.ai.PredictionEngine predictionEngine;
    private final com.ot.backend.service.AIService aiService;

    public SensorServiceImpl(
            SensorRepository sensorRepository,
            com.ot.backend.ai.PredictionEngine predictionEngine,
            com.ot.backend.service.AIService aiService) {
        this.sensorRepository = sensorRepository;
        this.predictionEngine = predictionEngine;
        this.aiService = aiService;
    }

    @Override
    public SensorData saveSensorData(SensorData sensorData) {
        SensorData saved = sensorRepository.save(sensorData);
        try {
            com.ot.backend.dto.SensorDataDTO dto = com.ot.backend.mapper.SensorMapper.toDto(saved);
            com.ot.backend.ai.PredictionEngine.AnomalyResult result = predictionEngine.analyze(dto);

            // Only clear old advice if we have new recommendations to replace them
            if (!result.getRecommendations().isEmpty()) {
                aiService.clearRecommendations();
            }

            // Save new warnings in DB
            for (String recText : result.getRecommendations()) {
                com.ot.backend.entity.AIRecommendation rec = new com.ot.backend.entity.AIRecommendation();
                rec.setRecommendation(recText);
                rec.setConfidence(result.isAnomaly() ? 0.95 : 1.0);
                aiService.saveRecommendation(rec);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return saved;
    }

    @Override
    public List<SensorData> getAllSensorData() {
        return sensorRepository.findAll();
    }

    @Override
    public SensorData getLatestSensorData() {
        return sensorRepository.findTopByOrderByCreatedAtDesc().orElse(null);
    }

}