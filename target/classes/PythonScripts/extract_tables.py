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

def fetch_10k_values(cik: str, key: str, year: int, form: str = "10-K") -> Any:
    """
    Fetches 10-K values for a given company and key for a specific year.
    """
    url = f"{BASE_URL}/CIK{cik}/us-gaap/{key}.json"
    try:
        response = requests.get(url, headers=HEADERS)
        if response.status_code == 200:
            data = response.json()
            usd_entries = data.get("units", {}).get("USD", [])
            usd_entries = sorted(usd_entries, key=lambda x: x.get("filed", ""), reverse=True)

            for entry in usd_entries:
                if entry.get("fy") == year and entry.get("fp", "").upper() == "FY" and entry.get("form") == form:
                    return entry.get("val")
            return None
        elif response.status_code == 404:
            print(f"404 Not Found: {key} for CIK {cik}")
        else:
            print(f"Failed to fetch data for {key} (CIK: {cik}): {response.status_code}")
    except Exception as e:
        print(f"Error fetching data for {key} (CIK: {cik}): {e}")
    return None

def process_hierarchy(hierarchy_df: pd.DataFrame, cik: str, year: int) -> pd.DataFrame:
    """
    Processes hierarchy and fetches values for a specific year.
    """
    hierarchy_df['API Key'] = hierarchy_df['name'].apply(lambda x: x.strip())
    valid_entries = []

    for _, row in hierarchy_df.iterrows():
        key = row['API Key']
        value = fetch_10k_values(cik, key, year)
        if value is not None:
            valid_entry = {
                "API Key": key,
                "depth": row["depth"],
                str(year): value
            }
            valid_entries.append(valid_entry)

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
    try:
        file_path = sys.argv[3]
        data = pd.read_csv(file_path)
        OUTPUT_DIR = sys.argv[4]
    except FileNotFoundError:
        print(f"Error: Could not find file {file_path}")
        return
    except Exception as e:
        print(f"Error loading data: {e}")
        return

    companies = json.loads(sys.argv[1])
    years = json.loads(sys.argv[2])
    print(f"Received companies: {companies}")
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

    # Process each company and year
    for company_name, cik in companies.items():
        print(f"Processing {company_name} (CIK: {cik})...")

        for year in years:
            print(f"Processing year {year}...")

            # Process and save each statement type
            bs_df = process_hierarchy(hierarchy_df_balance_sheet, cik, year)
            save_to_csv(bs_df, company_name, year, "balance", OUTPUT_DIR)

            cf_df = process_hierarchy(hierarchy_df_cash_flow, cik, year)
            save_to_csv(cf_df, company_name, year, "cash_flow", OUTPUT_DIR)

            is_df = process_hierarchy(hierarchy_df_income_stmt, cik, year)
            save_to_csv(is_df, company_name, year, "income", OUTPUT_DIR)

if __name__ == "__main__":
    main()