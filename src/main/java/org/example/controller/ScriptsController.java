package org.example.controller;

import org.example.bo.ScriptsService;
import org.example.vo.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")  // Allow all origins
public class ScriptsController {

    @Autowired
    private ScriptsService scriptsService;

    @Value("${output.base.dir}")
    private String outputBaseDir;

    /**
     * Extract financial tables from SEC data
     */
    @PostMapping("/extractTables")
    public ResponseEntity<extractTableResponse> extractTables(@RequestBody extractTableRequest request) {
        try {
            List<String> ciks = request.getCiks();
            List<Integer> years = request.getYears();

            // Check for empty input lists
            if (ciks.isEmpty() || years.isEmpty()) {
                return ResponseEntity.badRequest().body(
                        new extractTableResponse("error", "CIKs and years lists cannot be empty.", null, null)
                );
            }

            // Call the ScriptService's extractTables method
            extractTableResponse response = scriptsService.extractTables(ciks, years);

            // Return the response from ScriptService
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(
                    new extractTableResponse("error", e.getMessage(), null, null)
            );
        }
    }


    /**
     * List all generated files
     */
    @GetMapping("/files/list")
    public ResponseEntity<Map<String, List<String>>> listFiles() {
        try {
            Map<String, List<String>> files = new HashMap<>();
            files.put("csvFiles", scriptsService.getGeneratedCsvFiles());
            return ResponseEntity.ok(files);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * View CSV file content
     */
    @GetMapping("/files/view/csv/{cik}/{statement}/{year}")
    public ResponseEntity<String> viewCsvContent(
            @PathVariable String cik,
            @PathVariable String statement,
            @PathVariable Integer year) {
        try {
            String csvContent = scriptsService.getCsvContent(cik, statement, year);
            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_PLAIN)
                    .body(csvContent);
        } catch (IOException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Download CSV file
     */
    @GetMapping("/files/download/csv/{cik}/{statement}/{year}")
    public ResponseEntity<Resource> downloadCsvFile(
            @PathVariable String cik,
            @PathVariable String statement,
            @PathVariable Integer year) {
        try {
            Resource resource = scriptsService.getCsvAsResource(cik, statement, year);
            String filename = String.format("%s_%s_%d.csv", cik, statement, year);
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("text/csv"))
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + filename + "\"")
                    .body(resource);
        } catch (IOException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/files/view/html/{company}/{statement}/{year}")
    public ResponseEntity<String> viewStatementAsHtml(
            @PathVariable String company,
            @PathVariable String statement,
            @PathVariable Integer year) {
        try {
            String html = scriptsService.generateHtml(company, statement, year);
            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_HTML)
                    .body(html);
        } catch (IOException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Generate 3-statement model
     */
    @PostMapping("/generate-3statement-model")
    public ResponseEntity<ThreeStatementModelResponse> generate3StatementModel(
            @RequestBody ThreeStatementModelRequest request) {
        try {
            ThreeStatementModelResponse response = scriptsService.generate3StatementModel(
                    request.getCik(),
                    request.getYear()
            );
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(
                    new ThreeStatementModelResponse(
                            "error",
                            e.getMessage(),
                            null,
                            null
                    )
            );
        }
    }

    /**
     * View 3-statement model as HTML
     */
    @GetMapping("/view/3statement-model/{cik}/{year}")
    public ResponseEntity<String> view3StatementModel(
            @PathVariable String cik,
            @PathVariable Integer year) {

        try {
            String html = scriptsService.get3StatementModelAsHtml(cik, year);
            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_HTML)
                    .body(html);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
    }

    /**
     * Download 3-statement model CSV
     */
    @GetMapping("/download/3statement-model/{cik}/{year}")
    public ResponseEntity<Resource> download3StatementModel(
            @PathVariable String cik,
            @PathVariable Integer year) {
        try {
            Resource resource = scriptsService.get3StatementModelAsResource(cik, year);
            String filename = String.format("%s_3statementmodel_%d.csv", cik, year);

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("text/csv"))
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + filename + "\"")
                    .body(resource);
        } catch (IOException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Get structured file listing
     */
    @GetMapping("/files/structure")
    public ResponseEntity<Map<String, Map<Integer, List<String>>>> getFileStructure() {
        try {
            Map<String, Map<Integer, List<String>>> structure = scriptsService.getStructuredFileList();
            return ResponseEntity.ok(structure);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }


    @PostMapping("/calculate-metrics")
    public ResponseEntity<MetricsCalculationResponse> calculateMetrics(
            @RequestBody MetricsCalculationRequest request) {
        try {
            return ResponseEntity.ok(scriptsService.calculateMetrics(
                    request.getCik(),         // Make sure using getCik()
                    request.getFromYear(),
                    request.getToYear()
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(
                    new MetricsCalculationResponse(
                            "error",
                            e.getMessage(),
                            null,
                            null
                    )
            );
        }
    }

    @GetMapping("/view-metrics/{cik}")
    public ResponseEntity<Resource> viewMetricsCsv(@PathVariable String cik) {
        try {
            Path filePath = Paths.get(System.getProperty("user.dir"), outputBaseDir,"csvs",
                    "statement_csvs", cik, "metrics", cik + "_all_metrics.csv");

            Resource resource = new UrlResource(filePath.toUri());

            if (resource.exists()) {
                return ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType("text/csv"))
                        .header(HttpHeaders.CONTENT_DISPOSITION,
                                "attachment; filename=\"" + cik + "_metrics.csv\"")
                        .body(resource);
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (IOException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/monte-carlo-simulation")
    public ResponseEntity<MonteCarloResponse> runMonteCarloSimulation(@RequestBody MonteCarloRequest request) {
        try {
            // First check if metrics exist
            String metricsPath = Paths.get(outputBaseDir, "csvs", "statement_csvs",
                    request.getCik(), "metrics", request.getCik() + "_metrics.csv").toString();

            if (!Files.exists(Paths.get(metricsPath))) {
                return ResponseEntity.badRequest().body(
                        new MonteCarloResponse(
                                "error",
                                "Metrics file not found. Please calculate metrics first.",
                                null,
                                null
                        )
                );
            }

            return ResponseEntity.ok(scriptsService.runMonteCarloSimulation(
                    request.getCik(),
                    request.getNumSimulations(),
                    request.getSimulationYears(),
                    request.getTaxRate()
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(
                    new MonteCarloResponse(
                            "error",
                            e.getMessage(),
                            null,
                            null
                    )
            );
        }
    }

    @GetMapping("/view/simulation/{cik}")
    public ResponseEntity<Resource> viewSimulationResults(@PathVariable String cik) {
        try {
            Path filePath = Paths.get(System.getProperty("user.dir"), outputBaseDir,
                    "statement_csvs", cik, "simulations", cik + "_monte_carlo_results.html");

            Resource resource = new UrlResource(filePath.toUri());

            if (resource.exists()) {
                return ResponseEntity.ok()
                        .contentType(MediaType.TEXT_HTML)
                        .body(resource);
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (IOException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/download/simulation/{cik}")
    public ResponseEntity<Resource> downloadSimulationResults(@PathVariable String cik) {
        try {
            Path filePath = Paths.get(System.getProperty("user.dir"), outputBaseDir,
                    "statement_csvs", cik, "simulations", cik + "_monte_carlo_results.csv");

            Resource resource = new UrlResource(filePath.toUri());

            if (resource.exists()) {
                return ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType("text/csv"))
                        .header(HttpHeaders.CONTENT_DISPOSITION,
                                "attachment; filename=\"" + cik + "_monte_carlo_results.csv\"")
                        .body(resource);
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (IOException e) {
            return ResponseEntity.notFound().build();
        }
    }
}