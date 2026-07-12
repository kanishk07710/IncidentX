package com.incidentx.api.config;

import com.incidentx.api.model.Incident;
import com.incidentx.api.repository.IncidentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class DatabaseSeeder implements CommandLineRunner {

    @Autowired
    private IncidentRepository incidentRepository;

    @Override
    public void run(String... args) throws Exception {
        seedIncidents();
    }

    private void seedIncidents() {
        // Incident 1: Express memory leak
        Incident incident1 = Incident.builder()
                .id("express-memory-leak")
                .title("Express Global Logs Memory Leak")
                .difficulty("EASY")
                .category("memory")
                .description("""
                        ### Problem Statement
                        Users have complained about the application crashing under moderate usage.
                        
                        Our monitoring tools indicate that memory usage climbs linearly until the process runs out of memory (OOM) and crashes.
                        
                        ### Investigation Telemetry
                        - **Logs**: Process memory warning thresholds are hit periodically.
                        - **Stack Trace**: Standard Node.js heap allocation limit failures.
                        
                        ### Instructions
                        1. Inspect `solution.js`.
                        2. Identify where logs are being stored in memory indefinitely.
                        3. Fix the leak by either limiting the log size in-memory (e.g. max 100 entries) or using console output.
                        """)
                .logs("""
                        [2026-07-12 14:00:00] INFO Server started on port 3000
                        [2026-07-12 14:05:00] WARN Process memory exceeding warning threshold: 210MB
                        [2026-07-12 14:10:00] WARN Process memory exceeding warning threshold: 245MB
                        [2026-07-12 14:15:00] ERROR FATAL ERROR: Ineffective mark-compacts near heap limit Allocation failed - JavaScript heap out of memory
                        """)
                .metrics("""
                        [
                          {"timestamp": "14:00", "cpu": 15, "memory": 45},
                          {"timestamp": "14:05", "cpu": 22, "memory": 120},
                          {"timestamp": "14:10", "cpu": 25, "memory": 210},
                          {"timestamp": "14:15", "cpu": 30, "memory": 256}
                        ]
                        """)
                .stackTrace("""
                        <--- Last few GCs --->
                        [28034:0x633e240]    45213 ms: Scavenge 240.2 (250.2) -> 240.1 (250.2) MB
                        [28034:0x633e240]    45690 ms: Mark-sweep 245.5 (255.2) -> 245.4 (255.2) MB
                        FATAL ERROR: Ineffective mark-compacts near heap limit Allocation failed - JavaScript heap out of memory
                        """)
                .baseCode("""
                        {
                          "solution.js": "const express = require('express');\\nconst app = express();\\napp.use(express.json());\\n\\nconst requestLogs = [];\\n\\napp.post('/api/log', (req, res) => {\\n  const logEntry = {\\n    timestamp: new Date(),\\n    ip: req.ip,\\n    data: req.body\\n  };\\n  requestLogs.push(logEntry);\\n  res.status(200).json({ success: true, message: 'Logged' });\\n});\\n\\napp.get('/api/stats', (req, res) => {\\n  res.json({ totalLogs: requestLogs.length });\\n});\\n\\nmodule.exports = app;"
                        }
                        """)
                .hiddenTests("""
                        const assert = require('assert');
                        const app = require('./solution.js');
                        const http = require('http');

                        module.exports = [
                          {
                            name: "should return 200 on log post",
                            fn: async () => {
                              const server = app.listen(0);
                              const port = server.address().port;
                              
                              const postLog = (data) => new Promise((resolve, reject) => {
                                const req = http.request({
                                  hostname: 'localhost', port: port, path: '/api/log', method: 'POST',
                                  headers: { 'Content-Type': 'application/json' }
                                }, res => {
                                  let d = ''; res.on('data', c => d += c); res.on('end', () => resolve(res.statusCode));
                                });
                                req.on('error', reject); req.write(JSON.stringify(data)); req.end();
                              });

                              try {
                                const status = await postLog({ message: "Hello" });
                                assert.strictEqual(status, 200);
                              } finally {
                                server.close();
                              }
                            }
                          },
                          {
                            name: "should limit active logs in memory to prevent leak",
                            fn: async () => {
                              const server = app.listen(0);
                              const port = server.address().port;
                              
                              const postLog = (data) => new Promise((resolve, reject) => {
                                const req = http.request({
                                  hostname: 'localhost', port: port, path: '/api/log', method: 'POST',
                                  headers: { 'Content-Type': 'application/json' }
                                }, res => {
                                  let d = ''; res.on('data', c => d += c); res.on('end', () => resolve(res.statusCode));
                                });
                                req.on('error', reject); req.write(JSON.stringify(data)); req.end();
                              });

                              const getStats = () => new Promise((resolve, reject) => {
                                const req = http.get(`http://localhost:${port}/api/stats`, res => {
                                  let d = ''; res.on('data', c => d += c); res.on('end', () => resolve(JSON.parse(d)));
                                });
                                req.on('error', reject);
                              });

                              try {
                                for (let i = 0; i < 110; i++) {
                                  await postLog({ val: i });
                                }
                                const stats = await getStats();
                                assert.ok(stats.totalLogs <= 100, `Memory array size was ${stats.totalLogs}, exceeding the safe limit of 100.`);
                              } finally {
                                server.close();
                              }
                            }
                          }
                        ];
                        """)
                .referenceFix("""
                        {
                          "solution.js": "const express = require('express');\\nconst app = express();\\napp.use(express.json());\\n\\nlet requestLogs = [];\\n\\napp.post('/api/log', (req, res) => {\\n  const logEntry = {\\n    timestamp: new Date(),\\n    ip: req.ip,\\n    data: req.body\\n  };\\n  requestLogs.push(logEntry);\\n  if (requestLogs.length > 100) {\\n    requestLogs.shift();\\n  }\\n  res.status(200).json({ success: true, message: 'Logged' });\\n});\\n\\napp.get('/api/stats', (req, res) => {\\n  res.json({ totalLogs: requestLogs.length });\\n});\\n\\nmodule.exports = app;"
                        }
                        """)
                .hint("Look at the `requestLogs` array. It grows by one on every POST to `/api/log` — what ever removes items from it?")
                .build();

        // Incident 2: Event Loop blocking
        Incident incident2 = Incident.builder()
                .id("eventloop-blocking")
                .title("CPU-Intensive Event Loop Blocker")
                .difficulty("MEDIUM")
                .category("cpu")
                .description("""
                        ### Problem Statement
                        Under concurrent load, users experience extreme latency.
                        Ping requests and page loads time out.
                        
                        Investigation indicates that calculation requests run synchronously on the main thread, blocking the event loop and preventing standard I/O handlers from running.
                        
                        ### Instructions
                        1. Inspect `solution.js`.
                        2. The `calculatePrimesSync` function blocks execution.
                        3. Refactor this to perform calculation asynchronously (e.g., using `setImmediate` to divide computation in chunks or using standard event-loop friendly mechanisms).
                        """)
                .logs("""
                        [2026-07-12 14:30:00] INFO Server running on port 3000
                        [2026-07-12 14:30:15] WARN Request to /api/compute took 1250ms
                        [2026-07-12 14:30:20] ERROR Timeout on request to /api/ping from monitoring service
                        """)
                .metrics("""
                        [
                          {"timestamp": "14:30:00", "cpu": 5, "memory": 35},
                          {"timestamp": "14:30:15", "cpu": 100, "memory": 38},
                          {"timestamp": "14:30:20", "cpu": 100, "memory": 40}
                        ]
                        """)
                .stackTrace("""
                        Event loop block detected:
                        at calculatePrimesSync (solution.js:10:14)
                        at solution.js:19:20
                        at Layer.handle [as handle_request] (express/lib/router/layer.js:95:5)
                        """)
                .baseCode("""
                        {
                          "solution.js": "const express = require('express');\\nconst app = express();\\n\\nfunction calculatePrimesSync(limit) {\\n  const primes = [];\\n  for (let i = 2; i <= limit; i++) {\\n    let isPrime = true;\\n    for (let j = 2; j <= Math.sqrt(i); j++) {\\n      if (i % j === 0) { isPrime = false; break; }\\n    }\\n    if (isPrime) primes.push(i);\\n  }\\n  return primes;\\n}\\n\\napp.get('/api/compute', (req, res) => {\\n  const limit = parseInt(req.query.limit) || 40000;\\n  const result = calculatePrimesSync(limit);\\n  res.json({ count: result.length });\\n});\\n\\napp.get('/api/ping', (req, res) => {\\n  res.send('pong');\\n});\\n\\nmodule.exports = app;"
                        }
                        """)
                .hiddenTests("""
                        const assert = require('assert');
                        const app = require('./solution.js');
                        const http = require('http');

                        module.exports = [
                          {
                            name: "should return ping response quickly even when compute is running",
                            fn: async () => {
                              const server = app.listen(0);
                              const port = server.address().port;

                              const makeRequest = (path) => new Promise((resolve, reject) => {
                                const start = Date.now();
                                http.get(`http://localhost:${port}` + path, res => {
                                  let d = ''; res.on('data', c => d += c);
                                  res.on('end', () => resolve({ duration: Date.now() - start, statusCode: res.statusCode }));
                                }).on('error', reject);
                              });

                              try {
                                // Trigger a compute request with a limit
                                const computePromise = makeRequest('/api/compute?limit=30000');
                                
                                // Wait 20ms, then trigger ping request
                                await new Promise(r => setTimeout(r, 20));
                                const pingResult = await makeRequest('/api/ping');
                                
                                assert.strictEqual(pingResult.statusCode, 200);
                                assert.ok(pingResult.duration < 150, 'Ping took ' + pingResult.duration + 'ms. Event loop was likely blocked.');
                                
                                await computePromise;
                              } finally {
                                server.close();
                              }
                            }
                          }
                        ];
                        """)
                .referenceFix("""
                        {
                          "solution.js": "const express = require('express');\\nconst app = express();\\n\\nfunction calculatePrimesAsync(limit) {\\n  return new Promise((resolve) => {\\n    const primes = [];\\n    let i = 2;\\n    function chunk() {\\n      const start = Date.now();\\n      while (i <= limit && Date.now() - start < 10) {\\n        let isPrime = true;\\n        for (let j = 2; j <= Math.sqrt(i); j++) {\\n          if (i % j === 0) { isPrime = false; break; }\\n        }\\n        if (isPrime) primes.push(i);\\n        i++;\\n      }\\n      if (i <= limit) {\\n        setImmediate(chunk);\\n      } else {\\n        resolve(primes);\\n      }\\n    }\\n    chunk();\\n  });\\n}\\n\\napp.get('/api/compute', async (req, res) => {\\n  const limit = parseInt(req.query.limit) || 40000;\\n  const result = await calculatePrimesAsync(limit);\\n  res.json({ count: result.length });\\n});\\n\\napp.get('/api/ping', (req, res) => {\\n  res.send('pong');\\n});\\n\\nmodule.exports = app;"
                        }
                        """)
                .hint("`calculatePrimesSync` does all its work in one synchronous call. Node.js is single-threaded — what happens to every other request while that function is still running?")
                .build();

        // Incident 3: SQL Injection Vulnerability
        Incident incident3 = Incident.builder()
                .id("sql-injection")
                .title("Unsanitized Query Search Vulnerability")
                .difficulty("MEDIUM")
                .category("security")
                .description("""
                        ### Problem Statement
                        An external penetration testing audit has reported a high-severity vulnerability on our query search endpoint.
                        
                        Concatenated strings are executed directly on the SQL compiler. Exposing admin flags or tables via SQL injection payload is possible.
                        
                        ### Instructions
                        1. Inspect `solution.js`.
                        2. Modify the search endpoint to execute database commands safely by using parameters binding (calling `db.queryParam(sql, params)`) instead of string concatenation.
                        """)
                .logs("""
                        [2026-07-12 14:00:00] INFO Audit logger: query parameters received: q=' OR '1'='1
                        [2026-07-12 14:00:01] WARN Audit logger: returned secret admin flag FLAG_EXPOSED to external source
                        """)
                .metrics("[]")
                .stackTrace("")
                .baseCode("""
                        {
                          "solution.js": "const express = require('express');\\nconst app = express();\\napp.use(express.json());\\n\\nconst db = {\\n  query: async (sql) => {\\n    if (sql.includes(\"' OR '1'='1\")) {\\n      return [\\n        { id: 1, name: 'Flag', secret: 'FLAG_EXPOSED' },\\n        { id: 2, name: 'Item A', secret: 'None' }\\n      ];\\n    }\\n    return [{ id: 2, name: 'Item A', secret: 'None' }];\\n  },\\n  queryParam: async (sql, params) => {\\n    if (params && params[0] && params[0].includes(\"' OR '1'='1\")) {\\n      return [];\\n    }\\n    return [{ id: 2, name: 'Item A', secret: 'None' }];\\n  }\\n};\\n\\napp.get('/api/search', async (req, res) => {\\n  const q = req.query.q || '';\\n  const query = `SELECT * FROM items WHERE name = '${q}'`;\\n  const results = await db.query(query);\\n  res.json(results);\\n});\\n\\nmodule.exports = { app, db };"
                        }
                        """)
                .hiddenTests("""
                        const assert = require('assert');
                        const { app } = require('./solution.js');
                        const http = require('http');

                        module.exports = [
                          {
                            name: "should return search results for safe inputs",
                            fn: async () => {
                              const server = app.listen(0);
                              const port = server.address().port;
                              try {
                                await new Promise((resolve, reject) => {
                                  http.get('http://localhost:' + port + '/api/search?q=Item%20A', res => {
                                    let d = ''; res.on('data', c => d += c);
                                    res.on('end', () => {
                                      const data = JSON.parse(d);
                                      assert.strictEqual(res.statusCode, 200);
                                      assert.ok(data.length > 0);
                                      resolve();
                                    });
                                  }).on('error', reject);
                                });
                              } finally {
                                server.close();
                              }
                            }
                          },
                          {
                            name: "should block SQL Injection payload and not expose secret data",
                            fn: async () => {
                              const server = app.listen(0);
                              const port = server.address().port;
                              try {
                                await new Promise((resolve, reject) => {
                                  http.get('http://localhost:' + port + '/api/search?q=%27%20OR%20%271%27%3D%271', res => {
                                    let d = ''; res.on('data', c => d += c);
                                    res.on('end', () => {
                                      const data = JSON.parse(d);
                                      const hasExposedSecret = data.some(item => item.secret === "FLAG_EXPOSED");
                                      assert.ok(!hasExposedSecret, "SQL Injection payload succeeded! Secrets exposed.");
                                      resolve();
                                    });
                                  }).on('error', reject);
                                });
                              } finally {
                                server.close();
                              }
                            }
                          }
                        ];
                        """)
                .referenceFix("""
                        {
                          "solution.js": "const express = require('express');\\nconst app = express();\\napp.use(express.json());\\n\\nconst db = {\\n  query: async (sql) => {\\n    if (sql.includes(\"' OR '1'='1\")) {\\n      return [\\n        { id: 1, name: 'Flag', secret: 'FLAG_EXPOSED' },\\n        { id: 2, name: 'Item A', secret: 'None' }\\n      ];\\n    }\\n    return [{ id: 2, name: 'Item A', secret: 'None' }];\\n  },\\n  queryParam: async (sql, params) => {\\n    if (params && params[0] && params[0].includes(\"' OR '1'='1\")) {\\n      return [];\\n    }\\n    return [{ id: 2, name: 'Item A', secret: 'None' }];\\n  }\\n};\\n\\napp.get('/api/search', async (req, res) => {\\n  const q = req.query.q || '';\\n  const query = 'SELECT * FROM items WHERE name = ?';\\n  const results = await db.queryParam(query, [q]);\\n  res.json(results);\\n});\\n\\nmodule.exports = { app, db };"
                        }
                        """)
                .hint("The search endpoint builds `query` by directly concatenating `q` into a SQL string. What happens if `q` itself contains SQL syntax? There's a safer `db.queryParam(sql, params)` method sitting right there.")
                .build();

        // Incident 4: Uncaught Promise Rejection
        Incident incident4 = Incident.builder()
                .id("uncaught-rejection")
                .title("Uncaught Promise Rejection Crash")
                .difficulty("EASY")
                .category("stability")
                .description("""
                        ### Problem Statement
                        The web app crashes entirely and undergoes restarting when calling external endpoints.
                        This happens when a third-party billing connector timeouts.
                        
                        ### Instructions
                        1. Inspect `solution.js`.
                        2. Wrap the asynchronous callback or checkout handler using standard try-catch or `.catch()` block to return an error code safely and prevent process OOM/crashes.
                        """)
                .logs("""
                        [2026-07-12 15:00:00] ERROR UnhandledPromiseRejectionWarning: Error: Payment Gateway Connection Timeout
                        [2026-07-12 15:00:01] INFO Process terminated with exit code 1
                        """)
                .metrics("[]")
                .stackTrace("""
                        (node:12) UnhandledPromiseRejection: This error originated either by throwing inside of an async function without a catch block, or by rejecting a promise which was not handled with .catch().
                        at mockPaymentGateway (solution.js:6:11)
                        at solution.js:12:7
                        """)
                .baseCode("""
                        {
                          "solution.js": "const express = require('express');\\nconst app = express();\\n\\nconst mockPaymentGateway = async (shouldFail) => {\\n  if (shouldFail) {\\n    throw new Error('Payment Gateway Connection Timeout');\\n  }\\n  return { success: true };\\n};\\n\\napp.post('/api/checkout', (req, res) => {\\n  const fail = req.query.fail === 'true';\\n  mockPaymentGateway(fail).then(result => {\\n    res.json(result);\\n  });\\n});\\n\\nmodule.exports = app;"
                        }
                        """)
                .hiddenTests("""
                        const assert = require('assert');
                        const app = require('./solution.js');
                        const http = require('http');

                        module.exports = [
                          {
                            name: "should return error response without crashing process",
                            fn: async () => {
                              const server = app.listen(0);
                              const port = server.address().port;

                              const getResponse = (path) => new Promise((resolve, reject) => {
                                const req = http.request({
                                  hostname: 'localhost', port: port, path: path, method: 'POST'
                                }, res => {
                                  let d = ''; res.on('data', c => d += c);
                                  res.on('end', () => resolve({ status: res.statusCode, body: d }));
                                });
                                req.on('error', reject); req.end();
                              });

                              try {
                                const res1 = await getResponse('/api/checkout?fail=true');
                                assert.ok(res1.status >= 400, "Should return an error status code.");

                                const res2 = await getResponse('/api/checkout?fail=false');
                                assert.strictEqual(res2.status, 200, "Server crashed and was unable to handle requests!");
                              } finally {
                                server.close();
                              }
                            }
                          }
                        ];
                        """)
                .referenceFix("""
                        {
                          "solution.js": "const express = require('express');\\nconst app = express();\\n\\nconst mockPaymentGateway = async (shouldFail) => {\\n  if (shouldFail) {\\n    throw new Error('Payment Gateway Connection Timeout');\\n  }\\n  return { success: true };\\n};\\n\\napp.post('/api/checkout', (req, res) => {\\n  const fail = req.query.fail === 'true';\\n  mockPaymentGateway(fail).then(result => {\\n    res.json(result);\\n  }).catch(err => {\\n    res.status(500).json({ error: err.message });\\n  });\\n});\\n\\nmodule.exports = app;"
                        }
                        """)
                .hint("`mockPaymentGateway` returns a promise that can reject. The route handler chains `.then()` on it — but what handles the rejection case?")
                .build();

        // Incident 5: Race Condition in Concurrent Checkout
        Incident incident5 = Incident.builder()
                .id("race-condition-checkout")
                .title("Race Condition in Inventory Checkout")
                .difficulty("HARD")
                .category("concurrency")
                .description("""
                        ### Problem Statement
                        Customer support has flagged dozens of orders for a limited-stock item that shipped
                        more units than the warehouse ever had. Finance wants to know how inventory went negative.

                        Under concurrent checkout traffic (flash sale conditions), multiple requests appear to
                        read the same stock count before any of them commit their decrement, allowing more
                        orders to succeed than there was inventory for.

                        ### Instructions
                        1. Inspect `solution.js`.
                        2. The `/api/checkout` handler reads `stock`, awaits a simulated async operation
                           (e.g. payment processing), and only then writes the decremented value back.
                        3. Fix the check-then-act race so concurrent requests can never oversell stock —
                           serialize the critical section (e.g. queue/chain requests) or make the
                           check-and-decrement atomic before any `await`.
                        """)
                .logs("""
                        [2026-07-12 16:00:00] INFO Flash sale started. Stock: 3 units.
                        [2026-07-12 16:00:01] INFO 5 concurrent checkout requests received.
                        [2026-07-12 16:00:01] WARN Stock counter went negative: -2
                        [2026-07-12 16:00:02] ERROR Finance reconciliation mismatch: 5 orders shipped against 3 units of stock
                        """)
                .metrics("[]")
                .stackTrace("""
                        No exception thrown — this is a logic race condition, not a crash.
                        Concurrent requests interleave between the read of `stock` and the write-back,
                        each believing they observed the last available unit.
                        """)
                .baseCode("""
                        {
                          "solution.js": "const express = require('express');\\nconst app = express();\\n\\nlet stock = 3;\\n\\nfunction delay(ms) { return new Promise(resolve => setTimeout(resolve, ms)); }\\n\\napp.post('/api/checkout', async (req, res) => {\\n  const current = stock;\\n  await delay(10);\\n  if (current > 0) {\\n    stock = current - 1;\\n    return res.json({ success: true, remaining: stock });\\n  }\\n  res.status(409).json({ success: false, error: 'Out of stock' });\\n});\\n\\napp.get('/api/stock', (req, res) => {\\n  res.json({ stock });\\n});\\n\\nmodule.exports = app;"
                        }
                        """)
                .hiddenTests("""
                        const assert = require('assert');
                        const app = require('./solution.js');
                        const http = require('http');

                        module.exports = [
                          {
                            name: "should not oversell stock under concurrent checkout requests",
                            fn: async () => {
                              const server = app.listen(0);
                              const port = server.address().port;

                              const checkout = () => new Promise((resolve, reject) => {
                                const req = http.request({
                                  hostname: 'localhost', port: port, path: '/api/checkout', method: 'POST'
                                }, res => {
                                  let d = ''; res.on('data', c => d += c);
                                  res.on('end', () => resolve({ status: res.statusCode, body: JSON.parse(d || '{}') }));
                                });
                                req.on('error', reject); req.end();
                              });

                              const getStock = () => new Promise((resolve, reject) => {
                                http.get(`http://localhost:${port}/api/stock`, res => {
                                  let d = ''; res.on('data', c => d += c); res.on('end', () => resolve(JSON.parse(d)));
                                }).on('error', reject);
                              });

                              try {
                                const results = await Promise.all([checkout(), checkout(), checkout(), checkout(), checkout()]);
                                const successCount = results.filter(r => r.status === 200).length;
                                assert.strictEqual(successCount, 3, `Expected exactly 3 successful checkouts against 3 units of stock, got ${successCount}. Race condition allowed overselling.`);

                                const final = await getStock();
                                assert.ok(final.stock >= 0, `Stock went negative (${final.stock}) — race condition allowed overselling.`);
                              } finally {
                                server.close();
                              }
                            }
                          }
                        ];
                        """)
                .referenceFix("""
                        {
                          "solution.js": "const express = require('express');\\nconst app = express();\\n\\nlet stock = 3;\\nlet queue = Promise.resolve();\\n\\nfunction delay(ms) { return new Promise(resolve => setTimeout(resolve, ms)); }\\n\\napp.post('/api/checkout', (req, res) => {\\n  queue = queue.then(async () => {\\n    const current = stock;\\n    await delay(10);\\n    if (current > 0) {\\n      stock = current - 1;\\n      res.json({ success: true, remaining: stock });\\n    } else {\\n      res.status(409).json({ success: false, error: 'Out of stock' });\\n    }\\n  });\\n  return queue;\\n});\\n\\napp.get('/api/stock', (req, res) => {\\n  res.json({ stock });\\n});\\n\\nmodule.exports = app;"
                        }
                        """)
                .hint("Every request reads `stock` into `current`, then `await`s before writing `stock = current - 1`. If two requests both read before either writes, what value did they both see?")
                .build();

        // Incidents are static reference content owned by this seeder (not user data), so
        // upsert-on-boot keeps them current when hand-authored content changes, instead of
        // silently ignoring edits after the first seed.
        incidentRepository.save(incident1);
        incidentRepository.save(incident2);
        incidentRepository.save(incident3);
        incidentRepository.save(incident4);
        incidentRepository.save(incident5);
    }
}
