package org.example.vo;

import java.util.List;
import java.util.Map;

public class WaccCalculationResponse {
    private String status;
    private String message;
    private String filePath;
    private List<Map<String, Object>> results;

    // Constructors
    public WaccCalculationResponse(String status, String message, String filePath, List<Map<String, Object>> results) {
        this.status = status;
        this.message = message;
        this.filePath = filePath;
        this.results = results;
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

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public List<Map<String, Object>> getResults() {
        return results;
    }

    public void setResults(List<Map<String, Object>> results) {
        this.results = results;
    }
}
