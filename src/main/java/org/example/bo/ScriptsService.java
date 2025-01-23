package org.example.bo;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.utils.CSVUtils;
import org.example.vo.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.NumberFormat;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ScriptsService {

    @Value("${python.script.path:src/main/resources/PythonScripts/extract_tables.py}")
    private String pythonScriptPath;

    @Value("${gaap.taxonomy.path:src/main/resources/GAAP_Taxonomy.csv}")
    private String gaapTaxonomyPath;

    @Value("${output.base.dir:output}")
    private String outputBaseDir;

    private final ObjectMapper objectMapper;

    @Autowired
    public ScriptsService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ScriptsService() {
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Extract tables from SEC data
     */

    public extractTableResponse extractTables(List<String> ciks, List<Integer> years) {
        try {
            String companiesArg = objectMapper.writeValueAsString(ciks);
            String yearsArg = objectMapper.writeValueAsString(years);

            String scriptPath = Paths.get(System.getProperty("user.dir"), pythonScriptPath).toString();
            String gaapTaxonomy = Paths.get(System.getProperty("user.dir"), gaapTaxonomyPath).toString();
            String outputPath = Paths.get(System.getProperty("user.dir"), outputBaseDir).toString();

            // Create directory structure if it doesn't exist
            Path csvDirPath = Paths.get(outputPath, "csvs", "statement_csvs");
            Files.createDirectories(csvDirPath);

            boolean filesExist = true;
            // Check if directories already exist for each CIK and year
            for (String cik : ciks) {
                for (Integer year : years) {
                    // Construct the path based on the correct directory structure
                    Path yearDirPath = csvDirPath.resolve(cik).resolve(year.toString());

                    System.out.println("Checking directory: " + yearDirPath.toString());  // Debug log

                    if (!Files.exists(yearDirPath)) {
                        filesExist = false;  // At least one directory doesn't exist, so we need to run the script
                        break;  // Stop further checks and proceed to execute script
                    }
                }

                if (!filesExist) {
                    break;  // If any directory doesn't exist, stop the loop
                }
            }

            if (filesExist) {
                // If all directories exist, return a response saying so
                return new extractTableResponse(
                        "Skipped",
                        "Required directories already exist for the selected CIK and Year(s). Please check the 'Display Existing data tab' ",
                        Collections.emptyList(), // No new files
                        Collections.emptyList()
                );
            }

            // If directories don't exist, execute the Python script to generate them
            String[] command = {"python3", scriptPath, companiesArg, yearsArg, gaapTaxonomy, outputPath};
            System.out.println("Executing command: " + String.join(" ", command));

            Process process = executeScript(command);
            List<String> csvFiles = getGeneratedCsvFiles();

            return new extractTableResponse(
                    "Success",
                    String.format("Generated %d CSV files", csvFiles.size()),
                    csvFiles,
                    Collections.emptyList()
            );

        } catch (Exception e) {
            e.printStackTrace();
            return new extractTableResponse(
                    "Error",
                    e.getMessage(),
                    Collections.emptyList(),
                    Collections.emptyList()
            );
        }
    }




    /**
     * Execute Python script
     */
    private Process executeScript(String[] command) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        Process process = pb.start();

        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
                System.out.println("Python Output: " + line);
            }
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            String errorMessage = output.toString();
            System.err.println("Full Python output: " + errorMessage);
            throw new RuntimeException("Python script failed with exit code " + exitCode + "\nOutput: " + errorMessage);
        }

        return process;
    }

    /**
     * Get list of generated CSV files
     */
    public List<String> getGeneratedCsvFiles() {
        Path basePath = Paths.get(System.getProperty("user.dir"), outputBaseDir,"csvs", "statement_csvs");
        try (Stream<Path> paths = Files.walk(basePath)) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".csv"))
                    .map(path -> basePath.relativize(path).toString())
                    .collect(Collectors.toList());
        } catch (IOException e) {
            return new ArrayList<>();
        }
    }

    /**
     * Get CSV content as string
     */
    public String getCsvContent(String company, String statement, int year) throws IOException {
        Path csvPath = Paths.get(
                System.getProperty("user.dir"),
                outputBaseDir,
                "csvs",
                "statement_csvs",
                company,
                String.valueOf(year),
                String.format("%s_%s_%d.csv", company, statement, year)
        );
        return Files.readString(csvPath);
    }

    /**
     * Get CSV as downloadable resource
     */
    public Resource getCsvAsResource(String company, String statement, int year) throws IOException {
        Path csvPath = Paths.get(
                System.getProperty("user.dir"),
                outputBaseDir,
                "csvs",
                "statement_csvs",
                company,
                String.valueOf(year),
                String.format("%s_%s_%d.csv", company, statement, year)
        );

        Resource resource = new UrlResource(csvPath.toUri());
        if (resource.exists()) {
            return resource;
        } else {
            throw new FileNotFoundException("File not found: " + csvPath);
        }
    }

    /**
     * Generate 3-statement model
     */
    public ThreeStatementModelResponse generate3StatementModel(String company, Integer year)
            throws IOException, InterruptedException {

        System.out.println("\nStarting 3-statement model generation for " + company + " year " + year);

        // Clean and normalize all paths
        Path rootDir = Paths.get(System.getProperty("user.dir")).normalize();
        Path baseOutputDir = rootDir.resolve(outputBaseDir.trim()).normalize(); // Add trim() to remove any spaces
        Path statementCsvDir = baseOutputDir.resolve("csvs").resolve("statement_csvs").normalize();
        Path companyDir = statementCsvDir.resolve(company).normalize();
        Path companyYearPath = companyDir.resolve(String.valueOf(year)).normalize();

        // Debug print the directory structure
        System.out.println("\nChecking directory structure:");
        System.out.println("Root directory: " + rootDir);
        System.out.println("Base output directory: " + baseOutputDir);
        System.out.println("Statement CSV directory: " + statementCsvDir);
        System.out.println("Company directory: " + companyDir);
        System.out.println("Company year directory: " + companyYearPath);

        // First check if base directories exist, create if they don't
        if (!Files.exists(baseOutputDir)) {
            Files.createDirectories(baseOutputDir);
            System.out.println("Created base output directory: " + baseOutputDir);
        }
        if (!Files.exists(statementCsvDir)) {
            Files.createDirectories(statementCsvDir);
            System.out.println("Created statement CSV directory: " + statementCsvDir);
        }
        if (!Files.exists(companyDir)) {
            Files.createDirectories(companyDir);
            System.out.println("Created company directory: " + companyDir);
        }
        if (!Files.exists(companyYearPath)) {
            Files.createDirectories(companyYearPath);
            System.out.println("Created company year directory: " + companyYearPath);
        }

        // Define and check required input files
        Map<String, Path> requiredFiles = new HashMap<>();
        requiredFiles.put("balance", companyYearPath.resolve(company + "_balance_" + year + ".csv").normalize());
        requiredFiles.put("income", companyYearPath.resolve(company + "_income_" + year + ".csv").normalize());
        requiredFiles.put("cash_flow", companyYearPath.resolve(company + "_cash_flow_" + year + ".csv").normalize());

        // Verify all required files exist
        System.out.println("\nChecking required files:");
        List<String> missingFiles = new ArrayList<>();
        for (Map.Entry<String, Path> entry : requiredFiles.entrySet()) {
            System.out.println("Checking " + entry.getKey() + " file: " + entry.getValue());
            if (!Files.exists(entry.getValue())) {
                missingFiles.add(entry.getKey() + " (" + entry.getValue() + ")");
            }
        }

        if (!missingFiles.isEmpty()) {
            throw new FileNotFoundException("Missing required input files:\n" + String.join("\n", missingFiles));
        }

        // Setup Python script execution
        Path scriptPath = rootDir.resolve("src")
                .resolve("main")
                .resolve("resources")
                .resolve("PythonScripts")
                .resolve("generate_3statementmodel.py")
                .normalize();

        if (!Files.exists(scriptPath)) {
            throw new FileNotFoundException("Python script not found: " + scriptPath);
        }

        // Prepare output directory
        Path modelOutputDir = companyYearPath.resolve("3statement_model").normalize();
        Files.createDirectories(modelOutputDir);

        // Log execution details
        System.out.println("\nExecuting 3-statement model generation:");
        System.out.println("Script path: " + scriptPath);
        System.out.println("Input directory: " + companyYearPath);
        System.out.println("Output directory: " + modelOutputDir);

        // Prepare command with normalized paths
        String[] command = {
                "python3",
                scriptPath.toString(),
                company,
                year.toString(),
                baseOutputDir.toString(),
                baseOutputDir.toString()
        };


        // Execute Python script
        Process process = executeScript(command);

        // Verify and read output file
        Path outputFile = modelOutputDir.resolve(company + "_3statementmodel_" + year + ".csv").normalize();

        // Wait for file to be generated
        int maxAttempts = 10;
        int attempt = 0;
        while (!Files.exists(outputFile) && attempt < maxAttempts) {
            Thread.sleep(500); // Wait half second between checks
            attempt++;
        }

        if (!Files.exists(outputFile)) {
            throw new FileNotFoundException("Output file was not generated: " + outputFile);
        }

        // Read metrics from the generated file
        Map<String, Double> metrics = readMetricsFromFile(outputFile.toString());

        return new ThreeStatementModelResponse(
                "success",
                "3-statement model generated successfully",
                outputFile.toString(),
                metrics
        );
    }

    /**
     * Read metrics from generated CSV file
     */
    private Map<String, Double> readMetricsFromFile(String filePath) throws IOException {
        Map<String, Double> metrics = new HashMap<>();
        Path path = Paths.get(filePath);

        try (CSVParser parser = CSVParser.parse(path,
                StandardCharsets.UTF_8, CSVFormat.DEFAULT.withFirstRecordAsHeader())) {
            CSVRecord record = parser.iterator().next();
            for (String header : parser.getHeaderNames()) {
                if (!header.equals("Company") && !header.equals("Year")) {
                    try {
                        metrics.put(header, Double.parseDouble(record.get(header)));
                    } catch (NumberFormatException e) {
                        metrics.put(header, 0.0);
                    }
                }
            }
        }
        return metrics;
    }


    public String generateHtml(String company, String statement, int year) throws IOException {
        String csvContent = getCsvContent(company, statement, year);

        // Parse CSV content
        List<String[]> records = new ArrayList<>();
        try (CSVParser parser = CSVParser.parse(csvContent, CSVFormat.DEFAULT.withHeader())) {
            for (CSVRecord record : parser) {
                String[] row = new String[record.size()];
                for (int i = 0; i < record.size(); i++) {
                    row[i] = record.get(i);
                }
                records.add(row);
            }
        }

        // Generate styled HTML table
        StringBuilder tableHtml = new StringBuilder();
        tableHtml.append("<table class='financial-table'>");

        // Add headers
        if (!records.isEmpty()) {
            tableHtml.append("<thead><tr>");
            // Add headers, skipping the depth column (second column)
            for (int i = 0; i < records.get(0).length; i++) {
                if (i != 1) { // Skip the depth column
                    String header = records.get(0)[i];

                    // Format the third column (assume index 2 is the value column)
                    if (i == 2) {
                        try {
                            double value = Double.parseDouble(header); // Parse the header value
                            NumberFormat currencyFormatter = NumberFormat.getCurrencyInstance(Locale.US);
                            header = currencyFormatter.format(value); // Format as currency
                        } catch (NumberFormatException | NullPointerException e) {
                            // Leave the header unchanged if it's not a valid number
                        }
                    }

                    tableHtml.append("<th>").append(header).append("</th>");
                }
            }
            tableHtml.append("</tr></thead>");
        }

        // Add data rows without displaying the depth column
        tableHtml.append("<tbody>");
        for (String[] row : records.subList(1, records.size())) {
            int depth = Integer.parseInt(row[1]); // Use depth for indentation logic only
            String backgroundColor = getBackgroundColor(statement, depth);
            String textColor = depth < 3 ? "white" : "black";

            tableHtml.append("<tr style='")
                    .append(backgroundColor)
                    .append("; color: ")
                    .append(textColor)
                    .append(";'>");

            // Add the first column (Label) with indentation
            String label = "\u00A0".repeat(4 * depth) + row[0];
            tableHtml.append("<td>").append(label).append("</td>");

            // Format the third column (Value) as currency
            String formattedValue;
            try {
                double value = Double.parseDouble(row[2]); // Parse the value as a double
                NumberFormat currencyFormatter = NumberFormat.getCurrencyInstance(Locale.US);
                formattedValue = currencyFormatter.format(value); // Format as currency
            } catch (NumberFormatException | NullPointerException e) {
                formattedValue = "-"; // Handle empty or invalid values
            }

            tableHtml.append("<td>").append(formattedValue).append("</td>");
            tableHtml.append("</tr>");
        }
        tableHtml.append("</tbody></table>");



        // Return complete HTML document
        return String.format("""
            <html>
            <head>
                <title>%s %s %d</title>
                <style>
                    body {
                        font-family: Arial, sans-serif;
                        margin: 20px;
                        background-color: #f5f5f5;
                    }
                    .container {
                        background-color: white;
                        padding: 20px;
                        border-radius: 5px;
                        box-shadow: 0 2px 4px rgba(0,0,0,0.1);
                    }
                    .financial-table {
                        border-collapse: collapse;
                        width: 100%%;
                        margin: 20px 0;
                    }
                    .financial-table th, .financial-table td {
                        padding: 12px 8px;
                        border: 1px solid #ddd;
                        text-align: left;
                    }
                    .financial-table th {
                        background-color: #f8f9fa;
                        font-weight: bold;
                    }
                    .financial-table tr:hover {
                        opacity: 0.9;
                    }
                </style>
            </head>
            <body>
                <div class="container">
                    <h1>%s %s %d</h1>
                    %s
                </div>
            </body>
            </html>
            """,
                company, statement, year,
                company, statement, year,
                tableHtml.toString()
        );
    }

    private String getBackgroundColor(String statementType, int depth) {
        int r, g, b;
        int increment = 40;

        switch (statementType.toLowerCase()) {
            case "balance":
                // Dark blue base
                r = Math.min(1 + (increment * depth), 255);
                g = Math.min(5 + (increment * depth), 255);
                b = Math.min(18 + (increment * depth), 255);
                break;
            case "cash_flow":
                // Dark green base
                r = Math.min(0 + (increment * depth), 255);
                g = Math.min(43 + (increment * depth), 255);
                b = Math.min(0 + (increment * depth), 255);
                break;
            case "income":
                // Dark yellow/brown base
                r = Math.min(58 + (increment * depth), 255);
                g = Math.min(47 + (increment * depth), 255);
                b = Math.min(0 + (increment * depth), 255);
                break;
            default:
                return "background-color: #d0e7ff";
        }

        return String.format("background-color: rgb(%d, %d, %d)", r, g, b);
    }


    /**
     * Get 3-statement model as HTML
     */
    public String get3StatementModelAsHtml(String company, int year) throws IOException {
        Path modelPath = Paths.get(
                System.getProperty("user.dir"),
                outputBaseDir,
                "csvs",  // Add this folder in the path
                "statement_csvs",
                company,
                String.valueOf(year),
                "3statement_model",
                company + "_3statementmodel_" + year + ".csv"
        );


        System.out.println("Looking for file at: " + modelPath.toString());

        if (!Files.exists(modelPath)) {
            throw new FileNotFoundException("3-statement model not found for " + company + " " + year);
        }

        StringBuilder tableHtml = new StringBuilder();
        tableHtml.append("<table class='model-table'>");

        try (CSVParser parser = CSVParser.parse(modelPath,
                StandardCharsets.UTF_8, CSVFormat.DEFAULT.withFirstRecordAsHeader())) {
            // Add headers
            tableHtml.append("<thead><tr>");
            for (String header : parser.getHeaderNames()) {
                tableHtml.append("<th>").append(header).append("</th>");
            }
            tableHtml.append("</tr></thead><tbody>");

            // Add data
            for (CSVRecord record : parser) {
                tableHtml.append("<tr>");
                for (int i = 0; i < record.size(); i++) {
                    String value = record.get(i);

                    // Format as currency for all columns except the first two
                    if (i > 1 && value != null && !value.isEmpty()) {
                        try {
                            double numericValue = Double.parseDouble(value);
                            NumberFormat currencyFormatter = NumberFormat.getCurrencyInstance(Locale.US);
                            value = currencyFormatter.format(numericValue);
                        } catch (NumberFormatException e) {
                            // Leave value unchanged if not a number
                        }
                    }

                    tableHtml.append("<td>").append(value).append("</td>");
                }
                tableHtml.append("</tr>");
            }
        }
        tableHtml.append("</tbody></table>");

        return String.format("""
            <html>
            <head>
                <title>%s 3-Statement Model %d</title>
                <style>
                    body {
                        font-family: Arial, sans-serif;
                        margin: 20px;
                        background-color: #f5f5f5;
                    }
                    .container {
                        background-color: white;
                        padding: 20px;
                        border-radius: 5px;
                        box-shadow: 0 2px 4px rgba(0,0,0,0.1);
                    }
                    .model-table {
                        width: 100%%;
                        border-collapse: collapse;
                        margin-top: 20px;
                    }
                    .model-table th, .model-table td {
                        padding: 12px;
                        border: 1px solid #ddd;
                        text-align: right;
                    }
                    .model-table th {
                        background-color: #f8f9fa;
                        text-align: left;
                    }
                    .section-header {
                        background-color: #e9ecef;
                        font-weight: bold;
                    }
                </style>
            </head>
            <body>
                <div class="container">
                    <h1>%s 3-Statement Model %d</h1>
                    %s
                </div>
            </body>
            </html>
            """,
                company, year,
                company, year,
                tableHtml.toString()
        );
    }

    /**
     * Get 3-statement model as downloadable resource
     */
    public Resource get3StatementModelAsResource(String company, int year) throws IOException {
        Path modelPath = Paths.get(
                System.getProperty("user.dir"),
                outputBaseDir,
                "csvs",
                "statement_csvs",
                company,
                String.valueOf(year),
                "3statement_model",
                company + "_3statementmodel_" + year + ".csv"
        );

        Resource resource = new UrlResource(modelPath.toUri());
        if (resource.exists()) {
            return resource;
        } else {
            throw new FileNotFoundException("3-statement model not found: " + modelPath);
        }
    }

    /**
     * Get structured file listing
     */
    public Map<String, Map<Integer, List<String>>> getStructuredFileList() {
        Map<String, Map<Integer, List<String>>> structure = new HashMap<>();

        // Constructing the base path relative to the outputBaseDir
        Path basePath = Paths.get(outputBaseDir, "csvs", "statement_csvs");

        // Check if the directory exists
        if (!Files.exists(basePath)) {
            throw new RuntimeException("Directory does not exist: " + basePath.toString());
        }

        try (Stream<Path> paths = Files.walk(basePath)) {
            paths.filter(Files::isRegularFile)
                    .forEach(path -> {
                        // Get the relative path from the base directory
                        Path relativePath = basePath.relativize(path);

                        // Ensure the structure is at least two levels deep (company and year)
                        if (relativePath.getNameCount() >= 2) {
                            String company = relativePath.getName(0).toString();
                            try {
                                int year = Integer.parseInt(relativePath.getName(1).toString());
                                String fileName = relativePath.getFileName().toString();

                                // Store the files in the map
                                structure.computeIfAbsent(company, k -> new HashMap<>())
                                        .computeIfAbsent(year, k -> new ArrayList<>())
                                        .add(fileName);
                            } catch (NumberFormatException e) {
                                System.out.println("Invalid year format in path: " + relativePath);
                            }
                        } else {
                            System.out.println("Invalid directory structure: " + relativePath);
                        }
                    });
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException("Error reading files: " + e.getMessage(), e);
        }

        return structure;
    }


    /**
     * Record to hold data availability information
     */
    private record MissingDataInfo(boolean allDataAvailable, List<Integer> missingYears) {}

    /**
     * Check if required data files exist for given CIK and year range
     */
    private MissingDataInfo checkDataAvailability(String cik, Integer fromYear, Integer toYear) {
        List<Integer> missingYears = new ArrayList<>();

        for (int year = fromYear; year <= toYear; year++) {
            // Check existence of 3-statement model file
            Path modelPath = Paths.get(System.getProperty("user.dir"), outputBaseDir,"csvs",
                    "statement_csvs", cik, String.valueOf(year), "3statement_model",
                    cik + "_3statementmodel_" + year + ".csv");

            if (!Files.exists(modelPath)) {
                missingYears.add(year);
            }
        }

        return new MissingDataInfo(missingYears.isEmpty(), missingYears);
    }



    private List<Map<String, Object>> readMetricsFromCsv(String filePath, Integer fromYear, Integer toYear)
            throws IOException {
        Path fullPath = Paths.get(System.getProperty("user.dir"), filePath);
        System.out.println("Reading metrics from: " + fullPath);

        List<Map<String, Object>> metrics = new ArrayList<>();
        try (Reader reader = Files.newBufferedReader(fullPath);
             CSVParser csvParser = new CSVParser(reader, CSVFormat.DEFAULT.withFirstRecordAsHeader())) {

            for (CSVRecord record : csvParser) {
                try {
                    int year = Integer.parseInt(record.get("Year"));
                    if (year >= fromYear && year <= toYear) {
                        Map<String, Object> row = new HashMap<>();
                        for (String header : csvParser.getHeaderNames()) {
                            String value = record.get(header);
                            if (header.equals("Year")) {
                                row.put(header, Integer.parseInt(value));
                            } else if (header.equals("CIK")) {
                                row.put(header, value);
                            } else {
                                try {
                                    row.put(header, Double.parseDouble(value));
                                } catch (NumberFormatException e) {
                                    row.put(header, value);
                                }
                            }
                        }
                        metrics.add(row);
                    }
                } catch (NumberFormatException e) {
                    System.err.println("Error parsing year in CSV: " + e.getMessage());
                }
            }
        }

        return metrics;
    }


    /**
     * Calculate financial metrics for given CIK and year range
     */
    public MetricsCalculationResponse calculateMetrics(String cik, Integer fromYear, Integer toYear)
            throws IOException, InterruptedException {

        try {
            // Set up paths
            String scriptPath = Paths.get(System.getProperty("user.dir"),
                    "src", "main", "resources", "PythonScripts", "calculate_metrics.py").toString();

            String baseDir = Paths.get(System.getProperty("user.dir"), outputBaseDir).toString();

            // Execute Python script
            String[] command = {
                    "python3",
                    scriptPath,
                    cik,
                    fromYear.toString(),
                    toYear.toString(),
                    baseDir,
                    baseDir
            };

            System.out.println("Executing command: " + String.join(" ", command));
            Process process = executeScript(command);

            // Get metrics file paths
            String metricsFilePath = Paths.get(outputBaseDir, "csvs", "statement_csvs", cik,
                    "metrics", cik + "_metrics_" + fromYear + "-" + toYear + ".csv").toString();

            Path metricsPath = Paths.get(System.getProperty("user.dir"), metricsFilePath);

            // Check if file exists and has content
            if (Files.exists(metricsPath) && Files.size(metricsPath) > 0) {
                // Read the metrics from CSV
                List<Map<String, Object>> metrics = readMetricsFromCsv(metricsFilePath, fromYear, toYear);

                return new MetricsCalculationResponse(
                        "success",
                        String.format("Successfully calculated metrics for CIK %s from %d to %d",
                                cik, fromYear, toYear),
                        metricsFilePath,
                        metrics
                );
            } else {
                String alternateMetricsPath = Paths.get(outputBaseDir, "statement_csvs", cik,
                        "metrics", cik + "_metrics.csv").toString();
                Path alternatePath = Paths.get(System.getProperty("user.dir"), alternateMetricsPath);

                if (Files.exists(alternatePath) && Files.size(alternatePath) > 0) {
                    List<Map<String, Object>> metrics = readMetricsFromCsv(alternateMetricsPath, fromYear, toYear);

                    return new MetricsCalculationResponse(
                            "success",
                            String.format("Successfully calculated metrics for CIK %s from %d to %d",
                                    cik, fromYear, toYear),
                            alternateMetricsPath,
                            metrics
                    );
                }

                // If we get here, we couldn't find the metrics file
                throw new FileNotFoundException("Metrics file not found in expected locations");
            }

        } catch (Exception e) {
            String errorMessage = String.format("Error calculating metrics for CIK %s: %s",
                    cik, e.getMessage());
            System.err.println(errorMessage);
            e.printStackTrace();

            return new MetricsCalculationResponse(
                    "error",
                    errorMessage,
                    null,
                    null
            );
        }
    }

    public String getMetricsAsHtml(String cik, int fromYear, int toYear) throws IOException {
        String fileName = cik + "_metrics_" + fromYear + "-" + toYear + ".html";
        Path metricsPath = Paths.get(
                System.getProperty("user.dir"),
                outputBaseDir,
                "csvs",
                "statement_csvs",
                cik,
                "metrics",
                fileName
        );

        if (!Files.exists(metricsPath)) {
            throw new FileNotFoundException("Metrics file not found for " + cik + " from " + fromYear + " to " + toYear);
        }

        return Files.readString(metricsPath, StandardCharsets.UTF_8);
    }

    public MonteCarloResponse runMonteCarloSimulation(MonteCarloRequest request) {
        try {
            // Validate inputs
            Objects.requireNonNull(outputBaseDir, "Output base directory is null");
            Objects.requireNonNull(request, "MonteCarloRequest is null");
            Objects.requireNonNull(request.getCik(), "CIK is null in the request");
            Objects.requireNonNull(request.getNumSimulations(), "Number of simulations is null in the request");
            Objects.requireNonNull(request.getSimulationYears(), "Simulation years is null in the request");
            Objects.requireNonNull(request.getTaxRate(), "Tax rate is null in the request");

            // Debug logs for verification
            System.out.println("Output Base Directory: " + outputBaseDir);
            System.out.println("CIK: " + request.getCik());
            System.out.println("Number of Simulations: " + request.getNumSimulations());
            System.out.println("Simulation Years: " + request.getSimulationYears());
            System.out.println("Tax Rate: " + request.getTaxRate());

            // Construct paths for simulation directory and results file
            String simulationDir = Paths.get(outputBaseDir, "csvs", "statement_csvs", request.getCik(), "simulations").toString();
            Path simCsvPath = Paths.get(simulationDir,
                    request.getCik() + "_monte_carlo_results_" +
                            request.getFromYear() +
                            request.getToYear() +
                            request.getNumSimulations() +
                            request.getSimulationYears() +
                            request.getTaxRate() * 100 + ".csv");
            // Check if cached results exist
            if (Files.exists(simCsvPath)) {
                return new MonteCarloResponse(
                        "success",
                        "Simulation already exists and cached.",
                        simCsvPath.toString(),
                        CSVUtils.readSimulationResults(simCsvPath)
                );
            }

            // Prepare the command for the Python script
            String scriptPath = Paths.get(System.getProperty("user.dir"), "src", "main", "resources", "PythonScripts", "monte_carlo_simulations.py").toString();
            if (!Files.exists(Paths.get(scriptPath))) {
                throw new FileNotFoundException("Python script not found at: " + scriptPath);
            }

            String[] command = {
                    "python3",
                    scriptPath,
                    request.getCik(),
                    request.getNumSimulations().toString(),
                    request.getSimulationYears().toString(),
                    request.getTaxRate().toString(),
                    outputBaseDir,
                    request.getFromYear().toString(),
                    request.getToYear().toString()
            };

            // Debug log for command execution
            System.out.println("Executing command: " + String.join(" ", command));

            // Run the Python script
            Process process = new ProcessBuilder(command).start();

            // Capture script output
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println("Python Script Output: " + line);
                }
            }

            // Wait for the process to complete
            int exitCode = process.waitFor();

            // Capture script error output
            try (BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                String errorLine;
                while ((errorLine = errorReader.readLine()) != null) {
                    System.err.println("Python Script Error: " + errorLine);
                }
            }

            // Check exit code and handle failure
            if (exitCode != 0) {
                throw new RuntimeException("Python script failed with exit code " + exitCode);
            }

            // Check if the simulation results CSV was generated
            if (Files.exists(simCsvPath)) {
                return new MonteCarloResponse(
                        "success",
                        "Simulation completed successfully.",
                        simCsvPath.toString(),
                        CSVUtils.readSimulationResults(simCsvPath)
                );
            } else {
                throw new RuntimeException("Simulation results not generated. Check the Python script for errors.");
            }

        } catch (Exception e) {
            // Handle errors and log them
            System.err.println("Error during Monte Carlo simulation: " + e.getMessage());
            e.printStackTrace();
            return new MonteCarloResponse(
                    "error",
                    "Error during simulation: " + e.getMessage(),
                    null,
                    null
            );
        }
    }

    public List<String> listCiksWithMetrics() throws IOException {
        Path basePath = Paths.get(outputBaseDir, "csvs", "statement_csvs");
        if (!Files.exists(basePath)) throw new FileNotFoundException("Base directory not found!");

        // Return a list of CIK directories with a "metrics" subfolder
        try (Stream<Path> paths = Files.walk(basePath, 1)) {
            return paths.filter(path -> Files.isDirectory(path.resolve("metrics")))
                    .map(path -> path.getFileName().toString())
                    .collect(Collectors.toList());
        }
    }

    public List<String> listMetricFilesForCik(String cik) throws IOException {
        Path metricsDir = Paths.get(outputBaseDir, "csvs", "statement_csvs", cik, "metrics");
        if (!Files.exists(metricsDir)) throw new FileNotFoundException("Metrics directory not found for CIK: " + cik);

        // Return a list of all CSV files in the metrics folder
        try (Stream<Path> paths = Files.list(metricsDir)) {
            return paths.filter(path -> path.toString().endsWith(".csv"))
                    .map(path -> path.getFileName().toString())
                    .collect(Collectors.toList());
        }
    }

    public WaccCalculationResponse runWaccCalculation(WaccCalculationRequest request) {
        try {
            // Paths
            String cik = request.getCik();
            String ticker = request.getTicker(); // Use ticker here
            Path metricsFilePath = Paths.get(outputBaseDir, "csvs", "statement_csvs", cik, "metrics", request.getMetricFile());
            Path waccDir = Paths.get(outputBaseDir, "csvs", "statement_csvs", cik, "wacc");
            Files.createDirectories(waccDir);

            // Output WACC file path
            String outputFileName = cik + "_wacc_results_" + request.getMetricFile().replace("^.*metrics_", "");
            Path waccFilePath = waccDir.resolve(outputFileName);

            // Python script path
            Path scriptPath = Paths.get(System.getProperty("user.dir"), "src", "main", "resources", "PythonScripts", "wacc_calculation.py");
            if (!Files.exists(scriptPath)) throw new FileNotFoundException("Python script not found at: " + scriptPath);

            // Command for ProcessBuilder
            String[] command = {
                    "python3",
                    scriptPath.toString(),
                    metricsFilePath.toString(),
                    waccFilePath.toString(),
                    request.getCik(), // CIK for output file naming consistency
                    request.getTicker(), // Pass ticker to the Python script
                    request.getRiskFreeRate().toString(),
                    request.getMarketReturn().toString(),
                    request.getTaxRate().toString(),
                    request.getStartDate(),
                    request.getEndDate(),
                    request.getCostOfDebt().toString()
            };

            // Execute the Python script
            Process process = new ProcessBuilder(command).start();

            // Capture output
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println("[PYTHON OUTPUT]: " + line);
                }
            }

            // Wait for process to complete
            int exitCode = process.waitFor();
            if (exitCode != 0) throw new RuntimeException("Python script failed with exit code: " + exitCode);

            if (!Files.exists(waccFilePath)) throw new RuntimeException("WACC results file not generated!");

            return new WaccCalculationResponse("success", "WACC calculation completed successfully.", waccFilePath.toString(), null);
        } catch (Exception e) {
            e.printStackTrace();
            return new WaccCalculationResponse("error", "Error during WACC calculation: " + e.getMessage(), null, null);
        }
    }

    public List<String> getMetricFiles(String cik) throws IOException {
        String metricsDirPath = Paths.get(outputBaseDir, "csvs", "statement_csvs", cik, "metrics").toString();
        Path metricsDir = Paths.get(metricsDirPath);

        if (!Files.exists(metricsDir)) {
            throw new FileNotFoundException("Metrics directory does not exist for CIK: " + cik);
        }

        try (Stream<Path> paths = Files.list(metricsDir)) {
            return paths
                    .filter(Files::isRegularFile)
                    .map(Path::getFileName)
                    .map(Path::toString)
                    .filter(fileName -> fileName.endsWith(".csv"))
                    .collect(Collectors.toList());
        }
    }

    public List<String> getWaccCsvContent(String filePath) throws IOException {
        Path csvFile = Paths.get(filePath);

        if (!Files.exists(csvFile)) {
            throw new FileNotFoundException("CSV file not found at: " + filePath);
        }

        // Read all lines from the CSV
        try (Stream<String> lines = Files.lines(csvFile)) {
            return lines.collect(Collectors.toList()); // Collect as a list of strings
        }
    }


}

