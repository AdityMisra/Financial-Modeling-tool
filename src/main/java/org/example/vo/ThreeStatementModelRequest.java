package org.example.vo;

public class ThreeStatementModelRequest {
    private String company;
    private Integer year;

    // Constructors
    public ThreeStatementModelRequest() {}

    public ThreeStatementModelRequest(String company, Integer year) {
        this.company = company;
        this.year = year;
    }

    // Getters and Setters
    public String getCompany() {
        return company;
    }

    public void setCompany(String company) {
        this.company = company;
    }

    public Integer getYear() {
        return year;
    }

    public void setYear(Integer year) {
        this.year = year;
    }
}