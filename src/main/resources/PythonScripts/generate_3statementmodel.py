import pandas as pd
import os
import sys
import json

def calculate_hierarchical_sum(api_key, data_df, taxonomy_df, year):
    taxonomy_row = taxonomy_df[taxonomy_df['name'] == api_key]
    if taxonomy_row.empty:
        return 0.0

    start_depth = taxonomy_row.iloc[0]['depth']

    # Get parent value
    parent_value = data_df.loc[data_df['API Key'] == api_key, str(year)].sum()
    if parent_value != 0.0:
        return parent_value

    # Initialize hierarchy sum
    total = 0.0
    start_index = taxonomy_df.index[taxonomy_df['name'] == api_key].tolist()[0]
    i = start_index + 1

    while i < len(taxonomy_df):
        row = taxonomy_df.iloc[i]
        current_depth = row['depth']
        if current_depth <= start_depth:
            break

        child_key = row['name']
        child_value = data_df.loc[data_df['API Key'] == child_key, str(year)].sum()
        total += child_value
        i += 1

    return total

class FinancialStatement:
    def __init__(self, data: pd.DataFrame, taxonomy_df: pd.DataFrame):
        self.data = data.drop_duplicates(subset=['API Key'])
        self.taxonomy_df = taxonomy_df
        self.validate_data()

    def validate_data(self):
        required_cols = ['API Key', 'depth']
        missing_cols = [col for col in required_cols if col not in self.data.columns]
        if missing_cols:
            raise ValueError(f"Missing required columns: {missing_cols}")

class BalanceSheet(FinancialStatement):
    def calculate_for_year(self, year):
        total_assets = self.get_value('Assets', year)
        total_liabilities = self.get_value('Liabilities', year)
        working_capital = self.calculate_working_capital(year)
        return {
            "Total Assets": total_assets,
            "Total Liabilities": total_liabilities,
            "Working Capital": working_capital
        }

    def calculate_working_capital(self, year):
        current_assets = self.get_value('AssetsCurrent', year)
        current_liabilities = self.get_value('LiabilitiesCurrent', year)
        return current_assets - current_liabilities if current_assets and current_liabilities else None

    def get_value(self, concept, year):
        return calculate_hierarchical_sum(concept, self.data, self.taxonomy_df, year)

class CashFlow(FinancialStatement):
    def calculate_for_year(self, year):
        operating_cash_flow = self.get_value_with_fallback(
            'NetCashProvidedByUsedInOperatingActivities',
            'NetCashProvidedByUsedInOperatingActivitiesContinuingOperations',
            year
        )
        investing_cash_flow = self.get_value_with_fallback(
            'NetCashProvidedByUsedInInvestingActivities',
            'NetCashProvidedByUsedInInvestingActivitiesContinuingOperations',
            year
        )
        financing_cash_flow = self.get_value_with_fallback(
            'NetCashProvidedByUsedInFinancingActivities',
            'NetCashProvidedByUsedInFinancingActivitiesContinuingOperations',
            year
        )
        free_cash_flow = operating_cash_flow - self.get_value_with_fallback(
            'CapitalExpenditures',
            'PaymentsToAcquirePropertyPlantAndEquipment',
            year
        )
        return {
            "Operating Cash Flow": operating_cash_flow,
            "Investing Cash Flow": investing_cash_flow,
            "Financing Cash Flow": financing_cash_flow,
            "Free Cash Flow": free_cash_flow
        }

    def get_value_with_fallback(self, primary_key, fallback_key, year):
        primary_value = self.data.loc[self.data['API Key'] == primary_key, str(year)].sum()
        if primary_value != 0.0:
            return primary_value

        fallback_value = self.data.loc[self.data['API Key'] == fallback_key, str(year)].sum()
        return fallback_value if fallback_value != 0.0 else 0.0

class IncomeStatement(FinancialStatement):
    def calculate_for_year(self, year):
        revenue = self.get_value(['Revenues', 'RevenueFromContractWithCustomerExcludingAssessedTax'], year)
        net_income = self.get_value(['NetIncome', 'NetIncomeLoss'], year)
        ebitda = self.calculate_ebitda(year, net_income)
        return {
            "Revenue": revenue,
            "Net Income": net_income,
            "EBITDA": ebitda
        }

    def calculate_ebitda(self, year, net_income):
        interest_expense = self.get_value(['InterestExpense'], year)
        taxes = self.get_value(['IncomeTaxExpense'], year)
        depreciation = self.get_value(['DepreciationAndAmortization'], year)
        return net_income + (interest_expense or 0) + (taxes or 0) + (depreciation or 0)

    def get_value(self, keys, year):
        for key in keys:
            value = calculate_hierarchical_sum(key, self.data, self.taxonomy_df, year)
            if value != 0.0:
                return value
        return 0.0

def main():
    if len(sys.argv) != 5:
        print("Usage: python generate_3statementmodel.py <company> <year> <input_dir> <output_dir>")
        sys.exit(1)

    company = sys.argv[1]
    year = int(sys.argv[2])
    input_dir = sys.argv[3]
    output_dir = sys.argv[4]

    try:
        # Load GAAP taxonomy
        taxonomy_path = os.path.join("src", "main", "resources", "GAAP_Taxonomy.csv")
        taxonomy_df = pd.read_csv(taxonomy_path)[['name', 'parent', 'depth', 'weight']]

        # Load financial statements
        company_dir = os.path.join(input_dir, "statement_csvs", company, str(year))
        balance_df = pd.read_csv(os.path.join(company_dir, f"{company}_balance_{year}.csv"))
        income_df = pd.read_csv(os.path.join(company_dir, f"{company}_income_{year}.csv"))
        cashflow_df = pd.read_csv(os.path.join(company_dir, f"{company}_cash_flow_{year}.csv"))

        # Initialize financial statement objects
        balance_sheet = BalanceSheet(balance_df, taxonomy_df)
        income_statement = IncomeStatement(income_df, taxonomy_df)
        cash_flow = CashFlow(cashflow_df, taxonomy_df)

        # Calculate metrics for the year
        results = {
            "Company": company,
            "Year": year,
            **balance_sheet.calculate_for_year(year),
            **cash_flow.calculate_for_year(year),
            **income_statement.calculate_for_year(year)
        }

        # Convert to DataFrame and save
        results_df = pd.DataFrame([results])
        output_dir = os.path.join(input_dir, "statement_csvs", company, str(year), "3statement_model")
        os.makedirs(output_dir, exist_ok=True)

        output_file = os.path.join(output_dir, f"{company}_3statementmodel_{year}.csv")
        results_df.to_csv(output_file, index=False)
        print(f"Generated 3-statement model: {output_file}")

    except Exception as e:
        print(f"Error generating 3-statement model: {e}")
        sys.exit(1)

if __name__ == "__main__":
    main()