package org.example.vo;

public class MetricsCalculationRequest {
    private String cik;
    private Integer fromYear;
    private Integer toYear;

    // Default constructor
    public MetricsCalculationRequest() {}

    // Constructor with fields
    public MetricsCalculationRequest(String cik, Integer fromYear, Integer toYear) {
        this.cik = cik;
        this.fromYear = fromYear;
        this.toYear = toYear;
    }

    // Getters and setters
    public String getCik() {
        return cik;
    }

    public void setCik(String cik) {
        this.cik = cik;
    }

    public Integer getFromYear() {
        return fromYear;
    }

    public void setFromYear(Integer fromYear) {
        this.fromYear = fromYear;
    }

    public Integer getToYear() {
        return toYear;
    }

    public void setToYear(Integer toYear) {
        this.toYear = toYear;
    }
}