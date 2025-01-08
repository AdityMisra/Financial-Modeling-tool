package org.example.vo;


    // Default constructor
    public class MonteCarloRequest {
        private String cik;
        private Integer numSimulations;
        private Integer simulationYears;
        private Double taxRate;
        private Integer fromYear;
        private Integer toYear;

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
