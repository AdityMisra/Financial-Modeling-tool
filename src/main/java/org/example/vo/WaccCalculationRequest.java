package org.example.vo;

public class WaccCalculationRequest {
    private String cik;
    private String ticker;
    private String metricFile;
    private Double riskFreeRate;
    private Double marketReturn;
    private Double taxRate;
    private String startDate; // New field
    private String endDate;   // New field
    private Double costOfDebt; // New field

    // Getters and Setters
    public String getCik() { return cik; }
    public void setCik(String cik) { this.cik = cik; }

    public String getTicker() { return ticker; }
    public void setTicker(String ticker) { this.ticker = ticker; }

    public String getMetricFile() { return metricFile; }
    public void setMetricFile(String metricFile) { this.metricFile = metricFile; }

    public Double getRiskFreeRate() { return riskFreeRate; }
    public void setRiskFreeRate(Double riskFreeRate) { this.riskFreeRate = riskFreeRate; }

    public Double getMarketReturn() { return marketReturn; }
    public void setMarketReturn(Double marketReturn) { this.marketReturn = marketReturn; }

    public Double getTaxRate() { return taxRate; }
    public void setTaxRate(Double taxRate) { this.taxRate = taxRate; }

    public String getStartDate() { return startDate; }
    public void setStartDate(String startDate) { this.startDate = startDate; }

    public String getEndDate() { return endDate; }
    public void setEndDate(String endDate) { this.endDate = endDate; }

    public Double getCostOfDebt() { return costOfDebt; }
    public void setCostOfDebt(Double costOfDebt) { this.costOfDebt = costOfDebt; }



}
