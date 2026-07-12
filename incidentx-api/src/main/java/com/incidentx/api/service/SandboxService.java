package com.incidentx.api.service;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class SandboxService {

    @Value("${incidentx.sandbox.temp-dir:C:/Users/Kanishk/AppData/Local/Temp/incidentx}")
    private String baseTempDir;

    @Value("${incidentx.sandbox.image-name:incidentx-sandbox-node}")
    private String dockerImageName;

    @Value("${incidentx.sandbox.timeout-seconds:4}")
    private int timeoutSeconds;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class SandboxResult {
        private String status; // "PASSED", "FAILED", "ERROR", "TIMEOUT"
        private String resultsJson; // Parsed results from runner.js JSON or error details
        private String stdout;
        private String stderr;
    }

    public SandboxResult runSubmission(String submissionId, Map<String, String> files, String hiddenTests) {
        // Create a unique temporary directory for this submission
        String uniqueId = UUID.randomUUID().toString();
        Path submissionTempPath = Paths.get(baseTempDir, "sub_" + uniqueId);
        
        try {
            Files.createDirectories(submissionTempPath);
            log.info("Created temporary directory for submission: {}", submissionTempPath);

            // Write all files (user code, etc.)
            for (Map.Entry<String, String> entry : files.entrySet()) {
                Path filePath = submissionTempPath.resolve(entry.getKey());
                // Create parent directories if any (e.g. src/app.js)
                Files.createDirectories(filePath.getParent());
                Files.writeString(filePath, entry.getValue(), StandardCharsets.UTF_8);
            }

            // Write hidden test suite to tests.js
            Files.writeString(submissionTempPath.resolve("tests.js"), hiddenTests, StandardCharsets.UTF_8);

            // Path to mount, mapped properly for Docker Desktop
            String hostPath = submissionTempPath.toAbsolutePath().toString().replace("\\", "/");

            // Build docker command
            String containerName = "incidentx-sub-" + uniqueId;
            List<String> command = new ArrayList<>(List.of(
                "docker", "run", "--rm",
                "--name", containerName,
                "--network", "none",
                "--memory=256m",
                "--cpus=0.5",
                "--pids-limit=64",
                "--read-only",
                "--tmpfs", "/tmp:rw,noexec,nosuid,size=16m",
                "--user=node",
                "-v", hostPath + ":/workspace/submission:ro",
                dockerImageName
            ));

            log.info("Running Docker sandbox: {}", String.join(" ", command));
            ProcessBuilder pb = new ProcessBuilder(command);
            Process process = pb.start();

            // Set up stream reading
            byte[] stdoutBytes;
            byte[] stderrBytes;
            try (var stdoutStream = process.getInputStream();
                 var stderrStream = process.getErrorStream()) {
                
                // Read input concurrently / asynchronously, or immediately read after wait.
                // Since user process runs quick, we can wait and then read, but to prevent blocks, 
                // we should read after or use readAllBytes if the buffers are small.
                // To avoid hanging due to full buffer, we read them.
                // In simple implementations we read them after process completion or asynchronously.
                // We'll wait with timeout first.
                boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
                
                if (!finished) {
                    log.warn("Submission {} timed out in sandbox. Force killing container.", submissionId);
                    process.destroyForcibly();
                    // Force terminate the Docker container using docker kill
                    new ProcessBuilder("docker", "kill", containerName).start().waitFor(2, TimeUnit.SECONDS);
                    return new SandboxResult("TIMEOUT", "{\"error\": \"Execution timed out after " + timeoutSeconds + " seconds.\"}", "", "TIMEOUT");
                }

                stdoutBytes = stdoutStream.readAllBytes();
                stderrBytes = stderrStream.readAllBytes();
            }

            String stdout = new String(stdoutBytes, StandardCharsets.UTF_8);
            String stderr = new String(stderrBytes, StandardCharsets.UTF_8);

            log.info("Sandbox stdout:\n{}", stdout);
            if (!stderr.isEmpty()) {
                log.warn("Sandbox stderr:\n{}", stderr);
            }

            // Extract the result JSON from stdout
            String resultsJson = extractJsonResult(stdout);
            String status = "ERROR";
            if (resultsJson != null) {
                // Determine status based on parsed JSON output
                if (resultsJson.contains("\"status\": \"PASSED\"")) {
                    status = "PASSED";
                } else if (resultsJson.contains("\"status\": \"FAILED\"")) {
                    status = "FAILED";
                } else if (resultsJson.contains("\"status\": \"ERROR\"")) {
                    status = "ERROR";
                }
            } else {
                resultsJson = "{\"error\": \"Failed to parse sandbox test runner output.\"}";
            }

            return new SandboxResult(status, resultsJson, stdout, stderr);

        } catch (IOException | InterruptedException e) {
            log.error("Failed to run submission in sandbox", e);
            Thread.currentThread().interrupt();
            return new SandboxResult("ERROR", "{\"error\": \"Internal execution error: " + e.getMessage() + "\"}", "", "");
        } finally {
            // Clean up files
            deleteDirectory(submissionTempPath.toFile());
        }
    }

    private String extractJsonResult(String stdout) {
        String startMarker = "===RESULT_START===";
        String endMarker = "===RESULT_END===";
        int startIndex = stdout.indexOf(startMarker);
        int endIndex = stdout.indexOf(endMarker);

        if (startIndex != -1 && endIndex != -1 && endIndex > startIndex) {
            return stdout.substring(startIndex + startMarker.length(), endIndex).trim();
        }
        return null;
    }

    private void deleteDirectory(File directory) {
        File[] allContents = directory.listFiles();
        if (allContents != null) {
            for (File file : allContents) {
                deleteDirectory(file);
            }
        }
        directory.delete();
    }
}
