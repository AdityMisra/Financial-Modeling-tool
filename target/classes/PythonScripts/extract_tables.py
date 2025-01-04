import os
import sys
import json
import pandas as pd
import requests
import traceback

# Function to generate shades dynamically
def generate_shades(base_color, depth_levels, increment):
    shades = {}
    for depth in range(depth_levels):
        r = min(base_color[0] + increment * depth, 255)
        g = min(base_color[1] + increment * depth, 255)
        b = min(base_color[2] + increment * depth, 255)
        shades[depth] = f'background-color: rgb({int(r)}, {int(g)}, {int(b)}); color: {"white" if depth < 3 else "black"}; text-align: left;'
    return shades

# Function to fetch 10-K values from the SEC API
def fetch_10k_values(cik, key, years, form="10-K"):
    BASE_URL = "https://data.sec.gov/api/xbrl/companyconcept"
    HEADERS = {
        "User-Agent": "FinancialModeling/1.0 (am6532@columbia.edu)",
        "Accept-Encoding": "gzip, deflate",
        "Host": "data.sec.gov",
    }

    url = f"{BASE_URL}/CIK{cik}/us-gaap/{key}.json"
    print(f"Fetching data from SEC API: {url}")
    print(f"Headers: {HEADERS}")

    try:
        response = requests.get(url, headers=HEADERS)
        print(f"Response Status Code: {response.status_code}")
        if response.status_code == 200:
            print(f"Data fetched successfully for key: {key}")
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
        else:
            print(f"Failed to fetch data. Status Code: {response.status_code}. Response: {response.text}")
    except Exception as e:
        print(f"Error fetching data for {key} (CIK: {cik}): {e}")
    return {year: None for year in years}

# Function to process hierarchy with depth
def process_hierarchy_with_depth(data, definitions, cik, years):
    filtered_data = data[data['definition'].isin(definitions)]
    hierarchy_df = filtered_data[['name', 'parent', 'depth']].copy()

    hierarchy_df['API Key'] = hierarchy_df['name'].apply(lambda x: x.strip())
    valid_entries = []

    for _, row in hierarchy_df.iterrows():
        key = row['API Key']
        year_values = fetch_10k_values(cik, key, years)
        if any(value is not None for value in year_values.values()):
            valid_entry = {"API Key": key, "depth": row["depth"], **year_values}
            valid_entries.append(valid_entry)

    return pd.DataFrame(valid_entries)

# Function to apply dynamic styling based on depth
def apply_styling(df, shades):
    def style_row(row):
        return [shades.get(row['depth'], 'background-color: #d0e7ff; color: black; text-align: left;')] * len(row)
    return df.style.apply(style_row, axis=1)

# Function to process multiple companies and years
def process_multiple_companies(data, companies, years):
    SHADES = {
        "balance": generate_shades((1, 5, 18), depth_levels=6, increment=40),
        "cash_flow": generate_shades((0, 43, 0), depth_levels=6, increment=40),
        "income": generate_shades((58, 47, 0), depth_levels=6, increment=40),
    }

    results = {}
    for company_name, cik in companies.items():
        print(f"Processing {company_name} (CIK: {cik})...")

        # Process balance sheet
        bs_df = process_hierarchy_with_depth(data, balance_sheet_definitions, cik, years)
        bs_styled = apply_styling(bs_df, SHADES['balance'])
        results[f"{company_name}_balance"] = bs_styled

        # Process cash flow
        cf_df = process_hierarchy_with_depth(data, cash_flow_definitions, cik, years)
        cf_styled = apply_styling(cf_df, SHADES['cash_flow'])
        results[f"{company_name}_cash_flow"] = cf_styled

        # Process income statement
        is_df = process_hierarchy_with_depth(data, income_stmt_definitions, cik, years)
        is_styled = apply_styling(is_df, SHADES['income'])
        results[f"{company_name}_income"] = is_styled

    return results

# Function to save styled DataFrame to CSV and HTML
def save_results(statement, df, company_name, output_dir):
    """
    Saves the DataFrame as a CSV and HTML file.

    Args:
        statement (str): The type of financial statement (e.g., 'balance').
        df (pd.DataFrame): The DataFrame containing the data to save.
        company_name (str): The name of the company.
        output_dir (str): The directory to save the files.
    """
    sanitized_name = company_name.replace(" ", "_")
    # Generate CSV file
    csv_file = os.path.join(output_dir, f"{sanitized_name}_{statement}.csv")
    df.to_csv(csv_file, index=False)
    print(f"CSV saved: {csv_file}")

    # Generate HTML file
    html_file = os.path.join(output_dir, f"{sanitized_name}_{statement}.html")
    df.to_html(html_file, index=False)
    print(f"HTML saved: {html_file}")

# Main execution block
if __name__ == "__main__":
    try:
        # Arguments from the Spring Boot application
        companies_input = sys.argv[1]
        years_input = sys.argv[2]

        companies = json.loads(companies_input)
        years = json.loads(years_input)

        # File paths
        output_dir = "/Users/adityamisra/IdeaProjects/FinancialModeling/output"
        taxonomy_path = "/Users/adityamisra/IdeaProjects/FinancialModeling/src/main/resources/GAAP_Taxonomy.csv"

        if not os.path.exists(taxonomy_path):
            raise FileNotFoundError(f"GAAP_Taxonomy.csv not found at {taxonomy_path}")

        # Load GAAP Taxonomy CSV
        data = pd.read_csv(taxonomy_path)

        # Financial statement definitions
        balance_sheet_definitions = ["104000 - Statement of Financial Position, Classified"]
        cash_flow_definitions = [
            "152000 - Statement of Cash Flows, Indirect Based Operations",
            "160400 - Statement of Cash Flows, Insurance Based Operations",
            "160800 - Statement of Cash Flows, Securities Based Operations",
            "172600 - Statement of Cash Flows, Direct Method Operating Activities",
        ]
        income_stmt_definitions = ["124000 - Statement of Income (Including Gross Margin)"]

        # Create output directory
        os.makedirs(output_dir, exist_ok=True)

        # Process each company
        for company_name, cik in companies.items():
            print(f"Processing {company_name} (CIK: {cik})")

            # Process balance sheet
            bs_df = process_hierarchy_with_depth(data, balance_sheet_definitions, cik, years)
            save_results("balance", bs_df, company_name, output_dir)

            # Process cash flow
            cf_df = process_hierarchy_with_depth(data, cash_flow_definitions, cik, years)
            save_results("cash_flow", cf_df, company_name, output_dir)

            # Process income statement
            is_df = process_hierarchy_with_depth(data, income_stmt_definitions, cik, years)
            save_results("income", is_df, company_name, output_dir)

        print("=== Processing Complete ===")

    except Exception as e:
        print(f"An error occurred: {e}")
        traceback.print_exc()
        sys.exit(1)