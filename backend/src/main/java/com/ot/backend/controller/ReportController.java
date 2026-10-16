package com.ot.backend.controller;

import com.lowagie.text.Document;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.ot.backend.entity.SensorData;
import com.ot.backend.service.SensorService;
import com.ot.backend.entity.SterilizationSession;
import com.ot.backend.service.SterilizationService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/reports")
public class ReportController {

    @Autowired
    private SensorService sensorService;

    @Autowired
    private SterilizationService sterilizationService;

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        List<SterilizationSession> sessions = sterilizationService.getAllSessions();

        java.time.LocalDate today = java.time.LocalDate.now();

        long successful = 0;
        long failed = 0;
        long total = 0;
        long totalDurationSeconds = 0;
        int completedWithDurationCount = 0;

        for (SterilizationSession s : sessions) {
            if (s.getCreatedAt() != null && s.getCreatedAt().toLocalDate().equals(today)) {
                total++;
                String stat = s.getCurrentState() != null ? s.getCurrentState().name() : "";
                if (stat.equals("COMPLETED")) {
                    successful++;
                    // Use actual stage durations (seconds) stored by the state machine
                    double actualTotal = s.getActualUvTime() + s.getActualFogTime() + s.getActualFanTime();
                    if (actualTotal > 0) {
                        totalDurationSeconds += (long) actualTotal;
                        completedWithDurationCount++;
                    } else if (s.getCycleStartedAt() != null && s.getCompletedAt() != null) {
                        // Fallback: compute from cycle timestamps
                        long diff = java.time.Duration.between(s.getCycleStartedAt(), s.getCompletedAt()).toSeconds();
                        totalDurationSeconds += diff;
                        completedWithDurationCount++;
                    }
                } else if (stat.equals("ERROR") || stat.equals("PAUSED")) {
                    failed++;
                }
            }
        }

        long averageDurationSeconds = 0;
        String avgDuration = "-- min";
        if (completedWithDurationCount > 0) {
            averageDurationSeconds = totalDurationSeconds / completedWithDurationCount;
            long mins = averageDurationSeconds / 60;
            long secs = averageDurationSeconds % 60;
            avgDuration = mins > 0 ? mins + "m " + secs + "s" : secs + "s";
        }

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalCycles", total);
        stats.put("successfulCycles", successful);
        stats.put("failedCycles", failed);
        stats.put("averageDuration", avgDuration);
        stats.put("totalDurationSeconds", totalDurationSeconds);
        stats.put("averageDurationSeconds", averageDurationSeconds);

        return ResponseEntity.ok(stats);
    }

    @GetMapping("/download")
    public ResponseEntity<byte[]> downloadReport(@RequestParam String type,
            @RequestParam(defaultValue = "daily") String range) {

        if ("csv".equalsIgnoreCase(type) && "history".equalsIgnoreCase(range)) {
            // Export History (SterilizationSessions)
            List<SterilizationSession> sessions = sterilizationService.getAllSessions();
            StringBuilder csv = new StringBuilder();
            csv.append("Sl No,Date,Start Time,End Time,Duration (Secs),Status,Operator\n");

            java.time.format.DateTimeFormatter dateFormatter = java.time.format.DateTimeFormatter
                    .ofPattern("yyyy-MM-dd");
            java.time.format.DateTimeFormatter timeFormatter = java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss");

            for (int i = 0; i < sessions.size(); i++) {
                SterilizationSession session = sessions.get(i);

                String date = session.getCycleStartedAt() != null ? session.getCycleStartedAt().format(dateFormatter)
                        : (session.getCreatedAt() != null ? session.getCreatedAt().format(dateFormatter) : "--");
                String startTime = session.getCycleStartedAt() != null
                        ? session.getCycleStartedAt().format(timeFormatter)
                        : (session.getCreatedAt() != null ? session.getCreatedAt().format(timeFormatter) : "--");
                String endTime = session.getCompletedAt() != null ? session.getCompletedAt().format(timeFormatter)
                        : "--";

                String duration = "--";
                double actualTotal = session.getActualUvTime() + session.getActualFogTime()
                        + session.getActualFanTime();
                if (actualTotal > 0) {
                    duration = String.format("%.0f", actualTotal) + "s";
                } else if (session.getCycleStartedAt() != null && session.getCompletedAt() != null) {
                    long diff = java.time.Duration.between(session.getCycleStartedAt(), session.getCompletedAt())
                            .toSeconds();
                    duration = diff + "s";
                }

                String status = session.getCurrentState() != null ? session.getCurrentState().name() : "ERROR";

                csv.append(i + 1).append(",")
                        .append(date).append(",")
                        .append(startTime).append(",")
                        .append(endTime).append(",")
                        .append(duration).append(",")
                        .append(status).append(",")
                        .append("System\n");
            }
            byte[] bytes = csv.toString().getBytes();
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=AOTS_Sterilization_History.csv")
                    .contentType(MediaType.parseMediaType("text/csv"))
                    .body(bytes);
        }

        // Default export (sensor data)
        List<SensorData> data = sensorService.getAllSensorData();

        if ("csv".equalsIgnoreCase(type)) {
            StringBuilder csv = new StringBuilder();
            csv.append("ID,Temperature(C),Humidity(%),CO2(PPM),State,Timestamp\n");
            for (SensorData row : data) {
                csv.append(row.getId()).append(",")
                        .append(row.getTemperature()).append(",")
                        .append(row.getHumidity()).append(",")
                        .append(row.getCo2()).append(",")
                        .append(row.getProcessState() != null ? row.getProcessState() : "UNKNOWN").append(",")
                        .append(row.getCreatedAt()).append("\n");
            }
            byte[] bytes = csv.toString().getBytes();
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=AOTS_Report_" + range + ".csv")
                    .contentType(MediaType.parseMediaType("text/csv"))
                    .body(bytes);

        } else if ("pdf".equalsIgnoreCase(type)) {
            try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                Document document = new Document();
                PdfWriter.getInstance(document, out);
                document.open();
                document.add(
                        new Paragraph("AOTS Automated Sterilization System - " + range.toUpperCase() + " Tracker"));

                List<SterilizationSession> sessions = sterilizationService.getAllSessions();
                java.time.format.DateTimeFormatter dateFormatter = java.time.format.DateTimeFormatter
                        .ofPattern("yyyy-MM-dd");
                java.time.format.DateTimeFormatter timeFormatter = java.time.format.DateTimeFormatter
                        .ofPattern("HH:mm:ss");

                int sessionCnt = sessions.size();
                document.add(
                        new Paragraph("Compiled successfully. Total Recorded Cycles: " + sessionCnt + " sessions"));
                document.add(new Paragraph(" "));

                PdfPTable table = new PdfPTable(5); // 5 columns
                table.setWidthPercentage(100);

                // Headers
                Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD);
                String[] headers = { "Sl No", "Date", "Start Time", "Duration", "Status" };
                for (String header : headers) {
                    PdfPCell cell = new PdfPCell(new Phrase(header, headerFont));
                    cell.setPadding(5);
                    table.addCell(cell);
                }

                // Data
                for (int i = 0; i < sessions.size(); i++) {
                    SterilizationSession s = sessions.get(i);
                    String date = s.getCycleStartedAt() != null ? s.getCycleStartedAt().format(dateFormatter)
                            : (s.getCreatedAt() != null ? s.getCreatedAt().format(dateFormatter) : "--");
                    String startTime = s.getCycleStartedAt() != null ? s.getCycleStartedAt().format(timeFormatter)
                            : (s.getCreatedAt() != null ? s.getCreatedAt().format(timeFormatter) : "--");

                    String duration = "--";
                    double actualTotal = s.getActualUvTime() + s.getActualFogTime() + s.getActualFanTime();
                    if (actualTotal > 0) {
                        long totalSecs = (long) actualTotal;
                        duration = (totalSecs / 60) + "m " + (totalSecs % 60) + "s";
                    } else if (s.getCycleStartedAt() != null && s.getCompletedAt() != null) {
                        long diff = java.time.Duration.between(s.getCycleStartedAt(), s.getCompletedAt()).toSeconds();
                        duration = (diff / 60) + "m " + (diff % 60) + "s";
                    }

                    String statusStr = s.getCurrentState() != null ? s.getCurrentState().name() : "ERROR";

                    table.addCell(String.valueOf(i + 1));
                    table.addCell(date);
                    table.addCell(startTime);
                    table.addCell(duration);
                    table.addCell(statusStr);
                }

                document.add(table);

                document.close();
                return ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=AOTS_Report_" + range + ".pdf")
                        .contentType(MediaType.APPLICATION_PDF)
                        .body(out.toByteArray());
            } catch (Exception e) {
                return ResponseEntity.internalServerError().build();
            }
        }
        return ResponseEntity.badRequest().build();
    }
}
