import pandas as pd
import numpy as np
import os
import sys
import logging

# Setup logging for easier debugging
logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(levelname)s - %(message)s')
logger = logging.getLogger(__name__)

def run_monte_carlo_simulation(cik: str, num_simulations: int, simulation_years: int,
                               tax_rate: float, base_dir: str, from_year: int, to_year: int) -> str:
    """
    Run Monte Carlo simulation for the specified company
    """

    try:
        # Define the path for the metrics file based on the inputs
        metrics_file = os.path.join(base_dir, "csvs", "statement_csvs", cik, "metrics", f"{cik}_metrics_{from_year}-{to_year}.csv")

        # Check if the metrics file exists
        if not os.path.exists(metrics_file):
            logger.error(f"Metrics file not found: {metrics_file}")
            raise FileNotFoundError(f"Metrics file not found: {metrics_file}. Please calculate metrics first for CIK {cik}")

        logger.info(f"Loading historical data from {metrics_file}")

        # Load historical data
        try:
            historical_data = pd.read_csv(metrics_file)
        except Exception as e:
            logger.error(f"Error loading metrics file: {e}")
            raise e

        # Prepare storage for simulation results
        simulation_results = []

        # Extract the last year's data as the starting point for simulation
        last_year_data = historical_data.iloc[-1]
        last_year_revenue = last_year_data['Revenue']
        last_year_ebitda_margin = last_year_data['EBITDA Margin']
        last_year_capex_margin = last_year_data['CapEx Margin']

        # Calculate growth rate statistics
        revenue_growth_rate_mean = historical_data['Revenue Growth Rate'].mean()
        revenue_growth_rate_std = historical_data['Revenue Growth Rate'].std()

        logger.info(f"Starting {num_simulations} simulations for {simulation_years} years")

        # Perform simulations
        for sim in range(1, num_simulations + 1):
            simulated_revenue = last_year_revenue

            for year in range(1, simulation_years + 1):
                # Simulate revenue growth
                growth_rate = np.random.normal(revenue_growth_rate_mean, revenue_growth_rate_std)
                simulated_revenue *= (1 + growth_rate)

                # Calculate other financial metrics based on simulated revenue
                ebitda = simulated_revenue * last_year_ebitda_margin
                capex = simulated_revenue * last_year_capex_margin
                free_cash_flow = ebitda - capex
                net_income = ebitda * (1 - tax_rate)

                # Store simulation result for this year
                simulation_results.append({
                    "CIK": cik,
                    "Simulation": sim,
                    "Year": int(last_year_data['Year']) + year,
                    "Revenue": simulated_revenue,
                    "EBITDA": ebitda,
                    "CapEx": capex,
                    "Free Cash Flow": free_cash_flow,
                    "Net Income": net_income
                })

            if sim % 1000 == 0:
                logger.info(f"Completed {sim} simulations")

        # Convert results to DataFrame
        simulation_results_df = pd.DataFrame(simulation_results)

        # Create the simulation output directory if it doesn't exist
        sim_dir = os.path.join(base_dir, "csvs", "statement_csvs", cik, "simulations")
        os.makedirs(sim_dir, exist_ok=True)

        # Save the results to CSV and HTML files
        output_file = os.path.join(sim_dir, f"{cik}_monte_carlo_results_{from_year}-{to_year}.csv")
        simulation_results_df.to_csv(output_file, index=False)

        html_file = output_file.replace('.csv', '.html')
        simulation_results_df.to_html(html_file, index=False)

        logger.info(f"Simulation results saved to {output_file}")
        return output_file

    except Exception as e:
        logger.error(f"Error during Monte Carlo simulation: {str(e)}")
        sys.exit(1)

def main():
    if len(sys.argv) != 8:
        print("Usage: python monte_carlo_simulations.py <cik> <num_simulations> "
              "<simulation_years> <tax_rate> <base_dir>")
        sys.exit(1)

    try:
        cik = sys.argv[1]
        num_simulations = int(sys.argv[2])
        simulation_years = int(sys.argv[3])
        tax_rate = float(sys.argv[4])
        base_dir = sys.argv[5]
        from_year = int(sys.argv[6])
        to_year = int(sys.argv[7])

        output_file = run_monte_carlo_simulation(cik, num_simulations, simulation_years, tax_rate, base_dir, from_year, to_year)

        print(f"\nMonte Carlo simulation completed successfully!")
        print(f"Results saved to: {output_file}")

    except Exception as e:
        logger.error(f"Error in Monte Carlo simulation: {str(e)}")
        sys.exit(1)

if __name__ == "__main__":
    main()