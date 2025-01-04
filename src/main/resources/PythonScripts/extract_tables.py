import os
import sys
import pandas as pd
import requests
import json

# Paths

CSV_INPUT_PATH = "/Users/adityamisra/IdeaProjects/FinancialModeling/src/main/resources/GAAP_Taxonomy.csv"
OUTPUT_DIR = "/Users/adityamisra/IdeaProjects/FinancialModeling/output"

# SEC API details
BASE_URL = "https://data.sec.gov/api/xbrl/companyconcept"
HEADERS = {
    "User-Agent": "FinancialModeling/1.0 (your_email@example.com)",  # Replace with your email
    "Accept-Encoding": "gzip, deflate",
    "Host": "data.sec.gov"
}

# Styling for tables
SHADES = {
    "balance": {
        0: 'background-color: #010512; color: white; text-align: left;',
        1: 'background-color: #0a1a4a; color: white; text-align: left;',
        2: 'background-color: #193775; color: white; text-align: left;',
        3: 'background-color: #2c529e; color: white; text-align: left;',
        4: 'background-color: #3e6dc7; color: white; text-align: left;',
        5: 'background-color: #5089f0; color: white; text-align: left;'
    },
    "cash_flow": {
        0: 'background-color: #002b00; color: white; text-align: left;',
        1: 'background-color: #014d01; color: white; text-align: left;',
        2: 'background-color: #027502; color: white; text-align: left;',
        3: 'background-color: #038d03; color: white; text-align: left;',
        4: 'background-color: #04a804; color: white; text-align: left;',
        5: 'background-color: #05c305; color: white; text-align: left;'
    },
    "income": {
        0: 'background-color: #3a2f00; color: white; text-align: left;',
        1: 'background-color: #7a5e00; color: white; text-align: left;',
        2: 'background-color: #b58c00; color: white; text-align: left;',
        3: 'background-color: #f3bb00; color: white; text-align: left;',
        4: 'background-color: #ffd633; color: black; text-align: left;',
        5: 'background-color: #ffec80; color: black; text-align: left;'
    }
}

# Function to process hierarchy with depth
def process_hierarchy_with_depth(data, definitions):
    # Normalize the 'definition' column
    data['definition'] = data['definition'].str.strip().str.lower()

    # Normalize the definitions list
    definitions = [d.strip().lower() for d in definitions]

    # Debug: Log unique definitions in the data
    print("\nUnique definitions in the 'definition' column:")
    print(data['definition'].unique())

    # Filter rows based on the provided definitions
    filtered_data = data[data['definition'].isin(definitions)]

    # Debug: Check filtered data
    if filtered_data.empty:
        print("No rows matched the provided definitions.")
    else:
        print(f"Matched rows:\n{filtered_data}")

    # Return filtered data
    return filtered_data[['name', 'parent', 'depth']].copy() if not filtered_data.empty else pd.DataFrame(columns=['name', 'parent', 'depth'])

# Fetch 10-K values
def fetch_10k_values(cik, key, years, form="10-K"):
    url = f"{BASE_URL}/CIK{cik}/us-gaap/{key}.json"
    try:
        response = requests.get(url, headers=HEADERS)
        if response.status_code == 200:
            data = response.json()
            usd_entries = data.get("units", {}).get("USD", [])
            usd_entries = sorted(usd_entries, key=lambda x: x.get("filed", ""), reverse=True)
            results = {year: None for year in years}
            for entry in usd_entries:
                if entry.get("fy") in years and entry.get("fp") == "FY" and entry.get("form") == form:
                    results[entry.get("fy")] = entry.get("val")
            return results
        else:
            print(f"Error {response.status_code}: {key} for CIK {cik}")
    except Exception as e:
        print(f"Exception for {key} (CIK: {cik}): {e}")
    return {year: None for year in years}

# Process hierarchy and fetch values
def process_hierarchy(hierarchy_df, cik, years):
    hierarchy_df['API Key'] = hierarchy_df['name'].apply(lambda x: x.strip())
    valid_entries = []
    for _, row in hierarchy_df.iterrows():
        key = row['API Key']
        year_values = fetch_10k_values(cik, key, years)
        if any(year_values.values()):
            valid_entries.append({"API Key": key, "depth": row["depth"], **year_values})
    return pd.DataFrame(valid_entries)

# Add indentation
def add_indentation(df):
    df['API Key'] = df.apply(lambda row: '\u00A0' * (4 * row['depth']) + str(row['API Key']), axis=1)
    return df

# Apply styling
def apply_styling(df, shades):
    def style_row(row):
        return [shades.get(row['depth'], 'background-color: #d0e7ff; color: black; text-align: left;')] * len(row)
    return df.style.apply(style_row, axis=1)

# Save results to CSV and HTML
def save_results(results, output_dir):
    os.makedirs(output_dir, exist_ok=True)
    for key, styled_df in results.items():
        # Save as CSV
        csv_path = os.path.join(output_dir, f"{key.replace(' ', '_')}.csv")
        raw_df = styled_df.data if hasattr(styled_df, 'data') else styled_df
        raw_df.to_csv(csv_path, index=False)
        print(f"Saved {key} to {csv_path}")

        # Save as HTML
        html_path = os.path.join(output_dir, f"{key.replace(' ', '_')}.html")
        styled_df.to_html(html_path, index=False, escape=False)
        print(f"Saved {key} to {html_path}")

# Main process
if __name__ == "__main__":
    try:
        print("=== Python Script Execution Started ===")

        # Parse arguments from Spring Boot
        companies = json.loads(sys.argv[1])
        years = json.loads(sys.argv[2])

        print(f"Received companies: {companies}")
        print(f"Received years: {years}")

        # Step 1: Load GAAP Taxonomy
        if not os.path.exists(CSV_INPUT_PATH):
            raise FileNotFoundError(f"GAAP Taxonomy file not found at: {CSV_INPUT_PATH}")

        data = pd.read_csv(CSV_INPUT_PATH)
        print("GAAP Taxonomy loaded successfully.")

        # Load the CSV
        data = pd.read_csv(CSV_INPUT_PATH)

# Print the first few rows and column names
        print("First few rows of the CSV:")
        print(data.head())

        print("\nColumn names in the CSV:")
        print(data.columns)

# Print the data types of each column
        print("\nData types of each column:")
        print(data.dtypes)



        # Step 2: Define hierarchies
        balance_sheet_definitions = ["104000 - Statement of Financial Position, Classified"]
        cash_flow_definitions = [
            "152000 - Statement of Cash Flows, Indirect Based Operations",
            "160400 - Statement of Cash Flows, Insurance Based Operations",
            "160800 - Statement of Cash Flows, Securities Based Operations",
            "172600 - Statement of Cash Flows, Direct Method Operating Activities"
        ]
        income_stmt_definitions = ["124000 - Statement of Income (Including Gross Margin)"]

        # Step 3: Process and save results for each company
        results = {}
        for company, cik in companies.items():
            print(f"Processing {company} (CIK: {cik})...")

            # Process hierarchy with depth
            balance_sheet_data = process_hierarchy_with_depth(data, balance_sheet_definitions)
            print(f"Balance Sheet Data:\n{balance_sheet_data}")

# Process hierarchy
            balance_hierarchy = process_hierarchy(balance_sheet_data, cik, years)
            print(f"Balance Hierarchy for {cik}:\n{balance_hierarchy}")

# Add indentation
            indented_balance = add_indentation(balance_hierarchy)
            print(f"Indented Balance for {cik}:\n{indented_balance}")


            # Process each statement type
            results[f"{company}_balance"] = apply_styling(
                add_indentation(process_hierarchy(process_hierarchy_with_depth(data, balance_sheet_definitions), cik, years)),
                SHADES["balance"]
            )
            results[f"{company}_cash_flow"] = apply_styling(
                add_indentation(process_hierarchy(process_hierarchy_with_depth(data, cash_flow_definitions), cik, years)),
                SHADES["cash_flow"]
            )
            results[f"{company}_income"] = apply_styling(
                add_indentation(process_hierarchy(process_hierarchy_with_depth(data, income_stmt_definitions), cik, years)),
                SHADES["income"]
            )

        # Step 4: Save results to files
        save_results(results, OUTPUT_DIR)
        print("=== Processing Complete ===")
    except Exception as e:
        print(f"An error occurred: {e}")
        sys.exit(1)