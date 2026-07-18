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
        if ("ERROR".equalsIgnoreCase(status)) {
            return """
                    ### ⚠️ Sandbox Error
                    Your submission didn't get graded — the sandbox failed to execute it, which is a system-side issue rather than a problem with your fix.

                    This isn't a wrong-answer verdict. Please try submitting again; if it keeps happening, the sandbox environment likely needs attention (missing dependency, crashed runner, or a timeout provisioning the container).
                    """;
        }
        if ("TIMEOUT".equalsIgnoreCase(status)) {
            return """
                    ### ⏱️ Execution Timed Out
                    Your code ran but didn't finish within the time limit — this usually means an infinite loop, an unresolved Promise, or a server that never closes its listening socket.

                    **Look closely at:** any loop condition that never becomes false, and make sure any server/timers you start are actually closed/cleared before your handler returns.
                    """;
        }
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
                case "memory-cache-eviction" -> """
                        ### 🧠 AI Mentor Review — Success!
                        The cache is now bounded.

                        **Why this fix works:**
                        Evicting the oldest entry whenever `size()` would exceed `maxSize` turns an ever-growing `Map` into a fixed-footprint LRU-style cache. `Map` preserves insertion order in JavaScript, so `keys().next().value` is a cheap O(1) way to find the oldest entry without maintaining a separate queue.
                        """;
                case "memory-listener-leak" -> """
                        ### 🧠 AI Mentor Review — Success!
                        No more duplicate listeners.

                        **Why this fix works:**
                        Keeping a stable reference to the handler function lets you `removeListener` the previous one before adding a new one, so restarting the ticker never accumulates listeners. The general rule: any time a "setup" function can run more than once, make sure it tears down what it previously set up first.
                        """;
                case "cpu-naive-fibonacci" -> """
                        ### 🧠 AI Mentor Review — Success!
                        Exponential time is gone.

                        **Why this fix works:**
                        Memoization caches the result of each sub-problem the first time it's computed, so `computeFib(40)` does O(n) work instead of O(2^n). The same idea generalizes to any pure recursive function with overlapping sub-problems.
                        """;
                case "cpu-inefficient-dedupe" -> """
                        ### 🧠 AI Mentor Review — Success!
                        Linear time deduplication achieved.

                        **Why this fix works:**
                        A `Set` tracks "have I seen this value" in O(1) average time, versus `indexOf` which rescans the whole array from the start every time it's called. For large batches this is the difference between milliseconds and seconds.
                        """;
                case "security-path-traversal" -> """
                        ### 🧠 AI Mentor Review — Success!
                        Path traversal is blocked.

                        **Why this fix works:**
                        Resolving the requested path and then verifying it still starts with the allowed base directory closes the gap that `../` segments exploit. Never trust a joined/normalized path to stay inside its base directory without checking explicitly — normalization resolves `..`, it doesn't forbid it.
                        """;
                case "security-idor" -> """
                        ### 🧠 AI Mentor Review — Success!
                        Ownership is now enforced.

                        **Why this fix works:**
                        Checking `order.ownerId` against the requesting user before returning data closes the Insecure Direct Object Reference gap. Looking an object up by id is not the same as authorizing access to it — every object lookup needs its own ownership/permission check.
                        """;
                case "stability-json-parse-crash" -> """
                        ### 🧠 AI Mentor Review — Success!
                        Malformed input no longer crashes the parser.

                        **Why this fix works:**
                        Wrapping `JSON.parse` in a `try/catch` and returning a safe `{ ok: false }` result turns an uncaught `SyntaxError` into normal, handleable control flow. Any parse of untrusted input should be treated as fallible.
                        """;
                case "stability-batch-error" -> """
                        ### 🧠 AI Mentor Review — Success!
                        One bad item no longer takes down the batch.

                        **Why this fix works:**
                        Wrapping each iteration's `await worker(item)` in its own `try/catch` isolates failures per item instead of letting the first rejection abort the whole loop. This is the same principle behind `Promise.allSettled` — failures should be data, not control-flow surprises.
                        """;
                case "concurrency-double-init" -> """
                        ### 🧠 AI Mentor Review — Success!
                        Only one connection gets created now.

                        **Why this fix works:**
                        Caching the in-flight *promise* (not just the eventual value) means every concurrent caller that arrives before initialization finishes awaits the same promise instead of starting a new one. This is the standard fix for the "thundering herd" lazy-init race.
                        """;
                case "concurrency-limit" -> """
                        ### 🧠 AI Mentor Review — Success!
                        Concurrency is now bounded.

                        **Why this fix works:**
                        Running a fixed pool of workers that each pull the next item off a shared index caps how many operations are ever in flight simultaneously, instead of firing everything at once with `Promise.all(items.map(...))`. This is exactly the pattern behind libraries like `p-limit`.
                        """;
                case "database-n-plus-one" -> """
                        ### 🧠 AI Mentor Review — Success!
                        N+1 queries eliminated.

                        **Why this fix works:**
                        Fetching all posts for every user id in a single batched call, then grouping in memory, replaces N individual queries with one. This is the standard fix for N+1 patterns in any ORM — batch-load associated data instead of looping and querying per-row.
                        """;
                case "database-missing-transaction" -> """
                        ### 🧠 AI Mentor Review — Success!
                        Transfers are now atomic.

                        **Why this fix works:**
                        Wrapping the debit and credit in a single transaction means a failure partway through rolls back everything already written, so funds can never vanish partway through a multi-step operation. Any operation that writes to more than one place needs to either fully succeed or fully roll back.
                        """;
                case "database-unbounded-query" -> """
                        ### 🧠 AI Mentor Review — Success!
                        Indexed lookup in use.

                        **Why this fix works:**
                        Calling `db.query(filter)` instead of `db.getAll()` plus a manual filter lets the lookup use an index instead of scanning every row. The lesson generalizes directly to real databases: filter at the query layer, not after pulling everything into application memory.
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
                case "memory-cache-eviction" -> """
                        ### 🧠 AI Mentor Review — Hint
                        The cache keeps growing past its configured limit.

                        **Look closely at:**
                        - `set()` writes into `this.store` but never checks `this.store.size` against `this.maxSize`.
                        - **How to fix:** After inserting, if the size exceeds `maxSize`, remove the oldest entry — `this.store.keys().next().value` gives you the oldest key on a `Map`.
                        """;
                case "memory-listener-leak" -> """
                        ### 🧠 AI Mentor Review — Hint
                        Restarting the ticker leaves old listeners behind.

                        **Look closely at:**
                        - `start()` calls `this.emitter.on('tick', ...)` with a brand new anonymous function every time it's called.
                        - **How to fix:** Store the handler in a named property (e.g. `this._onTick`) and `removeListener` it before re-adding in `start()`.
                        """;
                case "cpu-naive-fibonacci" -> """
                        ### 🧠 AI Mentor Review — Hint
                        `computeFib(40)` is taking far too long.

                        **Look closely at:**
                        - `computeFib` calls itself twice per invocation with no caching — the same sub-problems are recomputed millions of times.
                        - **How to fix:** Add a cache (e.g. a `Map`) keyed by `n`, and return the cached result instead of recomputing it.
                        """;
                case "cpu-inefficient-dedupe" -> """
                        ### 🧠 AI Mentor Review — Hint
                        Deduping a large batch is too slow.

                        **Look closely at:**
                        - `arr.indexOf(value)` inside a `filter` callback rescans the whole array from the start for every element — that's O(n²) overall.
                        - **How to fix:** Use a `Set` to track values you've already seen; membership checks on a `Set` are O(1).
                        """;
                case "security-path-traversal" -> """
                        ### 🧠 AI Mentor Review — Hint
                        A crafted filename can escape the safe directory.

                        **Look closely at:**
                        - `readUserFile` resolves the joined path but never checks whether the result is still inside `/safe/`.
                        - **How to fix:** After resolving, verify `resolved.startsWith('/safe/')` before doing the lookup — reject anything that doesn't.
                        """;
                case "security-idor" -> """
                        ### 🧠 AI Mentor Review — Hint
                        Any user can fetch any order by id.

                        **Look closely at:**
                        - `getOrder` looks the order up by id and returns it — `requestingUserId` is accepted as a parameter but never used.
                        - **How to fix:** Compare `order.ownerId` to `requestingUserId` and return `null` (or a 403) when they don't match.
                        """;
                case "stability-json-parse-crash" -> """
                        ### 🧠 AI Mentor Review — Hint
                        Malformed input crashes the parser.

                        **Look closely at:**
                        - `JSON.parse(rawInput)` is called directly with no error handling.
                        - **How to fix:** Wrap the call in `try/catch` and return `{ ok: false, error }` in the `catch` branch instead of letting the `SyntaxError` propagate.
                        """;
                case "stability-batch-error" -> """
                        ### 🧠 AI Mentor Review — Hint
                        One failing item aborts the whole batch.

                        **Look closely at:**
                        - `await worker(item)` inside the loop has no `try/catch` — a single rejection propagates out of `processQueue` entirely.
                        - **How to fix:** Wrap each iteration's `await` in its own `try/catch`, pushing failures onto the `failed` array instead of letting them abort the loop.
                        """;
                case "concurrency-double-init" -> """
                        ### 🧠 AI Mentor Review — Hint
                        Concurrent callers each create their own connection.

                        **Look closely at:**
                        - `if (!instance)` is checked by every concurrent caller before the first `await expensiveCreate()` has resolved and assigned `instance`.
                        - **How to fix:** Cache the *promise* returned by `expensiveCreate()` itself, so a second caller arriving mid-initialization awaits the same in-flight promise.
                        """;
                case "concurrency-limit" -> """
                        ### 🧠 AI Mentor Review — Hint
                        Too many workers run at once.

                        **Look closely at:**
                        - `items.map(item => worker(item))` inside `Promise.all` starts every worker in the same tick — `maxConcurrent` is accepted but never used.
                        - **How to fix:** Run a fixed-size pool of workers that each pull the next item off a shared index as they finish, capping how many run simultaneously.
                        """;
                case "database-n-plus-one" -> """
                        ### 🧠 AI Mentor Review — Hint
                        One query per user doesn't scale.

                        **Look closely at:**
                        - `getUsersWithPosts` calls `db.getPostsByUser(u.id)` inside `.map()` — once per user.
                        - **How to fix:** Collect all user ids first, call `db.getPostsByUsers(userIds)` once, then group the results back onto each user in memory.
                        """;
                case "database-missing-transaction" -> """
                        ### 🧠 AI Mentor Review — Hint
                        A failed transfer leaves the source account debited.

                        **Look closely at:**
                        - `transferFunds` debits `fromId`, then credits `toId` as two separate writes with nothing to undo the first if the second fails.
                        - **How to fix:** Wrap both writes in `db.transaction(fn)`, which already exists and rolls back all writes made inside `fn` if it throws.
                        """;
                case "database-unbounded-query" -> """
                        ### 🧠 AI Mentor Review — Hint
                        A single-id lookup scans the entire table.

                        **Look closely at:**
                        - `searchRecords` calls `db.getAll()` and filters in JavaScript instead of using the indexed lookup that's already available.
                        - **How to fix:** Call `db.query(filter)` directly — it performs an indexed lookup instead of a full scan.
                        """;
                default -> "### 🧠 AI Mentor Review — Hint\nReview the test execution logs to identify which assertions failed.";
            };
        }
    }
}
