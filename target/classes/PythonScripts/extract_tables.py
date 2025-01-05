import os
import pandas as pd
import requests
from typing import Dict, List, Any, Tuple
import json
import sys
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

def generate_shades(base_color: Tuple[int, int, int], depth_levels: int, increment: int) -> dict:
    """
    Generate shades by lightening the base color for each depth level.
    """
    shades = {}
    for depth in range(depth_levels):
        r = min(base_color[0] + increment * depth, 255)
        g = min(base_color[1] + increment * depth, 255)
        b = min(base_color[2] + increment * depth, 255)
        shades[depth] = f'background-color: rgb({int(r)}, {int(g)}, {int(b)}); color: {"white" if depth < 3 else "black"}; text-align: left;'
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

def save_to_yearly_structure(df: pd.DataFrame, company_name: str, year: int, statement_type: str, output_dir: str):
    """
    Save data to yearly folder structure.
    """
    # Create company directory
    company_dir = os.path.join(output_dir, company_name)
    os.makedirs(company_dir, exist_ok=True)

    # Create year directory
    year_dir = os.path.join(company_dir, str(year))
    os.makedirs(year_dir, exist_ok=True)

    # Save CSV
    csv_filename = f"{company_name}_{statement_type}_{year}.csv"
    csv_path = os.path.join(year_dir, csv_filename)
    df.to_csv(csv_path, index=False)
    print(f"Saved {csv_filename}")

    # Save HTML with styling
    html_filename = f"{company_name}_{statement_type}_{year}.html"
    html_path = os.path.join(year_dir, html_filename)

    # Apply styling based on statement type
    SHADES = {
        "balance": generate_shades((1, 5, 18), depth_levels=6, increment=40),
        "cash_flow": generate_shades((0, 43, 0), depth_levels=6, increment=40),
        "income": generate_shades((58, 47, 0), depth_levels=6, increment=40)
    }

    df_indented = add_indentation(df)

    if statement_type in SHADES:
        styled_df = apply_styling(df_indented, SHADES[statement_type])
    else:
        styled_df = df_indented.style

    html_content = f"""
    <html>
    <head>
        <title>{company_name} {statement_type} {year}</title>
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
            <h1>{company_name} {statement_type} {year}</h1>
            {styled_df.to_html()}
        </div>
    </body>
    </html>
    """

    with open(html_path, 'w', encoding='utf-8') as f:
        f.write(html_content)
    print(f"Saved {html_filename}")

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

    # Process each company and year separately
    for company_name, cik in companies.items():
        print(f"Processing {company_name} (CIK: {cik})...")

        for year in years:
            print(f"Processing year {year}...")

            # Process each statement type
            bs_df = process_hierarchy(hierarchy_df_balance_sheet, cik, year)
            save_to_yearly_structure(bs_df, company_name, year, "balance", OUTPUT_DIR)

            cf_df = process_hierarchy(hierarchy_df_cash_flow, cik, year)
            save_to_yearly_structure(cf_df, company_name, year, "cash_flow", OUTPUT_DIR)

            is_df = process_hierarchy(hierarchy_df_income_stmt, cik, year)
            save_to_yearly_structure(is_df, company_name, year, "income", OUTPUT_DIR)

if __name__ == "__main__":
    main()