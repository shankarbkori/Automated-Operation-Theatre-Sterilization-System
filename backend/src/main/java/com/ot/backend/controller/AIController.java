package com.ot.backend.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import com.ot.backend.entity.AIRecommendation;
import com.ot.backend.service.AIService;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/ai")
@CrossOrigin(origins = { "http://localhost:8081", "http://127.0.0.1:8081" })
@Slf4j
public class AIController {

    private static final String PYTHON_PLAN_URL = "http://localhost:5000/api/ai/sterilization-plan";
    private static final String PYTHON_PREDICT_URL = "http://localhost:5000/api/ai/predict";
    private static final int DEFAULT_UV_SECONDS = 45;
    private static final int DEFAULT_FOG_SECONDS = 30;
    private static final int DEFAULT_EXHAUST_SECONDS = 25;

    private final AIService service;
    private final com.ot.backend.ai.PredictionEngine predictionEngine;
    private final RestTemplate restTemplate = new RestTemplate();

    public AIController(AIService service, com.ot.backend.ai.PredictionEngine predictionEngine) {
        this.service = service;
        this.predictionEngine = predictionEngine;
    }

    @PostMapping
    public AIRecommendation save(@RequestBody AIRecommendation recommendation) {
        return service.saveRecommendation(recommendation);
    }

    @GetMapping
    public List<AIRecommendation> all() {
        return service.getRecommendations();
    }

    @GetMapping("/test-predict")
    public com.ot.backend.ai.PredictionEngine.AnomalyResult testPredict() {
        com.ot.backend.dto.SensorDataDTO dto = new com.ot.backend.dto.SensorDataDTO();
        dto.setTemperature(20.0);
        dto.setHumidity(50.0);
        dto.setPm25(10.0);
        dto.setPm10(20.0);
        dto.setCo2(500.0);
        dto.setTvoc(100.0);
        dto.setCo(0.5);
        dto.setLight(450.0);
        dto.setMotionDetected(false);
        dto.setDoorClosed(true);
        return predictionEngine.analyze(dto);
    }

    /**
     * Proxy for Python AI predict endpoint.
     * The browser calls /api/ai/predict on the Java backend (port 8081),
     * and this method forwards the request to the Python ML server (port 5000).
     */
    @SuppressWarnings("unchecked")
    @GetMapping("/predict")
    public Map<String, Object> predict() {
        try {
            Map<String, Object> response = restTemplate.getForObject(PYTHON_PREDICT_URL, Map.class);
            if (response != null) {
                return response;
            }
        } catch (Exception e) {
            log.warn("Python AI predict call failed: {}", e.getMessage());
        }
        // Return safe defaults if Python is down
        Map<String, Object> fallback = new HashMap<>();
        fallback.put("success", true);
        Map<String, Object> data = new HashMap<>();
        data.put("estimatedSterilizationTimeMinutes", 0);
        data.put("unsafeAirQuality", false);
        data.put("abnormalSensorBehavior", false);
        data.put("anomalyScore", 1);
        fallback.put("data", data);
        return fallback;
    }

    @PostMapping("/sterilization-plan")
    public Map<String, Object> sterilizationPlan(@RequestBody(required = false) Map<String, Object> payload) {
        Map<String, Object> fallback = new HashMap<>();
        fallback.put("success", true);
        fallback.put("uvSeconds", DEFAULT_UV_SECONDS);
        fallback.put("foggingSeconds", DEFAULT_FOG_SECONDS);
        fallback.put("exhaustSeconds", DEFAULT_EXHAUST_SECONDS);
        fallback.put("totalSeconds", DEFAULT_UV_SECONDS + DEFAULT_FOG_SECONDS + DEFAULT_EXHAUST_SECONDS);
        fallback.put("aiAvailable", false);
        fallback.put("notes", "Backend fallback plan");

        try {
            Map<String, Object> body = payload != null ? payload : Map.of();
            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
            org.springframework.http.HttpEntity<Map<String, Object>> requestEntity = new org.springframework.http.HttpEntity<>(
                    body, headers);

            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.postForObject(PYTHON_PLAN_URL, requestEntity, Map.class);
            if (response == null) {
                return fallback;
            }

            // Check if Python service reported success
            Boolean pythonSuccess = (Boolean) response.get("success");
            if (Boolean.FALSE.equals(pythonSuccess)) {
                log.warn("Python AI service returned error: {}", response.get("notes"));
                return fallback;
            }

            int uv = toInt(response.get("uvSeconds"), DEFAULT_UV_SECONDS);
            int fog = toInt(response.get("foggingSeconds"), DEFAULT_FOG_SECONDS);
            int exh = toInt(response.get("exhaustSeconds"), DEFAULT_EXHAUST_SECONDS);
            int total = toInt(response.get("totalSeconds"), uv + fog + exh);
            boolean aiAvail = Boolean.TRUE.equals(response.get("aiAvailable"));

            Map<String, Object> out = new HashMap<>();
            out.put("success", true);
            out.put("uvSeconds", uv);
            out.put("foggingSeconds", fog);
            out.put("exhaustSeconds", exh);
            out.put("totalSeconds", total);
            out.put("aiAvailable", aiAvail);
            out.put("notes", String.valueOf(response.getOrDefault("notes", "AI proxy plan")));
            return out;
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private int toInt(Object value, int fallback) {
        if (value == null) {
            return fallback;
        }
        if (value instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (Exception e) {
            return fallback;
        }
    }

}