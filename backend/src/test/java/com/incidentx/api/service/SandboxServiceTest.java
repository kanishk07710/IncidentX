package com.incidentx.api.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class SandboxServiceTest {

    @Autowired
    private SandboxService sandboxService;

    @Test
    public void testSafeSolution() {
        Map<String, String> files = Map.of(
            "solution.js", "function add(a, b) { return a + b; } module.exports = { add };"
        );
        String tests = """
            const assert = require('assert');
            const sol = require('./solution.js');
            module.exports = [
              { name: 'add test', fn: () => assert.strictEqual(sol.add(1, 2), 3) }
            ];
            """;

        SandboxService.SandboxResult result = sandboxService.runSubmission("test-safe", files, tests);
        assertEquals("PASSED", result.getStatus());
        assertTrue(result.getResultsJson().contains("\"passed\": true"));
    }

    @Test
    public void testInfiniteLoopMitigation() {
        Map<String, String> files = Map.of(
            "solution.js", "function loop() { while(true) {} } module.exports = { loop };"
        );
        String tests = """
            const sol = require('./solution.js');
            module.exports = [
              { name: 'loop test', fn: () => sol.loop() }
            ];
            """;

        SandboxService.SandboxResult result = sandboxService.runSubmission("test-loop", files, tests);
        assertEquals("TIMEOUT", result.getStatus());
        assertTrue(result.getResultsJson().contains("timed out"));
    }

    @Test
    public void testForkBombMitigation() {
        Map<String, String> files = Map.of(
            "solution.js", """
                const { spawn } = require('child_process');
                function bomb() {
                  // Real fork-bomb behavior: spawn continuously, ignoring failures,
                  // so it keeps hammering the process table instead of giving up after one try.
                  while (true) {
                    try {
                      spawn(process.argv[0], ['-e', '']);
                    } catch (e) {}
                  }
                }
                module.exports = { bomb };
                """
        );
        String tests = """
            const sol = require('./solution.js');
            module.exports = [
              { name: 'bomb test', fn: () => sol.bomb() }
            ];
            """;

        SandboxService.SandboxResult result = sandboxService.runSubmission("test-bomb", files, tests);
        // Fork bomb should never finish cleanly (pids-limit throttles it until it hits the timeout).
        // It shouldn't crash the host database.
        assertNotEquals("PASSED", result.getStatus());
    }

    @Test
    public void testMaliciousWriteMitigation() {
        Map<String, String> files = Map.of(
            "solution.js", """
                const fs = require('fs');
                function exploit() {
                  fs.writeFileSync('/exploit.txt', 'malicious data');
                }
                module.exports = { exploit };
                """
        );
        String tests = """
            const sol = require('./solution.js');
            module.exports = [
              { name: 'write test', fn: () => sol.exploit() }
            ];
            """;

        SandboxService.SandboxResult result = sandboxService.runSubmission("test-write", files, tests);
        assertEquals("FAILED", result.getStatus());
        assertTrue(result.getResultsJson().contains("read-only file system") || result.getResultsJson().contains("EROFS"));
    }

    @Test
    public void testMemoryExhaustionMitigation() {
        Map<String, String> files = Map.of(
            "solution.js", """
                function leak() {
                  const arr = [];
                  while(true) {
                    arr.push(Buffer.alloc(10 * 1024 * 1024)); // allocate 10MB chunks
                  }
                }
                module.exports = { leak };
                """
        );
        String tests = """
            const sol = require('./solution.js');
            module.exports = [
              { name: 'leak test', fn: () => sol.leak() }
            ];
            """;

        SandboxService.SandboxResult result = sandboxService.runSubmission("test-leak", files, tests);
        assertNotEquals("PASSED", result.getStatus());
    }
}
