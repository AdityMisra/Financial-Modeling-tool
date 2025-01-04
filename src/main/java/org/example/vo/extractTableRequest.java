package org.example.vo;

import java.util.List;
import java.util.Map;

public class extractTableRequest {
    private Map<String, String> companies;
    private List<Integer> years;

    // Getters and setters
    public Map<String, String> getCompanies() {
        return companies;
    }

    public void setCompanies(Map<String, String> companies) {
        this.companies = companies;
    }

    public List<Integer> getYears() {
        return years;
    }

    public void setYears(List<Integer> years) {
        this.years = years;
    }
}