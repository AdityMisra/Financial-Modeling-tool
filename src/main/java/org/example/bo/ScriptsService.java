package org.example.bo;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.vo.extractTableResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

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

    public extractTableResponse extractTables(Map<String, String> companies, List<Integer> years) {
        try {
            String companiesArg = objectMapper.writeValueAsString(companies);
            String yearsArg = objectMapper.writeValueAsString(years);

            String scriptPath = Paths.get(System.getProperty("user.dir"), pythonScriptPath).toString();
            String gaapTaxonomy = Paths.get(System.getProperty("user.dir"), gaapTaxonomyPath).toString();
            String outputPath = Paths.get(System.getProperty("user.dir"), outputBaseDir).toString();

            // Create base directory for statements
            Files.createDirectories(Paths.get(outputPath, "statement_csvs"));

            String[] command = {"python3", scriptPath, companiesArg, yearsArg, gaapTaxonomy, outputPath};
            Process process = executeScript(command);

            List<String> csvFiles = listFiles(Paths.get(outputPath, "statement_csvs"));

            return new extractTableResponse(
                    "Success",
                    String.format("Generated financial statements", csvFiles.size()),
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


    private Process executeScript(String[] command) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        Process process = pb.start();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println("Python Output: " + line);
            }
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new RuntimeException("Python script failed with exit code " + exitCode);
        }

        return process;
    }

    public List<String> getGeneratedCsvFiles() {
        Path basePath = Paths.get(System.getProperty("user.dir"), outputBaseDir, "statement_csvs");
        Map<String, Map<String, List<String>>> fileStructure = new HashMap<>();

        try (Stream<Path> paths = Files.walk(basePath)) {
            List<String> files = paths
                    .filter(Files::isRegularFile)
                    .map(path -> {
                        // Get relative path from base directory
                        Path relativePath = basePath.relativize(path);
                        return relativePath.toString();
                    })
                    .collect(Collectors.toList());

            return files;
        } catch (IOException e) {
            return new ArrayList<>();
        }
    }


    private List<String> listFiles(Path directory) {
        try (Stream<Path> paths = Files.walk(directory)) {
            return paths
                    .filter(Files::isRegularFile)
                    .map(Path::getFileName)
                    .map(Path::toString)
                    .collect(Collectors.toList());
        } catch (IOException e) {
            return new ArrayList<>();
        }
    }

    public String getCsvContent(String company, String statement, int year) throws IOException {
        Path csvPath = Paths.get(
                System.getProperty("user.dir"),
                outputBaseDir,
                "statement_csvs",
                company,
                String.valueOf(year),
                String.format("%s_%s_%d.csv", company, statement, year)
        );
        return Files.readString(csvPath);
    }


    public Resource getCsvAsResource(String company, String statement, int year) throws IOException {
        Path csvPath = Paths.get(
                System.getProperty("user.dir"),
                outputBaseDir,
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
            for (String header : records.get(0)) {
                tableHtml.append("<th>").append(header).append("</th>");
            }
            tableHtml.append("</tr></thead>");
        }

        // Add data rows with styling based on depth
        tableHtml.append("<tbody>");
        for (String[] row : records.subList(1, records.size())) {
            int depth = Integer.parseInt(row[1]); // Assuming depth is in second column
            String backgroundColor = getBackgroundColor(statement, depth);
            String textColor = depth < 3 ? "white" : "black";

            tableHtml.append("<tr style='")
                    .append(backgroundColor)
                    .append("; color: ")
                    .append(textColor)
                    .append(";'>");

            // Add indentation to API Key
            String apiKey = "\u00A0".repeat(4 * depth) + row[0];
            tableHtml.append("<td>").append(apiKey).append("</td>");

            // Add remaining columns
            for (int i = 1; i < row.length; i++) {
                tableHtml.append("<td>").append(row[i]).append("</td>");
            }
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

    public Map<String, Map<Integer, List<String>>> getStructuredFileList() {
        Map<String, Map<Integer, List<String>>> structure = new HashMap<>();
        Path basePath = Paths.get(System.getProperty("user.dir"), outputBaseDir, "statement_csvs");

        try (Stream<Path> paths = Files.walk(basePath)) {
            paths.filter(Files::isRegularFile)
                    .forEach(path -> {
                        Path relativePath = basePath.relativize(path);
                        String company = relativePath.getName(0).toString();
                        int year = Integer.parseInt(relativePath.getName(1).toString());
                        String fileName = relativePath.getFileName().toString();

                        structure.computeIfAbsent(company, k -> new HashMap<>())
                                .computeIfAbsent(year, k -> new ArrayList<>())
                                .add(fileName);
                    });
        } catch (IOException e) {
            e.printStackTrace();
        }

        return structure;
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
}