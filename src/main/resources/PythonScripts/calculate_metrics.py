import pandas as pd
import os
import sys
import json
from typing import Tuple, List
import logging

# Set up logging
logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(levelname)s - %(message)s')
logger = logging.getLogger(__name__)

def get_metrics_file_path(cik: str, from_year: int, to_year: int, base_dir: str) -> str:
    """Get path to company's metrics file with start and end years included"""
    metrics_dir = os.path.join(base_dir, "csvs", "statement_csvs", cik, "metrics")
    os.makedirs(metrics_dir, exist_ok=True)
    return os.path.join(metrics_dir, f"{cik}_metrics_{from_year}-{to_year}.csv")

def check_data_availability(cik: str, from_year: int, to_year: int, base_dir: str) -> Tuple[bool, List[int]]:
    """Check if 3-statement models exist for requested years"""
    missing_years = []
    for year in range(from_year, to_year + 1):
        model_file = os.path.join(base_dir, "csvs", "statement_csvs", cik, str(year),
                                  "3statement_model", f"{cik}_3statementmodel_{year}.csv")
        if not os.path.exists(model_file):
            missing_years.append(year)
            logger.warning(f"Missing 3-statement model for year {year}")
        else:
            logger.info(f"Found 3-statement model for year {year}")

    return len(missing_years) == 0, missing_years

def calculate_year_metrics(data: pd.DataFrame) -> pd.DataFrame:
    """Calculate metrics within each year"""
    metrics = data.copy()

    # EBITDA Margin
    metrics["EBITDA Margin"] = metrics.apply(
        lambda row: row["EBITDA"] / row["Revenue"] if row["Revenue"] > 0 else None,
        axis=1
    )

    # CapEx Margin
    metrics["CapEx Margin"] = metrics.apply(
        lambda row: (row["Operating Cash Flow"] - row["Free Cash Flow"]) / row["Revenue"]
        if row["Revenue"] > 0 else None,
        axis=1
    )

    return metrics

def calculate_cross_year_metrics(data: pd.DataFrame) -> pd.DataFrame:
    """Calculate metrics that compare across years"""
    metrics = data.copy()
    metrics["Revenue Growth Rate"] = None

    for cik in metrics["CIK"].unique():
        company_data = metrics[metrics["CIK"] == cik].sort_values("Year")

        for i in range(1, len(company_data)):
            current_idx = company_data.index[i]
            prev_idx = company_data.index[i-1]

            current_revenue = company_data.loc[current_idx, "Revenue"]
            prev_revenue = company_data.loc[prev_idx, "Revenue"]

            if prev_revenue > 0:
                metrics.loc[current_idx, "Revenue Growth Rate"] = \
                    (current_revenue - prev_revenue) / prev_revenue

    return metrics

def generate_html_output(metrics: pd.DataFrame, cik: str, from_year: int, to_year: int) -> str:
    """Generate formatted HTML output for financial metrics."""
    html_template = """
    <html>
    <head>
        <title>Financial Metrics {cik} ({from_year}-{to_year})</title>
        <style>
            body {{ font-family: Arial, sans-serif; margin: 20px; background-color: #f4f4f4; }}
            table {{ border-collapse: collapse; width: 100%; }}
            th, td {{
                border: 1px solid #ddd;
                padding: 8px;
                text-align: right;
            }}
            th {{ background-color: #2c3e50; color: white; text-align: left; }}
            tr:nth-child(even) {{ background-color: #ecf0f1; }}
            .header {{
                background-color: #2C3E50;
                color: white;
                padding: 10px;
                margin-bottom: 20px;
                box-shadow: 0px 4px 6px rgba(0, 0, 0, 0.1);
            }}
            .metric {{ font-weight: bold; color: #16a085; }}
            h2 {{ color: #FFFFFF; }}
            p {{ color: #FFFFFF; }}
        </style>
    </head>
    <body>
        <div class="header">
            <h2>Financial Metrics Report</h2>
            <p>CIK: {cik}<br>Period: {from_year} - {to_year}</p>
        </div>
        {table_html}
    </body>
    </html>
    """

    # Preprocess DataFrame: Remove Company and make CIK the first column
    if "Company" in metrics.columns:
        metrics = metrics.drop(columns=["Company"])
    if "CIK" in metrics.columns:
        metrics = metrics[["CIK"] + [col for col in metrics.columns if col != "CIK"]]

    # Define columns for percentage and numeric formatting
    percentage_cols = ["EBITDA Margin", "CapEx Margin", "Revenue Growth Rate"]
    formatted_metrics = metrics.copy()

    # Apply formatting based on column type
    for col in formatted_metrics.columns:
        if col in percentage_cols:
            formatted_metrics[col] = formatted_metrics[col].apply(
                lambda x: f"{x:.2%}" if pd.notnull(x) else ""
            )
        elif col not in ["CIK", "Year"]:
            formatted_metrics[col] = formatted_metrics[col].apply(
                lambda x: f"${x:,.0f}" if pd.notnull(x) else ""
            )

    # Generate the table HTML
    table_html = formatted_metrics.to_html(index=False, classes='dataframe')
    return html_template.format(
        cik=cik,
        from_year=from_year,
        to_year=to_year,
        table_html=table_html
    )

def calculate_metrics(cik: str, from_year: int, to_year: int, base_dir: str) -> pd.DataFrame:
    """Calculate all metrics for the specified period"""
    logger.info(f"Starting metrics calculation for CIK {cik} ({from_year}-{to_year})")

    # Check for required data
    all_available, missing_years = check_data_availability(cik, from_year, to_year, base_dir)
    if not all_available:
        missing_years_str = ", ".join(map(str, missing_years))
        raise FileNotFoundError(
            f"Missing 3-statement models for years: {missing_years_str}. "
            f"Please generate 3-statement models first for CIK {cik}"
        )

    # Load all 3-statement models
    all_data = []
    for year in range(from_year, to_year + 1):
        model_file = os.path.join(base_dir, "csvs", "statement_csvs", cik, str(year),
                                  "3statement_model", f"{cik}_3statementmodel_{year}.csv")
        try:
            df = pd.read_csv(model_file)
            df['Year'] = year
            df['CIK'] = cik
            all_data.append(df)
            logger.info(f"Loaded data for year {year}")
        except Exception as e:
            logger.error(f"Error loading data for year {year}: {e}")
            raise

    # Combine and calculate metrics
    combined_data = pd.concat(all_data, ignore_index=True)
    metrics = calculate_year_metrics(combined_data)
    metrics = calculate_cross_year_metrics(metrics)

    # Save results
    metrics_file = get_metrics_file_path(cik, from_year, to_year, base_dir)
    metrics.to_csv(metrics_file, index=False)
    logger.info(f"Saved CSV metrics to {metrics_file}")

    # Generate and save HTML
    html_content = generate_html_output(metrics, cik, from_year, to_year)
    html_file = metrics_file.replace('.csv', '.html')
    with open(html_file, 'w') as f:
        f.write(html_content)
    logger.info(f"Saved HTML report to {html_file}")

    return metrics

def main():
    if len(sys.argv) != 6:
        print("Usage: python calculate_metrics.py <cik> <from_year> <to_year> <input_dir> <output_dir>")
        sys.exit(1)

    cik = sys.argv[1]
    from_year = int(sys.argv[2])
    to_year = int(sys.argv[3])
    input_dir = sys.argv[4]
    output_dir = sys.argv[5]

    try:
        logger.info(f"Starting metrics calculation process for CIK {cik}")

        # Verify 3-statement model availability
        all_available, missing_years = check_data_availability(cik, from_year, to_year, input_dir)
        if not all_available:
            missing_years_str = ", ".join(map(str, missing_years))
            print("\nError: Missing 3-statement models for the following years only:")
            print(f"Years: {missing_years_str}")
            print("\nPlease generate the required 3-statement models first for these specific years:")
            print("1. Use POST /api/extract-tables")
            print("2. Then use POST /api/generate-3statement-model")
            print(f"\nNote: 3-statement model for other years are already available")
            sys.exit(1)

        # Calculate metrics
        metrics = calculate_metrics(cik, from_year, to_year, input_dir)

        # Print summary
        print("\nMetrics calculation completed successfully!")
        print(f"\nResults saved to:")
        print(f"CSV: {get_metrics_file_path(cik, from_year, to_year, input_dir)}")
        print(f"HTML: {get_metrics_file_path(cik, from_year, to_year, input_dir).replace('.csv', '.html')}")

        print("\nMetrics Summary:")
        print(metrics.describe())

        return metrics

    except Exception as e:
        logger.error(f"Error in metrics calculation: {str(e)}")
        import traceback
        traceback.print_exc()
        sys.exit(1)

if __name__ == "__main__":
    main()
