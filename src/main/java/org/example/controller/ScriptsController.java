package org.example.controller;

import org.example.bo.ScriptsService;
import org.example.vo.extractTableRequest;
import org.example.vo.extractTableResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class ScriptsController {

    @Autowired
    private ScriptsService scriptsService;

    @PostMapping("/extract-tables")
    public extractTableResponse extractTables(@RequestBody extractTableRequest request) {
        // Delegate to the service
        return scriptsService.extractTables(request.getCompanies(), request.getYears());
    }
}