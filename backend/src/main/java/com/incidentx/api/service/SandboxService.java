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

    @Value("${incidentx.sandbox.temp-dir:${java.io.tmpdir}/incidentx}")
    private String baseTempDir;

    @Value("${incidentx.sandbox.image-name:incidentx-sandbox-node}")
    private String dockerImageName;

    @Value("${incidentx.sandbox.timeout-seconds:8}")
    private int timeoutSeconds;

    // "docker" (local dev with a real daemon), "node" (hosts like Render's standard web service,
    // which expose no Docker daemon — runner.js runs directly via the Node binary baked into the
    // API's own image), or "auto" (default): probe once for a usable Docker CLI + sandbox image
    // and fall back to node mode when either is missing. The auto default exists because a
    // deployment that lands in docker mode without a daemon fails EVERY submission with
    // "Cannot run program docker" — which is exactly what happened on Render when the service
    // was created without the SANDBOX_MODE=node env var render.yaml declares. Isolation is
    // weaker in node mode (process-level limits only, no container), an acceptable tradeoff
    // for this project's threat model.
    @Value("${incidentx.sandbox.mode:auto}")
    private String sandboxMode;

    // Resolved lazily on first submission when mode is "auto"; explicit modes bypass this.
    private volatile String resolvedAutoMode;

    // In "docker" mode, submitted code resolves require('express') etc. against the image's
    // own /workspace/node_modules (baked in at image build time). In "node" mode there is no
    // such image — submissions run straight out of a bare temp directory — so without this,
    // any incident whose solution.js requires a dependency (e.g. the express-based memory-leak
    // incident) fails EVERY submission, correct or not, with the same "Cannot find module"
    // error. We lazily npm-install the sandbox's package.json once into baseTempDir itself;
    // Node's module resolution walks up from each submission's subdirectory and finds it there.
    private volatile boolean nodeModulesReady = false;
    private static final int NODE_MODULES_INSTALL_TIMEOUT_SECONDS = 120;

    // Baked into the runtime image at build time (see backend/Dockerfile). When present, we
    // link it into baseTempDir instead of running npm install at runtime, which otherwise raced
    // Render free-tier cold starts (container spins down on idle; the first submission after
    // waking paid for a full registry install before it could even run).
    private static final String BAKED_SANDBOX_DEPS_DIR = "/opt/sandbox-deps";

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

            if ("node".equals(effectiveSandboxMode())) {
                return runWithNode(submissionTempPath);
            }
            return runWithDocker(submissionId, submissionTempPath);

        } catch (IOException e) {
            log.error("Failed to run submission in sandbox", e);
            return new SandboxResult("ERROR", "{\"error\": \"Internal execution error: " + e.getMessage() + "\"}", "", "");
        } finally {
            deleteDirectory(submissionTempPath.toFile());
        }
    }

    private String effectiveSandboxMode() {
        String configured = sandboxMode == null ? "" : sandboxMode.trim().toLowerCase();
        if ("node".equals(configured) || "docker".equals(configured)) {
            return configured;
        }
        String resolved = resolvedAutoMode;
        if (resolved != null) {
            return resolved;
        }
        synchronized (this) {
            if (resolvedAutoMode == null) {
                resolvedAutoMode = isDockerSandboxUsable() ? "docker" : "node";
                log.info("Sandbox mode '{}' auto-resolved to '{}'", sandboxMode, resolvedAutoMode);
            }
            return resolvedAutoMode;
        }
    }

    private boolean isDockerSandboxUsable() {
        try {
            Process probe = new ProcessBuilder("docker", "image", "inspect", dockerImageName)
                    .redirectErrorStream(true)
                    .start();
            if (!probe.waitFor(10, TimeUnit.SECONDS)) {
                probe.destroyForcibly();
                log.warn("Docker probe timed out; falling back to node sandbox mode");
                return false;
            }
            if (probe.exitValue() != 0) {
                log.warn("Docker CLI is present but sandbox image '{}' is not available; " +
                        "falling back to node sandbox mode", dockerImageName);
                return false;
            }
            return true;
        } catch (IOException e) {
            log.info("Docker CLI not available ({}); using node sandbox mode", e.getMessage());
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private SandboxResult runWithNode(Path submissionTempPath) throws IOException {
        try {
            ensureNodeModulesForNodeMode();

            Path runnerScript = submissionTempPath.resolve("runner.js");
            try (var in = getClass().getResourceAsStream("/sandbox/runner.js")) {
                if (in == null) {
                    throw new IOException("Bundled sandbox/runner.js resource not found on classpath");
                }
                Files.copy(in, runnerScript, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }

            List<String> command = List.of("node", "runner.js");
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(submissionTempPath.toFile());
            pb.environment().put("SUBMISSION_DIR", submissionTempPath.toAbsolutePath().toString());

            Process process = pb.start();

            byte[] stdoutBytes;
            byte[] stderrBytes;
            try (var stdoutStream = process.getInputStream();
                 var stderrStream = process.getErrorStream()) {

                boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
                if (!finished) {
                    log.warn("Submission timed out in node sandbox. Force killing process.");
                    process.destroyForcibly();
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

            return buildResultFromStdout(stdout, stderr);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new SandboxResult("ERROR", "{\"error\": \"Internal execution error: " + e.getMessage() + "\"}", "", "");
        }
    }

    private void ensureNodeModulesForNodeMode() throws IOException, InterruptedException {
        if (nodeModulesReady) {
            return;
        }
        synchronized (this) {
            if (nodeModulesReady) {
                return;
            }

            Path base = Paths.get(baseTempDir);
            Files.createDirectories(base);

            Path expressMarker = base.resolve("node_modules").resolve("express").resolve("package.json");
            if (Files.exists(expressMarker)) {
                nodeModulesReady = true;
                return;
            }

            Path bakedNodeModules = Paths.get(BAKED_SANDBOX_DEPS_DIR, "node_modules");
            Path bakedExpressMarker = bakedNodeModules.resolve("express").resolve("package.json");
            if (Files.exists(bakedExpressMarker)) {
                try {
                    Files.createSymbolicLink(base.resolve("node_modules"), bakedNodeModules);
                    nodeModulesReady = true;
                    log.info("Linked pre-baked sandbox node_modules from {} into {}", bakedNodeModules, base);
                    return;
                } catch (IOException e) {
                    log.warn("Failed to link pre-baked node_modules, falling back to npm install", e);
                }
            }

            log.info("Node sandbox mode: installing shared sandbox dependencies into {}", base);
            try (var in = getClass().getResourceAsStream("/sandbox/package.json")) {
                if (in == null) {
                    throw new IOException("Bundled sandbox/package.json resource not found on classpath");
                }
                Files.copy(in, base.resolve("package.json"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }

            boolean isWindows = System.getProperty("os.name", "").toLowerCase().contains("win");
            List<String> command = List.of(
                    isWindows ? "npm.cmd" : "npm",
                    "install", "--omit=dev", "--no-audit", "--no-fund"
            );
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(base.toFile());
            pb.redirectErrorStream(true);
            Process process = pb.start();

            String output;
            try (var out = process.getInputStream()) {
                output = new String(out.readAllBytes(), StandardCharsets.UTF_8);
            }
            boolean finished = process.waitFor(NODE_MODULES_INSTALL_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                log.error("Timed out installing sandbox node_modules into {}", base);
                return;
            }

            if (process.exitValue() == 0 && Files.exists(expressMarker)) {
                nodeModulesReady = true;
                log.info("Sandbox dependencies installed successfully into {}", base);
            } else {
                log.error("Failed to install sandbox node_modules (exit {}). npm output:\n{}", process.exitValue(), output);
            }
        }
    }

    private SandboxResult runWithDocker(String submissionId, Path submissionTempPath) throws IOException {
        try {
            // Path to mount, mapped properly for Docker Desktop
            String hostPath = submissionTempPath.toAbsolutePath().toString().replace("\\", "/");

            // Build docker command
            String containerName = "incidentx-sub-" + UUID.randomUUID();
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

            return buildResultFromStdout(stdout, stderr);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new SandboxResult("ERROR", "{\"error\": \"Internal execution error: " + e.getMessage() + "\"}", "", "");
        }
    }

    private SandboxResult buildResultFromStdout(String stdout, String stderr) {
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
