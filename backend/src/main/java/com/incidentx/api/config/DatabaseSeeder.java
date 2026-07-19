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

        // Incident 19: Recent searches unbounded growth
        Incident incident19 = Incident.builder()
                .id("memory-recent-searches")
                .title("Unbounded Recent Searches List")
                .difficulty("EASY")
                .category("memory")
                .description("""
                        ### Problem Statement
                        The "recent searches" widget keeps every search a user has ever made in memory,
                        instead of just the last handful — after a long session it holds thousands of entries.

                        ### Instructions
                        1. Inspect `solution.js`.
                        2. `add()` should keep at most `maxSize` recent searches.
                        3. Fix it so the list never exceeds `maxSize`, keeping the most recent ones.
                        """)
                .logs("""
                        [2026-07-16 09:00:00] WARN Recent searches list has grown to 4210 entries (maxSize=20)
                        """)
                .metrics("[]")
                .stackTrace("")
                .baseCode("""
                        {"solution.js":"class RecentSearches {\\n  constructor(maxSize) {\\n    this.maxSize = maxSize;\\n    this.items = [];\\n  }\\n\\n  add(query) {\\n    this.items.push(query);\\n  }\\n\\n  list() {\\n    return this.items;\\n  }\\n}\\n\\nmodule.exports = { RecentSearches };\\n"}
                        """)
                .hiddenTests("""
                        const assert = require('assert');
                        const { RecentSearches } = require('./solution.js');

                        module.exports = [
                          {
                            name: "should track added searches",
                            fn: () => {
                              const rs = new RecentSearches(20);
                              rs.add('hello');
                              assert.deepStrictEqual(rs.list(), ['hello']);
                            }
                          },
                          {
                            name: "should cap the list at maxSize entries",
                            fn: () => {
                              const rs = new RecentSearches(20);
                              for (let i = 0; i < 100; i++) {
                                rs.add('query' + i);
                              }
                              assert.ok(rs.list().length <= 20, `Recent searches grew to ${rs.list().length} entries, exceeding the cap of 20.`);
                              assert.strictEqual(rs.list()[rs.list().length - 1], 'query99', 'Most recent search should still be present.');
                            }
                          }
                        ];
                        """)
                .referenceFix("""
                        {"solution.js":"class RecentSearches {\\n  constructor(maxSize) {\\n    this.maxSize = maxSize;\\n    this.items = [];\\n  }\\n\\n  add(query) {\\n    this.items.push(query);\\n    if (this.items.length > this.maxSize) {\\n      this.items.shift();\\n    }\\n  }\\n\\n  list() {\\n    return this.items;\\n  }\\n}\\n\\nmodule.exports = { RecentSearches };\\n"}
                        """)
                .hint("`add()` pushes onto `this.items` but never checks the length against `this.maxSize`. Trim from the front once it grows past the cap.")
                .build();

        // Incident 20: Logged-out sessions never removed from memory
        Incident incident20 = Incident.builder()
                .id("memory-map-cleanup")
                .title("Logged-Out Sessions Never Freed")
                .difficulty("MEDIUM")
                .category("memory")
                .description("""
                        ### Problem Statement
                        The session store's memory footprint only ever grows, even though users log out
                        constantly throughout the day. Logged-out sessions are marked inactive but never
                        actually removed.

                        ### Instructions
                        1. Inspect `solution.js`.
                        2. `logout()` flips a flag instead of removing the session from the store.
                        3. Fix it so logging out actually deletes the session entry.
                        """)
                .logs("""
                        [2026-07-16 10:00:00] INFO 500 users logged in and back out over the last hour
                        [2026-07-16 10:00:01] WARN Session store still holds 500 entries after logout
                        """)
                .metrics("[]")
                .stackTrace("")
                .baseCode("""
                        {"solution.js":"class SessionStore {\\n  constructor() {\\n    this.sessions = new Map();\\n  }\\n\\n  login(token, userId) {\\n    this.sessions.set(token, { userId, active: true });\\n  }\\n\\n  logout(token) {\\n    const session = this.sessions.get(token);\\n    if (session) {\\n      session.active = false;\\n    }\\n  }\\n\\n  isValid(token) {\\n    const session = this.sessions.get(token);\\n    return !!session && session.active;\\n  }\\n\\n  size() {\\n    return this.sessions.size;\\n  }\\n}\\n\\nmodule.exports = { SessionStore };\\n"}
                        """)
                .hiddenTests("""
                        const assert = require('assert');
                        const { SessionStore } = require('./solution.js');

                        module.exports = [
                          {
                            name: "should invalidate a session after logout",
                            fn: () => {
                              const store = new SessionStore();
                              store.login('tok-1', 'user-1');
                              store.logout('tok-1');
                              assert.strictEqual(store.isValid('tok-1'), false);
                            }
                          },
                          {
                            name: "logging out should actually remove the session from memory",
                            fn: () => {
                              const store = new SessionStore();
                              for (let i = 0; i < 500; i++) {
                                store.login('tok-' + i, 'user-' + i);
                                store.logout('tok-' + i);
                              }
                              assert.strictEqual(store.size(), 0, `Session store still holds ${store.size()} entries after logout — logged-out sessions are never removed from memory.`);
                            }
                          }
                        ];
                        """)
                .referenceFix("""
                        {"solution.js":"class SessionStore {\\n  constructor() {\\n    this.sessions = new Map();\\n  }\\n\\n  login(token, userId) {\\n    this.sessions.set(token, { userId, active: true });\\n  }\\n\\n  logout(token) {\\n    this.sessions.delete(token);\\n  }\\n\\n  isValid(token) {\\n    const session = this.sessions.get(token);\\n    return !!session && session.active;\\n  }\\n\\n  size() {\\n    return this.sessions.size;\\n  }\\n}\\n\\nmodule.exports = { SessionStore };\\n"}
                        """)
                .hint("`logout()` sets `session.active = false` but the entry stays in `this.sessions` forever. Use `this.sessions.delete(token)` instead.")
                .build();

        // Incident 21: Duplicate content stored redundantly
        Incident incident21 = Incident.builder()
                .id("memory-duplicate-buffers")
                .title("Identical Uploads Stored Redundantly")
                .difficulty("HARD")
                .category("memory")
                .description("""
                        ### Problem Statement
                        Users frequently re-upload the exact same file content under different names.
                        The blob store keeps a full separate copy for every upload, even when the content
                        is byte-for-byte identical to something already stored.

                        ### Instructions
                        1. Inspect `solution.js`.
                        2. `store()` always saves a new copy of the content.
                        3. Fix it so identical content is only stored once, no matter how many keys reference it.
                        """)
                .logs("""
                        [2026-07-16 11:00:00] WARN 5 uploads with identical content stored as 5 separate blobs
                        """)
                .metrics("[]")
                .stackTrace("")
                .baseCode("""
                        {"solution.js":"class BlobStore {\\n  constructor() {\\n    this.blobs = new Map();\\n  }\\n\\n  store(key, content) {\\n    this.blobs.set(key, content);\\n  }\\n\\n  get(key) {\\n    return this.blobs.get(key);\\n  }\\n\\n  uniqueContentCount() {\\n    return this.blobs.size;\\n  }\\n}\\n\\nmodule.exports = { BlobStore };\\n"}
                        """)
                .hiddenTests("""
                        const assert = require('assert');
                        const { BlobStore } = require('./solution.js');

                        module.exports = [
                          {
                            name: "should retrieve stored content by key",
                            fn: () => {
                              const store = new BlobStore();
                              store.store('file-a', 'hello world');
                              assert.strictEqual(store.get('file-a'), 'hello world');
                            }
                          },
                          {
                            name: "should not duplicate storage for identical content under different keys",
                            fn: () => {
                              const store = new BlobStore();
                              const content = 'the quick brown fox';
                              store.store('upload-1', content);
                              store.store('upload-2', content);
                              store.store('upload-3', content);
                              store.store('upload-4', content);
                              store.store('upload-5', content);
                              assert.strictEqual(store.get('upload-3'), content);
                              assert.strictEqual(store.uniqueContentCount(), 1, `Expected 1 unique piece of content stored, but found ${store.uniqueContentCount()} — identical uploads are being stored redundantly instead of deduplicated.`);
                            }
                          }
                        ];
                        """)
                .referenceFix("""
                        {"solution.js":"class BlobStore {\\n  constructor() {\\n    this.blobs = new Map();\\n    this.contentToKey = new Map();\\n  }\\n\\n  store(key, content) {\\n    const existingKey = this.contentToKey.get(content);\\n    if (existingKey !== undefined) {\\n      this.blobs.set(key, this.blobs.get(existingKey));\\n      return;\\n    }\\n    this.blobs.set(key, content);\\n    this.contentToKey.set(content, key);\\n  }\\n\\n  get(key) {\\n    return this.blobs.get(key);\\n  }\\n\\n  uniqueContentCount() {\\n    return this.contentToKey.size;\\n  }\\n}\\n\\nmodule.exports = { BlobStore };\\n"}
                        """)
                .hint("Track a `contentToKey` map from content to the key it's already stored under. On `store()`, if the content already exists, point the new key at the existing storage instead of duplicating it.")
                .build();

        // Incident 22: Cross-call accumulation via shared module state
        Incident incident22 = Incident.builder()
                .id("memory-pagination-accumulator")
                .title("Paginated Results Accumulate Across Calls")
                .difficulty("MEDIUM")
                .category("memory")
                .description("""
                        ### Problem Statement
                        A "fetch all pages" helper is called once per report generation. Each call is supposed
                        to return a fresh result set, but the returned array keeps growing across unrelated
                        calls and includes stale data from previous reports.

                        ### Instructions
                        1. Inspect `solution.js`.
                        2. `fetchAllPages` accumulates into a module-level array that's never reset.
                        3. Fix it so each call returns its own independent, correctly-sized result.
                        """)
                .logs("""
                        [2026-07-16 12:00:00] WARN fetchAllPages(5) returned 35 items after several prior report runs
                        """)
                .metrics("[]")
                .stackTrace("")
                .baseCode("""
                        {"solution.js":"let allPagesEverFetched = [];\\n\\nfunction mockFetchPage(pageNum) {\\n  return [{ page: pageNum, item: 'data-' + pageNum }];\\n}\\n\\nfunction fetchAllPages(totalPages) {\\n  for (let i = 1; i <= totalPages; i++) {\\n    allPagesEverFetched.push(...mockFetchPage(i));\\n  }\\n  return allPagesEverFetched;\\n}\\n\\nmodule.exports = { fetchAllPages };\\n"}
                        """)
                .hiddenTests("""
                        const assert = require('assert');
                        const { fetchAllPages } = require('./solution.js');

                        module.exports = [
                          {
                            name: "should fetch the requested number of pages",
                            fn: () => {
                              const result = fetchAllPages(5);
                              assert.strictEqual(result.length, 5);
                            }
                          },
                          {
                            name: "repeated calls should not accumulate results from previous calls",
                            fn: () => {
                              fetchAllPages(5);
                              fetchAllPages(5);
                              const third = fetchAllPages(5);
                              assert.strictEqual(third.length, 5, `Expected 5 items from a fresh call, got ${third.length} — results are accumulating across calls in shared module state instead of being scoped per-call.`);
                            }
                          }
                        ];
                        """)
                .referenceFix("""
                        {"solution.js":"function mockFetchPage(pageNum) {\\n  return [{ page: pageNum, item: 'data-' + pageNum }];\\n}\\n\\nfunction fetchAllPages(totalPages) {\\n  const results = [];\\n  for (let i = 1; i <= totalPages; i++) {\\n    results.push(...mockFetchPage(i));\\n  }\\n  return results;\\n}\\n\\nmodule.exports = { fetchAllPages };\\n"}
                        """)
                .hint("`allPagesEverFetched` is declared once at module scope and reused across every call. Move the accumulator inside the function so each call starts with a fresh local array.")
                .build();

        // Incident 23: Object pool never actually reuses released objects
        Incident incident23 = Incident.builder()
                .id("memory-object-pool-leak")
                .title("Object Pool Never Reuses Released Objects")
                .difficulty("HARD")
                .category("memory")
                .description("""
                        ### Problem Statement
                        A connection-like object pool is meant to reuse expensive-to-create objects. Under
                        sustained load, profiling shows a brand new object is allocated on almost every
                        `acquire()` call, even though `release()` is being called faithfully after each use.

                        ### Instructions
                        1. Inspect `solution.js`.
                        2. `release()` currently does nothing with the object it's given.
                        3. Fix it so released objects go back into `this.available` for `acquire()` to reuse,
                           up to `this.maxSize`.
                        """)
                .logs("""
                        [2026-07-16 13:00:00] WARN Object pool created 50 objects across 50 acquire/release cycles (expected ~1)
                        """)
                .metrics("[]")
                .stackTrace("")
                .baseCode("""
                        {"solution.js":"class ObjectPool {\\n  constructor(factory, maxSize) {\\n    this.factory = factory;\\n    this.maxSize = maxSize;\\n    this.available = [];\\n    this.totalCreated = 0;\\n  }\\n\\n  acquire() {\\n    if (this.available.length > 0) {\\n      return this.available.pop();\\n    }\\n    this.totalCreated++;\\n    return this.factory();\\n  }\\n\\n  release(obj) {\\n    // objects are simply dropped instead of being returned to the pool\\n  }\\n}\\n\\nmodule.exports = { ObjectPool };\\n"}
                        """)
                .hiddenTests("""
                        const assert = require('assert');
                        const { ObjectPool } = require('./solution.js');

                        module.exports = [
                          {
                            name: "acquire should create from the factory when the pool is empty",
                            fn: () => {
                              const pool = new ObjectPool(() => ({}), 10);
                              const obj = pool.acquire();
                              assert.ok(obj);
                            }
                          },
                          {
                            name: "released objects should be reused instead of always creating new ones",
                            fn: () => {
                              const pool = new ObjectPool(() => ({ id: Math.random() }), 10);
                              for (let i = 0; i < 50; i++) {
                                const obj = pool.acquire();
                                pool.release(obj);
                              }
                              assert.ok(pool.totalCreated <= 2, `Pool created ${pool.totalCreated} objects across 50 acquire/release cycles — released objects aren't being returned to the pool for reuse.`);
                            }
                          }
                        ];
                        """)
                .referenceFix("""
                        {"solution.js":"class ObjectPool {\\n  constructor(factory, maxSize) {\\n    this.factory = factory;\\n    this.maxSize = maxSize;\\n    this.available = [];\\n    this.totalCreated = 0;\\n  }\\n\\n  acquire() {\\n    if (this.available.length > 0) {\\n      return this.available.pop();\\n    }\\n    this.totalCreated++;\\n    return this.factory();\\n  }\\n\\n  release(obj) {\\n    if (this.available.length < this.maxSize) {\\n      this.available.push(obj);\\n    }\\n  }\\n}\\n\\nmodule.exports = { ObjectPool };\\n"}
                        """)
                .hint("`release(obj)` is an empty function body. Push `obj` back onto `this.available` (respecting `this.maxSize`) so the next `acquire()` can pop it instead of calling the factory again.")
                .build();

        // Incident 24: Expired cache entries never purged
        Incident incident24 = Incident.builder()
                .id("memory-ttl-cache-purge")
                .title("Expired Cache Entries Never Purged")
                .difficulty("MEDIUM")
                .category("memory")
                .description("""
                        ### Problem Statement
                        A TTL-based cache correctly stops *returning* expired values, but the underlying
                        storage keeps growing forever — expired entries are detected on read but never
                        actually removed.

                        ### Instructions
                        1. Inspect `solution.js`.
                        2. `get()` checks whether an entry has expired and returns `undefined` if so, but
                           leaves the stale entry sitting in `this.store`.
                        3. Fix it so an expired entry is deleted from the store the moment it's detected.
                        """)
                .logs("""
                        [2026-07-16 14:00:00] WARN Cache holds 200 entries, all expired, after being read
                        """)
                .metrics("[]")
                .stackTrace("")
                .baseCode("""
                        {"solution.js":"class TTLCache {\\n  constructor(now = () => Date.now()) {\\n    this.now = now;\\n    this.store = new Map();\\n  }\\n\\n  set(key, value, ttlMs) {\\n    this.store.set(key, { value, expiresAt: this.now() + ttlMs });\\n  }\\n\\n  get(key) {\\n    const entry = this.store.get(key);\\n    if (!entry) return undefined;\\n    if (this.now() > entry.expiresAt) {\\n      return undefined;\\n    }\\n    return entry.value;\\n  }\\n\\n  size() {\\n    return this.store.size;\\n  }\\n}\\n\\nmodule.exports = { TTLCache };\\n"}
                        """)
                .hiddenTests("""
                        const assert = require('assert');
                        const { TTLCache } = require('./solution.js');

                        module.exports = [
                          {
                            name: "should return a value before it expires",
                            fn: () => {
                              let t = 1000;
                              const cache = new TTLCache(() => t);
                              cache.set('a', 'hello', 500);
                              assert.strictEqual(cache.get('a'), 'hello');
                            }
                          },
                          {
                            name: "expired entries should be purged from memory once accessed",
                            fn: () => {
                              let t = 0;
                              const cache = new TTLCache(() => t);
                              for (let i = 0; i < 200; i++) {
                                cache.set('key' + i, i, 10);
                              }
                              t = 100000;
                              for (let i = 0; i < 200; i++) {
                                cache.get('key' + i);
                              }
                              assert.ok(cache.size() <= 5, `Cache still holds ${cache.size()} expired entries in memory — expired entries are detected but never removed from the store.`);
                            }
                          }
                        ];
                        """)
                .referenceFix("""
                        {"solution.js":"class TTLCache {\\n  constructor(now = () => Date.now()) {\\n    this.now = now;\\n    this.store = new Map();\\n  }\\n\\n  set(key, value, ttlMs) {\\n    this.store.set(key, { value, expiresAt: this.now() + ttlMs });\\n  }\\n\\n  get(key) {\\n    const entry = this.store.get(key);\\n    if (!entry) return undefined;\\n    if (this.now() > entry.expiresAt) {\\n      this.store.delete(key);\\n      return undefined;\\n    }\\n    return entry.value;\\n  }\\n\\n  size() {\\n    return this.store.size;\\n  }\\n}\\n\\nmodule.exports = { TTLCache };\\n"}
                        """)
                .hint("When `get()` finds `this.now() > entry.expiresAt`, it should call `this.store.delete(key)` before returning `undefined`, not just return early.")
                .build();

        // Incident 25: Write buffer flush trigger misses overshoot
        Incident incident25 = Incident.builder()
                .id("memory-batched-writes")
                .title("Write Buffer Never Flushes on Bulk Adds")
                .difficulty("MEDIUM")
                .category("memory")
                .description("""
                        ### Problem Statement
                        A write buffer is supposed to flush automatically once it reaches a batch size
                        threshold. It works fine when items trickle in one at a time, but when items arrive
                        in small bulk chunks, the buffer's length "jumps over" the exact threshold and the
                        buffer never flushes at all — growing without bound.

                        ### Instructions
                        1. Inspect `solution.js`.
                        2. `add()` only triggers a flush when `pending.length === batchSize` exactly.
                        3. Fix the comparison so a flush triggers once the buffer reaches *or exceeds* the
                           threshold, not just exactly equals it.
                        """)
                .logs("""
                        [2026-07-16 15:00:00] WARN Write buffer holds 300 unflushed items (batchSize=10) — bulk adds keep overshooting the exact trigger
                        """)
                .metrics("[]")
                .stackTrace("")
                .baseCode("""
                        {"solution.js":"class WriteBuffer {\\n  constructor(batchSize, sink) {\\n    this.batchSize = batchSize;\\n    this.sink = sink;\\n    this.pending = [];\\n    this.flushCount = 0;\\n  }\\n\\n  add(items) {\\n    this.pending.push(...items);\\n    if (this.pending.length === this.batchSize) {\\n      this.flush();\\n    }\\n  }\\n\\n  flush() {\\n    if (this.pending.length === 0) return;\\n    this.sink(this.pending.splice(0));\\n    this.flushCount++;\\n  }\\n}\\n\\nmodule.exports = { WriteBuffer };\\n"}
                        """)
                .hiddenTests("""
                        const assert = require('assert');
                        const { WriteBuffer } = require('./solution.js');

                        module.exports = [
                          {
                            name: "should flush once batch size is reached via single-item adds",
                            fn: () => {
                              const sunk = [];
                              const buf = new WriteBuffer(5, (items) => sunk.push(...items));
                              for (let i = 0; i < 5; i++) buf.add([i]);
                              assert.strictEqual(buf.flushCount, 1);
                            }
                          },
                          {
                            name: "should still flush when adds arrive in chunks that overshoot the exact batch size",
                            fn: () => {
                              const buf = new WriteBuffer(10, () => {});
                              for (let i = 0; i < 10; i++) {
                                buf.add([i * 3, i * 3 + 1, i * 3 + 2]);
                              }
                              assert.ok(buf.flushCount >= 2, `Expected at least 2 flushes across 30 buffered items with batchSize 10, but only ${buf.flushCount} occurred — the buffer never flushes when adds overshoot the exact batch size threshold.`);
                              assert.ok(buf.pending.length < 10, `Buffer is holding ${buf.pending.length} unflushed items, exceeding the intended batch size of 10.`);
                            }
                          }
                        ];
                        """)
                .referenceFix("""
                        {"solution.js":"class WriteBuffer {\\n  constructor(batchSize, sink) {\\n    this.batchSize = batchSize;\\n    this.sink = sink;\\n    this.pending = [];\\n    this.flushCount = 0;\\n  }\\n\\n  add(items) {\\n    this.pending.push(...items);\\n    if (this.pending.length >= this.batchSize) {\\n      this.flush();\\n    }\\n  }\\n\\n  flush() {\\n    if (this.pending.length === 0) return;\\n    this.sink(this.pending.splice(0));\\n    this.flushCount++;\\n  }\\n}\\n\\nmodule.exports = { WriteBuffer };\\n"}
                        """)
                .hint("`if (this.pending.length === this.batchSize)` never fires if a single `add()` call pushes multiple items and skips past the exact threshold. Use `>=` instead of `===`.")
                .build();

        // Incident 26: Nested-loop membership search
        Incident incident26 = Incident.builder()
                .id("cpu-nested-loop-search")
                .title("O(n*m) Nested-Loop Search Between Two Lists")
                .difficulty("EASY")
                .category("cpu")
                .description("""
                        ### Problem Statement
                        A "find common tags" feature compares two lists with a nested loop. It's fine for
                        small lists, but for larger catalogs the comparison takes noticeably long — every
                        element of one list is compared against every element of the other.

                        ### Instructions
                        1. Inspect `solution.js`.
                        2. `findCommon` uses a nested loop, giving O(n*m) time.
                        3. Fix it to run in roughly linear time using a `Set` for lookups.
                        """)
                .logs("""
                        [2026-07-17 08:00:00] WARN findCommon() took 392ms comparing two 30,000-element lists
                        """)
                .metrics("[]")
                .stackTrace("")
                .baseCode("""
                        {"solution.js":"function findCommon(arrA, arrB) {\\n  const common = [];\\n  for (const a of arrA) {\\n    for (const b of arrB) {\\n      if (a === b) {\\n        common.push(a);\\n        break;\\n      }\\n    }\\n  }\\n  return common;\\n}\\n\\nmodule.exports = { findCommon };\\n"}
                        """)
                .hiddenTests("""
                        const assert = require('assert');
                        const { findCommon } = require('./solution.js');

                        module.exports = [
                          {
                            name: "should find common elements between two arrays",
                            fn: () => {
                              assert.deepStrictEqual(findCommon([1, 2, 3], [2, 3, 4]), [2, 3]);
                            }
                          },
                          {
                            name: "should find common elements in large arrays fast enough",
                            fn: () => {
                              const a = Array.from({ length: 30000 }, (_, i) => i);
                              const b = Array.from({ length: 30000 }, (_, i) => i + 15000);
                              const start = Date.now();
                              const result = findCommon(a, b);
                              const duration = Date.now() - start;
                              assert.strictEqual(result.length, 15000);
                              assert.ok(duration < 250, `findCommon took ${duration}ms on 30,000-element arrays — the nested-loop scan is O(n*m) and too slow at this scale.`);
                            }
                          }
                        ];
                        """)
                .referenceFix("""
                        {"solution.js":"function findCommon(arrA, arrB) {\\n  const setB = new Set(arrB);\\n  return arrA.filter((a) => setB.has(a));\\n}\\n\\nmodule.exports = { findCommon };\\n"}
                        """)
                .hint("Build a `Set` from the second array once, then `filter` the first array with O(1) `.has()` lookups instead of scanning the second array for every element.")
                .build();

        // Incident 27: Repeated Array.shift() in a loop
        Incident incident27 = Incident.builder()
                .id("cpu-array-shift-loop")
                .title("Repeated Array.shift() Makes Queue Processing O(n²)")
                .difficulty("MEDIUM")
                .category("cpu")
                .description("""
                        ### Problem Statement
                        A queue-processing function drains a queue by repeatedly calling `shift()` on the
                        front of an array. For small queues this is invisible, but for large batches it
                        becomes dramatically slower than it should be.

                        ### Instructions
                        1. Inspect `solution.js`.
                        2. `processQueue` calls `queue.shift()` in a loop — each `shift()` re-indexes the
                           entire remaining array, making the whole loop O(n²).
                        3. Fix it to process the queue in linear time.
                        """)
                .logs("""
                        [2026-07-17 09:00:00] WARN processQueue() took 591ms for a 25,000-item queue
                        """)
                .metrics("[]")
                .stackTrace("")
                .baseCode("""
                        {"solution.js":"function processQueue(items) {\\n  const processed = [];\\n  const queue = items.slice();\\n  while (queue.length > 0) {\\n    const item = queue.shift();\\n    processed.push(item * 2);\\n  }\\n  return processed;\\n}\\n\\nmodule.exports = { processQueue };\\n"}
                        """)
                .hiddenTests("""
                        const assert = require('assert');
                        const { processQueue } = require('./solution.js');

                        module.exports = [
                          {
                            name: "should double every item in order",
                            fn: () => {
                              assert.deepStrictEqual(processQueue([1, 2, 3]), [2, 4, 6]);
                            }
                          },
                          {
                            name: "should process a large queue fast enough",
                            fn: () => {
                              const items = Array.from({ length: 25000 }, (_, i) => i);
                              const start = Date.now();
                              const result = processQueue(items);
                              const duration = Date.now() - start;
                              assert.strictEqual(result.length, 25000);
                              assert.ok(duration < 250, `processQueue took ${duration}ms on 25,000 items — repeatedly shift()-ing the front of an array is O(n) per call, making the whole loop O(n^2).`);
                            }
                          }
                        ];
                        """)
                .referenceFix("""
                        {"solution.js":"function processQueue(items) {\\n  const processed = [];\\n  for (let i = 0; i < items.length; i++) {\\n    processed.push(items[i] * 2);\\n  }\\n  return processed;\\n}\\n\\nmodule.exports = { processQueue };\\n"}
                        """)
                .hint("Instead of `queue.shift()` in a loop, iterate over the original array with an index (`for (let i = 0; i < items.length; i++)`) — no re-indexing needed.")
                .build();

        // Incident 28: Config re-parsed from scratch on every call
        Incident incident28 = Incident.builder()
                .id("cpu-repeated-json-parse")
                .title("Config Re-Parsed From Scratch on Every Request")
                .difficulty("MEDIUM")
                .category("cpu")
                .description("""
                        ### Problem Statement
                        A hot request path reads application config via `getConfig(rawJson)`. Profiling shows
                        a significant chunk of every request's time is spent inside `JSON.parse`, even though
                        the underlying config text never changes between requests.

                        ### Instructions
                        1. Inspect `solution.js`.
                        2. `getConfig` parses `rawJson` from scratch on every single call.
                        3. Fix it to parse once and reuse the cached result for subsequent calls with the
                           same input.
                        """)
                .logs("""
                        [2026-07-17 10:00:00] WARN 500 getConfig() calls took 651ms total — JSON.parse dominates request latency
                        """)
                .metrics("[]")
                .stackTrace("")
                .baseCode("""
                        {"solution.js":"function getConfig(rawJson) {\\n  return JSON.parse(rawJson);\\n}\\n\\nmodule.exports = { getConfig };\\n"}
                        """)
                .hiddenTests("""
                        const assert = require('assert');
                        const { getConfig } = require('./solution.js');

                        module.exports = [
                          {
                            name: "should parse and return the config object",
                            fn: () => {
                              const result = getConfig('{"timeout": 5000}');
                              assert.strictEqual(result.timeout, 5000);
                            }
                          },
                          {
                            name: "repeated calls with the same config should be fast (cached, not re-parsed)",
                            fn: () => {
                              const bigObj = {};
                              for (let i = 0; i < 3000; i++) {
                                bigObj['key' + i] = { id: i, name: 'item-' + i, tags: ['a', 'b', 'c'] };
                              }
                              const rawJson = JSON.stringify(bigObj);

                              getConfig(rawJson);
                              const start = Date.now();
                              for (let i = 0; i < 500; i++) {
                                getConfig(rawJson);
                              }
                              const duration = Date.now() - start;
                              assert.ok(duration < 250, `500 repeated getConfig() calls took ${duration}ms — the config is being re-parsed from scratch on every call instead of cached.`);
                            }
                          }
                        ];
                        """)
                .referenceFix("""
                        {"solution.js":"let cached = null;\\nlet cachedRaw = null;\\n\\nfunction getConfig(rawJson) {\\n  if (cached === null || cachedRaw !== rawJson) {\\n    cached = JSON.parse(rawJson);\\n    cachedRaw = rawJson;\\n  }\\n  return cached;\\n}\\n\\nmodule.exports = { getConfig };\\n"}
                        """)
                .hint("Cache the parsed result (and the raw string it came from) in module-level variables. Only re-parse when the raw input actually changes.")
                .build();

        // Incident 29: Bubble sort instead of native sort
        Incident incident29 = Incident.builder()
                .id("cpu-inefficient-sort")
                .title("Hand-Rolled Bubble Sort Instead of Native Sort")
                .difficulty("HARD")
                .category("cpu")
                .description("""
                        ### Problem Statement
                        A leaderboard sorting function uses a hand-written bubble sort. It works correctly
                        but scales terribly — sorting a season's worth of scores takes far longer than it
                        should.

                        ### Instructions
                        1. Inspect `solution.js`.
                        2. `sortScores` implements an O(n²) bubble sort.
                        3. Replace it with the engine's native, highly-optimized sort.
                        """)
                .logs("""
                        [2026-07-17 11:00:00] WARN sortScores() took 275ms sorting 15,000 scores
                        """)
                .metrics("[]")
                .stackTrace("")
                .baseCode("""
                        {"solution.js":"function sortScores(scores) {\\n  const arr = scores.slice();\\n  for (let i = 0; i < arr.length; i++) {\\n    for (let j = 0; j < arr.length - i - 1; j++) {\\n      if (arr[j] > arr[j + 1]) {\\n        const tmp = arr[j];\\n        arr[j] = arr[j + 1];\\n        arr[j + 1] = tmp;\\n      }\\n    }\\n  }\\n  return arr;\\n}\\n\\nmodule.exports = { sortScores };\\n"}
                        """)
                .hiddenTests("""
                        const assert = require('assert');
                        const { sortScores } = require('./solution.js');

                        module.exports = [
                          {
                            name: "should sort numbers in ascending order",
                            fn: () => {
                              assert.deepStrictEqual(sortScores([5, 1, 4, 2, 3]), [1, 2, 3, 4, 5]);
                            }
                          },
                          {
                            name: "should sort a large array fast enough",
                            fn: () => {
                              const arr = Array.from({ length: 15000 }, () => Math.random());
                              const start = Date.now();
                              const result = sortScores(arr);
                              const duration = Date.now() - start;
                              for (let i = 1; i < result.length; i++) {
                                assert.ok(result[i - 1] <= result[i], "Array is not sorted correctly.");
                              }
                              assert.ok(duration < 200, `Sorting 15,000 scores took ${duration}ms — bubble sort is O(n^2) and too slow at this scale.`);
                            }
                          }
                        ];
                        """)
                .referenceFix("""
                        {"solution.js":"function sortScores(scores) {\\n  return scores.slice().sort((a, b) => a - b);\\n}\\n\\nmodule.exports = { sortScores };\\n"}
                        """)
                .hint("Replace the manual double loop with `scores.slice().sort((a, b) => a - b)` — V8's native sort is far faster than any hand-rolled O(n²) sort.")
                .build();

        // Incident 30: Repeated Array.concat() flattening
        Incident incident30 = Incident.builder()
                .id("cpu-recursive-flatten")
                .title("Repeated Array.concat() Makes Flattening O(n²)")
                .difficulty("HARD")
                .category("cpu")
                .description("""
                        ### Problem Statement
                        A batch import flattens thousands of small chunks into one array using
                        `result = result.concat(chunk)` in a loop. As the number of chunks grows, this
                        becomes dramatically slower than a linear-time flatten.

                        ### Instructions
                        1. Inspect `solution.js`.
                        2. Every `Array.concat()` call copies the *entire* accumulated result so far,
                           making the loop O(n²) overall.
                        3. Fix it to flatten in roughly linear time.
                        """)
                .logs("""
                        [2026-07-17 12:00:00] WARN flattenChunks() took 414ms for 10,000 chunks
                        """)
                .metrics("[]")
                .stackTrace("")
                .baseCode("""
                        {"solution.js":"function flattenChunks(chunks) {\\n  let result = [];\\n  for (const chunk of chunks) {\\n    result = result.concat(chunk);\\n  }\\n  return result;\\n}\\n\\nmodule.exports = { flattenChunks };\\n"}
                        """)
                .hiddenTests("""
                        const assert = require('assert');
                        const { flattenChunks } = require('./solution.js');

                        module.exports = [
                          {
                            name: "should flatten chunks into a single array",
                            fn: () => {
                              assert.deepStrictEqual(flattenChunks([[1, 2], [3], [4, 5, 6]]), [1, 2, 3, 4, 5, 6]);
                            }
                          },
                          {
                            name: "should flatten a large number of chunks fast enough",
                            fn: () => {
                              const chunks = Array.from({ length: 10000 }, (_, i) => [i, i + 1, i + 2]);
                              const start = Date.now();
                              const result = flattenChunks(chunks);
                              const duration = Date.now() - start;
                              assert.strictEqual(result.length, 30000);
                              assert.ok(duration < 250, `Flattening 10,000 chunks took ${duration}ms — repeated Array.concat() re-copies the whole accumulator each time, making this O(n^2).`);
                            }
                          }
                        ];
                        """)
                .referenceFix("""
                        {"solution.js":"function flattenChunks(chunks) {\\n  return chunks.flat();\\n}\\n\\nmodule.exports = { flattenChunks };\\n"}
                        """)
                .hint("Use `chunks.flat()` instead of accumulating via repeated `.concat()` calls in a loop.")
                .build();

        // Incident 31: Unnecessary deep clone on every call
        Incident incident31 = Incident.builder()
                .id("cpu-deep-clone-naive")
                .title("Config Deep-Cloned on Every Read Despite Being Read-Only")
                .difficulty("MEDIUM")
                .category("cpu")
                .description("""
                        ### Problem Statement
                        A shared configuration object is never mutated by any caller, yet the accessor
                        function deep-clones the entire thing via `JSON.parse(JSON.stringify(...))` on every
                        single call, burning CPU for no reason.

                        ### Instructions
                        1. Inspect `solution.js`.
                        2. `getReadOnlyConfig` deep-clones `bigConfig` on every call.
                        3. Since the config is never mutated, fix it to return the same reference directly.
                        """)
                .logs("""
                        [2026-07-17 13:00:00] WARN 200 getReadOnlyConfig() calls took 663ms — deep cloning dominates
                        """)
                .metrics("[]")
                .stackTrace("")
                .baseCode("""
                        {"solution.js":"const bigConfig = {};\\nfor (let i = 0; i < 5000; i++) {\\n  bigConfig['k' + i] = { a: i, b: 'x'.repeat(20), c: [1, 2, 3] };\\n}\\n\\nfunction getReadOnlyConfig() {\\n  return JSON.parse(JSON.stringify(bigConfig));\\n}\\n\\nmodule.exports = { getReadOnlyConfig };\\n"}
                        """)
                .hiddenTests("""
                        const assert = require('assert');
                        const { getReadOnlyConfig } = require('./solution.js');

                        module.exports = [
                          {
                            name: "should return the expected config shape",
                            fn: () => {
                              const config = getReadOnlyConfig();
                              assert.strictEqual(config.k0.a, 0);
                              assert.strictEqual(config.k4999.a, 4999);
                            }
                          },
                          {
                            name: "repeated calls should be fast since the config is never mutated",
                            fn: () => {
                              const start = Date.now();
                              for (let i = 0; i < 200; i++) {
                                getReadOnlyConfig();
                              }
                              const duration = Date.now() - start;
                              assert.ok(duration < 250, `200 calls to getReadOnlyConfig() took ${duration}ms — the config is being deep-cloned on every call even though it's never mutated and could just be returned directly.`);
                            }
                          }
                        ];
                        """)
                .referenceFix("""
                        {"solution.js":"const bigConfig = {};\\nfor (let i = 0; i < 5000; i++) {\\n  bigConfig['k' + i] = { a: i, b: 'x'.repeat(20), c: [1, 2, 3] };\\n}\\n\\nfunction getReadOnlyConfig() {\\n  return bigConfig;\\n}\\n\\nmodule.exports = { getReadOnlyConfig };\\n"}
                        """)
                .hint("`JSON.parse(JSON.stringify(bigConfig))` makes a full deep copy every call. Since nothing mutates the result, just `return bigConfig;` directly.")
                .build();

        // Incident 32: Array.includes() inside a filter loop
        Incident incident32 = Incident.builder()
                .id("cpu-array-includes-loop")
                .title("Array.includes() Inside a Loop Rescans a Large Blocklist")
                .difficulty("EASY")
                .category("cpu")
                .description("""
                        ### Problem Statement
                        A moderation filter checks each incoming item against a blocklist using
                        `blockedList.includes(x)` inside a `.filter()` callback. As the blocklist grows,
                        filtering a batch of items gets slower and slower.

                        ### Instructions
                        1. Inspect `solution.js`.
                        2. `filterBlocked` calls `.includes()` on the blocklist array for every item.
                        3. Fix it to use a `Set` for O(1) membership checks.
                        """)
                .logs("""
                        [2026-07-17 14:00:00] WARN filterBlocked() took 347ms for 30,000 items against a 30,000-entry blocklist
                        """)
                .metrics("[]")
                .stackTrace("")
                .baseCode("""
                        {"solution.js":"function filterBlocked(items, blockedList) {\\n  return items.filter((x) => blockedList.includes(x));\\n}\\n\\nmodule.exports = { filterBlocked };\\n"}
                        """)
                .hiddenTests("""
                        const assert = require('assert');
                        const { filterBlocked } = require('./solution.js');

                        module.exports = [
                          {
                            name: "should filter out blocked items",
                            fn: () => {
                              assert.deepStrictEqual(filterBlocked([1, 2, 3, 4], [2, 4]), [2, 4]);
                            }
                          },
                          {
                            name: "should filter a large list against a large blocklist fast enough",
                            fn: () => {
                              const items = Array.from({ length: 30000 }, (_, i) => i);
                              const blockedList = Array.from({ length: 30000 }, (_, i) => i);
                              const start = Date.now();
                              const result = filterBlocked(items, blockedList);
                              const duration = Date.now() - start;
                              assert.strictEqual(result.length, 30000);
                              assert.ok(duration < 200, `Filtering 30,000 items against a 30,000-entry blocklist took ${duration}ms — Array.includes() inside the loop rescans the whole blocklist every time.`);
                            }
                          }
                        ];
                        """)
                .referenceFix("""
                        {"solution.js":"function filterBlocked(items, blockedList) {\\n  const blockedSet = new Set(blockedList);\\n  return items.filter((x) => blockedSet.has(x));\\n}\\n\\nmodule.exports = { filterBlocked };\\n"}
                        """)
                .hint("Build a `Set` from `blockedList` once before filtering, then use `.has()` instead of `.includes()` inside the loop.")
                .build();

        // Incident 33: Stored XSS via unescaped output
        Incident incident33 = Incident.builder()
                .id("security-xss-unescaped-output")
                .title("Stored XSS via Unescaped Comment Rendering")
                .difficulty("EASY")
                .category("security")
                .description("""
                        ### Problem Statement
                        User-submitted comments are rendered directly into HTML without any escaping. A
                        comment containing a `<script>` tag executes in every other visitor's browser who
                        views the page — a classic stored XSS vulnerability.

                        ### Instructions
                        1. Inspect `solution.js`.
                        2. `renderComment` interpolates `username` and `text` straight into an HTML template.
                        3. Fix it to HTML-escape both values before rendering.
                        """)
                .logs("""
                        [2026-07-18 08:00:00] WARN Comment containing <script> tag rendered without escaping
                        """)
                .metrics("[]")
                .stackTrace("")
                .baseCode("""
                        {"solution.js":"function renderComment(username, text) {\\n  return `<div class=\\"comment\\"><strong>${username}</strong>: ${text}</div>`;\\n}\\n\\nmodule.exports = { renderComment };\\n"}
                        """)
                .hiddenTests("""
                        const assert = require('assert');
                        const { renderComment } = require('./solution.js');

                        module.exports = [
                          {
                            name: "should render a normal comment",
                            fn: () => {
                              const html = renderComment('alice', 'Great post!');
                              assert.ok(html.includes('alice'));
                              assert.ok(html.includes('Great post!'));
                            }
                          },
                          {
                            name: "should escape script tags to prevent stored XSS",
                            fn: () => {
                              const html = renderComment('mallory', '<script>alert(document.cookie)</script>');
                              assert.ok(!html.includes('<script>'), 'Raw <script> tag made it into the rendered HTML — this is a stored XSS vulnerability.');
                            }
                          }
                        ];
                        """)
                .referenceFix("""
                        {"solution.js":"function escapeHtml(str) {\\n  return String(str)\\n    .replace(/&/g, '&amp;')\\n    .replace(/</g, '&lt;')\\n    .replace(/>/g, '&gt;')\\n    .replace(/\\"/g, '&quot;')\\n    .replace(/'/g, '&#39;');\\n}\\n\\nfunction renderComment(username, text) {\\n  return `<div class=\\"comment\\"><strong>${escapeHtml(username)}</strong>: ${escapeHtml(text)}</div>`;\\n}\\n\\nmodule.exports = { renderComment };\\n"}
                        """)
                .hint("Write an `escapeHtml` helper that replaces &, <, >, a double quote, and a single quote with their HTML entities, and run both `username` and `text` through it before interpolating.")
                .build();

        // Incident 34: Weak password strength check
        Incident incident34 = Incident.builder()
                .id("security-weak-password-check")
                .title("Password Strength Check Only Looks at Length")
                .difficulty("MEDIUM")
                .category("security")
                .description("""
                        ### Problem Statement
                        The signup form's "strong password" validator accepts anything six characters or
                        longer — including all-digit passwords like `123456` — giving users false confidence
                        that their account is protected by a real strength check.

                        ### Instructions
                        1. Inspect `solution.js`.
                        2. `isStrongPassword` only checks `password.length >= 6`.
                        3. Fix it to require a real mix: minimum length 8, plus lowercase, uppercase, and a digit.
                        """)
                .logs("""
                        [2026-07-18 09:00:00] WARN Password '123456' accepted as 'strong' during signup
                        """)
                .metrics("[]")
                .stackTrace("")
                .baseCode("""
                        {"solution.js":"function isStrongPassword(password) {\\n  return password.length >= 6;\\n}\\n\\nmodule.exports = { isStrongPassword };\\n"}
                        """)
                .hiddenTests("""
                        const assert = require('assert');
                        const { isStrongPassword } = require('./solution.js');

                        module.exports = [
                          {
                            name: "should accept a genuinely strong password",
                            fn: () => {
                              assert.strictEqual(isStrongPassword('Str0ngPass1'), true);
                            }
                          },
                          {
                            name: "should reject a weak all-digit password that merely meets a length minimum",
                            fn: () => {
                              assert.strictEqual(isStrongPassword('123456'), false, "'123456' was accepted as a strong password — length alone isn't a real strength check.");
                            }
                          }
                        ];
                        """)
                .referenceFix("""
                        {"solution.js":"function isStrongPassword(password) {\\n  if (password.length < 8) return false;\\n  if (!/[a-z]/.test(password)) return false;\\n  if (!/[A-Z]/.test(password)) return false;\\n  if (!/[0-9]/.test(password)) return false;\\n  return true;\\n}\\n\\nmodule.exports = { isStrongPassword };\\n"}
                        """)
                .hint("Add checks for a minimum length of 8, at least one lowercase letter, one uppercase letter, and one digit — return false if any check fails.")
                .build();

        // Incident 35: Mass assignment vulnerability
        Incident incident35 = Incident.builder()
                .id("security-mass-assignment")
                .title("Mass Assignment Lets Clients Set Privileged Fields")
                .difficulty("MEDIUM")
                .category("security")
                .description("""
                        ### Problem Statement
                        The "update profile" endpoint blindly spreads every field a client sends into the
                        user record. A crafted request that includes `isAdmin: true` silently grants that
                        user admin privileges.

                        ### Instructions
                        1. Inspect `solution.js`.
                        2. `updateProfile` merges `{ ...user, ...updates }` with no field whitelist.
                        3. Fix it to only apply a small, explicit whitelist of safe fields.
                        """)
                .logs("""
                        [2026-07-18 10:00:00] WARN updateProfile request included 'isAdmin: true' and it was applied
                        """)
                .metrics("[]")
                .stackTrace("")
                .baseCode("""
                        {"solution.js":"function updateProfile(user, updates) {\\n  return { ...user, ...updates };\\n}\\n\\nmodule.exports = { updateProfile };\\n"}
                        """)
                .hiddenTests("""
                        const assert = require('assert');
                        const { updateProfile } = require('./solution.js');

                        module.exports = [
                          {
                            name: "should update allowed profile fields",
                            fn: () => {
                              const user = { name: 'Alice', bio: 'old bio', isAdmin: false };
                              const result = updateProfile(user, { name: 'Alice B.', bio: 'new bio' });
                              assert.strictEqual(result.name, 'Alice B.');
                              assert.strictEqual(result.bio, 'new bio');
                            }
                          },
                          {
                            name: "should block mass assignment of privileged fields like isAdmin",
                            fn: () => {
                              const user = { name: 'Bob', isAdmin: false };
                              const result = updateProfile(user, { name: 'Bob', isAdmin: true });
                              assert.strictEqual(result.isAdmin, false, "updateProfile allowed a client-supplied 'isAdmin' field to overwrite a privileged flag — this is a mass assignment vulnerability.");
                            }
                          }
                        ];
                        """)
                .referenceFix("""
                        {"solution.js":"const ALLOWED_FIELDS = ['name', 'bio', 'email'];\\n\\nfunction updateProfile(user, updates) {\\n  const safeUpdates = {};\\n  for (const key of ALLOWED_FIELDS) {\\n    if (key in updates) {\\n      safeUpdates[key] = updates[key];\\n    }\\n  }\\n  return { ...user, ...safeUpdates };\\n}\\n\\nmodule.exports = { updateProfile };\\n"}
                        """)
                .hint("Define an `ALLOWED_FIELDS` whitelist (e.g. name, bio, email) and only copy those specific keys from `updates` onto the user, instead of spreading the whole object.")
                .build();

        // Incident 36: Open redirect
        Incident incident36 = Incident.builder()
                .id("security-open-redirect")
                .title("Open Redirect via Unvalidated returnTo Parameter")
                .difficulty("MEDIUM")
                .category("security")
                .description("""
                        ### Problem Statement
                        After login, users are redirected to a `returnTo` URL supplied by the client with no
                        validation. An attacker can craft a login link with `returnTo=https://evil-site.com`
                        to redirect victims to a phishing page right after they authenticate.

                        ### Instructions
                        1. Inspect `solution.js`.
                        2. `getRedirectUrl` returns `returnTo` as-is if it's truthy.
                        3. Fix it to only allow same-origin relative paths, falling back to `defaultUrl`
                           for anything else.
                        """)
                .logs("""
                        [2026-07-18 11:00:00] WARN Login redirected to https://evil-phishing-site.com/login via returnTo param
                        """)
                .metrics("[]")
                .stackTrace("")
                .baseCode("""
                        {"solution.js":"function getRedirectUrl(returnTo, defaultUrl) {\\n  return returnTo || defaultUrl;\\n}\\n\\nmodule.exports = { getRedirectUrl };\\n"}
                        """)
                .hiddenTests("""
                        const assert = require('assert');
                        const { getRedirectUrl } = require('./solution.js');

                        module.exports = [
                          {
                            name: "should allow a relative same-origin path",
                            fn: () => {
                              assert.strictEqual(getRedirectUrl('/settings', '/dashboard'), '/settings');
                            }
                          },
                          {
                            name: "should block redirecting to an external domain (open redirect)",
                            fn: () => {
                              const result = getRedirectUrl('https://evil-phishing-site.com/login', '/dashboard');
                              assert.strictEqual(result, '/dashboard', `getRedirectUrl returned '${result}' — an attacker-controlled returnTo URL was allowed through, enabling phishing redirects.`);
                            }
                          }
                        ];
                        """)
                .referenceFix("""
                        {"solution.js":"function getRedirectUrl(returnTo, defaultUrl) {\\n  if (typeof returnTo === 'string' && returnTo.startsWith('/') && !returnTo.startsWith('//')) {\\n    return returnTo;\\n  }\\n  return defaultUrl;\\n}\\n\\nmodule.exports = { getRedirectUrl };\\n"}
                        """)
                .hint("Only accept `returnTo` values that start with a single `/` (not `//`, which is protocol-relative and can still point off-site). Anything else should fall back to `defaultUrl`.")
                .build();

        // Incident 37: Prototype pollution via unguarded deep merge
        Incident incident37 = Incident.builder()
                .id("security-prototype-pollution")
                .title("Prototype Pollution via Unguarded Deep Merge")
                .difficulty("HARD")
                .category("security")
                .description("""
                        ### Problem Statement
                        A settings deep-merge utility recursively copies keys from a client-supplied object
                        onto a target. Because it doesn't special-case `__proto__`, a crafted payload can
                        pollute `Object.prototype` itself — corrupting every plain object in the running
                        process.

                        ### Instructions
                        1. Inspect `solution.js`.
                        2. `merge` copies every key from `source`, including `__proto__`, `constructor`, and
                           `prototype`.
                        3. Fix it to skip those dangerous keys entirely.
                        """)
                .logs("""
                        [2026-07-18 12:00:00] ERROR Object.prototype.polluted = 'yes' detected after a settings merge — global object corruption
                        """)
                .metrics("[]")
                .stackTrace("")
                .baseCode("""
                        {"solution.js":"function merge(target, source) {\\n  for (const key in source) {\\n    if (typeof source[key] === 'object' && source[key] !== null) {\\n      target[key] = target[key] || {};\\n      merge(target[key], source[key]);\\n    } else {\\n      target[key] = source[key];\\n    }\\n  }\\n  return target;\\n}\\n\\nmodule.exports = { merge };\\n"}
                        """)
                .hiddenTests("""
                        const assert = require('assert');
                        const { merge } = require('./solution.js');

                        module.exports = [
                          {
                            name: "should merge plain objects normally",
                            fn: () => {
                              const result = merge({ a: 1 }, { b: 2 });
                              assert.deepStrictEqual(result, { a: 1, b: 2 });
                            }
                          },
                          {
                            name: "should not allow prototype pollution via a __proto__ payload",
                            fn: () => {
                              const payload = JSON.parse('{"__proto__":{"polluted":"yes"}}');
                              merge({}, payload);
                              assert.strictEqual({}.polluted, undefined, "Object.prototype was polluted via a __proto__ merge payload — every plain object in the process is now compromised.");
                            }
                          }
                        ];
                        """)
                .referenceFix("""
                        {"solution.js":"const DANGEROUS_KEYS = new Set(['__proto__', 'constructor', 'prototype']);\\n\\nfunction merge(target, source) {\\n  for (const key in source) {\\n    if (DANGEROUS_KEYS.has(key)) {\\n      continue;\\n    }\\n    if (typeof source[key] === 'object' && source[key] !== null) {\\n      target[key] = target[key] || {};\\n      merge(target[key], source[key]);\\n    } else {\\n      target[key] = source[key];\\n    }\\n  }\\n  return target;\\n}\\n\\nmodule.exports = { merge };\\n"}
                        """)
                .hint("Maintain a set of dangerous keys (`__proto__`, `constructor`, `prototype`) and `continue` past them at the top of the loop in `merge`, before any recursion or assignment happens.")
                .build();

        // Incident 38: Insecure token generation
        Incident incident38 = Incident.builder()
                .id("security-insecure-random-token")
                .title("Session Tokens Generated with Math.random()")
                .difficulty("MEDIUM")
                .category("security")
                .description("""
                        ### Problem Statement
                        Session tokens are generated using `Math.random()`, which is not cryptographically
                        secure — its output is predictable enough that an attacker who observes a few tokens
                        can potentially predict future ones.

                        ### Instructions
                        1. Inspect `solution.js`.
                        2. `generateToken` uses `Math.random().toString(36)`.
                        3. Fix it to use Node's `crypto` module for a cryptographically strong token.
                        """)
                .logs("""
                        [2026-07-18 13:00:00] WARN Security audit flagged Math.random()-based session token generation
                        """)
                .metrics("[]")
                .stackTrace("")
                .baseCode("""
                        {"solution.js":"function generateToken() {\\n  return Math.random().toString(36).substring(2);\\n}\\n\\nmodule.exports = { generateToken };\\n"}
                        """)
                .hiddenTests("""
                        const assert = require('assert');
                        const { generateToken } = require('./solution.js');

                        module.exports = [
                          {
                            name: "should generate a non-empty token",
                            fn: () => {
                              const token = generateToken();
                              assert.ok(token.length > 0);
                            }
                          },
                          {
                            name: "should generate a cryptographically strong 256-bit hex token",
                            fn: () => {
                              const token = generateToken();
                              assert.strictEqual(token.length, 64, `Expected a 64-character hex token (256 bits from crypto.randomBytes), got length ${token.length} — Math.random() is not cryptographically secure and produces short, predictable tokens.`);
                              assert.ok(/^[0-9a-f]+$/.test(token), 'Token is not a valid hex string.');
                            }
                          }
                        ];
                        """)
                .referenceFix("""
                        {"solution.js":"const crypto = require('crypto');\\n\\nfunction generateToken() {\\n  return crypto.randomBytes(32).toString('hex');\\n}\\n\\nmodule.exports = { generateToken };\\n"}
                        """)
                .hint("Use `crypto.randomBytes(32).toString('hex')` from Node's built-in `crypto` module instead of `Math.random()`.")
                .build();

        // Incident 39: Command injection via unsanitized shell input
        Incident incident39 = Incident.builder()
                .id("security-command-injection")
                .title("Command Injection in Diagnostic Ping Tool")
                .difficulty("HARD")
                .category("security")
                .description("""
                        ### Problem Statement
                        A network diagnostic tool builds a shell command by directly concatenating a
                        user-supplied hostname. A host value containing shell metacharacters (like `;` or
                        `&&`) lets an attacker chain arbitrary additional commands onto the ping.

                        ### Instructions
                        1. Inspect `solution.js`.
                        2. `pingHost` concatenates `host` directly into a command string with no validation.
                        3. Fix it to reject any host that isn't a plain hostname/IP before it ever reaches
                           the shell.
                        """)
                .logs("""
                        [2026-07-18 14:00:00] ERROR Diagnostic ping request with payload '8.8.8.8; rm -rf /tmp' reached the shell layer
                        """)
                .metrics("[]")
                .stackTrace("")
                .baseCode("""
                        {"solution.js":"const shell = {\\n  exec: (cmd) => {\\n    if (/[;&|`$]/.test(cmd)) {\\n      return { injected: true, output: 'INJECTED COMMAND EXECUTED' };\\n    }\\n    return { injected: false, output: 'PING OK' };\\n  }\\n};\\n\\nfunction pingHost(host) {\\n  return shell.exec('ping -c 1 ' + host);\\n}\\n\\nmodule.exports = { pingHost, shell };\\n"}
                        """)
                .hiddenTests("""
                        const assert = require('assert');
                        const { pingHost } = require('./solution.js');

                        module.exports = [
                          {
                            name: "should ping a valid host normally",
                            fn: () => {
                              const result = pingHost('8.8.8.8');
                              assert.strictEqual(result.injected, false);
                            }
                          },
                          {
                            name: "should reject a host containing shell metacharacters instead of passing it to the shell",
                            fn: () => {
                              assert.throws(
                                () => pingHost('8.8.8.8; rm -rf /tmp'),
                                /Invalid host/,
                                'Expected a command-injection payload to be rejected before reaching the shell, but it was passed through.'
                              );
                            }
                          }
                        ];
                        """)
                .referenceFix("""
                        {"solution.js":"const shell = {\\n  exec: (cmd) => {\\n    if (/[;&|`$]/.test(cmd)) {\\n      return { injected: true, output: 'INJECTED COMMAND EXECUTED' };\\n    }\\n    return { injected: false, output: 'PING OK' };\\n  }\\n};\\n\\nfunction pingHost(host) {\\n  if (!/^[a-zA-Z0-9.-]+$/.test(host)) {\\n    throw new Error('Invalid host');\\n  }\\n  return shell.exec('ping -c 1 ' + host);\\n}\\n\\nmodule.exports = { pingHost, shell };\\n"}
                        """)
                .hint("Validate `host` against a strict pattern like `/^[a-zA-Z0-9.-]+$/` and throw before ever building the shell command if it doesn't match.")
                .build();

        // Incident 40: Silent null-reference crash
        Incident incident40 = Incident.builder()
                .id("stability-null-reference")
                .title("Crash on Users Without a Profile")
                .difficulty("EASY")
                .category("stability")
                .description("""
                        ### Problem Statement
                        Fetching a user's email crashes for any account that hasn't completed profile setup
                        yet — `user.profile` is `undefined` for those accounts, and accessing `.email` on it
                        throws.

                        ### Instructions
                        1. Inspect `solution.js`.
                        2. `getUserEmail` accesses `user.profile.email` without checking `user.profile` exists.
                        3. Fix it to return `null` gracefully when there's no profile, instead of throwing.
                        """)
                .logs("""
                        [2026-07-19 08:00:00] ERROR TypeError: Cannot read properties of undefined (reading 'email')
                        """)
                .metrics("[]")
                .stackTrace("""
                        TypeError: Cannot read properties of undefined (reading 'email')
                        at getUserEmail (solution.js:2:25)
                        """)
                .baseCode("""
                        {"solution.js":"function getUserEmail(user) {\\n  return user.profile.email;\\n}\\n\\nmodule.exports = { getUserEmail };\\n"}
                        """)
                .hiddenTests("""
                        const assert = require('assert');
                        const { getUserEmail } = require('./solution.js');

                        module.exports = [
                          {
                            name: "should return the email when a profile exists",
                            fn: () => {
                              assert.strictEqual(getUserEmail({ profile: { email: 'a@b.com' } }), 'a@b.com');
                            }
                          },
                          {
                            name: "should not crash for a user with no profile",
                            fn: () => {
                              let result;
                              try {
                                result = getUserEmail({ name: 'no-profile-user' });
                              } catch (e) {
                                assert.fail('getUserEmail threw instead of handling a missing profile gracefully: ' + e.message);
                              }
                              assert.strictEqual(result, null);
                            }
                          }
                        ];
                        """)
                .referenceFix("""
                        {"solution.js":"function getUserEmail(user) {\\n  return user.profile ? user.profile.email : null;\\n}\\n\\nmodule.exports = { getUserEmail };\\n"}
                        """)
                .hint("Check whether `user.profile` exists before accessing `.email` on it — return `null` when it doesn't.")
                .build();

        // Incident 41: Off-by-one array index
        Incident incident41 = Incident.builder()
                .id("stability-array-index-oob")
                .title("Off-By-One Index Returns undefined Instead of Last Item")
                .difficulty("EASY")
                .category("stability")
                .description("""
                        ### Problem Statement
                        A "most recent item" helper is supposed to return the last element of a list, but
                        it consistently returns `undefined` instead — an off-by-one error in the index
                        calculation.

                        ### Instructions
                        1. Inspect `solution.js`.
                        2. `getLastItem` indexes with `items[items.length]`, which is always one past the end.
                        3. Fix the index so it correctly returns the last element.
                        """)
                .logs("""
                        [2026-07-19 09:00:00] WARN getLastItem() returned undefined for a non-empty array
                        """)
                .metrics("[]")
                .stackTrace("")
                .baseCode("""
                        {"solution.js":"function getLastItem(items) {\\n  return items[items.length];\\n}\\n\\nmodule.exports = { getLastItem };\\n"}
                        """)
                .hiddenTests("""
                        const assert = require('assert');
                        const { getLastItem } = require('./solution.js');

                        module.exports = [
                          {
                            name: "should return the last item of the array",
                            fn: () => {
                              assert.strictEqual(getLastItem([10, 20, 30]), 30, "getLastItem did not return the last element — check for an off-by-one index error.");
                            }
                          },
                          {
                            name: "should return the correct last item for a single-element array",
                            fn: () => {
                              assert.strictEqual(getLastItem(['only']), 'only');
                            }
                          }
                        ];
                        """)
                .referenceFix("""
                        {"solution.js":"function getLastItem(items) {\\n  return items[items.length - 1];\\n}\\n\\nmodule.exports = { getLastItem };\\n"}
                        """)
                .hint("Array indices go from `0` to `length - 1`. `items[items.length]` is always out of bounds — it should be `items[items.length - 1]`.")
                .build();

        // Incident 42: Retry logic that never actually retries
        Incident incident42 = Incident.builder()
                .id("stability-retry-gives-up-immediately")
                .title("Retry Wrapper Gives Up After a Single Attempt")
                .difficulty("MEDIUM")
                .category("stability")
                .description("""
                        ### Problem Statement
                        A wrapper meant to retry flaky operations up to `maxAttempts` times instead gives up
                        the instant the first attempt fails, even though the underlying operation would have
                        succeeded on a later try. Transient network blips now surface as hard failures.

                        ### Instructions
                        1. Inspect `solution.js`.
                        2. `retryOperation` calls `fn()` once and returns immediately, ignoring `maxAttempts`.
                        3. Fix it to actually retry on failure, up to `maxAttempts` times, before giving up.
                        """)
                .logs("""
                        [2026-07-19 10:00:00] ERROR Operation failed on first attempt; no retry was attempted despite maxAttempts=5
                        """)
                .metrics("[]")
                .stackTrace("")
                .baseCode("""
                        {"solution.js":"async function retryOperation(fn, maxAttempts) {\\n  return fn();\\n}\\n\\nmodule.exports = { retryOperation };\\n"}
                        """)
                .hiddenTests("""
                        const assert = require('assert');
                        const { retryOperation } = require('./solution.js');

                        module.exports = [
                          {
                            name: "should return the result immediately when the operation succeeds",
                            fn: async () => {
                              const result = await retryOperation(async () => 'ok', 3);
                              assert.strictEqual(result, 'ok');
                            }
                          },
                          {
                            name: "should retry a transiently-failing operation until it succeeds",
                            fn: async () => {
                              let attempts = 0;
                              const flaky = async () => {
                                attempts++;
                                if (attempts < 3) throw new Error('temporary failure');
                                return 'recovered';
                              };
                              let result;
                              try {
                                result = await retryOperation(flaky, 5);
                              } catch (e) {
                                assert.fail('retryOperation gave up instead of retrying the transient failure: ' + e.message);
                              }
                              assert.strictEqual(result, 'recovered');
                              assert.ok(attempts >= 3, `Expected at least 3 attempts, only made ${attempts}.`);
                            }
                          }
                        ];
                        """)
                .referenceFix("""
                        {"solution.js":"async function retryOperation(fn, maxAttempts) {\\n  let lastErr;\\n  for (let i = 0; i < maxAttempts; i++) {\\n    try {\\n      return await fn();\\n    } catch (err) {\\n      lastErr = err;\\n    }\\n  }\\n  throw lastErr;\\n}\\n\\nmodule.exports = { retryOperation };\\n"}
                        """)
                .hint("Wrap the call to `fn()` in a loop up to `maxAttempts` times, with a `try/catch` inside the loop that swallows the error and tries again — only throw once every attempt has been exhausted.")
                .build();

        // Incident 43: Missing default case in error-code switch
        Incident incident43 = Incident.builder()
                .id("stability-missing-default-catch")
                .title("Unknown Error Codes Silently Return undefined")
                .difficulty("MEDIUM")
                .category("stability")
                .description("""
                        ### Problem Statement
                        An error-code handler covers a couple of known cases but has no `default` branch.
                        Whenever a new or unexpected error code shows up, the function silently returns
                        `undefined`, and the caller crashes trying to read `.status` off of it.

                        ### Instructions
                        1. Inspect `solution.js`.
                        2. `handleErrorCode`'s `switch` has no `default` case.
                        3. Fix it to return a safe fallback object for any unrecognized code.
                        """)
                .logs("""
                        [2026-07-19 11:00:00] ERROR TypeError: Cannot read properties of undefined (reading 'status') after an unrecognized error code
                        """)
                .metrics("[]")
                .stackTrace("")
                .baseCode("""
                        {"solution.js":"function handleErrorCode(code) {\\n  switch (code) {\\n    case 'NOT_FOUND':\\n      return { status: 404, message: 'Not found' };\\n    case 'UNAUTHORIZED':\\n      return { status: 401, message: 'Unauthorized' };\\n  }\\n}\\n\\nmodule.exports = { handleErrorCode };\\n"}
                        """)
                .hiddenTests("""
                        const assert = require('assert');
                        const { handleErrorCode } = require('./solution.js');

                        module.exports = [
                          {
                            name: "should handle a known error code",
                            fn: () => {
                              assert.deepStrictEqual(handleErrorCode('NOT_FOUND'), { status: 404, message: 'Not found' });
                            }
                          },
                          {
                            name: "should handle an unrecognized error code safely instead of returning undefined",
                            fn: () => {
                              const result = handleErrorCode('SOME_NEW_ERROR_TYPE');
                              assert.ok(result && typeof result.status === 'number', `handleErrorCode returned ${JSON.stringify(result)} for an unknown code — downstream code calling .status on this will crash.`);
                            }
                          }
                        ];
                        """)
                .referenceFix("""
                        {"solution.js":"function handleErrorCode(code) {\\n  switch (code) {\\n    case 'NOT_FOUND':\\n      return { status: 404, message: 'Not found' };\\n    case 'UNAUTHORIZED':\\n      return { status: 401, message: 'Unauthorized' };\\n    default:\\n      return { status: 500, message: 'Unknown error: ' + code };\\n  }\\n}\\n\\nmodule.exports = { handleErrorCode };\\n"}
                        """)
                .hint("Add a `default:` case to the switch that returns a generic error object (e.g. status 500) instead of falling through to an implicit `undefined` return.")
                .build();

        // Incident 44: Double response send crashes the handler
        Incident incident44 = Incident.builder()
                .id("stability-double-response")
                .title("Missing return Sends a Response Twice")
                .difficulty("MEDIUM")
                .category("stability")
                .description("""
                        ### Problem Statement
                        A request handler sends an early error response for invalid input, but forgets to
                        `return` afterward — execution falls through and sends a second response for the
                        same request, which crashes a real HTTP handler with "Cannot set headers after they
                        are sent".

                        ### Instructions
                        1. Inspect `solution.js`.
                        2. `handleRequest` sends an error response inside an `if` block with no `return`.
                        3. Fix it by returning immediately after the early error response.
                        """)
                .logs("""
                        [2026-07-19 12:00:00] ERROR Cannot set headers after they are sent to the client
                        """)
                .metrics("[]")
                .stackTrace("""
                        Error: Cannot set headers after they are sent to the client
                        at handleRequest (solution.js:9:11)
                        """)
                .baseCode("""
                        {"solution.js":"function createMockRes() {\\n  let sent = false;\\n  return {\\n    send: (body) => {\\n      if (sent) {\\n        throw new Error('Cannot set headers after they are sent to the client');\\n      }\\n      sent = true;\\n      return body;\\n    },\\n    wasSent: () => sent,\\n  };\\n}\\n\\nfunction handleRequest(req, res) {\\n  if (!req.body) {\\n    res.send({ error: 'Missing body' });\\n  }\\n  res.send({ success: true });\\n}\\n\\nmodule.exports = { handleRequest, createMockRes };\\n"}
                        """)
                .hiddenTests("""
                        const assert = require('assert');
                        const { handleRequest, createMockRes } = require('./solution.js');

                        module.exports = [
                          {
                            name: "should send a success response for a valid request",
                            fn: () => {
                              const res = createMockRes();
                              handleRequest({ body: { x: 1 } }, res);
                              assert.ok(res.wasSent());
                            }
                          },
                          {
                            name: "should not attempt to send a second response after an early error response",
                            fn: () => {
                              const res = createMockRes();
                              try {
                                handleRequest({}, res);
                              } catch (e) {
                                assert.fail('handleRequest sent a response twice for an invalid request (' + e.message + ') — a real Express handler would crash with this exact error.');
                              }
                            }
                          }
                        ];
                        """)
                .referenceFix("""
                        {"solution.js":"function createMockRes() {\\n  let sent = false;\\n  return {\\n    send: (body) => {\\n      if (sent) {\\n        throw new Error('Cannot set headers after they are sent to the client');\\n      }\\n      sent = true;\\n      return body;\\n    },\\n    wasSent: () => sent,\\n  };\\n}\\n\\nfunction handleRequest(req, res) {\\n  if (!req.body) {\\n    res.send({ error: 'Missing body' });\\n    return;\\n  }\\n  res.send({ success: true });\\n}\\n\\nmodule.exports = { handleRequest, createMockRes };\\n"}
                        """)
                .hint("Add a `return;` statement right after `res.send({ error: 'Missing body' })` so the function doesn't fall through to the second `res.send()` call.")
                .build();

        // Incident 45: NaN silently propagating from empty-array division
        Incident incident45 = Incident.builder()
                .id("stability-nan-propagation")
                .title("Empty Score List Silently Produces NaN")
                .difficulty("EASY")
                .category("stability")
                .description("""
                        ### Problem Statement
                        An average-score calculator divides the total by the list length with no guard for
                        an empty list. For students with no graded assignments yet, this silently produces
                        `NaN`, which then corrupts every downstream calculation and report that uses it.

                        ### Instructions
                        1. Inspect `solution.js`.
                        2. `averageScore` divides by `scores.length` unconditionally.
                        3. Fix it to handle the empty-array case explicitly instead of producing `NaN`.
                        """)
                .logs("""
                        [2026-07-19 13:00:00] WARN averageScore([]) returned NaN, corrupting a downstream report total
                        """)
                .metrics("[]")
                .stackTrace("")
                .baseCode("""
                        {"solution.js":"function averageScore(scores) {\\n  const total = scores.reduce((a, b) => a + b, 0);\\n  return total / scores.length;\\n}\\n\\nmodule.exports = { averageScore };\\n"}
                        """)
                .hiddenTests("""
                        const assert = require('assert');
                        const { averageScore } = require('./solution.js');

                        module.exports = [
                          {
                            name: "should compute the average of a normal list of scores",
                            fn: () => {
                              assert.strictEqual(averageScore([2, 4, 6]), 4);
                            }
                          },
                          {
                            name: "should not silently produce NaN for an empty list",
                            fn: () => {
                              const result = averageScore([]);
                              assert.ok(!Number.isNaN(result), 'averageScore([]) returned NaN, which will silently corrupt any downstream calculation instead of being handled explicitly.');
                            }
                          }
                        ];
                        """)
                .referenceFix("""
                        {"solution.js":"function averageScore(scores) {\\n  if (scores.length === 0) {\\n    return 0;\\n  }\\n  const total = scores.reduce((a, b) => a + b, 0);\\n  return total / scores.length;\\n}\\n\\nmodule.exports = { averageScore };\\n"}
                        """)
                .hint("Check `if (scores.length === 0) return 0;` (or throw a clear error) before dividing, instead of letting `0 / 0` silently produce `NaN`.")
                .build();

        // Incident 46: Recursive processing overflows the call stack
        Incident incident46 = Incident.builder()
                .id("stability-stack-overflow-recursion")
                .title("Recursive Linked-List Sum Overflows the Call Stack")
                .difficulty("HARD")
                .category("stability")
                .description("""
                        ### Problem Statement
                        A function that sums values across a linked list works fine in testing with small
                        lists, but crashes with "Maximum call stack size exceeded" in production, where
                        lists routinely have tens of thousands of nodes.

                        ### Instructions
                        1. Inspect `solution.js`.
                        2. `sumList` recurses one call per node with no tail-call optimization available in
                           Node.js, so long lists blow the call stack.
                        3. Fix it by converting the recursion to an iterative loop.
                        """)
                .logs("""
                        [2026-07-19 14:00:00] ERROR RangeError: Maximum call stack size exceeded while summing a 100,000-node list
                        """)
                .metrics("[]")
                .stackTrace("""
                        RangeError: Maximum call stack size exceeded
                        at sumList (solution.js:3:24)
                        at sumList (solution.js:3:24)
                        at sumList (solution.js:3:24)
                        ... (thousands more frames)
                        """)
                .baseCode("""
                        {"solution.js":"function sumList(node) {\\n  if (node === null) return 0;\\n  return node.value + sumList(node.next);\\n}\\n\\nmodule.exports = { sumList };\\n"}
                        """)
                .hiddenTests("""
                        const assert = require('assert');
                        const { sumList } = require('./solution.js');

                        function buildList(n) {
                          let head = null;
                          for (let i = n; i >= 1; i--) {
                            head = { value: i, next: head };
                          }
                          return head;
                        }

                        module.exports = [
                          {
                            name: "should sum a small linked list correctly",
                            fn: () => {
                              const list = buildList(5);
                              assert.strictEqual(sumList(list), 15);
                            }
                          },
                          {
                            name: "should handle a very long linked list without overflowing the call stack",
                            fn: () => {
                              const list = buildList(100000);
                              let result;
                              try {
                                result = sumList(list);
                              } catch (e) {
                                assert.fail('sumList crashed on a long list (' + e.message + ') — recursive processing overflows the call stack on large inputs.');
                              }
                              assert.strictEqual(result, (100000 * 100001) / 2);
                            }
                          }
                        ];
                        """)
                .referenceFix("""
                        {"solution.js":"function sumList(node) {\\n  let total = 0;\\n  let current = node;\\n  while (current !== null) {\\n    total += current.value;\\n    current = current.next;\\n  }\\n  return total;\\n}\\n\\nmodule.exports = { sumList };\\n"}
                        """)
                .hint("Replace the recursive `node.value + sumList(node.next)` with a `while` loop that walks `node.next` and accumulates a running total — no call stack growth either way.")
                .build();

        // Incident 47: forEach with async callbacks doesn't await
        Incident incident47 = Incident.builder()
                .id("concurrency-out-of-order-processing")
                .title("forEach With Async Callbacks Returns Before Work Finishes")
                .difficulty("MEDIUM")
                .category("concurrency")
                .description("""
                        ### Problem Statement
                        A function applies a sequence of numeric deltas and returns the running total.
                        Callers consistently get back `0` instead of the correct sum — the function resolves
                        before any of its async work has actually completed.

                        ### Instructions
                        1. Inspect `solution.js`.
                        2. `applyDeltas` uses `deltas.forEach(async delta => ...)` — `forEach` does not wait
                           for async callbacks, so the function returns immediately.
                        3. Fix it to actually wait for each delta to be applied before returning the total.
                        """)
                .logs("""
                        [2026-07-20 08:00:00] WARN applyDeltas([1,2,3,4,5]) resolved to 0 instead of 15
                        """)
                .metrics("[]")
                .stackTrace("")
                .baseCode("""
                        {"solution.js":"async function applyDeltas(deltas) {\\n  let total = 0;\\n  deltas.forEach(async (delta) => {\\n    await new Promise((resolve) => setImmediate(resolve));\\n    total += delta;\\n  });\\n  return total;\\n}\\n\\nmodule.exports = { applyDeltas };\\n"}
                        """)
                .hiddenTests("""
                        const assert = require('assert');
                        const { applyDeltas } = require('./solution.js');

                        module.exports = [
                          {
                            name: "should apply all deltas and resolve with the correct total",
                            fn: async () => {
                              const total = await applyDeltas([1, 2, 3, 4, 5]);
                              assert.strictEqual(total, 15, `Expected 15, got ${total} — the function resolved before its async work actually finished.`);
                            }
                          },
                          {
                            name: "should work correctly for a longer sequence too",
                            fn: async () => {
                              const total = await applyDeltas([10, -3, 7, 2]);
                              assert.strictEqual(total, 16);
                            }
                          }
                        ];
                        """)
                .referenceFix("""
                        {"solution.js":"async function applyDeltas(deltas) {\\n  let total = 0;\\n  for (const delta of deltas) {\\n    await new Promise((resolve) => setImmediate(resolve));\\n    total += delta;\\n  }\\n  return total;\\n}\\n\\nmodule.exports = { applyDeltas };\\n"}
                        """)
                .hint("`Array.prototype.forEach` ignores the return value of its callback entirely, so it never waits for async callbacks. Use a `for...of` loop with `await` instead.")
                .build();

        // Incident 48: Cache stampede on concurrent misses
        Incident incident48 = Incident.builder()
                .id("concurrency-cache-stampede")
                .title("Cache Stampede on Concurrent Requests for the Same Key")
                .difficulty("HARD")
                .category("concurrency")
                .description("""
                        ### Problem Statement
                        When a cache entry is cold, a burst of concurrent requests for the same key each
                        independently trigger the expensive computation — instead of the first request
                        computing it once and the rest reusing that in-flight result.

                        ### Instructions
                        1. Inspect `solution.js`.
                        2. `getOrCompute` only checks the *completed* cache, not any in-flight computation.
                        3. Fix it so concurrent requests for the same key share a single in-flight computation.
                        """)
                .logs("""
                        [2026-07-20 09:00:00] WARN 5 concurrent requests for the same cache key triggered 5 separate expensive computations
                        """)
                .metrics("[]")
                .stackTrace("")
                .baseCode("""
                        {"solution.js":"const cache = new Map();\\nlet computeCallCount = 0;\\n\\nasync function expensiveCompute(key) {\\n  computeCallCount++;\\n  await new Promise((resolve) => setTimeout(resolve, 10));\\n  return 'value-for-' + key;\\n}\\n\\nasync function getOrCompute(key) {\\n  if (cache.has(key)) return cache.get(key);\\n  const value = await expensiveCompute(key);\\n  cache.set(key, value);\\n  return value;\\n}\\n\\nmodule.exports = {\\n  getOrCompute,\\n  _getComputeCallCount: () => computeCallCount,\\n  _reset: () => { cache.clear(); computeCallCount = 0; },\\n};\\n"}
                        """)
                .hiddenTests("""
                        const assert = require('assert');
                        const solution = require('./solution.js');

                        module.exports = [
                          {
                            name: "should compute and cache a value for a key",
                            fn: async () => {
                              solution._reset();
                              const value = await solution.getOrCompute('a');
                              assert.strictEqual(value, 'value-for-a');
                            }
                          },
                          {
                            name: "concurrent requests for the same key should only compute once (no cache stampede)",
                            fn: async () => {
                              solution._reset();
                              const results = await Promise.all([
                                solution.getOrCompute('shared-key'),
                                solution.getOrCompute('shared-key'),
                                solution.getOrCompute('shared-key'),
                                solution.getOrCompute('shared-key'),
                                solution.getOrCompute('shared-key'),
                              ]);
                              for (const r of results) {
                                assert.strictEqual(r, 'value-for-shared-key');
                              }
                              assert.strictEqual(
                                solution._getComputeCallCount(),
                                1,
                                `Expected the expensive computation to run exactly once, but it ran ${solution._getComputeCallCount()} times — concurrent requests for the same key are each recomputing independently instead of sharing the in-flight result.`
                              );
                            }
                          }
                        ];
                        """)
                .referenceFix("""
                        {"solution.js":"const cache = new Map();\\nconst inFlight = new Map();\\nlet computeCallCount = 0;\\n\\nasync function expensiveCompute(key) {\\n  computeCallCount++;\\n  await new Promise((resolve) => setTimeout(resolve, 10));\\n  return 'value-for-' + key;\\n}\\n\\nasync function getOrCompute(key) {\\n  if (cache.has(key)) return cache.get(key);\\n  if (inFlight.has(key)) return inFlight.get(key);\\n\\n  const promise = expensiveCompute(key).then((value) => {\\n    cache.set(key, value);\\n    inFlight.delete(key);\\n    return value;\\n  });\\n  inFlight.set(key, promise);\\n  return promise;\\n}\\n\\nmodule.exports = {\\n  getOrCompute,\\n  _getComputeCallCount: () => computeCallCount,\\n  _reset: () => { cache.clear(); inFlight.clear(); computeCallCount = 0; },\\n};\\n"}
                        """)
                .hint("Track in-flight promises per key in a separate `Map`. If a request arrives for a key that's already being computed, return the existing in-flight promise instead of starting a new computation.")
                .build();

        // Incident 49: Partial batch failure leaves no rollback
        Incident incident49 = Incident.builder()
                .id("concurrency-partial-failure-rollback")
                .title("Failed Seat Reservation Leaves Earlier Ones Dangling")
                .difficulty("MEDIUM")
                .category("concurrency")
                .description("""
                        ### Problem Statement
                        A checkout flow reserves several seats one at a time. If a later seat in the batch
                        turns out to be unavailable, the whole operation throws — but the seats already
                        reserved earlier in the same batch are never released, silently leaking capacity.

                        ### Instructions
                        1. Inspect `solution.js`.
                        2. `reserveSeats` throws on the first failed request without releasing anything
                           reserved so far.
                        3. Fix it so a failure rolls back every reservation made earlier in the same batch.
                        """)
                .logs("""
                        [2026-07-20 10:00:00] ERROR Seat unavailable — batch aborted, 2 earlier reservations left dangling
                        """)
                .metrics("[]")
                .stackTrace("")
                .baseCode("""
                        {"solution.js":"class SeatSystem {\\n  constructor() {\\n    this.reservedCount = 0;\\n  }\\n\\n  reserve() {\\n    this.reservedCount++;\\n    return {\\n      release: () => {\\n        this.reservedCount--;\\n      },\\n    };\\n  }\\n}\\n\\nasync function reserveSeats(system, requests) {\\n  const reservations = [];\\n  for (const req of requests) {\\n    if (req.shouldFail) {\\n      throw new Error('Seat unavailable');\\n    }\\n    reservations.push(system.reserve());\\n  }\\n  return reservations;\\n}\\n\\nmodule.exports = { SeatSystem, reserveSeats };\\n"}
                        """)
                .hiddenTests("""
                        const assert = require('assert');
                        const { SeatSystem, reserveSeats } = require('./solution.js');

                        module.exports = [
                          {
                            name: "should reserve all seats when every request succeeds",
                            fn: async () => {
                              const system = new SeatSystem();
                              const reservations = await reserveSeats(system, [{}, {}, {}]);
                              assert.strictEqual(reservations.length, 3);
                              assert.strictEqual(system.reservedCount, 3);
                            }
                          },
                          {
                            name: "should release already-made reservations if a later request fails",
                            fn: async () => {
                              const system = new SeatSystem();
                              await assert.rejects(() =>
                                reserveSeats(system, [{}, {}, { shouldFail: true }])
                              );
                              assert.strictEqual(
                                system.reservedCount,
                                0,
                                `Expected 0 reserved seats after a failed batch, but ${system.reservedCount} remain reserved — successful reservations made before the failure were never rolled back.`
                              );
                            }
                          }
                        ];
                        """)
                .referenceFix("""
                        {"solution.js":"class SeatSystem {\\n  constructor() {\\n    this.reservedCount = 0;\\n  }\\n\\n  reserve() {\\n    this.reservedCount++;\\n    return {\\n      release: () => {\\n        this.reservedCount--;\\n      },\\n    };\\n  }\\n}\\n\\nasync function reserveSeats(system, requests) {\\n  const reservations = [];\\n  try {\\n    for (const req of requests) {\\n      if (req.shouldFail) {\\n        throw new Error('Seat unavailable');\\n      }\\n      reservations.push(system.reserve());\\n    }\\n    return reservations;\\n  } catch (err) {\\n    for (const r of reservations) {\\n      r.release();\\n    }\\n    throw err;\\n  }\\n}\\n\\nmodule.exports = { SeatSystem, reserveSeats };\\n"}
                        """)
                .hint("Wrap the reservation loop in a `try/catch`. On failure, iterate over whatever reservations were already made and call `.release()` on each before re-throwing.")
                .build();

        // Incident 50: Missing debounce causes redundant writes
        Incident incident50 = Incident.builder()
                .id("concurrency-debounce-missing")
                .title("Draft Autosave Fires on Every Keystroke Instead of Debouncing")
                .difficulty("MEDIUM")
                .category("concurrency")
                .description("""
                        ### Problem Statement
                        An autosave function is meant to collapse a rapid burst of calls into a single save
                        using the last known state, but it currently persists on every single call — a burst
                        of keystrokes triggers a storm of redundant, concurrent writes.

                        ### Instructions
                        1. Inspect `solution.js`.
                        2. `debounce(fn)` returns a wrapper that just calls `fn` immediately every time.
                        3. Fix it so rapid, back-to-back calls collapse into a single trailing call with the
                           last arguments.
                        """)
                .logs("""
                        [2026-07-20 11:00:00] WARN 5 rapid autosave calls triggered 5 separate persist operations
                        """)
                .metrics("[]")
                .stackTrace("")
                .baseCode("""
                        {"solution.js":"function debounce(fn) {\\n  return function (...args) {\\n    fn(...args);\\n  };\\n}\\n\\nmodule.exports = { debounce };\\n"}
                        """)
                .hiddenTests("""
                        const assert = require('assert');
                        const { debounce } = require('./solution.js');

                        module.exports = [
                          {
                            name: "should eventually call the underlying function",
                            fn: async () => {
                              let called = 0;
                              const d = debounce(() => { called++; });
                              d();
                              await Promise.resolve();
                              await Promise.resolve();
                              await Promise.resolve();
                              assert.ok(called >= 1);
                            }
                          },
                          {
                            name: "rapid synchronous calls should collapse into a single call with the last arguments",
                            fn: async () => {
                              const calls = [];
                              const d = debounce((v) => calls.push(v));
                              d(1);
                              d(2);
                              d(3);
                              d(4);
                              d(5);
                              await Promise.resolve();
                              await Promise.resolve();
                              await Promise.resolve();
                              assert.strictEqual(calls.length, 1, `Expected exactly 1 call after 5 rapid synchronous calls, got ${calls.length} — the function isn't debounced.`);
                              assert.strictEqual(calls[0], 5, `Expected the debounced call to use the last arguments (5), got ${calls[0]}.`);
                            }
                          }
                        ];
                        """)
                .referenceFix("""
                        {"solution.js":"function debounce(fn) {\\n  let scheduled = false;\\n  let lastArgs = null;\\n\\n  return function (...args) {\\n    lastArgs = args;\\n    if (!scheduled) {\\n      scheduled = true;\\n      Promise.resolve().then(() => {\\n        scheduled = false;\\n        fn(...lastArgs);\\n      });\\n    }\\n  };\\n}\\n\\nmodule.exports = { debounce };\\n"}
                        """)
                .hint("Track whether a flush is already scheduled. On the first call, schedule `fn` to run on the next microtask (`Promise.resolve().then(...)`) using whatever the *latest* arguments turn out to be by the time it runs.")
                .build();

        // Incident 51: Connection pool exhausted by failing queries
        Incident incident51 = Incident.builder()
                .id("concurrency-connection-pool-exhaustion")
                .title("Failed Queries Leak Connections Out of the Pool")
                .difficulty("HARD")
                .category("concurrency")
                .description("""
                        ### Problem Statement
                        A fixed-size connection pool only releases a connection on the success path. Once
                        enough queries fail in a row, the pool runs out of available connections entirely —
                        even though every one of those "in use" connections is actually sitting idle.

                        ### Instructions
                        1. Inspect `solution.js`.
                        2. `runQuery` calls `pool.release(conn)` only after checking `shouldFail`, so a
                           thrown error skips the release entirely.
                        3. Fix it to always release the connection, whether the query succeeds or fails.
                        """)
                .logs("""
                        [2026-07-20 12:00:00] ERROR Connection pool exhausted after 5 failed queries in a row (0/5 available)
                        """)
                .metrics("[]")
                .stackTrace("")
                .baseCode("""
                        {"solution.js":"class ConnectionPool {\\n  constructor(size) {\\n    this.available = size;\\n    this.max = size;\\n  }\\n\\n  acquire() {\\n    if (this.available <= 0) {\\n      throw new Error('Pool exhausted');\\n    }\\n    this.available--;\\n    return { id: Math.random() };\\n  }\\n\\n  release(conn) {\\n    this.available++;\\n  }\\n}\\n\\nasync function runQuery(pool, shouldFail) {\\n  const conn = pool.acquire();\\n  if (shouldFail) {\\n    throw new Error('Query failed');\\n  }\\n  pool.release(conn);\\n  return 'ok';\\n}\\n\\nmodule.exports = { ConnectionPool, runQuery };\\n"}
                        """)
                .hiddenTests("""
                        const assert = require('assert');
                        const { ConnectionPool, runQuery } = require('./solution.js');

                        module.exports = [
                          {
                            name: "should release the connection after a successful query",
                            fn: async () => {
                              const pool = new ConnectionPool(5);
                              const result = await runQuery(pool, false);
                              assert.strictEqual(result, 'ok');
                              assert.strictEqual(pool.available, 5);
                            }
                          },
                          {
                            name: "failed queries should still release their connection back to the pool",
                            fn: async () => {
                              const pool = new ConnectionPool(5);
                              for (let i = 0; i < 5; i++) {
                                try {
                                  await runQuery(pool, true);
                                } catch (e) {
                                  // expected — the query itself fails
                                }
                              }
                              assert.strictEqual(
                                pool.available,
                                5,
                                `Pool has only ${pool.available}/5 connections available after 5 failed queries — failing queries are leaking connections instead of releasing them back to the pool.`
                              );
                            }
                          }
                        ];
                        """)
                .referenceFix("""
                        {"solution.js":"class ConnectionPool {\\n  constructor(size) {\\n    this.available = size;\\n    this.max = size;\\n  }\\n\\n  acquire() {\\n    if (this.available <= 0) {\\n      throw new Error('Pool exhausted');\\n    }\\n    this.available--;\\n    return { id: Math.random() };\\n  }\\n\\n  release(conn) {\\n    this.available++;\\n  }\\n}\\n\\nasync function runQuery(pool, shouldFail) {\\n  const conn = pool.acquire();\\n  try {\\n    if (shouldFail) {\\n      throw new Error('Query failed');\\n    }\\n    return 'ok';\\n  } finally {\\n    pool.release(conn);\\n  }\\n}\\n\\nmodule.exports = { ConnectionPool, runQuery };\\n"}
                        """)
                .hint("Wrap the query logic in `try { ... } finally { pool.release(conn); }` so the connection is released no matter how the query turns out.")
                .build();

        // Incident 52: Concurrent flush triggers double-send a batch
        Incident incident52 = Incident.builder()
                .id("concurrency-batched-flush-race")
                .title("Concurrent Flush Triggers Double-Send the Same Batch")
                .difficulty("MEDIUM")
                .category("concurrency")
                .description("""
                        ### Problem Statement
                        Two independent code paths can each decide "the buffer is full, flush now" around
                        the same time. Because `flush()` has no guard against re-entry, both invocations
                        capture and send the exact same batch of data — resulting in a duplicate send.

                        ### Instructions
                        1. Inspect `solution.js`.
                        2. `flush()` has no protection against being called again while a previous flush is
                           still in progress (it `await`s before actually sending).
                        3. Fix it with an in-progress flag so a second concurrent call is a no-op.
                        """)
                .logs("""
                        [2026-07-20 13:00:00] WARN Two concurrent flush() calls both sent the same 2-item batch to the sink
                        """)
                .metrics("[]")
                .stackTrace("")
                .baseCode("""
                        {"solution.js":"class WriteBuffer {\\n  constructor(sink) {\\n    this.items = [];\\n    this.sink = sink;\\n    this.flushCalls = 0;\\n  }\\n\\n  add(item) {\\n    this.items.push(item);\\n  }\\n\\n  async flush() {\\n    const batch = this.items;\\n    await Promise.resolve();\\n    this.flushCalls++;\\n    this.sink(batch);\\n    this.items = [];\\n  }\\n}\\n\\nmodule.exports = { WriteBuffer };\\n"}
                        """)
                .hiddenTests("""
                        const assert = require('assert');
                        const { WriteBuffer } = require('./solution.js');

                        module.exports = [
                          {
                            name: "should flush all added items to the sink",
                            fn: async () => {
                              const sunk = [];
                              const buf = new WriteBuffer((batch) => sunk.push(...batch));
                              buf.add(1);
                              buf.add(2);
                              await buf.flush();
                              assert.deepStrictEqual(sunk, [1, 2]);
                            }
                          },
                          {
                            name: "concurrent flush triggers should not double-send the same batch",
                            fn: async () => {
                              const sunk = [];
                              const buf = new WriteBuffer((batch) => sunk.push(batch));
                              buf.add('a');
                              buf.add('b');
                              await Promise.all([buf.flush(), buf.flush()]);
                              assert.strictEqual(sunk.length, 1, `Expected exactly 1 flush to the sink, got ${sunk.length} — concurrent flush triggers are double-sending the same batch.`);
                            }
                          }
                        ];
                        """)
                .referenceFix("""
                        {"solution.js":"class WriteBuffer {\\n  constructor(sink) {\\n    this.items = [];\\n    this.sink = sink;\\n    this.flushCalls = 0;\\n    this.flushing = false;\\n  }\\n\\n  add(item) {\\n    this.items.push(item);\\n  }\\n\\n  async flush() {\\n    if (this.flushing) {\\n      return;\\n    }\\n    this.flushing = true;\\n    const batch = this.items;\\n    this.items = [];\\n    await Promise.resolve();\\n    this.flushCalls++;\\n    this.sink(batch);\\n    this.flushing = false;\\n  }\\n}\\n\\nmodule.exports = { WriteBuffer };\\n"}
                        """)
                .hint("Add a `this.flushing` boolean. At the very top of `flush()`, return immediately if it's already `true`; otherwise set it `true` synchronously (before any `await`) and clear the buffer right away, so a concurrent call sees an empty buffer.")
                .build();

        // Incident 53: Duplicate payment charges from retried requests
        Incident incident53 = Incident.builder()
                .id("concurrency-idempotency-missing")
                .title("Retried Payment Requests Charge the Card Twice")
                .difficulty("MEDIUM")
                .category("concurrency")
                .description("""
                        ### Problem Statement
                        Clients retry payment requests on network timeouts, always resending the exact same
                        `requestId`. The payment handler doesn't recognize duplicate requestIds, so a single
                        logical payment can end up charging the customer's card more than once.

                        ### Instructions
                        1. Inspect `solution.js`.
                        2. `processPayment` charges the card on every call, regardless of `requestId`.
                        3. Fix it to track processed requestIds and return the cached result for a duplicate
                           instead of charging again.
                        """)
                .logs("""
                        [2026-07-20 14:00:00] ERROR Card charged twice for the same requestId 'req-dup' after a client retry
                        """)
                .metrics("[]")
                .stackTrace("")
                .baseCode("""
                        {"solution.js":"const charges = [];\\nlet chargeCallCount = 0;\\n\\nfunction chargeCard(amount) {\\n  chargeCallCount++;\\n  charges.push(amount);\\n  return { chargeId: 'ch_' + charges.length, amount };\\n}\\n\\nconst processedRequests = new Map();\\n\\nfunction processPayment(requestId, amount) {\\n  return chargeCard(amount);\\n}\\n\\nmodule.exports = {\\n  processPayment,\\n  _getChargeCallCount: () => chargeCallCount,\\n  _reset: () => { charges.length = 0; chargeCallCount = 0; processedRequests.clear(); },\\n};\\n"}
                        """)
                .hiddenTests("""
                        const assert = require('assert');
                        const solution = require('./solution.js');

                        module.exports = [
                          {
                            name: "should process a new payment request",
                            fn: () => {
                              solution._reset();
                              const result = solution.processPayment('req-1', 100);
                              assert.strictEqual(result.amount, 100);
                            }
                          },
                          {
                            name: "a retried request with the same requestId should not be charged twice",
                            fn: () => {
                              solution._reset();
                              const first = solution.processPayment('req-dup', 250);
                              const second = solution.processPayment('req-dup', 250);
                              assert.deepStrictEqual(second, first);
                              assert.strictEqual(
                                solution._getChargeCallCount(),
                                1,
                                `Expected the card to be charged exactly once, but it was charged ${solution._getChargeCallCount()} times — a retried request with the same requestId is not being deduplicated.`
                              );
                            }
                          }
                        ];
                        """)
                .referenceFix("""
                        {"solution.js":"const charges = [];\\nlet chargeCallCount = 0;\\n\\nfunction chargeCard(amount) {\\n  chargeCallCount++;\\n  charges.push(amount);\\n  return { chargeId: 'ch_' + charges.length, amount };\\n}\\n\\nconst processedRequests = new Map();\\n\\nfunction processPayment(requestId, amount) {\\n  if (processedRequests.has(requestId)) {\\n    return processedRequests.get(requestId);\\n  }\\n  const result = chargeCard(amount);\\n  processedRequests.set(requestId, result);\\n  return result;\\n}\\n\\nmodule.exports = {\\n  processPayment,\\n  _getChargeCallCount: () => chargeCallCount,\\n  _reset: () => { charges.length = 0; chargeCallCount = 0; processedRequests.clear(); },\\n};\\n"}
                        """)
                .hint("Check `processedRequests` for the `requestId` before charging. If it's already there, return the cached result; otherwise charge, store the result under that `requestId`, and return it.")
                .build();

        // Incident 54: upsert always inserts instead of updating
        Incident incident54 = Incident.builder()
                .id("database-duplicate-writes-no-upsert")
                .title("Upsert Always Inserts, Never Updates")
                .difficulty("EASY")
                .category("database")
                .description("""
                        ### Problem Statement
                        A function meant to create-or-update a user record always inserts a brand new row,
                        even when a record for that user already exists. Repeated profile edits leave behind
                        a growing trail of duplicate rows for the same logical user.

                        ### Instructions
                        1. Inspect `solution.js`.
                        2. `upsertUser` unconditionally calls `db.insert(...)`.
                        3. Fix it to update the existing record when one is found, and only insert when it's
                           genuinely new.
                        """)
                .logs("""
                        [2026-07-21 08:00:00] WARN 3 duplicate rows found for the same userId after repeated profile edits
                        """)
                .metrics("[]")
                .stackTrace("")
                .baseCode("""
                        {"solution.js":"const users = [];\\nlet nextId = 1;\\n\\nconst db = {\\n  insert: (userId, data) => { users.push({ id: nextId++, userId, ...data }); },\\n  findByUserId: (userId) => users.filter((u) => u.userId === userId),\\n};\\n\\nfunction upsertUser(userId, data) {\\n  db.insert(userId, data);\\n}\\n\\nmodule.exports = { upsertUser, db };\\n"}
                        """)
                .hiddenTests("""
                        const assert = require('assert');
                        const { upsertUser, db } = require('./solution.js');

                        module.exports = [
                          {
                            name: "should create a new user on first upsert",
                            fn: () => {
                              upsertUser('u1', { name: 'Alice' });
                              assert.strictEqual(db.findByUserId('u1').length, 1);
                            }
                          },
                          {
                            name: "should update the existing record instead of creating a duplicate",
                            fn: () => {
                              upsertUser('u2', { name: 'Bob' });
                              upsertUser('u2', { name: 'Bobby' });
                              const records = db.findByUserId('u2');
                              assert.strictEqual(records.length, 1, `Expected exactly 1 record for u2, found ${records.length} — upsertUser is always inserting instead of updating an existing record.`);
                              assert.strictEqual(records[0].name, 'Bobby');
                            }
                          }
                        ];
                        """)
                .referenceFix("""
                        {"solution.js":"const users = [];\\nlet nextId = 1;\\n\\nconst db = {\\n  insert: (userId, data) => { users.push({ id: nextId++, userId, ...data }); },\\n  findByUserId: (userId) => users.filter((u) => u.userId === userId),\\n};\\n\\nfunction upsertUser(userId, data) {\\n  const existing = db.findByUserId(userId);\\n  if (existing.length > 0) {\\n    Object.assign(existing[0], data);\\n  } else {\\n    db.insert(userId, data);\\n  }\\n}\\n\\nmodule.exports = { upsertUser, db };\\n"}
                        """)
                .hint("Call `db.findByUserId(userId)` first. If a record is already there, `Object.assign` the new data onto it; otherwise fall back to `db.insert(...)`.")
                .build();

        // Incident 55: List endpoint has no pagination
        Incident incident55 = Incident.builder()
                .id("database-missing-pagination")
                .title("Order List Endpoint Returns the Entire Table")
                .difficulty("MEDIUM")
                .category("database")
                .description("""
                        ### Problem Statement
                        The "list orders" function ignores any page/pageSize arguments and always returns
                        every row in the table. As the order history grows, this response gets larger and
                        larger with no bound.

                        ### Instructions
                        1. Inspect `solution.js`.
                        2. `listOrders` returns the full `orders` array regardless of arguments.
                        3. Fix it to accept `{ page, pageSize }` and return only that slice.
                        """)
                .logs("""
                        [2026-07-21 09:00:00] WARN listOrders() returned all 500 rows instead of a 20-row page
                        """)
                .metrics("[]")
                .stackTrace("")
                .baseCode("""
                        {"solution.js":"const orders = Array.from({ length: 500 }, (_, i) => ({ id: i, total: i * 10 }));\\n\\nfunction listOrders(options) {\\n  return orders;\\n}\\n\\nmodule.exports = { listOrders };\\n"}
                        """)
                .hiddenTests("""
                        const assert = require('assert');
                        const { listOrders } = require('./solution.js');

                        module.exports = [
                          {
                            name: "should return a page of orders matching the requested pageSize",
                            fn: () => {
                              const page = listOrders({ page: 1, pageSize: 20 });
                              assert.strictEqual(page.length, 20, `Expected a page of 20 orders, got ${page.length} — listOrders is returning the entire table instead of respecting pagination.`);
                            }
                          },
                          {
                            name: "should return the correct page of orders",
                            fn: () => {
                              const page2 = listOrders({ page: 2, pageSize: 20 });
                              assert.strictEqual(page2[0].id, 20);
                            }
                          }
                        ];
                        """)
                .referenceFix("""
                        {"solution.js":"const orders = Array.from({ length: 500 }, (_, i) => ({ id: i, total: i * 10 }));\\n\\nfunction listOrders(options = {}) {\\n  const page = options.page || 1;\\n  const pageSize = options.pageSize || 20;\\n  const start = (page - 1) * pageSize;\\n  return orders.slice(start, start + pageSize);\\n}\\n\\nmodule.exports = { listOrders };\\n"}
                        """)
                .hint("Compute `start = (page - 1) * pageSize` and return `orders.slice(start, start + pageSize)`, defaulting `page` to 1 and `pageSize` to a reasonable value like 20.")
                .build();

        // Incident 56: Cache not invalidated after write
        Incident incident56 = Incident.builder()
                .id("database-stale-cache-after-write")
                .title("Read-Through Cache Serves Stale Data After a Write")
                .difficulty("MEDIUM")
                .category("database")
                .description("""
                        ### Problem Statement
                        A record is updated successfully in the underlying store, but a separate read-through
                        cache keeps serving the old value indefinitely — the cache is never told the
                        underlying data changed.

                        ### Instructions
                        1. Inspect `solution.js`.
                        2. `db.update` writes to `records` but never touches `cache`.
                        3. Fix it so an update also refreshes the cached value for that key.
                        """)
                .logs("""
                        [2026-07-21 10:00:00] WARN get('r1') returned stale value 'old' immediately after updateRecord('r1', 'new')
                        """)
                .metrics("[]")
                .stackTrace("")
                .baseCode("""
                        {"solution.js":"const records = new Map([['r1', { id: 'r1', value: 'old' }]]);\\nconst cache = new Map();\\n\\nconst db = {\\n  get: (id) => {\\n    if (cache.has(id)) return cache.get(id);\\n    const v = records.get(id);\\n    cache.set(id, v);\\n    return v;\\n  },\\n  update: (id, newValue) => {\\n    records.set(id, newValue);\\n  },\\n};\\n\\nfunction updateRecord(id, newValue) {\\n  db.update(id, newValue);\\n}\\n\\nmodule.exports = { updateRecord, db };\\n"}
                        """)
                .hiddenTests("""
                        const assert = require('assert');
                        const { updateRecord, db } = require('./solution.js');

                        module.exports = [
                          {
                            name: "should return the current value before any update",
                            fn: () => {
                              assert.strictEqual(db.get('r1').value, 'old');
                            }
                          },
                          {
                            name: "should return the updated value after a write, not a stale cached one",
                            fn: () => {
                              db.get('r1');
                              updateRecord('r1', { id: 'r1', value: 'new' });
                              const result = db.get('r1');
                              assert.strictEqual(result.value, 'new', `Expected the updated value 'new', got '${result.value}' — the cache was never invalidated after the write.`);
                            }
                          }
                        ];
                        """)
                .referenceFix("""
                        {"solution.js":"const records = new Map([['r1', { id: 'r1', value: 'old' }]]);\\nconst cache = new Map();\\n\\nconst db = {\\n  get: (id) => {\\n    if (cache.has(id)) return cache.get(id);\\n    const v = records.get(id);\\n    cache.set(id, v);\\n    return v;\\n  },\\n  update: (id, newValue) => {\\n    records.set(id, newValue);\\n    cache.set(id, newValue);\\n  },\\n};\\n\\nfunction updateRecord(id, newValue) {\\n  db.update(id, newValue);\\n}\\n\\nmodule.exports = { updateRecord, db };\\n"}
                        """)
                .hint("In `db.update`, after writing to `records`, also call `cache.set(id, newValue)` so the cache reflects the write immediately.")
                .build();

        // Incident 57: Deleting a user orphans their posts
        Incident incident57 = Incident.builder()
                .id("database-orphaned-related-records")
                .title("Deleting a User Leaves Their Posts Orphaned")
                .difficulty("MEDIUM")
                .category("database")
                .description("""
                        ### Problem Statement
                        Deleting a user account removes the user row but leaves every post they ever wrote
                        still referencing that now-nonexistent `userId` — dangling foreign-key references
                        that break anything joining posts back to their author.

                        ### Instructions
                        1. Inspect `solution.js`.
                        2. `deleteUserAccount` only calls `db.deleteUser(userId)`.
                        3. Fix it to also remove that user's posts (`db.deletePostsByUser` is already
                           available on `db`).
                        """)
                .logs("""
                        [2026-07-21 11:00:00] WARN 2 posts still reference deleted userId 1 after account deletion
                        """)
                .metrics("[]")
                .stackTrace("")
                .baseCode("""
                        {"solution.js":"let users = [{ id: 1, name: 'Alice' }, { id: 2, name: 'Bob' }];\\nlet posts = [\\n  { id: 101, userId: 1, title: 'Hi' },\\n  { id: 102, userId: 1, title: 'Yo' },\\n  { id: 103, userId: 2, title: 'Hey' },\\n];\\n\\nconst db = {\\n  deleteUser: (userId) => { users = users.filter((u) => u.id !== userId); },\\n  deletePostsByUser: (userId) => { posts = posts.filter((p) => p.userId !== userId); },\\n  getAllPosts: () => posts,\\n};\\n\\nfunction deleteUserAccount(userId) {\\n  db.deleteUser(userId);\\n}\\n\\nmodule.exports = { deleteUserAccount, db };\\n"}
                        """)
                .hiddenTests("""
                        const assert = require('assert');
                        const { deleteUserAccount, db } = require('./solution.js');

                        module.exports = [
                          {
                            name: "should remove the user's posts along with the account",
                            fn: () => {
                              deleteUserAccount(1);
                              const orphaned = db.getAllPosts().filter((p) => p.userId === 1);
                              assert.strictEqual(orphaned.length, 0, `Found ${orphaned.length} posts still referencing deleted user 1 — deleting a user leaves orphaned related records behind.`);
                            }
                          },
                          {
                            name: "should leave other users' posts untouched",
                            fn: () => {
                              const remaining = db.getAllPosts().filter((p) => p.userId === 2);
                              assert.strictEqual(remaining.length, 1);
                            }
                          }
                        ];
                        """)
                .referenceFix("""
                        {"solution.js":"let users = [{ id: 1, name: 'Alice' }, { id: 2, name: 'Bob' }];\\nlet posts = [\\n  { id: 101, userId: 1, title: 'Hi' },\\n  { id: 102, userId: 1, title: 'Yo' },\\n  { id: 103, userId: 2, title: 'Hey' },\\n];\\n\\nconst db = {\\n  deleteUser: (userId) => { users = users.filter((u) => u.id !== userId); },\\n  deletePostsByUser: (userId) => { posts = posts.filter((p) => p.userId !== userId); },\\n  getAllPosts: () => posts,\\n};\\n\\nfunction deleteUserAccount(userId) {\\n  db.deleteUser(userId);\\n  db.deletePostsByUser(userId);\\n}\\n\\nmodule.exports = { deleteUserAccount, db };\\n"}
                        """)
                .hint("Call `db.deletePostsByUser(userId)` right after `db.deleteUser(userId)` inside `deleteUserAccount`.")
                .build();

        // Incident 58: Count query scans the whole table
        Incident incident58 = Incident.builder()
                .id("database-count-query-inefficiency")
                .title("Total Count Fetches the Whole Table Just to Measure It")
                .difficulty("EASY")
                .category("database")
                .description("""
                        ### Problem Statement
                        A "total records" counter fetches every single row from the table just to read
                        `.length` off the result, instead of using the store's own indexed count operation.

                        ### Instructions
                        1. Inspect `solution.js`.
                        2. `getTotalCount` calls `db.getAll().length`.
                        3. Fix it to call the already-available `db.count()` instead.
                        """)
                .logs("""
                        [2026-07-21 12:00:00] WARN getTotalCount() scanned all 5000 records just to report a count
                        """)
                .metrics("[]")
                .stackTrace("")
                .baseCode("""
                        {"solution.js":"const records = Array.from({ length: 5000 }, (_, i) => ({ id: i }));\\nlet scans = 0;\\n\\nconst db = {\\n  getAll: () => { scans += records.length; return records; },\\n  count: () => { scans += 1; return records.length; },\\n  _getScans: () => scans,\\n  _resetScans: () => { scans = 0; },\\n};\\n\\nfunction getTotalCount() {\\n  return db.getAll().length;\\n}\\n\\nmodule.exports = { getTotalCount, db };\\n"}
                        """)
                .hiddenTests("""
                        const assert = require('assert');
                        const { getTotalCount, db } = require('./solution.js');

                        module.exports = [
                          {
                            name: "should return the correct total count",
                            fn: () => {
                              assert.strictEqual(getTotalCount(), 5000);
                            }
                          },
                          {
                            name: "should use an indexed count instead of scanning the whole table",
                            fn: () => {
                              db._resetScans();
                              getTotalCount();
                              const scans = db._getScans();
                              assert.ok(scans <= 5, `getTotalCount() scanned ${scans} records just to count them — use the indexed db.count() instead of db.getAll().length.`);
                            }
                          }
                        ];
                        """)
                .referenceFix("""
                        {"solution.js":"const records = Array.from({ length: 5000 }, (_, i) => ({ id: i }));\\nlet scans = 0;\\n\\nconst db = {\\n  getAll: () => { scans += records.length; return records; },\\n  count: () => { scans += 1; return records.length; },\\n  _getScans: () => scans,\\n  _resetScans: () => { scans = 0; },\\n};\\n\\nfunction getTotalCount() {\\n  return db.count();\\n}\\n\\nmodule.exports = { getTotalCount, db };\\n"}
                        """)
                .hint("`db.count()` already exists and returns the count without a full scan — use it instead of `db.getAll().length`.")
                .build();

        // Incident 59: JSON column never parsed back on read
        Incident incident59 = Incident.builder()
                .id("database-json-column-serialization-bug")
                .title("Stored JSON Value Read Back as a Raw String")
                .difficulty("MEDIUM")
                .category("database")
                .description("""
                        ### Problem Statement
                        Values are serialized with `JSON.stringify` before being stored, but reading them
                        back returns the raw string instead of parsing it — every caller expecting an object
                        gets a string and breaks trying to access its properties.

                        ### Instructions
                        1. Inspect `solution.js`.
                        2. `db.get` returns the stored string as-is.
                        3. Fix it to `JSON.parse` the value back into an object before returning it.
                        """)
                .logs("""
                        [2026-07-21 13:00:00] ERROR TypeError: Cannot read properties of undefined (reading 'timeout') — got a string back from db.get()
                        """)
                .metrics("[]")
                .stackTrace("")
                .baseCode("""
                        {"solution.js":"const store = new Map();\\n\\nconst db = {\\n  set: (key, value) => { store.set(key, JSON.stringify(value)); },\\n  get: (key) => {\\n    return store.get(key);\\n  },\\n};\\n\\nmodule.exports = { db };\\n"}
                        """)
                .hiddenTests("""
                        const assert = require('assert');
                        const { db } = require('./solution.js');

                        module.exports = [
                          {
                            name: "should store a value under a key",
                            fn: () => {
                              db.set('a', { x: 1 });
                              assert.ok(db.get('a') !== undefined);
                            }
                          },
                          {
                            name: "should return a real object, not a raw JSON string",
                            fn: () => {
                              db.set('config', { timeout: 5000, retries: 3 });
                              const result = db.get('config');
                              assert.strictEqual(typeof result, 'object', `Expected an object back from db.get(), got ${typeof result} — the stored JSON string is never parsed back into an object.`);
                              assert.strictEqual(result.timeout, 5000);
                            }
                          }
                        ];
                        """)
                .referenceFix("""
                        {"solution.js":"const store = new Map();\\n\\nconst db = {\\n  set: (key, value) => { store.set(key, JSON.stringify(value)); },\\n  get: (key) => {\\n    const raw = store.get(key);\\n    return raw === undefined ? undefined : JSON.parse(raw);\\n  },\\n};\\n\\nmodule.exports = { db };\\n"}
                        """)
                .hint("`db.get` should call `JSON.parse` on the raw stored string before returning it (guard for `undefined` when the key doesn't exist).")
                .build();

        // Incident 60: Composite key collapsed to a partial key
        Incident incident60 = Incident.builder()
                .id("database-composite-key-collision")
                .title("Cart Items for Different Products Overwrite Each Other")
                .difficulty("MEDIUM")
                .category("database")
                .description("""
                        ### Problem Statement
                        A shopping cart stores items keyed only by `userId`, even though it should be keyed
                        by the combination of `userId` and `productId`. Adding a second product to the same
                        user's cart silently overwrites the first one.

                        ### Instructions
                        1. Inspect `solution.js`.
                        2. `setItem`/`getItem` use `userId` alone as the storage key.
                        3. Fix it to key by the composite of `userId` and `productId`.
                        """)
                .logs("""
                        [2026-07-21 14:00:00] WARN Adding product p2 to user u2's cart silently overwrote product p1
                        """)
                .metrics("[]")
                .stackTrace("")
                .baseCode("""
                        {"solution.js":"const cart = new Map();\\n\\nconst db = {\\n  setItem: (userId, productId, qty) => {\\n    cart.set(userId, { productId, qty });\\n  },\\n  getItem: (userId, productId) => {\\n    return cart.get(userId);\\n  },\\n};\\n\\nmodule.exports = { db };\\n"}
                        """)
                .hiddenTests("""
                        const assert = require('assert');
                        const { db } = require('./solution.js');

                        module.exports = [
                          {
                            name: "should store and retrieve a single cart item",
                            fn: () => {
                              db.setItem('u1', 'p1', 3);
                              assert.strictEqual(db.getItem('u1', 'p1').qty, 3);
                            }
                          },
                          {
                            name: "different products for the same user should not overwrite each other",
                            fn: () => {
                              db.setItem('u2', 'p1', 2);
                              db.setItem('u2', 'p2', 5);
                              const item1 = db.getItem('u2', 'p1');
                              const item2 = db.getItem('u2', 'p2');
                              assert.strictEqual(item1 && item1.qty, 2, `Expected product p1's quantity to still be 2, got ${item1 && item1.qty} — storing by userId alone lets different products silently overwrite each other.`);
                              assert.strictEqual(item2 && item2.qty, 5);
                            }
                          }
                        ];
                        """)
                .referenceFix("""
                        {"solution.js":"const cart = new Map();\\n\\nconst db = {\\n  setItem: (userId, productId, qty) => {\\n    cart.set(userId + '::' + productId, { productId, qty });\\n  },\\n  getItem: (userId, productId) => {\\n    return cart.get(userId + '::' + productId);\\n  },\\n};\\n\\nmodule.exports = { db };\\n"}
                        """)
                .hint("Build the storage key from both `userId` and `productId`, e.g. `userId + '::' + productId`, in both `setItem` and `getItem`.")
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
        incidentRepository.save(incident19);
        incidentRepository.save(incident20);
        incidentRepository.save(incident21);
        incidentRepository.save(incident22);
        incidentRepository.save(incident23);
        incidentRepository.save(incident24);
        incidentRepository.save(incident25);
        incidentRepository.save(incident26);
        incidentRepository.save(incident27);
        incidentRepository.save(incident28);
        incidentRepository.save(incident29);
        incidentRepository.save(incident30);
        incidentRepository.save(incident31);
        incidentRepository.save(incident32);
        incidentRepository.save(incident33);
        incidentRepository.save(incident34);
        incidentRepository.save(incident35);
        incidentRepository.save(incident36);
        incidentRepository.save(incident37);
        incidentRepository.save(incident38);
        incidentRepository.save(incident39);
        incidentRepository.save(incident40);
        incidentRepository.save(incident41);
        incidentRepository.save(incident42);
        incidentRepository.save(incident43);
        incidentRepository.save(incident44);
        incidentRepository.save(incident45);
        incidentRepository.save(incident46);
        incidentRepository.save(incident47);
        incidentRepository.save(incident48);
        incidentRepository.save(incident49);
        incidentRepository.save(incident50);
        incidentRepository.save(incident51);
        incidentRepository.save(incident52);
        incidentRepository.save(incident53);
        incidentRepository.save(incident54);
        incidentRepository.save(incident55);
        incidentRepository.save(incident56);
        incidentRepository.save(incident57);
        incidentRepository.save(incident58);
        incidentRepository.save(incident59);
        incidentRepository.save(incident60);
    }
}
