package org.example.vo;

import java.util.Map;
import java.util.List;

public class MetricsCalculationResponse {
    private String status;
    private String message;
    private String metricsFilePath;
    private List<Map<String, Object>> metrics;

    // Default constructor
    public MetricsCalculationResponse() {}

    // Constructor with fields
    public MetricsCalculationResponse(String status, String message, String metricsFilePath, List<Map<String, Object>> metrics) {
        this.status = status;
        this.message = message;
        this.metricsFilePath = metricsFilePath;
        this.metrics = metrics;
    }

    // Getters and setters
    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getMetricsFilePath() {
        return metricsFilePath;
    }

    public void setMetricsFilePath(String metricsFilePath) {
        this.metricsFilePath = metricsFilePath;
    }

    public List<Map<String, Object>> getMetrics() {
        return metrics;
    }

    public void setMetrics(List<Map<String, Object>> metrics) {
        this.metrics = metrics;
    }
}
