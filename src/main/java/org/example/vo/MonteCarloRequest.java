package org.example.vo;

public class MonteCarloRequest {
    private String cik;
    private Integer numSimulations;
    private Integer simulationYears;
    private Double taxRate;

    // Default constructor
    public MonteCarloRequest() {}

    // Constructor with fields
    public MonteCarloRequest(String cik, Integer numSimulations, Integer simulationYears, Double taxRate) {
        this.cik = cik;
        this.numSimulations = numSimulations;
        this.simulationYears = simulationYears;
        this.taxRate = taxRate;
    }

    // Getters and setters
    public String getCik() {
        return cik;
    }

    public void setCik(String cik) {
        this.cik = cik;
    }

    public Integer getNumSimulations() {
        return numSimulations;
    }

    public void setNumSimulations(Integer numSimulations) {
        this.numSimulations = numSimulations;
    }

    public Integer getSimulationYears() {
        return simulationYears;
    }

    public void setSimulationYears(Integer simulationYears) {
        this.simulationYears = simulationYears;
    }

    public Double getTaxRate() {
        return taxRate;
    }

    public void setTaxRate(Double taxRate) {
        this.taxRate = taxRate;
    }
}