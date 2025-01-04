package org.example.vo;

import java.util.List;

public class extractTableResponse {
    private String status;
    private String message;
    private List<String> csvFiles;
    private List<String> htmlFiles;

    public extractTableResponse(String status, String message, List<String> csvFiles, List<String> htmlFiles) {
        this.status = status;
        this.message = message;
        this.csvFiles = csvFiles;
        this.htmlFiles = htmlFiles;
    }

    // Getters and setters
    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public List<String> getCsvFiles() {
        return csvFiles;
    }

    public void setCsvFiles(List<String> csvFiles) {
        this.csvFiles = csvFiles;
    }

    public List<String> getHtmlFiles() {
        return htmlFiles;
    }

    public void setHtmlFiles(List<String> htmlFiles) {
        this.htmlFiles = htmlFiles;
    }
}