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

def save_to_csv(df: pd.DataFrame, company_name: str, year: int, statement_type: str, output_dir: str):
    """
    Save data to CSV with the structure:
    statement_csvs/Company/Year/Company_statement_year.csv
    """
    # Create directory structure
    company_dir = os.path.join(output_dir, "statement_csvs", company_name, str(year))
    os.makedirs(company_dir, exist_ok=True)

    # Save CSV
    csv_filename = f"{company_name}_{statement_type}_{year}.csv"
    csv_path = os.path.join(company_dir, csv_filename)
    df.to_csv(csv_path, index=False)
    print(f"Saved {csv_filename}")


def main():
    if len(sys.argv) != 5:
        print("Usage: python generate_3statementmodel.py <company> <year> <input_dir> <output_dir>")
        sys.exit(1)

    companies = json.loads(sys.argv[1])
    years = json.loads(sys.argv[2])
    print(f"Received companies: {companies}")
    print(f"Received years: {years}")

    try:
        file_path = sys.argv[3]
        data = pd.read_csv(file_path)
        OUTPUT_DIR = sys.argv[4]

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

        # Process each company
        for company_name, cik in companies.items():
            print(f"Processing {company_name} (CIK: {cik})...")

            # Get data for all years at once
            bs_df = process_hierarchy(hierarchy_df_balance_sheet, cik, years)
            cf_df = process_hierarchy(hierarchy_df_cash_flow, cik, years)
            is_df = process_hierarchy(hierarchy_df_income_stmt, cik, years)

            # Save files for each year separately
            for year in years:
                print(f"Saving files for year {year}...")

                # Create year-specific DataFrames
                year_bs = bs_df[['API Key', 'depth', str(year)]]
                year_cf = cf_df[['API Key', 'depth', str(year)]]
                year_is = is_df[['API Key', 'depth', str(year)]]

                # Save to respective directories
                save_to_csv(year_bs, company_name, year, "balance", OUTPUT_DIR)
                save_to_csv(year_cf, company_name, year, "cash_flow", OUTPUT_DIR)
                save_to_csv(year_is, company_name, year, "income", OUTPUT_DIR)

    except Exception as e:
        print(f"Error processing data: {e}")
        import traceback
        traceback.print_exc()
        sys.exit(1)

if __name__ == "__main__":
    main()