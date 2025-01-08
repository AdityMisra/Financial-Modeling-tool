package org.example.utils;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

public class CSVUtils {

    public static List<Map<String, Object>> readSimulationResults(Path filePath) throws IOException {
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