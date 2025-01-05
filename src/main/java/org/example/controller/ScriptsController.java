package org.example.controller;

import org.example.bo.ScriptsService;
import org.example.vo.extractTableRequest;
import org.example.vo.extractTableResponse;
import org.example.vo.ThreeStatementModelRequest;
import org.example.vo.ThreeStatementModelResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ScriptsController {

    @Autowired
    private ScriptsService scriptsService;

    /**
     * Extract financial tables from SEC data
     */
    @PostMapping("/extract-tables")
    public extractTableResponse extractTables(@RequestBody extractTableRequest request) {
        return scriptsService.extractTables(request.getCompanies(), request.getYears());
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
    @GetMapping("/files/view/csv/{company}/{statement}/{year}")
    public ResponseEntity<String> viewCsvContent(
            @PathVariable String company,
            @PathVariable String statement,
            @PathVariable Integer year) {
        try {
            String csvContent = scriptsService.getCsvContent(company, statement, year);
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
    @GetMapping("/files/download/csv/{company}/{statement}/{year}")
    public ResponseEntity<Resource> downloadCsvFile(
            @PathVariable String company,
            @PathVariable String statement,
            @PathVariable Integer year) {
        try {
            Resource resource = scriptsService.getCsvAsResource(company, statement, year);
            String filename = String.format("%s_%s_%d.csv", company, statement, year);
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
     * Generate 3-statement model
     */
    @PostMapping("/generate-3statement-model")
    public ResponseEntity<ThreeStatementModelResponse> generate3StatementModel(
            @RequestBody ThreeStatementModelRequest request) {
        try {
            ThreeStatementModelResponse response = scriptsService.generate3StatementModel(
                    request.getCompany(),
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
    @GetMapping("/view/3statement-model/{company}/{year}")
    public ResponseEntity<String> view3StatementModel(
            @PathVariable String company,
            @PathVariable Integer year) {
        try {
            String html = scriptsService.get3StatementModelAsHtml(company, year);
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
    @GetMapping("/download/3statement-model/{company}/{year}")
    public ResponseEntity<Resource> download3StatementModel(
            @PathVariable String company,
            @PathVariable Integer year) {
        try {
            Resource resource = scriptsService.get3StatementModelAsResource(company, year);
            String filename = String.format("%s_3statementmodel_%d.csv", company, year);

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
}