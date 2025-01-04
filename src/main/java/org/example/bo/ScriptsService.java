package org.example.bo;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.vo.extractTableResponse;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class ScriptsService {

    public extractTableResponse extractTables(Map<String, String> companies, List<Integer> years) {
        try {
            System.out.println("=== Java Service Execution Started ===");
            System.out.println("Companies: " + companies);
            System.out.println("Years: " + years);

            // Convert inputs to JSON-like strings
            String companiesArg = new ObjectMapper().writeValueAsString(companies);
            String yearsArg = new ObjectMapper().writeValueAsString(years);

            System.out.println("Formatted companies: " + companiesArg);
            System.out.println("Formatted years: " + yearsArg);

            // Path to the Python script
            String scriptPath = Paths.get(System.getProperty("user.dir"), "src", "main", "resources", "PythonScripts", "extract_tables.py").toString();
            System.out.println("Python script path: " + scriptPath);

            // Prepare command
            String[] command = {"python3", scriptPath, companiesArg, yearsArg};
            System.out.println("Command: " + String.join(" ", command));

            // Execute the Python script
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true); // Merge stdout and stderr for easier debugging
            Process process = pb.start();

            // Capture output
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
                System.out.println("Python Output: " + line); // Print Python output for debugging
            }

            // Wait for the process to complete
            int exitCode = process.waitFor();
            System.out.println("Python script exit code: " + exitCode);

            if (exitCode != 0) {
                throw new RuntimeException("Python script failed with exit code " + exitCode + ":\n" + output.toString());
            }

            System.out.println("Python script executed successfully.");
            return new extractTableResponse("Success", "Files generated successfully.", new ArrayList<>(), new ArrayList<>());

        } catch (Exception e) {
            System.err.println("An error occurred while executing the Python script.");
            e.printStackTrace();
            throw new RuntimeException("Error while executing Python script: " + e.getMessage(), e);
        }
    }


}