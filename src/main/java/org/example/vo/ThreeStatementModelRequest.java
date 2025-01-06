package org.example.vo;

public class ThreeStatementModelRequest {
    private String cik;
    private Integer year;

    // Constructors
    public ThreeStatementModelRequest() {}

    public ThreeStatementModelRequest(String cik, Integer year) {
        this.cik = cik;
        this.year = year;
    }

    // Getters and Setters
    public String getCik() {
        return cik;
    }

    public void setCik(String cik) {
        this.cik = cik;
    }

    public Integer getYear() {
        return year;
    }

    public void setYear(Integer year) {
        this.year = year;
    }
}