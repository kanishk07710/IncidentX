const path = require('path');

async function runSandbox() {
  const results = {
    status: "PASSED",
    tests: [],
    error: null
  };

  try {
    const submissionDir = process.env.SUBMISSION_DIR || '/workspace/submission';
    const testsPath = path.resolve(submissionDir, 'tests.js');
    
    // Attempt to require the tests module.
    // If the user's code has syntax errors or crashes on require, this block will fail.
    let tests;
    try {
      tests = require(testsPath);
    } catch (requireErr) {
      results.status = "ERROR";
      results.error = "Compilation/Initialization Error: " + (requireErr.stack || requireErr.message || String(requireErr));
      return results;
    }

    if (!Array.isArray(tests)) {
      results.status = "ERROR";
      results.error = "Invalid test suite: tests.js must export an array of test cases.";
      return results;
    }

    for (const test of tests) {
      const testResult = {
        name: test.name || "Unnamed Test",
        passed: true,
        message: null
      };

      try {
        if (typeof test.fn !== 'function') {
          throw new Error(`Test function for '${testResult.name}' is not a function.`);
        }
        // Support both sync and async test functions
        await test.fn();
      } catch (err) {
        testResult.passed = false;
        testResult.message = err.message || String(err);
        results.status = "FAILED";
      }

      results.tests.push(testResult);
    }
  } catch (err) {
    results.status = "ERROR";
    results.error = "Internal Sandbox Error: " + (err.stack || err.message || String(err));
  }

  return results;
}

runSandbox().then(results => {
  console.log("===RESULT_START===");
  console.log(JSON.stringify(results, null, 2));
  console.log("===RESULT_END===");
  process.exit(results.status === "PASSED" ? 0 : 1);
});
