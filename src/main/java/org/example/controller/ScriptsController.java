package org.example.controller;

import org.example.bo.ScriptsService;
import org.example.vo.extractTableRequest;
import org.example.vo.extractTableResponse;
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
     * List all generated files (both CSV and HTML)
     */
    @GetMapping("/files/list")
    public ResponseEntity<Map<String, List<String>>> listFiles() {
        try {
            Map<String, List<String>> files = new HashMap<>();
            files.put("csvFiles", scriptsService.getGeneratedCsvFiles());
            files.put("htmlFiles", scriptsService.getGeneratedHtmlFiles());
            return ResponseEntity.ok(files);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * View CSV file content
     */
    @GetMapping("/files/view/csv/{fileName}")
    public ResponseEntity<String> viewCsvContent(@PathVariable String fileName) {
        try {
            String content = scriptsService.getFileContent("csvs", fileName);
            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_PLAIN)
                    .body(content);
        } catch (IOException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Download CSV file
     */
    @GetMapping("/files/download/csv/{fileName}")
    public ResponseEntity<Resource> downloadCsvFile(@PathVariable String fileName) {
        try {
            Resource resource = scriptsService.getFileAsResource("csvs", fileName);
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("text/csv"))
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + fileName + "\"")
                    .body(resource);
        } catch (IOException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * View HTML content for direct rendering
     */
    @GetMapping("/files/view/html/{fileName}")
    public ResponseEntity<String> viewHtmlContent(@PathVariable String fileName) {
        try {
            String content = scriptsService.getFileContent("html", fileName);
            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_HTML)
                    .body(content);
        } catch (IOException e) {
            return ResponseEntity.notFound().build();
        }
    }




}