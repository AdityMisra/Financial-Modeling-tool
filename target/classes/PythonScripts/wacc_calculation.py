import pandas as pd
import numpy as np
import yfinance as yf
import sys

def calculate_beta(stock_ticker, index_ticker, start_date, end_date):
    """
    Calculate beta using historical daily returns of the stock and market index.
    """
    try:
        print(f"[INFO] Fetching data for {stock_ticker} and {index_ticker} from {start_date} to {end_date}")

        # Fetch historical stock and index data with auto_adjust=False
        stock_data = yf.Ticker(stock_ticker).history(start=start_date, end=end_date, auto_adjust=False)
        index_data = yf.Ticker(index_ticker).history(start=start_date, end=end_date, auto_adjust=False)

        # Ensure data contains 'Close' column
        if 'Close' not in stock_data.columns or 'Close' not in index_data.columns:
            raise ValueError(f"'Close' column not found in data for {stock_ticker} or {index_ticker}")

        # Use 'Close' prices for calculations
        stock_prices = stock_data['Close']
        index_prices = index_data['Close']

        # Debugging fetched data
        print(f"[DEBUG] Stock data head for {stock_ticker}:\n{stock_prices.head()}")
        print(f"[DEBUG] Index data head for {index_ticker}:\n{index_prices.head()}")

        # Calculate daily returns
        stock_returns = stock_prices.pct_change().dropna()
        index_returns = index_prices.pct_change().dropna()

        # Align the data by index (dates)
        aligned_data = pd.concat([stock_returns, index_returns], axis=1)
        aligned_data.columns = ["Stock", "Index"]
        aligned_data = aligned_data.dropna()

        # Debugging aligned data
        print(f"[DEBUG] Aligned data head:\n{aligned_data.head()}")
        print(f"[DEBUG] Aligned data size: {len(aligned_data)}")

        # Ensure aligned_data is not empty
        if aligned_data.empty:
            raise ValueError("No overlapping data between stock and index to calculate beta.")

        # Calculate covariance and variance
        covariance = aligned_data["Stock"].cov(aligned_data["Index"])
        variance = aligned_data["Index"].var()

        print(f"[DEBUG] Covariance between stock and index: {covariance}")
        print(f"[DEBUG] Variance of index returns: {variance}")

        # Calculate beta
        beta = covariance / variance
        print(f"[INFO] Calculated beta for {stock_ticker}: {beta}")
        return beta

    except Exception as e:
        print(f"[ERROR] Failed to calculate beta for {stock_ticker}: {e}")
        return None

def calculate_cost_of_equity(beta, risk_free_rate, market_return):
    """
    Calculate the cost of equity using the CAPM formula.
    """
    if beta is not None:
        return risk_free_rate + beta * (market_return - risk_free_rate)
    return None

def calculate_wacc(equity_value, debt_value, cost_of_equity, cost_of_debt, tax_rate):
    """
    Calculate the Weighted Average Cost of Capital (WACC).
    """
    if equity_value is None or debt_value is None or cost_of_equity is None:
        return None
    total_value = equity_value + debt_value
    equity_weight = equity_value / total_value
    debt_weight = debt_value / total_value
    after_tax_cost_of_debt = cost_of_debt * (1 - tax_rate)
    return equity_weight * cost_of_equity + debt_weight * after_tax_cost_of_debt

def main():
    """
    Main function to handle the WACC calculation.
    """
    try:
        args = sys.argv
        if len(args) != 11:
            raise ValueError("Invalid number of arguments provided.")

        metrics_file = args[1]
        wacc_file = args[2]
        cik = args[3]
        stock_ticker = args[4]
        risk_free_rate = float(args[5])
        market_return = float(args[6])
        tax_rate = float(args[7])
        start_date = args[8]
        end_date = args[9]
        cost_of_debt = float(args[10])

        print(f"[DEBUG] Metrics File Path: {metrics_file}")
        print(f"[DEBUG] WACC File Path: {wacc_file}")
        print(f"[DEBUG] Input arguments: {args}")

        metrics_data = pd.read_csv(metrics_file)
        print(f"[INFO] Reading metrics file: {metrics_file}")
        print(f"[DEBUG] Metrics data head:\n{metrics_data.head()}")

        # Normalize and validate CIK
        if not metrics_data['Company'].astype(str).str.zfill(len(cik)).str.contains(cik).any():
            raise ValueError(f"Provided CIK ({cik}) does not match the data in the metrics file.")

        beta = calculate_beta(stock_ticker, "^GSPC", start_date, end_date)
        if beta is None:
            raise RuntimeError("Beta calculation failed.")

        latest_data = metrics_data[metrics_data['Company'].astype(str).str.zfill(len(cik)) == cik].iloc[-1]
        equity_value = latest_data["Total Assets"] - latest_data["Total Liabilities"]
        debt_value = latest_data["Total Liabilities"]
        cost_of_equity = calculate_cost_of_equity(beta, risk_free_rate, market_return)

        wacc = calculate_wacc(
            equity_value=equity_value,
            debt_value=debt_value,
            cost_of_equity=cost_of_equity,
            cost_of_debt=cost_of_debt,
            tax_rate=tax_rate
        )

        if wacc is None:
            raise RuntimeError("WACC calculation failed.")

        wacc_results = pd.DataFrame([{
            "CIK": cik,
            "Stock Ticker": stock_ticker,
            "Beta": beta,
            "Cost of Equity": cost_of_equity,
            "Cost of Debt": cost_of_debt,
            "WACC": wacc
        }])
        wacc_results.to_csv(wacc_file, index=False)
        print(f"[INFO] WACC results saved to {wacc_file}")
        print(f"[DEBUG] WACC Results:\n{wacc_results}")

    except Exception as e:
        print(f"[ERROR] Script execution error: {e}")
        sys.exit(1)

if __name__ == "__main__":
    main()

