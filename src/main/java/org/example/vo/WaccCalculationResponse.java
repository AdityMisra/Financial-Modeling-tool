package org.example.vo;

import java.util.List;

public class WaccCalculationResponse {
    private String status;
    private String message;
    private String filePath;
    private List<String> results; // Updated to handle List<String> for CSV content

    // Constructors, Getters, and Setters
    public WaccCalculationResponse(String status, String message, String filePath, List<String> results) {
        this.status = status;
        this.message = message;
        this.filePath = filePath;
        this.results = results;
    }

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

    public List<String> getResults() {
        return results;
    }

    public void setResults(List<String> results) {
        this.results = results;
    }
}