import os
import pandas as pd
import requests
from typing import Dict, List, Any
import json
import sys

# API Configuration
BASE_URL = "https://data.sec.gov/api/xbrl/companyconcept"

HEADERS = {
    "User-Agent": "Your Name (your_email@example.com)",  # Replace with your name/email
    "Accept-Encoding": "gzip, deflate",
    "Host": "data.sec.gov"
}

def process_multiple_companies(hierarchy_df_balance_sheet: pd.DataFrame,
                               hierarchy_df_cash_flow: pd.DataFrame,
                               hierarchy_df_income_stmt: pd.DataFrame,
                               ciks: List[str],  # Now it's a list of CIKs, not a dictionary of names and CIKs
                               years: List[int]) -> Dict[str, pd.DataFrame]:
    """
    Process multiple companies and return their financial statements.
    """
    results = {}
    for cik in ciks:  # Iterate directly over the list of CIKs
        print(f"Processing CIK: {cik}...")

        # Process balance sheet
        bs_df = process_hierarchy(hierarchy_df_balance_sheet, cik, years)
        results[f"{cik}_balance"] = bs_df

        # Process cash flow
        cf_df = process_hierarchy(hierarchy_df_cash_flow, cik, years)
        results[f"{cik}_cash_flow"] = cf_df

        # Process income statement
        is_df = process_hierarchy(hierarchy_df_income_stmt, cik, years)
        results[f"{cik}_income"] = is_df

    return results


def process_hierarchy_with_depth(data: pd.DataFrame, definitions: List[str]) -> pd.DataFrame:
    """
    Processes the hierarchy using the existing 'depth' column.
    """
    filtered_data = data[data['definition'].isin(definitions)]
    hierarchy_df = filtered_data[['name', 'parent', 'depth']].copy()
    return hierarchy_df

def fetch_10k_values(cik: str, key: str, years: List[int], form: str = "10-K") -> Dict[int, Any]:
    """
    Fetches 10-K values for a given company and key for multiple years.
    """
    url = f"{BASE_URL}/CIK{cik}/us-gaap/{key}.json"
    try:
        response = requests.get(url, headers=HEADERS)
        if response.status_code == 200:
            data = response.json()
            usd_entries = data.get("units", {}).get("USD", [])
            # Sort entries by 'filed' date (descending)
            usd_entries = sorted(usd_entries, key=lambda x: x.get("filed", ""), reverse=True)

            # Extract values for all years in one pass
            results = {year: None for year in years}
            for entry in usd_entries:
                fy = entry.get("fy")
                fp = entry.get("fp", "").upper()
                if fy in years and fp == "FY" and entry.get("form") == form:
                    results[fy] = entry.get("val")
            return results
        elif response.status_code == 404:
            print(f"404 Not Found: {key} for CIK {cik}")
        else:
            print(f"Failed to fetch data for {key} (CIK: {cik}): {response.status_code}")
    except Exception as e:
        print(f"Error fetching data for {key} (CIK: {cik}): {e}")
    return {year: None for year in years}

def process_hierarchy(hierarchy_df: pd.DataFrame, cik: str, years: List[int]) -> pd.DataFrame:
    """
    Processes hierarchy and fetches values for all years.
    """
    hierarchy_df['API Key'] = hierarchy_df['name'].apply(lambda x: x.strip())
    valid_entries = []

    for _, row in hierarchy_df.iterrows():
        key = row['API Key']
        year_values = fetch_10k_values(cik, key, years)
        if any(value is not None for value in year_values.values()):
            entry = {
                "API Key": key,
                "depth": row["depth"],
                **{str(year): year_values[year] for year in years}
            }
            valid_entries.append(entry)

    return pd.DataFrame(valid_entries)

def save_to_csv(df: pd.DataFrame, cik: str, year: int, statement_type: str, output_dir: str):
    """
    Save data to CSV for each year with the structure:
    statement_csvs/CIK/Year/CIK_statement_year.csv
    """
    # Create directory structure
    cik_dir = os.path.join(output_dir, "statement_csvs", cik, str(year))
    os.makedirs(cik_dir, exist_ok=True)

    # Save CSV for the specific year
    csv_filename = f"{cik}_{statement_type}_{year}.csv"
    csv_path = os.path.join(cik_dir, csv_filename)

    # Filter DataFrame to include only the data for the current year
    year_df = df[["API Key", "depth", str(year)]]  # Filter to include the year column only
    year_df.to_csv(csv_path, index=False)
    print(f"Saved {csv_filename}")


def main():
    # Load the dataset
    try:
        file_path = sys.argv[3]
        data = pd.read_csv(file_path)
        OUTPUT_DIR = sys.argv[4]
        output_directory_csv = os.path.join(OUTPUT_DIR, "csvs")
    except FileNotFoundError:
        print(f"Error: Could not find file {file_path}")
        return
    except Exception as e:
        print(f"Error loading data: {e}")
        return

    # Parse the new input format
    try:
        input_data = json.loads(sys.argv[1])  # Parse the JSON string passed as an argument

        # Check if input data is a dictionary
        if isinstance(input_data, dict):
            ciks = input_data.get("ciks", [])
            years = input_data.get("years", [])
        elif isinstance(input_data, list):
            # If the input is a list, treat it as CIKs and assume years are passed in the second argument
            ciks = input_data
            years = json.loads(sys.argv[2])  # Assume years are passed as another argument
        else:
            print("Error: Invalid input format. Expected a dictionary or list.")
            return
    except Exception as e:
        print(f"Error parsing input data: {e}")
        return

    print(f"Received CIKs: {ciks}")
    print(f"Received years: {years}")

    # Define statement definitions
    balance_sheet_definitions = ['104000 - Statement - Statement of Financial Position, Classified']
    cash_flow_definitions = [
        '152200 - Statement - Statement of Cash Flows',
        '160000 - Statement - Statement of Cash Flows, Deposit Based Operations',
        '164000 - Statement - Statement of Cash Flows, Insurance Based Operations',
        '168400 - Statement - Statement of Cash Flows, Securities Based Operations',
        '172600 - Statement - Statement of Cash Flows, Direct Method Operating Activities'
    ]
    income_stmt_definitions = ['124000 - Statement - Statement of Income (Including Gross Margin)']

    # Process hierarchies
    hierarchy_df_balance_sheet = process_hierarchy_with_depth(data, balance_sheet_definitions)
    hierarchy_df_cash_flow = process_hierarchy_with_depth(data, cash_flow_definitions)
    hierarchy_df_income_stmt = process_hierarchy_with_depth(data, income_stmt_definitions)

    # Process companies (now passing CIKs directly)
    results = process_multiple_companies(hierarchy_df_balance_sheet,
                                         hierarchy_df_cash_flow,
                                         hierarchy_df_income_stmt,
                                         ciks,
                                         years)

    for cik_statement, df in results.items():
        # Extract the cik, statement type, and year from the key
        cik, statement_type = cik_statement.split("_", 1)
        # You may want to specify the year explicitly or iterate over all years if needed
        for year in years:
            save_to_csv(df, cik, year, statement_type, output_dir=output_directory_csv)

if __name__ == "__main__":
    main()