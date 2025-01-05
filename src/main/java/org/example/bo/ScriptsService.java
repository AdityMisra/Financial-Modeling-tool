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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
     * Extract tables using Python script
     */
    public extractTableResponse extractTables(Map<String, String> companies, List<Integer> years) {
        try {
            System.out.println("=== Java Service Execution Started ===");

            // Convert inputs to JSON strings
            String companiesArg = objectMapper.writeValueAsString(companies);
            String yearsArg = objectMapper.writeValueAsString(years);

            // Prepare paths
            String scriptPath = Paths.get(System.getProperty("user.dir"), pythonScriptPath).toString();
            String gaapTaxonomy = Paths.get(System.getProperty("user.dir"), gaapTaxonomyPath).toString();
            String outputPath = Paths.get(System.getProperty("user.dir"), outputBaseDir).toString();

            // Create output directories
            Files.createDirectories(Paths.get(outputPath, "csvs"));
            Files.createDirectories(Paths.get(outputPath, "html"));

            // Execute Python script
            Process process = executeScript(scriptPath, companiesArg, yearsArg, gaapTaxonomy, outputPath);

            // Process results
            List<String> csvFiles = listFiles(Paths.get(outputPath, "csvs"));
            List<String> htmlFiles = listFiles(Paths.get(outputPath, "html"));

            return new extractTableResponse(
                    "Success",
                    String.format("Generated %d CSV files and %d HTML files", csvFiles.size(), htmlFiles.size()),
                    csvFiles,
                    htmlFiles
            );

        } catch (Exception e) {
            System.err.println("Error executing Python script: " + e.getMessage());
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
     * Execute Python script and capture output
     */
    private Process executeScript(String scriptPath, String companiesArg, String yearsArg,
                                  String gaapTaxonomy, String outputPath)
            throws IOException, InterruptedException {

        String[] command = {"python3", scriptPath, companiesArg, yearsArg, gaapTaxonomy, outputPath};
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        Process process = pb.start();

        // Capture output
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

    /**
     * List files in a directory
     */
    private List<String> listFiles(Path directory) {
        try (Stream<Path> paths = Files.walk(directory)) {
            return paths
                    .filter(Files::isRegularFile)
                    .map(Path::getFileName)
                    .map(Path::toString)
                    .collect(Collectors.toList());
        } catch (IOException e) {
            System.err.println("Error listing files in directory: " + directory);
            return new ArrayList<>();
        }
    }

    /**
     * Get all generated CSV files
     */
    public List<String> getGeneratedCsvFiles() {
        return listFiles(Paths.get(System.getProperty("user.dir"), outputBaseDir, "csvs"));
    }

    /**
     * Get all generated HTML files
     */
    public List<String> getGeneratedHtmlFiles() {
        return listFiles(Paths.get(System.getProperty("user.dir"), outputBaseDir, "html"));
    }

    /**
     * Get file content as string
     */
    public String getFileContent(String fileType, String fileName) throws IOException {
        validateFileType(fileType);
        Path filePath = getFilePath(fileType, fileName);
        return Files.readString(filePath);
    }

    /**
     * Get file as Resource for downloading
     */
    public Resource getFileAsResource(String fileType, String fileName) throws IOException {
        validateFileType(fileType);
        Path filePath = getFilePath(fileType, fileName);
        Resource resource = new UrlResource(filePath.toUri());

        if (resource.exists()) {
            return resource;
        } else {
            throw new FileNotFoundException("File not found: " + fileName);
        }
    }

    /**
     * Validate file type (csvs or html)
     */
    private void validateFileType(String fileType) {
        if (!fileType.equals("csvs") && !fileType.equals("html")) {
            throw new IllegalArgumentException("Invalid file type: " + fileType);
        }
    }

    /**
     * Get full file path
     */
    private Path getFilePath(String fileType, String fileName) {
        return Paths.get(System.getProperty("user.dir"), outputBaseDir, fileType, fileName);
    }
}


