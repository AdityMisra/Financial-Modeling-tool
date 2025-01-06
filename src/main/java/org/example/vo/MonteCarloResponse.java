package org.example.vo;

import java.util.List;
import java.util.Map;

public class MonteCarloResponse {
    private String status;
    private String message;
    private String simulationFilePath;
    private List<Map<String, Object>> simulationResults;

    // Constructors
    public MonteCarloResponse() {}

    public MonteCarloResponse(String status, String message, String simulationFilePath,
                              List<Map<String, Object>> simulationResults) {
        this.status = status;
        this.message = message;
        this.simulationFilePath = simulationFilePath;
        this.simulationResults = simulationResults;
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

    public String getSimulationFilePath() {
        return simulationFilePath;
    }

    public void setSimulationFilePath(String simulationFilePath) {
        this.simulationFilePath = simulationFilePath;
    }

    public List<Map<String, Object>> getSimulationResults() {
        return simulationResults;
    }

    public void setSimulationResults(List<Map<String, Object>> simulationResults) {
        this.simulationResults = simulationResults;
    }
}

