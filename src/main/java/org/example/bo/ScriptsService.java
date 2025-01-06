package org.example.bo;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.vo.MetricsCalculationResponse;
import org.example.vo.MonteCarloResponse;
import org.example.vo.extractTableResponse;
import org.example.vo.ThreeStatementModelResponse;
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
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
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
    public extractTableResponse extractTables(List <String> ciks, List<Integer> years) {
        try {
            String companiesArg = objectMapper.writeValueAsString(ciks);
            String yearsArg = objectMapper.writeValueAsString(years);

            String scriptPath = Paths.get(System.getProperty("user.dir"), pythonScriptPath).toString();
            String gaapTaxonomy = Paths.get(System.getProperty("user.dir"), gaapTaxonomyPath).toString();
            String outputPath = Paths.get(System.getProperty("user.dir"), outputBaseDir).toString();

            // Create directory structure
            Files.createDirectories(Paths.get(outputPath, "statement_csvs"));

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
                for (String value : record) {
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
        Path basePath = Paths.get(System.getProperty("user.dir"), outputBaseDir,"csvs", "statement_csvs");

        try (Stream<Path> paths = Files.walk(basePath)) {
            paths.filter(Files::isRegularFile)
                    .forEach(path -> {
                        Path relativePath = basePath.relativize(path);
                        if (relativePath.getNameCount() >= 2) {
                            String company = relativePath.getName(0).toString();
                            int year = Integer.parseInt(relativePath.getName(1).toString());
                            String fileName = relativePath.getFileName().toString();

                            structure.computeIfAbsent(company, k -> new HashMap<>())
                                    .computeIfAbsent(year, k -> new ArrayList<>())
                                    .add(fileName);
                        }
                    });
        } catch (IOException e) {
            e.printStackTrace();
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
                    "metrics", cik + "_metrics.csv").toString();

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

    public MonteCarloResponse runMonteCarloSimulation(String cik, Integer numSimulations,
                                                      Integer simulationYears, Double taxRate) throws IOException, InterruptedException {

        try {
            // Set up paths
            String scriptPath = Paths.get(System.getProperty("user.dir"),
                    "src", "main", "resources", "PythonScripts", "monte_carlo_simulations.py").toString();

            String baseDir = Paths.get(System.getProperty("user.dir"), outputBaseDir).toString();

            // Verify metrics file exists
            Path metricsPath = Paths.get(baseDir,"csvs", "statement_csvs", cik, "metrics",
                    cik + "_metrics.csv");
            if (!Files.exists(metricsPath)) {
                return new MonteCarloResponse(
                        "error",
                        "Metrics file not found. Please calculate metrics first.",
                        null,
                        null
                );
            }

            // Execute Python script
            String[] command = {
                    "python3",
                    scriptPath,
                    cik,
                    numSimulations.toString(),
                    simulationYears.toString(),
                    taxRate.toString(),
                    baseDir
            };

            System.out.println("Executing Monte Carlo simulation command: " + String.join(" ", command));
            Process process = executeScript(command);

            // Check for simulation results
            String simulationPath = Paths.get(outputBaseDir, "csvs","statement_csvs", cik,
                    "simulations", cik + "_monte_carlo_results.csv").toString();

            Path simFilePath = Paths.get(System.getProperty("user.dir"), simulationPath);

            if (Files.exists(simFilePath)) {
                // Read simulation results
                List<Map<String, Object>> results = readSimulationResults(simFilePath);

                return new MonteCarloResponse(
                        "success",
                        String.format("Successfully generated Monte Carlo simulation with %d simulations for %d years",
                                numSimulations, simulationYears),
                        simulationPath,
                        results
                );
            } else {
                return new MonteCarloResponse(
                        "error",
                        "Simulation failed: Output file not generated",
                        null,
                        null
                );
            }

        } catch (Exception e) {
            String errorMessage = String.format("Error running Monte Carlo simulation for CIK %s: %s",
                    cik, e.getMessage());
            System.err.println(errorMessage);
            e.printStackTrace();

            return new MonteCarloResponse(
                    "error",
                    errorMessage,
                    null,
                    null
            );
        }
    }

    private List<Map<String, Object>> readSimulationResults(Path filePath) throws IOException {
        List<Map<String, Object>> results = new ArrayList<>();
        try (Reader reader = Files.newBufferedReader(filePath);
             CSVParser csvParser = CSVParser.parse(reader, CSVFormat.DEFAULT.withFirstRecordAsHeader())) {

            for (CSVRecord record : csvParser) {
                Map<String, Object> row = new HashMap<>();
                for (String header : csvParser.getHeaderNames()) {
                    String value = record.get(header);
                    try {
                        if (header.equals("Year") || header.equals("Simulation")) {
                            row.put(header, Integer.parseInt(value));
                        } else if (header.equals("CIK")) {
                            row.put(header, value);
                        } else {
                            row.put(header, Double.parseDouble(value));
                        }
                    } catch (NumberFormatException e) {
                        row.put(header, value);
                    }
                }
                results.add(row);
            }
        }
        return results;
    }
}