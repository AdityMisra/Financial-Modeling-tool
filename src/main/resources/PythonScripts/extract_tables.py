# config.py
import os
import pandas as pd
from pandas.io.formats.style import Styler

# API Configuration
BASE_URL = "https://data.sec.gov/api/xbrl/companyconcept"

HEADERS = {
    "User-Agent": "Your Name (your_email@example.com)",  # Replace with your name/email
    "Accept-Encoding": "gzip, deflate",
    "Host": "data.sec.gov"
}

# utils.py
import pandas as pd
import requests
from typing import Dict, List, Any, Tuple
import os
import sys
import json

def check_existing_data(output_dir: str, company: str, year: int) -> bool:
    """
    Check if data for a specific company and year already exists in CSV files.
    Returns True if the year column exists in any of the company's files.
    """
    statement_types = ['balance', 'cash_flow', 'income']
    for statement in statement_types:
        file_path = os.path.join(output_dir, f"{company}_{statement}.csv")
        if os.path.exists(file_path):
            try:
                df = pd.read_csv(file_path)
                if str(year) in df.columns:
                    return True
            except pd.errors.EmptyDataError:
                continue
    return False

def merge_with_existing_data(new_df: pd.DataFrame, output_dir: str, filename: str) -> pd.DataFrame:
    """
    Merge new data with existing CSV file based on 'API Key' and 'depth',
    with strict prevention of duplicate year columns.
    """
    file_path = os.path.join(output_dir, filename)
    if os.path.exists(file_path):
        try:
            # Read existing data
            existing_df = pd.read_csv(file_path)

            # Identify all year columns from both dataframes
            existing_year_cols = [col for col in existing_df.columns if str(col).isdigit()]
            new_year_cols = [col for col in new_df.columns if str(col).isdigit()]

            # Create a single set of year columns without duplicates
            all_years = sorted(set(map(str, existing_year_cols + new_year_cols)), key=lambda x: int(x))

            # Create a new dataframe with all unique API Keys
            all_api_keys = pd.concat([
                existing_df[['API Key', 'depth']],
                new_df[['API Key', 'depth']]
            ]).drop_duplicates(subset=['API Key', 'depth'])

            # Initialize the result dataframe with API Keys and depth
            result_df = all_api_keys.copy()

            # Fill in values for each year
            for year in all_years:
                if year in new_df.columns:
                    # Prefer values from new data
                    year_data = new_df.set_index(['API Key', 'depth'])[year]
                elif year in existing_df.columns:
                    # Use existing data if no new data available
                    year_data = existing_df.set_index(['API Key', 'depth'])[year]
                else:
                    continue

                result_df = result_df.merge(
                    year_data.reset_index(),
                    on=['API Key', 'depth'],
                    how='left'
                )

            # Ensure columns are in the correct order
            final_columns = ['API Key', 'depth'] + all_years
            result_df = result_df[final_columns]

            return result_df

        except Exception as e:
            print(f"Error merging data for {filename}: {str(e)}")
            return new_df
    return new_df

def generate_shades(base_color: Tuple[int, int, int], depth_levels: int, increment: int) -> dict:
    """
    Generate shades by lightening the base color for each depth level.
    """
    shades = {}
    for depth in range(depth_levels):
        r = min(base_color[0] + increment * depth, 255)
        g = min(base_color[1] + increment * depth, 255)
        b = min(base_color[2] + increment * depth, 255)
        shades[
            depth] = f'background-color: rgb({int(r)}, {int(g)}, {int(b)}); color: {"white" if depth < 3 else "black"}; text-align: left;'
    return shades


def apply_styling(df: pd.DataFrame, shades: dict) -> pd.io.formats.style.Styler:
    """
    Apply color styling to DataFrame based on depth.
    """

    def style_row(row):
        return [shades.get(row['depth'], 'background-color: #d0e7ff; color: black; text-align: left;')] * len(row)

    return df.style.apply(style_row, axis=1)


def add_indentation(df: pd.DataFrame) -> pd.DataFrame:
    """
    Add indentation to the 'API Key' column based on the depth.
    """
    styled_df = df.copy()
    styled_df['API Key'] = styled_df.apply(
        lambda row: '\u00A0' * (4 * row['depth']) + str(row['API Key']), axis=1
    )
    return styled_df


def process_hierarchy_with_depth(data: pd.DataFrame, definitions: List[str]) -> pd.DataFrame:
    """
    Processes the hierarchy using the existing 'depth' column.
    """
    filtered_data = data[data['definition'].isin(definitions)]
    hierarchy_df = filtered_data[['name', 'parent', 'depth']].copy()
    return hierarchy_df


def fetch_10k_values(cik: str, key: str, years: List[int], form: str = "10-K") -> Dict[int, Any]:
    """
    Fetches 10-K values for a given company and key.
    """
    url = f"{BASE_URL}/CIK{cik}/us-gaap/{key}.json"
    try:
        response = requests.get(url, headers=HEADERS)
        if response.status_code == 200:
            data = response.json()
            usd_entries = data.get("units", {}).get("USD", [])
            usd_entries = sorted(usd_entries, key=lambda x: x.get("filed", ""), reverse=True)

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
    Processes hierarchy and fetches values for each entry.
    """
    hierarchy_df['API Key'] = hierarchy_df['name'].apply(lambda x: x.strip())
    valid_entries = []

    for _, row in hierarchy_df.iterrows():
        key = row['API Key']
        year_values = fetch_10k_values(cik, key, years)
        if any(value is not None for value in year_values.values()):
            valid_entry = {"API Key": key, "depth": row["depth"], **year_values}
            valid_entries.append(valid_entry)

    return pd.DataFrame(valid_entries)


def save_results_to_csv(results: Dict[str, pd.DataFrame], output_dir: str = "output_csvs"):
    """
    Save results to CSV files with guaranteed unique year columns.
    """
    os.makedirs(output_dir, exist_ok=True)

    for key, df in results.items():
        file_name = f"{key.replace(' ', '_')}.csv"
        merged_df = merge_with_existing_data(df, output_dir, file_name)
        merged_df.to_csv(os.path.join(output_dir, file_name), index=False)
        print(f"Saved {key} to {file_name}")

def save_styled_results_to_html(results: Dict[str, pd.DataFrame], output_dir: str = "output_html"):
    """
    Style and save results as HTML files, updating existing files with new data.
    """
    os.makedirs(output_dir, exist_ok=True)

    SHADES = {
        "balance": generate_shades((1, 5, 18), depth_levels=6, increment=40),
        "cash_flow": generate_shades((0, 43, 0), depth_levels=6, increment=40),
        "income": generate_shades((58, 47, 0), depth_levels=6, increment=40)
    }

    for key, df in results.items():
        print(f"Processing {key} for HTML export...")

        # Get the CSV file path
        csv_file_name = f"{key.replace(' ', '_')}.csv"
        csv_dir = output_dir.replace("html", "csvs")

        try:
            # Merge with existing data first
            merged_df = merge_with_existing_data(df, csv_dir, csv_file_name)

            # Add indentation
            df_indented = add_indentation(merged_df)

            # Apply styling based on statement type
            if 'balance' in key:
                styled_df = apply_styling(df_indented, SHADES['balance'])
            elif 'cash_flow' in key:
                styled_df = apply_styling(df_indented, SHADES['cash_flow'])
            elif 'income' in key:
                styled_df = apply_styling(df_indented, SHADES['income'])
            else:
                print(f"Unknown table type for {key}. Using default styling...")
                styled_df = df_indented.style

            html_content = f"""
            <html>
            <head>
                <title>{key}</title>
                <style>
                    body {{
                        font-family: Arial, sans-serif;
                        margin: 20px;
                        background-color: #f5f5f5;
                    }}
                    .container {{
                        background-color: white;
                        padding: 20px;
                        border-radius: 5px;
                        box-shadow: 0 2px 4px rgba(0,0,0,0.1);
                    }}
                    h1 {{
                        color: #333;
                        margin-bottom: 20px;
                    }}
                    table {{
                        border-collapse: collapse;
                        width: 100%;
                        margin: 20px 0;
                        background-color: white;
                    }}
                    th, td {{
                        padding: 12px 8px;
                        border: 1px solid #ddd;
                    }}
                    th {{
                        background-color: #f8f9fa;
                        font-weight: bold;
                    }}
                    tr:hover {{
                        opacity: 0.9;
                    }}
                </style>
            </head>
            <body>
                <div class="container">
                    <h1>{key}</h1>
                    {styled_df.to_html()}
                </div>
            </body>
            </html>
            """

            file_name = f"{key.replace(' ', '_')}.html"
            with open(os.path.join(output_dir, file_name), 'w', encoding='utf-8') as f:
                f.write(html_content)
            print(f"Saved {key} to {file_name}")

        except Exception as e:
            print(f"Error processing {key}: {str(e)}")
            continue

def process_multiple_companies(hierarchy_df_balance_sheet: pd.DataFrame,
                               hierarchy_df_cash_flow: pd.DataFrame,
                               hierarchy_df_income_stmt: pd.DataFrame,
                               companies: Dict[str, str],
                               years: List[int]) -> Dict[str, pd.DataFrame]:
    """
    Process multiple companies and return their financial statements.
    Ensures no duplicate year columns are created.
    """
    results = {}

    for company_name, cik in companies.items():
        print(f"Processing {company_name} (CIK: {cik})...")

        # Convert years to strings in the hierarchy processing
        years_str = [str(year) for year in years]

        # Process each statement type
        bs_df = process_hierarchy(hierarchy_df_balance_sheet, cik, years)
        cf_df = process_hierarchy(hierarchy_df_cash_flow, cik, years)
        is_df = process_hierarchy(hierarchy_df_income_stmt, cik, years)

        # Ensure year columns are strings
        for df in [bs_df, cf_df, is_df]:
            for year in years:
                if year in df.columns:
                    df.rename(columns={year: str(year)}, inplace=True)

        results[f"{company_name}_balance"] = bs_df
        results[f"{company_name}_cash_flow"] = cf_df
        results[f"{company_name}_income"] = is_df

    return results


def main():
    try:
        file_path = sys.argv[3]
        data = pd.read_csv(file_path)
        OUTPUT_DIR = sys.argv[4]
        output_directory_csv = os.path.join(OUTPUT_DIR, "csvs")
        output_directory_html = os.path.join(OUTPUT_DIR, "html")
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

    # Check if we need to process this data
    should_process = False
    for company_name in companies.keys():
        for year in years:
            if not check_existing_data(output_directory_csv, company_name, year):
                should_process = True
                break
        if should_process:
            break

    if not should_process:
        print("All requested data already exists in the files. No processing needed.")
        return

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

    # Process companies
    results = process_multiple_companies(hierarchy_df_balance_sheet,
                                         hierarchy_df_cash_flow,
                                         hierarchy_df_income_stmt,
                                         companies,
                                         years)

    save_results_to_csv(results, output_dir=output_directory_csv)
    save_styled_results_to_html(results, output_dir=output_directory_html)

if __name__ == "__main__":
    main()