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

        // Incident 6: Unbounded cache growth
        Incident incident6 = Incident.builder()
                .id("memory-cache-eviction")
                .title("Unbounded Session Cache Growth")
                .difficulty("MEDIUM")
                .category("memory")
                .description("""
                        ### Problem Statement
                        The session cache service has been running for a few days and its resident memory
                        keeps climbing with no plateau in sight, tracking almost exactly with the number of
                        unique sessions ever created — not the number of *active* sessions.

                        ### Instructions
                        1. Inspect `solution.js`.
                        2. The `Cache` class is meant to hold at most `maxSize` entries.
                        3. Fix `set()` so the cache evicts an old entry whenever it would otherwise grow
                           past `maxSize`.
                        """)
                .logs("""
                        [2026-07-14 09:00:00] INFO Session cache initialized, maxSize=50
                        [2026-07-14 09:30:00] WARN Session cache size: 4210 (expected <= 50)
                        [2026-07-14 10:00:00] WARN Session cache size: 8945 (expected <= 50)
                        """)
                .metrics("""
                        [
                          {"timestamp": "09:00", "cpu": 8, "memory": 40},
                          {"timestamp": "09:30", "cpu": 10, "memory": 95},
                          {"timestamp": "10:00", "cpu": 12, "memory": 180}
                        ]
                        """)
                .stackTrace("")
                .baseCode("""
                        {"solution.js":"class Cache {\\n  constructor(maxSize) {\\n    this.maxSize = maxSize;\\n    this.store = new Map();\\n  }\\n\\n  set(key, value) {\\n    this.store.set(key, value);\\n  }\\n\\n  get(key) {\\n    return this.store.get(key);\\n  }\\n\\n  size() {\\n    return this.store.size;\\n  }\\n}\\n\\nmodule.exports = { Cache };\\n"}
                        """)
                .hiddenTests("""
                        const assert = require('assert');
                        const { Cache } = require('./solution.js');

                        module.exports = [
                          {
                            name: "should store and retrieve values",
                            fn: () => {
                              const cache = new Cache(50);
                              cache.set('a', 1);
                              assert.strictEqual(cache.get('a'), 1);
                            }
                          },
                          {
                            name: "should evict oldest entries once maxSize is exceeded",
                            fn: () => {
                              const cache = new Cache(50);
                              for (let i = 0; i < 80; i++) {
                                cache.set('key' + i, i);
                              }
                              assert.ok(cache.size() <= 50, `Cache grew to ${cache.size()} entries, exceeding maxSize of 50 — this is a memory leak.`);
                              assert.strictEqual(cache.get('key79'), 79, "Most recently added entry should still be present.");
                            }
                          }
                        ];
                        """)
                .referenceFix("""
                        {"solution.js":"class Cache {\\n  constructor(maxSize) {\\n    this.maxSize = maxSize;\\n    this.store = new Map();\\n  }\\n\\n  set(key, value) {\\n    this.store.set(key, value);\\n    if (this.store.size > this.maxSize) {\\n      const oldestKey = this.store.keys().next().value;\\n      this.store.delete(oldestKey);\\n    }\\n  }\\n\\n  get(key) {\\n    return this.store.get(key);\\n  }\\n\\n  size() {\\n    return this.store.size;\\n  }\\n}\\n\\nmodule.exports = { Cache };\\n"}
                        """)
                .hint("`set()` writes into `this.store` unconditionally. Nothing ever checks the current size against `this.maxSize` or removes an old entry. `Map` preserves insertion order — its first key is always the oldest.")
                .build();

        // Incident 7: Event listener leak
        Incident incident7 = Incident.builder()
                .id("memory-listener-leak")
                .title("Ticker Service Listener Leak")
                .difficulty("HARD")
                .category("memory")
                .description("""
                        ### Problem Statement
                        A background job restarts the `Ticker` service periodically (e.g. after a config
                        reload). After a few dozen restarts, each tick fires noticeably more work than it
                        should, and process memory grows with every restart even though nothing else changed.

                        ### Instructions
                        1. Inspect `solution.js`.
                        2. `start()` is called every time the ticker is (re)configured.
                        3. Fix it so restarting the ticker never leaves more than one `'tick'` listener
                           registered on the emitter.
                        """)
                .logs("""
                        [2026-07-14 11:00:00] INFO Ticker restarted (reload #1)
                        [2026-07-14 11:05:00] INFO Ticker restarted (reload #2)
                        [2026-07-14 11:10:00] WARN MaxListenersExceededWarning: Possible EventEmitter memory leak detected. 11 tick listeners added.
                        """)
                .metrics("""
                        [
                          {"timestamp": "11:00", "cpu": 5, "memory": 38},
                          {"timestamp": "11:05", "cpu": 6, "memory": 52},
                          {"timestamp": "11:10", "cpu": 9, "memory": 88}
                        ]
                        """)
                .stackTrace("""
                        (node) MaxListenersExceededWarning: Possible EventEmitter memory leak detected.
                        11 tick listeners added to [EventEmitter]. MaxListeners is 1000. Use emitter.setMaxListeners() to increase limit
                        at Ticker.start (solution.js:9:18)
                        """)
                .baseCode("""
                        {"solution.js":"const EventEmitter = require('events');\\n\\nclass Ticker {\\n  constructor() {\\n    this.emitter = new EventEmitter();\\n    this.emitter.setMaxListeners(1000);\\n    this.count = 0;\\n  }\\n\\n  start() {\\n    this.emitter.on('tick', () => {\\n      this.count++;\\n    });\\n  }\\n\\n  tick() {\\n    this.emitter.emit('tick');\\n  }\\n}\\n\\nmodule.exports = { Ticker };\\n"}
                        """)
                .hiddenTests("""
                        const assert = require('assert');
                        const { Ticker } = require('./solution.js');

                        module.exports = [
                          {
                            name: "should register exactly one tick listener no matter how many times start() is called",
                            fn: () => {
                              const ticker = new Ticker();
                              for (let i = 0; i < 20; i++) {
                                ticker.start();
                              }
                              const count = ticker.emitter.listenerCount('tick');
                              assert.strictEqual(count, 1, `Expected exactly 1 'tick' listener, found ${count} — start() is leaking listeners on every call.`);
                            }
                          },
                          {
                            name: "should increment count exactly once per tick() regardless of restart count",
                            fn: () => {
                              const ticker = new Ticker();
                              ticker.start();
                              ticker.start();
                              ticker.start();
                              ticker.tick();
                              assert.strictEqual(ticker.count, 1, `Expected count to be 1 after a single tick(), got ${ticker.count} — duplicate listeners are firing multiple times.`);
                            }
                          }
                        ];
                        """)
                .referenceFix("""
                        {"solution.js":"const EventEmitter = require('events');\\n\\nclass Ticker {\\n  constructor() {\\n    this.emitter = new EventEmitter();\\n    this.emitter.setMaxListeners(1000);\\n    this.count = 0;\\n    this._onTick = () => {\\n      this.count++;\\n    };\\n  }\\n\\n  start() {\\n    this.emitter.removeListener('tick', this._onTick);\\n    this.emitter.on('tick', this._onTick);\\n  }\\n\\n  tick() {\\n    this.emitter.emit('tick');\\n  }\\n}\\n\\nmodule.exports = { Ticker };\\n"}
                        """)
                .hint("Every call to `start()` registers a brand new anonymous `'tick'` listener — none of the previous ones are ever removed. Keep a reference to the handler so you can `removeListener` it before re-adding.")
                .build();

        // Incident 8: Naive recursive Fibonacci
        Incident incident8 = Incident.builder()
                .id("cpu-naive-fibonacci")
                .title("Naive Recursive Fibonacci Blocking Requests")
                .difficulty("EASY")
                .category("cpu")
                .description("""
                        ### Problem Statement
                        A "lucky number" feature computes a Fibonacci value on demand. Support has noticed
                        that for larger inputs the whole API becomes unresponsive for the better part of a
                        second — every other in-flight request stalls until the Fibonacci call returns.

                        ### Instructions
                        1. Inspect `solution.js`.
                        2. `computeFib` recomputes the same sub-problems over and over (exponential time).
                        3. Fix it so computing `computeFib(40)` takes well under a second, without changing
                           its return value for any input.
                        """)
                .logs("""
                        [2026-07-15 08:00:00] INFO GET /api/fib?n=40
                        [2026-07-15 08:00:01] WARN Request /api/fib?n=40 took 761ms
                        [2026-07-15 08:00:01] ERROR Health check timeout while fib request was in flight
                        """)
                .metrics("""
                        [
                          {"timestamp": "08:00:00", "cpu": 5, "memory": 30},
                          {"timestamp": "08:00:01", "cpu": 100, "memory": 31}
                        ]
                        """)
                .stackTrace("""
                        Event loop block detected:
                        at computeFib (solution.js:2:10)
                        at computeFib (solution.js:3:24)
                        at computeFib (solution.js:3:24)
                        ... (millions of stack frames, exponential recursion)
                        """)
                .baseCode("""
                        {"solution.js":"function computeFib(n) {\\n  if (n <= 1) return n;\\n  return computeFib(n - 1) + computeFib(n - 2);\\n}\\n\\nmodule.exports = { computeFib };\\n"}
                        """)
                .hiddenTests("""
                        const assert = require('assert');
                        const { computeFib } = require('./solution.js');

                        module.exports = [
                          {
                            name: "should compute the correct fibonacci value",
                            fn: () => {
                              assert.strictEqual(computeFib(20), 6765);
                            }
                          },
                          {
                            name: "should compute fib(40) fast enough to not block concurrent requests",
                            fn: () => {
                              const start = Date.now();
                              const result = computeFib(40);
                              const duration = Date.now() - start;
                              assert.strictEqual(result, 102334155);
                              assert.ok(duration < 250, `computeFib(40) took ${duration}ms — naive recursion without memoization would block the event loop on every request.`);
                            }
                          }
                        ];
                        """)
                .referenceFix("""
                        {"solution.js":"function computeFib(n, cache = new Map()) {\\n  if (n <= 1) return n;\\n  if (cache.has(n)) return cache.get(n);\\n  const result = computeFib(n - 1, cache) + computeFib(n - 2, cache);\\n  cache.set(n, result);\\n  return result;\\n}\\n\\nmodule.exports = { computeFib };\\n"}
                        """)
                .hint("`computeFib(n)` calls itself twice, and each of those calls itself twice again — the same sub-problems get solved millions of times. Cache results you've already computed (memoization).")
                .build();

        // Incident 9: O(n^2) dedupe
        Incident incident9 = Incident.builder()
                .id("cpu-inefficient-dedupe")
                .title("O(n²) Deduplication Blocking the Server")
                .difficulty("HARD")
                .category("cpu")
                .description("""
                        ### Problem Statement
                        A batch import endpoint deduplicates incoming record IDs before storing them. It
                        works fine in staging with small test files, but production imports of tens of
                        thousands of IDs make the whole server unresponsive for over half a second per batch.

                        ### Instructions
                        1. Inspect `solution.js`.
                        2. `dedupeIds` uses `Array.prototype.indexOf` inside a `filter` callback — for every
                           element, it rescans from the start of the array.
                        3. Fix it to deduplicate in roughly linear time.
                        """)
                .logs("""
                        [2026-07-15 09:00:00] INFO POST /api/import — 40000 ids received
                        [2026-07-15 09:00:01] WARN Batch import took 615ms for 40000 ids
                        """)
                .metrics("""
                        [
                          {"timestamp": "09:00:00", "cpu": 10, "memory": 45},
                          {"timestamp": "09:00:01", "cpu": 100, "memory": 60}
                        ]
                        """)
                .stackTrace("""
                        Event loop block detected:
                        at Array.filter (<anonymous>)
                        at dedupeIds (solution.js:2:15)
                        """)
                .baseCode("""
                        {"solution.js":"function dedupeIds(ids) {\\n  return ids.filter((value, index, arr) => arr.indexOf(value) === index);\\n}\\n\\nmodule.exports = { dedupeIds };\\n"}
                        """)
                .hiddenTests("""
                        const assert = require('assert');
                        const { dedupeIds } = require('./solution.js');

                        module.exports = [
                          {
                            name: "should correctly remove duplicate ids while preserving order",
                            fn: () => {
                              const result = dedupeIds([1, 2, 2, 3, 1, 4]);
                              assert.deepStrictEqual(result, [1, 2, 3, 4]);
                            }
                          },
                          {
                            name: "should dedupe a large batch fast enough to not block the server",
                            fn: () => {
                              const spread = 39950;
                              const data = [];
                              for (let i = 0; i < 40000; i++) data.push(i % spread);
                              const start = Date.now();
                              const result = dedupeIds(data);
                              const duration = Date.now() - start;
                              assert.strictEqual(result.length, spread);
                              assert.ok(duration < 250, `Deduping 40,000 ids took ${duration}ms — an O(n^2) indexOf-based dedupe is far too slow at this scale.`);
                            }
                          }
                        ];
                        """)
                .referenceFix("""
                        {"solution.js":"function dedupeIds(ids) {\\n  return Array.from(new Set(ids));\\n}\\n\\nmodule.exports = { dedupeIds };\\n"}
                        """)
                .hint("`indexOf` scans the array from the beginning every single time it's called. Use a `Set` to track values you've already seen in O(1) per lookup instead.")
                .build();

        // Incident 10: Path traversal
        Incident incident10 = Incident.builder()
                .id("security-path-traversal")
                .title("Path Traversal in File Reader")
                .difficulty("HARD")
                .category("security")
                .description("""
                        ### Problem Statement
                        A security audit found that the "download my file" endpoint can be tricked into
                        returning files well outside the user's own upload directory by passing a filename
                        containing `../` segments.

                        ### Instructions
                        1. Inspect `solution.js`.
                        2. `readUserFile` joins the requested filename onto the safe base directory and
                           resolves it, but never verifies the resolved path is still inside that directory.
                        3. Fix it so any path that would escape `/safe/` is rejected.
                        """)
                .logs("""
                        [2026-07-15 10:00:00] INFO GET /api/files?name=readme.txt
                        [2026-07-15 10:00:05] WARN GET /api/files?name=../secret/admin-config.txt
                        [2026-07-15 10:00:05] ERROR Audit: request resolved outside /safe/ and was served anyway
                        """)
                .metrics("[]")
                .stackTrace("")
                .baseCode("""
                        {"solution.js":"const path = require('path');\\n\\nconst files = new Map([\\n  ['/safe/readme.txt', 'Welcome!'],\\n  ['/safe/notes.txt', 'Some notes'],\\n  ['/secret/admin-config.txt', 'API_KEY=super-secret-value'],\\n]);\\n\\nfunction readUserFile(filename) {\\n  const resolved = path.posix.normalize(path.posix.join('/safe', filename));\\n  if (!files.has(resolved)) {\\n    return null;\\n  }\\n  return files.get(resolved);\\n}\\n\\nmodule.exports = { readUserFile };\\n"}
                        """)
                .hiddenTests("""
                        const assert = require('assert');
                        const { readUserFile } = require('./solution.js');

                        module.exports = [
                          {
                            name: "should read files within the safe directory",
                            fn: () => {
                              assert.strictEqual(readUserFile('readme.txt'), 'Welcome!');
                              assert.strictEqual(readUserFile('notes.txt'), 'Some notes');
                            }
                          },
                          {
                            name: "should block path traversal outside the safe directory",
                            fn: () => {
                              const result = readUserFile('../secret/admin-config.txt');
                              assert.strictEqual(result, null, 'Path traversal succeeded! Secret file contents were exposed: ' + result);
                            }
                          }
                        ];
                        """)
                .referenceFix("""
                        {"solution.js":"const path = require('path');\\n\\nconst files = new Map([\\n  ['/safe/readme.txt', 'Welcome!'],\\n  ['/safe/notes.txt', 'Some notes'],\\n  ['/secret/admin-config.txt', 'API_KEY=super-secret-value'],\\n]);\\n\\nfunction readUserFile(filename) {\\n  const resolved = path.posix.normalize(path.posix.join('/safe', filename));\\n  if (!resolved.startsWith('/safe/')) {\\n    return null;\\n  }\\n  if (!files.has(resolved)) {\\n    return null;\\n  }\\n  return files.get(resolved);\\n}\\n\\nmodule.exports = { readUserFile };\\n"}
                        """)
                .hint("`path.posix.normalize(path.posix.join('/safe', filename))` will happily resolve `../secret/x` to `/secret/x`. After resolving, check that the result still starts with `/safe/` before looking it up.")
                .build();

        // Incident 11: Insecure Direct Object Reference
        Incident incident11 = Incident.builder()
                .id("security-idor")
                .title("Insecure Direct Object Reference on Orders")
                .difficulty("MEDIUM")
                .category("security")
                .description("""
                        ### Problem Statement
                        A customer reported they could see another customer's order total simply by
                        incrementing the order id in the URL. There is no ownership check on the order
                        lookup endpoint — any authenticated user can fetch any order by id.

                        ### Instructions
                        1. Inspect `solution.js`.
                        2. `getOrder` looks up an order by id but ignores who is asking for it.
                        3. Fix it so a user can only fetch orders they own.
                        """)
                .logs("""
                        [2026-07-15 11:00:00] INFO user-1 fetched order-1 (owner: user-1) — OK
                        [2026-07-15 11:00:05] WARN user-1 fetched order-2 (owner: user-2) — returned 200 with full order data
                        """)
                .metrics("[]")
                .stackTrace("")
                .baseCode("""
                        {"solution.js":"const orders = new Map([\\n  ['order-1', { id: 'order-1', ownerId: 'user-1', total: 42.5 }],\\n  ['order-2', { id: 'order-2', ownerId: 'user-2', total: 99.0 }],\\n]);\\n\\nfunction getOrder(orderId, requestingUserId) {\\n  const order = orders.get(orderId);\\n  if (!order) return null;\\n  return order;\\n}\\n\\nmodule.exports = { getOrder };\\n"}
                        """)
                .hiddenTests("""
                        const assert = require('assert');
                        const { getOrder } = require('./solution.js');

                        module.exports = [
                          {
                            name: "owner can view their own order",
                            fn: () => {
                              const order = getOrder('order-1', 'user-1');
                              assert.ok(order, 'Expected the owner to be able to fetch their own order.');
                              assert.strictEqual(order.id, 'order-1');
                            }
                          },
                          {
                            name: "should block access to another user's order (IDOR)",
                            fn: () => {
                              const result = getOrder('order-2', 'user-1');
                              assert.strictEqual(result, null, "IDOR vulnerability: user-1 was able to view user-2's order: " + JSON.stringify(result));
                            }
                          }
                        ];
                        """)
                .referenceFix("""
                        {"solution.js":"const orders = new Map([\\n  ['order-1', { id: 'order-1', ownerId: 'user-1', total: 42.5 }],\\n  ['order-2', { id: 'order-2', ownerId: 'user-2', total: 99.0 }],\\n]);\\n\\nfunction getOrder(orderId, requestingUserId) {\\n  const order = orders.get(orderId);\\n  if (!order) return null;\\n  if (order.ownerId !== requestingUserId) return null;\\n  return order;\\n}\\n\\nmodule.exports = { getOrder };\\n"}
                        """)
                .hint("`getOrder` finds the order by id and returns it unconditionally. Compare `order.ownerId` against the `requestingUserId` parameter it's already being passed.")
                .build();

        // Incident 12: Unhandled JSON.parse crash
        Incident incident12 = Incident.builder()
                .id("stability-json-parse-crash")
                .title("Malformed Config Crashes Parser")
                .difficulty("EASY")
                .category("stability")
                .description("""
                        ### Problem Statement
                        Whenever a client sends a slightly malformed config payload, the request handler
                        throws instead of responding with a clean error — and in production this kind of
                        unguarded parse has been known to bring the whole process down.

                        ### Instructions
                        1. Inspect `solution.js`.
                        2. `parseConfig` calls `JSON.parse` directly on untrusted input.
                        3. Fix it to return `{ ok: false, error }` for invalid input instead of throwing.
                        """)
                .logs("""
                        [2026-07-15 12:00:00] INFO Config payload parsed successfully
                        [2026-07-15 12:00:10] ERROR Uncaught SyntaxError: Unexpected token o in JSON at position 1
                        [2026-07-15 12:00:10] INFO Process restarted by supervisor
                        """)
                .metrics("[]")
                .stackTrace("""
                        SyntaxError: Unexpected token o in JSON at position 1
                        at JSON.parse (<anonymous>)
                        at parseConfig (solution.js:2:20)
                        """)
                .baseCode("""
                        {"solution.js":"function parseConfig(rawInput) {\\n  const data = JSON.parse(rawInput);\\n  return { ok: true, data };\\n}\\n\\nmodule.exports = { parseConfig };\\n"}
                        """)
                .hiddenTests("""
                        const assert = require('assert');
                        const { parseConfig } = require('./solution.js');

                        module.exports = [
                          {
                            name: "parses valid JSON config",
                            fn: () => {
                              const result = parseConfig('{"timeout": 5000}');
                              assert.strictEqual(result.ok, true);
                              assert.strictEqual(result.data.timeout, 5000);
                            }
                          },
                          {
                            name: "should not crash on malformed JSON input",
                            fn: () => {
                              let result;
                              try {
                                result = parseConfig('{not valid json');
                              } catch (e) {
                                assert.fail('parseConfig threw instead of returning a safe error result: ' + e.message);
                              }
                              assert.strictEqual(result.ok, false, 'Expected { ok: false } for malformed input.');
                            }
                          }
                        ];
                        """)
                .referenceFix("""
                        {"solution.js":"function parseConfig(rawInput) {\\n  try {\\n    const data = JSON.parse(rawInput);\\n    return { ok: true, data };\\n  } catch (e) {\\n    return { ok: false, error: e.message };\\n  }\\n}\\n\\nmodule.exports = { parseConfig };\\n"}
                        """)
                .hint("`JSON.parse` throws a `SyntaxError` on invalid input — wrap the call in a `try/catch` and return a safe result object in the `catch` branch instead of letting it propagate.")
                .build();

        // Incident 13: Unhandled per-item batch error
        Incident incident13 = Incident.builder()
                .id("stability-batch-error")
                .title("One Bad Item Crashes the Whole Batch")
                .difficulty("MEDIUM")
                .category("stability")
                .description("""
                        ### Problem Statement
                        A nightly job processes a queue of items one at a time. When a single item fails
                        (e.g. a malformed record), the entire batch aborts and every item after the failing
                        one — including perfectly valid ones — never gets processed.

                        ### Instructions
                        1. Inspect `solution.js`.
                        2. `processQueue` awaits each `worker(item)` call with no error handling.
                        3. Fix it so a failing item is recorded in `failed` and processing continues with
                           the rest of the queue.
                        """)
                .logs("""
                        [2026-07-15 13:00:00] INFO Batch job started, 3 items queued
                        [2026-07-15 13:00:01] ERROR processing failed for item 2
                        [2026-07-15 13:00:01] ERROR Batch job aborted — items after #2 were never processed
                        """)
                .metrics("[]")
                .stackTrace("""
                        Error: processing failed for item 2
                        at worker (tests.js:20:15)
                        at processQueue (solution.js:5:24)
                        """)
                .baseCode("""
                        {"solution.js":"async function processQueue(items, worker) {\\n  const succeeded = [];\\n  const failed = [];\\n  for (const item of items) {\\n    const result = await worker(item);\\n    succeeded.push(result);\\n  }\\n  return { succeeded, failed };\\n}\\n\\nmodule.exports = { processQueue };\\n"}
                        """)
                .hiddenTests("""
                        const assert = require('assert');
                        const { processQueue } = require('./solution.js');

                        module.exports = [
                          {
                            name: "should process all items when none fail",
                            fn: async () => {
                              const result = await processQueue([1, 2, 3], async (n) => n * 2);
                              assert.deepStrictEqual(result.succeeded, [2, 4, 6]);
                            }
                          },
                          {
                            name: "one failing item should not crash the whole batch",
                            fn: async () => {
                              const worker = async (n) => {
                                if (n === 2) throw new Error('processing failed for item 2');
                                return n * 10;
                              };
                              let result;
                              try {
                                result = await processQueue([1, 2, 3], worker);
                              } catch (e) {
                                assert.fail('processQueue rejected entirely instead of isolating the failing item: ' + e.message);
                              }
                              assert.deepStrictEqual(result.succeeded, [10, 30], 'Items 1 and 3 should have succeeded.');
                              assert.strictEqual(result.failed.length, 1, 'Item 2 should be recorded as failed.');
                            }
                          }
                        ];
                        """)
                .referenceFix("""
                        {"solution.js":"async function processQueue(items, worker) {\\n  const succeeded = [];\\n  const failed = [];\\n  for (const item of items) {\\n    try {\\n      const result = await worker(item);\\n      succeeded.push(result);\\n    } catch (err) {\\n      failed.push({ item, error: err.message });\\n    }\\n  }\\n  return { succeeded, failed };\\n}\\n\\nmodule.exports = { processQueue };\\n"}
                        """)
                .hint("`await worker(item)` isn't wrapped in anything — if the returned promise rejects, it propagates straight out of `processQueue` and aborts the loop. Wrap each iteration's `await` in its own `try/catch`.")
                .build();

        // Incident 14: Unsynchronized lazy singleton init
        Incident incident14 = Incident.builder()
                .id("concurrency-double-init")
                .title("Duplicate Connections from Unsynchronized Lazy Init")
                .difficulty("MEDIUM")
                .category("concurrency")
                .description("""
                        ### Problem Statement
                        A connection pool is supposed to be created once, lazily, on first use. Under
                        traffic bursts at startup, monitoring shows several *separate* connections being
                        opened within milliseconds of each other instead of just one.

                        ### Instructions
                        1. Inspect `solution.js`.
                        2. `getConnection` checks `if (!instance)` and awaits creating one — but that check
                           happens again for every concurrent caller before the first one finishes.
                        3. Fix it so concurrent callers all share the result of a single in-flight creation.
                        """)
                .logs("""
                        [2026-07-15 14:00:00] INFO 5 concurrent requests triggered getConnection() on cold start
                        [2026-07-15 14:00:00] WARN expensiveCreate() called 5 times — expected 1
                        """)
                .metrics("[]")
                .stackTrace("""
                        No exception thrown — this is a logic race condition, not a crash.
                        Concurrent calls interleave between the `if (!instance)` check and the
                        `instance = await expensiveCreate()` assignment.
                        """)
                .baseCode("""
                        {"solution.js":"let instance = null;\\nlet createCallCount = 0;\\n\\nasync function expensiveCreate() {\\n  createCallCount++;\\n  await new Promise((resolve) => setTimeout(resolve, 10));\\n  return { id: createCallCount, createdAt: Date.now() };\\n}\\n\\nasync function getConnection() {\\n  if (!instance) {\\n    instance = await expensiveCreate();\\n  }\\n  return instance;\\n}\\n\\nmodule.exports = {\\n  getConnection,\\n  _getCreateCallCount: () => createCallCount,\\n  _reset: () => { instance = null; createCallCount = 0; },\\n};\\n"}
                        """)
                .hiddenTests("""
                        const assert = require('assert');
                        const solution = require('./solution.js');

                        module.exports = [
                          {
                            name: "concurrent getConnection() calls should share a single instance",
                            fn: async () => {
                              solution._reset();
                              const results = await Promise.all([
                                solution.getConnection(),
                                solution.getConnection(),
                                solution.getConnection(),
                                solution.getConnection(),
                                solution.getConnection(),
                              ]);
                              const first = results[0];
                              for (const r of results) {
                                assert.strictEqual(r, first, 'Concurrent calls returned different connection instances.');
                              }
                              assert.strictEqual(
                                solution._getCreateCallCount(),
                                1,
                                `Expected expensiveCreate() to run exactly once, but it ran ${solution._getCreateCallCount()} times.`
                              );
                            }
                          }
                        ];
                        """)
                .referenceFix("""
                        {"solution.js":"let instance = null;\\nlet initPromise = null;\\nlet createCallCount = 0;\\n\\nasync function expensiveCreate() {\\n  createCallCount++;\\n  await new Promise((resolve) => setTimeout(resolve, 10));\\n  return { id: createCallCount, createdAt: Date.now() };\\n}\\n\\nasync function getConnection() {\\n  if (instance) return instance;\\n  if (!initPromise) {\\n    initPromise = expensiveCreate();\\n  }\\n  instance = await initPromise;\\n  return instance;\\n}\\n\\nmodule.exports = {\\n  getConnection,\\n  _getCreateCallCount: () => createCallCount,\\n  _reset: () => { instance = null; initPromise = null; createCallCount = 0; },\\n};\\n"}
                        """)
                .hint("Cache the *promise* returned by `expensiveCreate()`, not just its resolved value — so a second caller arriving before the first `await` finishes reuses the same in-flight promise instead of starting a new creation.")
                .build();

        // Incident 15: Missing concurrency limit
        Incident incident15 = Incident.builder()
                .id("concurrency-limit")
                .title("Unbounded Parallel Fan-Out Overwhelms Downstream Service")
                .difficulty("HARD")
                .category("concurrency")
                .description("""
                        ### Problem Statement
                        A batch job fires every item at a rate-limited downstream API simultaneously via
                        `Promise.all`. The downstream service starts rejecting requests once too many are
                        in flight at once, and the whole batch fails.

                        ### Instructions
                        1. Inspect `solution.js`.
                        2. `runAll` accepts a `maxConcurrent` argument but never actually enforces it.
                        3. Fix it so no more than `maxConcurrent` worker calls are ever in flight at the
                           same time, while still processing every item and returning results in order.
                        """)
                .logs("""
                        [2026-07-15 15:00:00] INFO Batch of 20 items dispatched with maxConcurrent=3
                        [2026-07-15 15:00:00] WARN Downstream service observed 20 concurrent in-flight requests
                        [2026-07-15 15:00:01] ERROR Downstream rate limiter rejected 17 requests
                        """)
                .metrics("[]")
                .stackTrace("")
                .baseCode("""
                        {"solution.js":"async function runAll(items, worker, maxConcurrent) {\\n  return Promise.all(items.map((item) => worker(item)));\\n}\\n\\nmodule.exports = { runAll };\\n"}
                        """)
                .hiddenTests("""
                        const assert = require('assert');
                        const { runAll } = require('./solution.js');

                        module.exports = [
                          {
                            name: "should process all items and return correct results in order",
                            fn: async () => {
                              const results = await runAll([1, 2, 3, 4, 5], async (n) => n * 2, 2);
                              assert.deepStrictEqual(results, [2, 4, 6, 8, 10]);
                            }
                          },
                          {
                            name: "should never run more than maxConcurrent workers at once",
                            fn: async () => {
                              let inFlight = 0;
                              let maxObserved = 0;
                              const worker = async (n) => {
                                inFlight++;
                                maxObserved = Math.max(maxObserved, inFlight);
                                await new Promise((resolve) => setTimeout(resolve, 15));
                                inFlight--;
                                return n;
                              };
                              const items = Array.from({ length: 20 }, (_, i) => i);
                              await runAll(items, worker, 3);
                              assert.ok(maxObserved <= 3, `Observed ${maxObserved} concurrent workers running at once, exceeding the limit of 3 — this can overwhelm a rate-limited downstream resource.`);
                            }
                          }
                        ];
                        """)
                .referenceFix("""
                        {"solution.js":"async function runAll(items, worker, maxConcurrent) {\\n  const results = new Array(items.length);\\n  let nextIndex = 0;\\n\\n  async function runWorker() {\\n    while (nextIndex < items.length) {\\n      const currentIndex = nextIndex++;\\n      results[currentIndex] = await worker(items[currentIndex]);\\n    }\\n  }\\n\\n  const workers = Array.from({ length: Math.min(maxConcurrent, items.length) }, runWorker);\\n  await Promise.all(workers);\\n  return results;\\n}\\n\\nmodule.exports = { runAll };\\n"}
                        """)
                .hint("`items.map(item => worker(item))` starts every worker call in the very same tick. Instead, run a fixed pool of `maxConcurrent` \"workers\" that each pull the next item off a shared index as they finish.")
                .build();

        // Incident 16: N+1 query pattern
        Incident incident16 = Incident.builder()
                .id("database-n-plus-one")
                .title("N+1 Query Pattern Loading User Posts")
                .difficulty("EASY")
                .category("database")
                .description("""
                        ### Problem Statement
                        The "users with their posts" endpoint is fine with a handful of users in staging,
                        but in production, with thousands of users, it issues one database query *per user*
                        on top of the initial users query — and response times scale linearly with the
                        number of users.

                        ### Instructions
                        1. Inspect `solution.js`.
                        2. `getUsersWithPosts` calls `db.getPostsByUser(id)` once per user inside `.map()`.
                        3. Fix it to fetch all users' posts in a single batched query
                           (`db.getPostsByUsers(ids)` is already available).
                        """)
                .logs("""
                        [2026-07-15 16:00:00] INFO GET /api/users-with-posts
                        [2026-07-15 16:00:00] WARN 4 database queries issued for 3 users (expected 2)
                        """)
                .metrics("[]")
                .stackTrace("")
                .baseCode("""
                        {"solution.js":"const users = [\\n  { id: 1, name: 'Alice' },\\n  { id: 2, name: 'Bob' },\\n  { id: 3, name: 'Carol' },\\n];\\nconst posts = [\\n  { id: 101, userId: 1, title: 'Hello' },\\n  { id: 102, userId: 1, title: 'World' },\\n  { id: 103, userId: 2, title: 'Foo' },\\n  { id: 104, userId: 3, title: 'Bar' },\\n];\\n\\nlet queryCount = 0;\\n\\nconst db = {\\n  getUsers: () => { queryCount++; return users; },\\n  getPostsByUser: (userId) => { queryCount++; return posts.filter((p) => p.userId === userId); },\\n  getPostsByUsers: (userIds) => { queryCount++; return posts.filter((p) => userIds.includes(p.userId)); },\\n  _getQueryCount: () => queryCount,\\n  _resetQueryCount: () => { queryCount = 0; },\\n};\\n\\nfunction getUsersWithPosts() {\\n  const allUsers = db.getUsers();\\n  return allUsers.map((u) => ({ ...u, posts: db.getPostsByUser(u.id) }));\\n}\\n\\nmodule.exports = { getUsersWithPosts, db };\\n"}
                        """)
                .hiddenTests("""
                        const assert = require('assert');
                        const { getUsersWithPosts, db } = require('./solution.js');

                        module.exports = [
                          {
                            name: "should attach the correct posts to each user",
                            fn: () => {
                              const result = getUsersWithPosts();
                              const alice = result.find((u) => u.name === 'Alice');
                              assert.strictEqual(alice.posts.length, 2);
                            }
                          },
                          {
                            name: "should not issue an N+1 query per user",
                            fn: () => {
                              db._resetQueryCount();
                              getUsersWithPosts();
                              const count = db._getQueryCount();
                              assert.ok(count <= 2, `Expected at most 2 queries (one for users, one batched for posts), but issued ${count} — this is an N+1 query pattern that won't scale.`);
                            }
                          }
                        ];
                        """)
                .referenceFix("""
                        {"solution.js":"const users = [\\n  { id: 1, name: 'Alice' },\\n  { id: 2, name: 'Bob' },\\n  { id: 3, name: 'Carol' },\\n];\\nconst posts = [\\n  { id: 101, userId: 1, title: 'Hello' },\\n  { id: 102, userId: 1, title: 'World' },\\n  { id: 103, userId: 2, title: 'Foo' },\\n  { id: 104, userId: 3, title: 'Bar' },\\n];\\n\\nlet queryCount = 0;\\n\\nconst db = {\\n  getUsers: () => { queryCount++; return users; },\\n  getPostsByUser: (userId) => { queryCount++; return posts.filter((p) => p.userId === userId); },\\n  getPostsByUsers: (userIds) => { queryCount++; return posts.filter((p) => userIds.includes(p.userId)); },\\n  _getQueryCount: () => queryCount,\\n  _resetQueryCount: () => { queryCount = 0; },\\n};\\n\\nfunction getUsersWithPosts() {\\n  const allUsers = db.getUsers();\\n  const userIds = allUsers.map((u) => u.id);\\n  const allPosts = db.getPostsByUsers(userIds);\\n  return allUsers.map((u) => ({ ...u, posts: allPosts.filter((p) => p.userId === u.id) }));\\n}\\n\\nmodule.exports = { getUsersWithPosts, db };\\n"}
                        """)
                .hint("Collect all the user ids first, then make a single `db.getPostsByUsers(userIds)` call, and group the results back onto each user in memory.")
                .build();

        // Incident 17: Missing transaction / non-atomic transfer
        Incident incident17 = Incident.builder()
                .id("database-missing-transaction")
                .title("Funds Vanish on Failed Transfer")
                .difficulty("HARD")
                .category("database")
                .description("""
                        ### Problem Statement
                        Finance flagged a discrepancy: when a fund transfer fails partway through (e.g. the
                        destination account doesn't exist), the source account has already been debited —
                        but since the credit step never completes, the money simply disappears instead of
                        the whole transfer being rolled back.

                        ### Instructions
                        1. Inspect `solution.js`.
                        2. `transferFunds` debits the source account, then credits the destination — as two
                           separate, non-atomic writes.
                        3. Fix it so a failure anywhere in the transfer leaves both accounts exactly as they
                           were before the transfer started (use the provided `db.transaction(fn)` helper
                           to wrap the fix, or roll back manually on error).
                        """)
                .logs("""
                        [2026-07-15 17:00:00] INFO Transfer acct-a -> acct-b: 30 — OK
                        [2026-07-15 17:05:00] ERROR Transfer acct-a -> acct-nonexistent: 30 failed — Destination account does not exist
                        [2026-07-15 17:05:00] ERROR Finance reconciliation: acct-a debited 30 but no matching credit anywhere
                        """)
                .metrics("[]")
                .stackTrace("""
                        Error: Destination account does not exist
                        at transferFunds (solution.js:14:11)
                        """)
                .baseCode("""
                        {"solution.js":"const accounts = new Map([['acct-a', 100], ['acct-b', 50]]);\\n\\nconst db = {\\n  getBalance: (id) => accounts.get(id),\\n  setBalance: (id, value) => accounts.set(id, value),\\n  _reset: () => { accounts.set('acct-a', 100); accounts.set('acct-b', 50); },\\n};\\n\\nfunction transferFunds(fromId, toId, amount) {\\n  const fromBalance = db.getBalance(fromId);\\n  db.setBalance(fromId, fromBalance - amount);\\n\\n  const toBalance = db.getBalance(toId);\\n  if (toBalance === undefined) {\\n    throw new Error('Destination account does not exist');\\n  }\\n  db.setBalance(toId, toBalance + amount);\\n}\\n\\nmodule.exports = { transferFunds, db };\\n"}
                        """)
                .hiddenTests("""
                        const assert = require('assert');
                        const { transferFunds, db } = require('./solution.js');

                        module.exports = [
                          {
                            name: "should transfer funds between two valid accounts",
                            fn: () => {
                              db._reset();
                              transferFunds('acct-a', 'acct-b', 30);
                              assert.strictEqual(db.getBalance('acct-a'), 70);
                              assert.strictEqual(db.getBalance('acct-b'), 80);
                            }
                          },
                          {
                            name: "should roll back the debit if the credit step fails",
                            fn: () => {
                              db._reset();
                              assert.throws(() => transferFunds('acct-a', 'acct-nonexistent', 30));
                              assert.strictEqual(
                                db.getBalance('acct-a'),
                                100,
                                `Account was debited (balance ${db.getBalance('acct-a')}) even though the transfer failed — funds vanished instead of rolling back.`
                              );
                            }
                          }
                        ];
                        """)
                .referenceFix("""
                        {"solution.js":"const accounts = new Map([['acct-a', 100], ['acct-b', 50]]);\\n\\nconst db = {\\n  getBalance: (id) => accounts.get(id),\\n  setBalance: (id, value) => accounts.set(id, value),\\n  _reset: () => { accounts.set('acct-a', 100); accounts.set('acct-b', 50); },\\n  transaction: (fn) => {\\n    const snapshot = new Map(accounts);\\n    try {\\n      fn();\\n    } catch (err) {\\n      accounts.clear();\\n      for (const [k, v] of snapshot) accounts.set(k, v);\\n      throw err;\\n    }\\n  },\\n};\\n\\nfunction transferFunds(fromId, toId, amount) {\\n  db.transaction(() => {\\n    const fromBalance = db.getBalance(fromId);\\n    db.setBalance(fromId, fromBalance - amount);\\n\\n    const toBalance = db.getBalance(toId);\\n    if (toBalance === undefined) {\\n      throw new Error('Destination account does not exist');\\n    }\\n    db.setBalance(toId, toBalance + amount);\\n  });\\n}\\n\\nmodule.exports = { transferFunds, db };\\n"}
                        """)
                .hint("Wrap the debit-then-credit sequence in a single `db.transaction(fn)` call. Have the transaction helper snapshot state beforehand and restore it if `fn` throws.")
                .build();

        // Incident 18: Unbounded full-table scan
        Incident incident18 = Incident.builder()
                .id("database-unbounded-query")
                .title("Full Table Scan Instead of Indexed Lookup")
                .difficulty("MEDIUM")
                .category("database")
                .description("""
                        ### Problem Statement
                        A record search endpoint that looks up a single record by id is scanning the entire
                        table into memory on every call instead of using the indexed lookup the mock data
                        layer already provides — response times grow with total table size instead of
                        staying constant.

                        ### Instructions
                        1. Inspect `solution.js`.
                        2. `searchRecords` calls `db.getAll()` and filters in JavaScript.
                        3. Fix it to use `db.query(filter)`, which performs an indexed lookup instead of a
                           full scan.
                        """)
                .logs("""
                        [2026-07-15 18:00:00] INFO GET /api/records?id=42
                        [2026-07-15 18:00:00] WARN Query scanned 2000 records to return a single row
                        """)
                .metrics("[]")
                .stackTrace("")
                .baseCode("""
                        {"solution.js":"const records = [];\\nfor (let i = 0; i < 2000; i++) {\\n  records.push({ id: i, category: i % 50 });\\n}\\n\\nlet recordsScanned = 0;\\n\\nconst db = {\\n  getAll: () => { recordsScanned += records.length; return records; },\\n  query: (filter) => {\\n    const matches = records.filter((r) => r.id === filter.id);\\n    recordsScanned += matches.length;\\n    return matches;\\n  },\\n  _getRecordsScanned: () => recordsScanned,\\n  _resetScanCount: () => { recordsScanned = 0; },\\n};\\n\\nfunction searchRecords(filter) {\\n  const all = db.getAll();\\n  return all.filter((r) => r.id === filter.id);\\n}\\n\\nmodule.exports = { searchRecords, db };\\n"}
                        """)
                .hiddenTests("""
                        const assert = require('assert');
                        const { searchRecords, db } = require('./solution.js');

                        module.exports = [
                          {
                            name: "should find the correct record by id",
                            fn: () => {
                              const results = searchRecords({ id: 42 });
                              assert.strictEqual(results.length, 1);
                              assert.strictEqual(results[0].id, 42);
                            }
                          },
                          {
                            name: "should use an indexed lookup instead of scanning the entire table",
                            fn: () => {
                              db._resetScanCount();
                              searchRecords({ id: 42 });
                              const scanned = db._getRecordsScanned();
                              assert.ok(scanned <= 10, `Scanned ${scanned} records for a single-id lookup — this is a full table scan instead of an indexed query and won't scale.`);
                            }
                          }
                        ];
                        """)
                .referenceFix("""
                        {"solution.js":"const records = [];\\nfor (let i = 0; i < 2000; i++) {\\n  records.push({ id: i, category: i % 50 });\\n}\\n\\nlet recordsScanned = 0;\\n\\nconst db = {\\n  getAll: () => { recordsScanned += records.length; return records; },\\n  query: (filter) => {\\n    const matches = records.filter((r) => r.id === filter.id);\\n    recordsScanned += matches.length;\\n    return matches;\\n  },\\n  _getRecordsScanned: () => recordsScanned,\\n  _resetScanCount: () => { recordsScanned = 0; },\\n};\\n\\nfunction searchRecords(filter) {\\n  return db.query(filter);\\n}\\n\\nmodule.exports = { searchRecords, db };\\n"}
                        """)
                .hint("`db.query(filter)` already exists and performs an indexed lookup — `searchRecords` just needs to call it instead of `db.getAll()` plus a manual filter.")
                .build();

        // Incidents are static reference content owned by this seeder (not user data), so
        // upsert-on-boot keeps them current when hand-authored content changes, instead of
        // silently ignoring edits after the first seed.
        incidentRepository.save(incident1);
        incidentRepository.save(incident2);
        incidentRepository.save(incident3);
        incidentRepository.save(incident4);
        incidentRepository.save(incident5);
        incidentRepository.save(incident6);
        incidentRepository.save(incident7);
        incidentRepository.save(incident8);
        incidentRepository.save(incident9);
        incidentRepository.save(incident10);
        incidentRepository.save(incident11);
        incidentRepository.save(incident12);
        incidentRepository.save(incident13);
        incidentRepository.save(incident14);
        incidentRepository.save(incident15);
        incidentRepository.save(incident16);
        incidentRepository.save(incident17);
        incidentRepository.save(incident18);
    }
}
