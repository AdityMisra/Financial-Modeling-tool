package org.example.vo;

import java.util.List;
import java.util.Map;

public class extractTableRequest {
    private List<String> ciks;  // Changed from Map<String, String> companies
    private List<Integer> years;

    // Default constructor
    public extractTableRequest() {}

    // Constructor
    public extractTableRequest(List<String> ciks, List<Integer> years) {
        this.ciks = ciks;
        this.years = years;
    }

    // Getters and setters
    public List<String> getCiks() {
        return ciks;
    }

    public void setCiks(List<String> ciks) {
        this.ciks = ciks;
    }

    public List<Integer> getYears() {
        return years;
    }

    public void setYears(List<Integer> years) {
        this.years = years;
    }
}