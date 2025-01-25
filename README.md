# Financial Modeling Project

This project focuses on building a financial modeling tool to analyze companies' financials, predict future metrics using simulations, and calculate key financial metrics and ratios. Below is a detailed breakdown of the project phases, goals, and setup instructions.

---

## **Phase 1: Initial Development**

- Extracting Income Statement, Balance Sheet, and Cash Flow Statement from the latest 10-K filings.

- Create a three-statement model to link components within and across statements.

- Calculates Working Capital, EBITDA, and Free Cash Flow.

- Performing Monte Carlo simulations for EBITDA, Cash Balances, and Net Income.

- Calculating WACC using current cost of debt and 3-year historical beta
---

## **Phase 2: Work in Progress**
- The next phase will build upon the foundation laid in Phase 1. Details will be updated as the project evolves.

---

## **Pre-Requisites to Setup**

### **System Requirements:**
- Python 3.x

### **Required Python Libraries:**
1. **Pandas**
 - For data manipulation and analysis.
   ```bash
   pip install pandas
   ```

2. **Requests**
 - For making HTTP requests to fetch financial data.
   ```bash
   pip install requests
   ```
   Example installation command:
   ```bash
   /Library/Developer/CommandLineTools/usr/bin/python3 -m pip install requests
   ```
   Installation path:
   ```bash
   /Library/Developer/CommandLineTools/usr/bin/python3
   ```

3. **yfinance**
 - For accessing historical market data and financial information.
   ```bash
   pip install yfinance
   ```

---

## **How to Run the Project**
1. Clone the repository to your local system.
2. Ensure all pre-requisite libraries are installed.
3. Run the Python scripts to extract, model, and analyze financial data.

---

## **Future Goals:**
- Extend the analysis to multiple companies.
- Integrate additional financial metrics and forecasting techniques.
- Build a user-friendly interface for input and visualization.

---




