package org.example.vo;

import java.util.Map;

public class ThreeStatementModelResponse {
    private String status;
    private String message;
    private String filePath;
    private Map<String, Double> metrics;

    // Constructors
    public ThreeStatementModelResponse() {}

    public ThreeStatementModelResponse(String status, String message, String filePath, Map<String, Double> metrics) {
        this.status = status;
        this.message = message;
        this.filePath = filePath;
        this.metrics = metrics;
    }

    // Getters and Setters
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

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public Map<String, Double> getMetrics() {
        return metrics;
    }

    public void setMetrics(Map<String, Double> metrics) {
        this.metrics = metrics;
    }
}