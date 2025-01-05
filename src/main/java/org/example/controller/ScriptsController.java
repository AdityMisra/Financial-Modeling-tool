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

    @PostMapping("/extract-tables")
    public extractTableResponse extractTables(@RequestBody extractTableRequest request) {
        return scriptsService.extractTables(request.getCompanies(), request.getYears());
    }

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
}