package com.ot.backend.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ot.backend.dto.SensorDataDTO;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class PredictionEngine {

    private final ObjectMapper mapper = new ObjectMapper();

    public AnomalyResult analyze(SensorDataDTO data) {
        try {
            String motionVal = data.isMotionDetected() ? "1" : "0";
            String doorVal = data.isDoorClosed() ? "1" : "0";

            // The python model predict script is located in ../ai/predict.py relative to
            // the backend execution context.
            // But we specify "ai/predict.py" which is relative to the project root CWD,
            // which is d:\Automated-OT-Sterilization-System!
            // Wait, what is the working directory of the Spring Boot application when run?
            // When Spring Boot runs in development, the CWD is usually the backend folder
            // (d:\Automated-OT-Sterilization-System\backend)
            // or the project root. To be extremely robust, we can locate python scripts by
            // checking parent path or using relative paths.
            // Let's check where the python predict.py file exists. If it exists in
            // "../ai/predict.py", we use that.
            // Let's search inside Java if we can use an absolute path or check if file
            // exists.
            java.io.File scriptFile = new java.io.File("ai/predict.py");
            if (!scriptFile.exists()) {
                scriptFile = new java.io.File("../ai/predict.py");
            }

            String scriptPath = scriptFile.getAbsolutePath();

            ProcessBuilder pb = new ProcessBuilder(
                    "python",
                    scriptPath,
                    String.valueOf(data.getTemperature()),
                    String.valueOf(data.getHumidity()),
                    String.valueOf(data.getPm25()),
                    String.valueOf(data.getPm10()),
                    String.valueOf(data.getCo2()),
                    String.valueOf(data.getTvoc()),
                    String.valueOf(data.getCo()),
                    String.valueOf(data.getLight()),
                    motionVal,
                    doorVal);

            // Set process working directory to the parent of scriptPath, so joblib can find
            // anomaly_model.joblib in its own ai/ folder!
            pb.directory(scriptFile.getParentFile());
            pb.redirectErrorStream(true);

            Process process = pb.start();

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            StringBuilder builder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line).append("\n");
            }

            boolean finished = process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return new AnomalyResult(false, List.of("AI predict timed out after 10s"), "TIMEOUT");
            }

            String jsonOutput = builder.toString().trim();
            if (jsonOutput.startsWith("{")) {
                Map<String, Object> map = mapper.readValue(jsonOutput, Map.class);
                boolean anomaly = (boolean) map.getOrDefault("anomaly", false);
                List<String> recs = (List<String>) map.getOrDefault("recommendations", new ArrayList<>());
                return new AnomalyResult(anomaly, recs, jsonOutput);
            } else {
                System.err.println("PredictionEngine Non-JSON Output: " + jsonOutput);
                return new AnomalyResult(false, List.of("AI predict syntax output parse failed. Got: " + jsonOutput),
                        jsonOutput);
            }
        } catch (Exception e) {
            e.printStackTrace();
            return new AnomalyResult(false, List.of("AI engine process execution error: " + e.getMessage()),
                    e.getMessage());
        }
    }

    public static class AnomalyResult {
        private final boolean anomaly;
        private final List<String> recommendations;
        private final String rawOutput;

        public AnomalyResult(boolean anomaly, List<String> recommendations, String rawOutput) {
            this.anomaly = anomaly;
            this.recommendations = recommendations;
            this.rawOutput = rawOutput;
        }

        public boolean isAnomaly() {
            return anomaly;
        }

        public List<String> getRecommendations() {
            return recommendations;
        }

        public String getRawOutput() {
            return rawOutput;
        }
    }
}
