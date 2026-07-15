package com.incidentx.api.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Service
@Slf4j
public class AiMentorService {

    @Value("${ai.api-key:mock}")
    private String apiKey;

    @Value("${ai.model:gpt-4o-mini}")
    private String modelName;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public String generateFeedback(String incidentId, String code, String sandboxStatus, String resultsJson) {
        log.info("Generating AI feedback for incident: {}, status: {}", incidentId, sandboxStatus);

        // If no api key is configured, use high-quality mock responses tailored to the seeded incidents.
        if (apiKey.equals("mock") || apiKey.trim().isEmpty()) {
            return generateMockFeedback(incidentId, sandboxStatus);
        }

        try {
            // Build simple chat completion request to OpenAI API
            String prompt = String.format(
                    "You are a Senior Production Debugging Mentor on IncidentX. " +
                    "Review this submission for incident '%s'.\\n" +
                    "Sandbox Execution Status: %s\\n" +
                    "Sandbox Test Results: %s\\n" +
                    "Submitted Code:\\n%s\\n\\n" +
                    "Provide a brief, supportive, and constructive explanation of the bug, " +
                    "why the tests failed (or passed), and how to improve. Highlight production best practices.",
                    incidentId, sandboxStatus, resultsJson, code
            );

            // Simple escaped JSON payload
            String payload = String.format(
                    "{\"model\": \"%s\", \"messages\": [{\"role\": \"user\", \"content\": \"%s\"}], \"temperature\": 0.2}",
                    modelName, prompt.replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "")
            );

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.openai.com/v1/chat/completions"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                // Quick parsing of choices[0].message.content (rough string parse to avoid external deps)
                String body = response.body();
                int contentStart = body.indexOf("\"content\":");
                if (contentStart != -1) {
                    int textStart = body.indexOf("\"", contentStart + 10);
                    int textEnd = body.indexOf("\"", textStart + 1);
                    if (textStart != -1 && textEnd != -1) {
                        return body.substring(textStart + 1, textEnd)
                                .replace("\\n", "\n")
                                .replace("\\\"", "\"");
                    }
                }
                return body;
            } else {
                log.warn("AI API returned status {}: {}. Falling back to mock feedback.", response.statusCode(), response.body());
                return generateMockFeedback(incidentId, sandboxStatus);
            }

        } catch (Exception e) {
            log.error("Error communicating with AI service. Falling back to mock feedback.", e);
            return generateMockFeedback(incidentId, sandboxStatus);
        }
    }

    private String generateMockFeedback(String incidentId, String status) {
        if ("PASSED".equalsIgnoreCase(status)) {
            return switch (incidentId) {
                case "express-memory-leak" -> """
                        ### 🧠 AI Mentor Review — Success!
                        Great job fixing the memory leak!
                        
                        **Why this fix works:**
                        By shifting logs out of the array or capping `requestLogs.length <= 100`, you prevent the array from growing linearly. In a production environment, you should never store user transaction logs in a global JavaScript array. Instead, stream logs directly to standard output (`process.stdout`) and let an agent (like FluentBit or Datadog) aggregate them.
                        
                        *Tip: Shift-and-push is a standard circular buffer mechanism for basic in-memory metric collections.*
                        """;
                case "eventloop-blocking" -> """
                        ### 🧠 AI Mentor Review — Success!
                        Excellent refactoring. You freed up the event loop.
                        
                        **Why this fix works:**
                        By splitting the prime calculation into small time-sliced batches (using `setImmediate` or `setTimeout`), you allow the Node.js event loop to process incoming network requests (like `/api/ping`) during execution pauses. Alternatively, spawning worker threads (`worker_threads`) is the industry standard for offloading heavy CPU computation in Node.js apps.
                        """;
                case "sql-injection" -> """
                        ### 🧠 AI Mentor Review — Success!
                        Vulnerability resolved. The search endpoint is now secure.
                        
                        **Why this fix works:**
                        You switched from query string concatenation (`SELECT ... = '${q}'`) to parameterized queries (`SELECT ... = ?`). Parameters are passed separately to the database engine and treated as literal values rather than executable SQL commands, neutralizing injection payloads like `' OR '1'='1`.
                        """;
                case "uncaught-rejection" -> """
                        ### 🧠 AI Mentor Review — Success!
                        Process crash resolved!
                        
                        **Why this fix works:**
                        Adding a `.catch(...)` block to the Promise handle or wrapping it inside an async/await `try-catch` prevents uncaught exceptions from bubbling up. In Node.js, uncaught rejections crash the main process. Gracefully catching errors ensures the HTTP request terminates with an error code (like `500 Internal Server Error`) while keeping the container healthy.
                        """;
                case "race-condition-checkout" -> """
                        ### 🧠 AI Mentor Review — Success!
                        The race condition is closed. Concurrent checkouts can no longer oversell stock.

                        **Why this fix works:**
                        You serialized the critical section by chaining each request onto a shared promise
                        queue, so the read-check-write of `stock` for one request always completes before
                        the next one begins. This turns a check-then-act race into an atomic operation
                        without needing a real lock. In production, the same pattern shows up as database
                        row locks, `SELECT ... FOR UPDATE`, or atomic decrement operations (e.g. Redis `DECR`)
                        — the goal is always the same: never let two requests act on a stale read.
                        """;
                default -> "### 🧠 AI Mentor Review — Success!\nYour solution successfully passed all deterministic test suites.";
            };
        } else {
            return switch (incidentId) {
                case "express-memory-leak" -> """
                        ### 🧠 AI Mentor Review — Hint
                        The sandbox tests indicate that memory array sizes exceed warning limits when simulated traffic is high.
                        
                        **Look closely at:**
                        - The `requestLogs` array in `solution.js`. It expands on every POST request to `/api/log` but is never cleared or truncated.
                        - **How to fix:** Modify `solution.js` to ensure the array does not grow beyond a fixed limit (e.g. check length and remove oldest items using `shift()`), or remove the in-memory array storage entirely.
                        """;
                case "eventloop-blocking" -> """
                        ### 🧠 AI Mentor Review — Hint
                        The ping request took too long or timed out. This means the event loop is blocked.
                        
                        **Look closely at:**
                        - The `calculatePrimesSync` function is synchronous and CPU-intensive. Since Node.js is single-threaded, nothing else can run until this completes.
                        - **How to fix:** Convert `calculatePrimesSync` to run asynchronously in chunks. You can use a helper function that does a few iterations, checks if e.g. 10ms have elapsed, and schedules the next chunk using `setImmediate(chunk)`.
                        """;
                case "sql-injection" -> """
                        ### 🧠 AI Mentor Review — Hint
                        Your search endpoint is vulnerable to SQL injection. An injection payload was able to bypass filter structures.
                        
                        **Look closely at:**
                        - How query strings are combined: `SELECT * FROM items WHERE name = '${q}'`
                        - **How to fix:** Use parameter binding. Replace `${q}` in your SQL query string with a placeholder `?` (or relevant parameter indicator) and invoke the safe method `db.queryParam(sql, [q])` instead of `db.query(sql)`.
                        """;
                case "uncaught-rejection" -> """
                        ### 🧠 AI Mentor Review — Hint
                        The Node.js server crashed during a failed payment simulation.
                        
                        **Look closely at:**
                        - Inside `solution.js`, `mockPaymentGateway` returns a Promise that is rejected on error.
                        - The express route handler calls `.then()` but does not chain a `.catch(...)` error handler.
                        - **How to fix:** Chain `.catch(err => { res.status(500).json({ error: err.message }); })` to the promise call to handle the rejection gracefully.
                        """;
                case "race-condition-checkout" -> """
                        ### 🧠 AI Mentor Review — Hint
                        Under concurrent load, more checkouts succeeded than there was stock for.

                        **Look closely at:**
                        - The handler reads `stock` into `current`, `await`s a simulated delay, then writes
                          `stock = current - 1`. Between the read and the write, other concurrent requests
                          can read the *same* pre-decrement value of `stock`.
                        - **How to fix:** Serialize the read-check-write so only one request's critical
                          section runs at a time — for example, chain each request onto a shared promise
                          (`queue = queue.then(...)`) so the next request's read only happens after the
                          previous request's write has committed.
                        """;
                default -> "### 🧠 AI Mentor Review — Hint\nReview the test execution logs to identify which assertions failed.";
            };
        }
    }
}
