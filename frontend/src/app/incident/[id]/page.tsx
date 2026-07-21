"use client";
import { useEffect, useRef, useState, use } from "react";
import { useRouter } from "next/navigation";
import { apiFetch, apiFetchWithRetry } from "@/lib/api";
import { subscribeToSubmission } from "@/lib/ws";
import styles from "./workspace.module.css";

interface IncidentDetail {
  id: string;
  title: string;
  description: string;
  difficulty: string;
  category: string;
  logs: string;
  metrics: string;
  stackTrace: string;
  baseCode: string;
}

interface SubmissionResult {
  id: number;
  status: string;
  results: string;
  aiFeedback: string;
}

interface HintResponse {
  hint: string;
  pointsDeducted: number;
  newRating: number;
}

type TabId = "description" | "logs" | "metrics" | "stacktrace";

export default function IncidentWorkspace({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const { id } = use(params);
  const [incident, setIncident] = useState<IncidentDetail | null>(null);
  const [code, setCode] = useState<Record<string, string>>({});
  const [activeFile, setActiveFile] = useState<string>("");
  const [activeTab, setActiveTab] = useState<TabId>("description");
  const [submitting, setSubmitting] = useState(false);
  const [result, setResult] = useState<SubmissionResult | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState(false);
  const [wakingUp, setWakingUp] = useState(false);
  const [reloadKey, setReloadKey] = useState(0);
  const [hint, setHint] = useState<HintResponse | null>(null);
  const [hintLoading, setHintLoading] = useState(false);
  const [hintError, setHintError] = useState<string | null>(null);
  const router = useRouter();
  const unsubscribeRef = useRef<(() => void) | null>(null);
  const pollIntervalRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const giveUpTimeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  function stopWatchingSubmission() {
    unsubscribeRef.current?.();
    unsubscribeRef.current = null;
    if (pollIntervalRef.current) {
      clearInterval(pollIntervalRef.current);
      pollIntervalRef.current = null;
    }
    if (giveUpTimeoutRef.current) {
      clearTimeout(giveUpTimeoutRef.current);
      giveUpTimeoutRef.current = null;
    }
  }

  useEffect(() => {
    return () => {
      stopWatchingSubmission();
    };
  }, []);

  useEffect(() => {
    let cancelled = false;

    async function load() {
      setLoading(true);
      setLoadError(false);
      try {
        const res = await apiFetchWithRetry(`/api/incidents/${id}`, {}, () => setWakingUp(true));
        if (cancelled) return;

        if (res.status === 401) {
          router.push("/");
          return;
        }

        // A transient failure (e.g. a brief post-deploy backend blip) used to be swallowed
        // silently and render as "Incident not found" — indistinguishable from the incident
        // genuinely not existing. Only a real 404 means "not found"; anything else is a
        // retryable load failure.
        if (res.status === 404) {
          return;
        }
        if (!res.ok) {
          setLoadError(true);
          return;
        }

        const data: IncidentDetail = await res.json();
        if (cancelled) return;
        setIncident(data);

        // Parse base code files
        try {
          const files = JSON.parse(data.baseCode);
          setCode(files);
          const firstFile = Object.keys(files)[0];
          if (firstFile) setActiveFile(firstFile);
        } catch {
          setCode({ "solution.js": "// Could not load starter code" });
          setActiveFile("solution.js");
        }
      } catch {
        if (!cancelled) setLoadError(true);
      } finally {
        if (!cancelled) {
          setWakingUp(false);
          setLoading(false);
        }
      }
    }
    load();
    return () => {
      cancelled = true;
    };
  }, [id, router, reloadKey]);

  async function handleSubmit() {
    stopWatchingSubmission();
    setSubmitting(true);
    setResult(null);

    try {
      const res = await apiFetch("/api/submissions", {
        method: "POST",
        body: JSON.stringify({
          incidentId: id,
          submittedCode: JSON.stringify(code),
        }),
      });

      if (res.ok) {
        // The backend grades in the background and can finish before the WebSocket below even
        // finishes connecting, in which case the push is missed (no message replay for late
        // subscribers) — so a poll of GET /api/submissions/:id runs alongside it as a fallback,
        // and a hard timeout guarantees this never spins forever if both somehow stall.
        const data: SubmissionResult = await res.json();
        setResult(data);

        function settle(update: SubmissionResult) {
          setResult(update);
          setSubmitting(false);
          stopWatchingSubmission();
        }

        unsubscribeRef.current = subscribeToSubmission(data.id, (update) => {
          if (update.status !== "PENDING") settle(update);
        });

        pollIntervalRef.current = setInterval(async () => {
          try {
            const pollRes = await apiFetch(`/api/submissions/${data.id}`);
            if (pollRes.ok) {
              const polled: SubmissionResult = await pollRes.json();
              if (polled.status !== "PENDING") settle(polled);
            }
          } catch {
            // network hiccup — next tick retries
          }
        }, 3000);

        giveUpTimeoutRef.current = setTimeout(() => {
          settle({
            id: data.id,
            status: "ERROR",
            results: '{"error": "Grading is taking longer than expected. Please refresh and check the submission history, or try again."}',
            aiFeedback: "",
          });
        }, 60000);
      } else {
        setResult({
          id: 0,
          status: "ERROR",
          results: '{"error": "Submission failed. Check authentication."}',
          aiFeedback: "Could not process submission.",
        });
        setSubmitting(false);
      }
    } catch {
      setResult({
        id: 0,
        status: "ERROR",
        results: '{"error": "Network error"}',
        aiFeedback: "Could not connect to server.",
      });
      setSubmitting(false);
    }
  }

  async function handleGetHint() {
    if (hint || hintLoading) return;
    setHintLoading(true);
    setHintError(null);
    try {
      const res = await apiFetch(`/api/incidents/${id}/hint`, { method: "POST" });
      if (res.status === 401) {
        router.push("/");
        return;
      }
      if (res.ok) {
        const data: HintResponse = await res.json();
        setHint(data);
      } else {
        setHintError("Couldn't fetch a hint right now — try again in a moment.");
      }
    } catch {
      setHintError("Couldn't reach the server for a hint.");
    } finally {
      setHintLoading(false);
    }
  }

  function parseMetrics(): Array<{ timestamp: string; cpu: number; memory: number }> {
    try {
      return incident?.metrics ? JSON.parse(incident.metrics) : [];
    } catch {
      return [];
    }
  }

  function parseTestResults(): Array<{ name: string; passed: boolean; message?: string }> {
    if (!result?.results) return [];
    try {
      const parsed = JSON.parse(result.results);
      return parsed.tests || [];
    } catch {
      return [];
    }
  }

  function parseSandboxError(): string | null {
    if (!result?.results) return null;
    try {
      const parsed = JSON.parse(result.results);
      return parsed.error || null;
    } catch {
      return null;
    }
  }

  if (loading) {
    return (
      <main className={styles.main}>
        <div className={styles.loadingContainer}>
          <div className="spinner" />
          <p>
            {wakingUp
              ? "Waking up the server — this can take up to a minute after a period of inactivity…"
              : "Loading incident..."}
          </p>
        </div>
      </main>
    );
  }

  if (loadError) {
    return (
      <main className={styles.main}>
        <div className={styles.loadingContainer}>
          <p>Couldn&rsquo;t load this incident.</p>
          <button className="btn btn-primary" onClick={() => setReloadKey((k) => k + 1)}>
            Try Again
          </button>
        </div>
      </main>
    );
  }

  if (!incident) {
    return (
      <main className={styles.main}>
        <div className={styles.loadingContainer}>
          <p>Incident not found.</p>
          <button className="btn btn-secondary" onClick={() => router.push("/dashboard")}>
            ← Back to Dashboard
          </button>
        </div>
      </main>
    );
  }

  const metrics = parseMetrics();
  const tests = parseTestResults();
  const sandboxError = parseSandboxError();
  const maxMem = Math.max(...metrics.map((m) => m.memory), 1);

  return (
    <main className={styles.main}>
      {/* Header */}
      <header className={styles.header}>
        <div className={styles.headerLeft}>
          <button className="btn btn-ghost" onClick={() => router.push("/dashboard")}>
            ← Back
          </button>
          <div className={styles.headerTitle}>
            <h1 className={styles.incidentTitle}>{incident.title}</h1>
            <div className={styles.headerMeta}>
              <span className={`badge badge-${incident.difficulty.toLowerCase()}`}>
                {incident.difficulty}
              </span>
              <span className={styles.categoryTag}>{incident.category}</span>
            </div>
          </div>
        </div>
        <div className={styles.headerRight}>
          <button
            className="btn btn-secondary"
            onClick={handleGetHint}
            disabled={hintLoading || !!hint}
            title="Reveals a nudge, costs rating points"
          >
            {hintLoading ? (
              <>
                <span className="spinner" style={{ width: 16, height: 16 }} />
                Thinking...
              </>
            ) : hint ? (
              <>💡 Hint Revealed</>
            ) : (
              <>💡 Get Hint (-10 rating)</>
            )}
          </button>
          <button
            className={`btn btn-primary ${styles.submitBtn}`}
            onClick={handleSubmit}
            disabled={submitting}
          >
            {submitting ? (
              <>
                <span className="spinner" style={{ width: 16, height: 16 }} />
                Running in Sandbox...
              </>
            ) : (
              <>🚀 Submit Fix</>
            )}
          </button>
        </div>
      </header>

      {/* Workspace grid */}
      <div className={styles.workspace}>
        {/* Left panel: Investigation */}
        <div className={styles.leftPanel}>
          <div className={`glass-card ${styles.panel}`}>
            <div className="tabs">
              {(
                [
                  ["description", "📋 Brief"],
                  ["logs", "📝 Logs"],
                  ["metrics", "📊 Metrics"],
                  ["stacktrace", "💥 Stack Trace"],
                ] as [TabId, string][]
              ).map(([tabId, label]) => (
                <button
                  key={tabId}
                  className={`tab ${activeTab === tabId ? "active" : ""}`}
                  onClick={() => setActiveTab(tabId)}
                >
                  {label}
                </button>
              ))}
            </div>

            <div className={styles.panelContent}>
              {hint && (
                <div className={`${styles.hintBanner} animate-in`}>
                  <span className={styles.hintIcon}>💡</span>
                  <span>{hint.hint}</span>
                </div>
              )}

              {hintError && (
                <div className={`${styles.hintBanner} ${styles.hintBannerError} animate-in`}>
                  <span className={styles.hintIcon}>⚠️</span>
                  <span>{hintError}</span>
                </div>
              )}

              {activeTab === "description" && (
                <div className={styles.descriptionContent}>
                  <div
                    dangerouslySetInnerHTML={{
                      __html: incident.description
                        .replace(
                          /### (.*)/g,
                          '<h3 class="' + styles.descH3 + '">$1</h3>'
                        )
                        .replace(/\n/g, "<br/>"),
                    }}
                  />
                </div>
              )}

              {activeTab === "logs" && (
                <pre className="code-block">{incident.logs || "No logs available."}</pre>
              )}

              {activeTab === "metrics" && (
                <div className={styles.metricsView}>
                  {metrics.length > 0 ? (
                    <div className={styles.metricsChart}>
                      <div className={styles.chartHeader}>
                        <span className={styles.chartLabel}>Memory (MB)</span>
                        <span className={styles.chartLabel}>Over Time</span>
                      </div>
                      <div className={styles.barChart}>
                        {metrics.map((m, i) => (
                          <div key={i} className={styles.barGroup}>
                            <div
                              className={styles.bar}
                              style={{
                                height: `${(m.memory / maxMem) * 100}%`,
                                background:
                                  m.memory > 200
                                    ? "var(--status-error)"
                                    : m.memory > 100
                                    ? "var(--status-warning)"
                                    : "var(--accent-cyan)",
                              }}
                            >
                              <span className={styles.barValue}>{m.memory}</span>
                            </div>
                            <span className={styles.barLabel}>{m.timestamp}</span>
                          </div>
                        ))}
                      </div>
                      <table className={styles.metricsTable}>
                        <thead>
                          <tr>
                            <th>Time</th>
                            <th>CPU %</th>
                            <th>Memory MB</th>
                          </tr>
                        </thead>
                        <tbody>
                          {metrics.map((m, i) => (
                            <tr key={i}>
                              <td>{m.timestamp}</td>
                              <td>{m.cpu}%</td>
                              <td>{m.memory}</td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    </div>
                  ) : (
                    <p className={styles.emptyText}>No metrics available.</p>
                  )}
                </div>
              )}

              {activeTab === "stacktrace" && (
                <pre className="code-block">
                  {incident.stackTrace || "No stack trace available."}
                </pre>
              )}
            </div>
          </div>
        </div>

        {/* Right panel: Code + Results */}
        <div className={styles.rightPanel}>
          {/* Code editor */}
          <div className={`glass-card ${styles.panel}`}>
            <div className={styles.editorHeader}>
              <div className={styles.fileTabs}>
                {Object.keys(code).map((filename) => (
                  <button
                    key={filename}
                    className={`${styles.fileTab} ${activeFile === filename ? styles.fileTabActive : ""}`}
                    onClick={() => setActiveFile(filename)}
                  >
                    <span className={styles.fileIcon}>📄</span>
                    {filename}
                  </button>
                ))}
              </div>
            </div>
            <textarea
              className="code-editor"
              value={code[activeFile] || ""}
              onChange={(e) =>
                setCode((prev) => ({ ...prev, [activeFile]: e.target.value }))
              }
              spellCheck={false}
            />
          </div>

          {/* Results panel */}
          {result && (
            <div className={`glass-card ${styles.panel} ${styles.resultsPanel} animate-in`}>
              <div className={styles.resultsHeader}>
                <div
                  className={`${styles.statusBadge} ${
                    result.status === "PASSED"
                      ? styles.statusPassed
                      : result.status === "TIMEOUT"
                      ? styles.statusTimeout
                      : result.status === "ERROR"
                      ? styles.statusError
                      : result.status === "PENDING"
                      ? styles.statusPending
                      : styles.statusFailed
                  }`}
                >
                  {result.status === "PASSED"
                    ? "✅ ALL TESTS PASSED"
                    : result.status === "TIMEOUT"
                    ? "⏱️ EXECUTION TIMED OUT"
                    : result.status === "ERROR"
                    ? "⚠️ SANDBOX ERROR — NOT A WRONG-ANSWER VERDICT"
                    : result.status === "PENDING"
                    ? "🧪 GRADING IN SANDBOX…"
                    : "❌ TESTS FAILED"}
                </div>
              </div>

              {/* Sandbox/infra error — distinct from a wrong-answer verdict */}
              {result.status === "ERROR" && (
                <div className={styles.sandboxErrorBox}>
                  <p>
                    Your code wasn&rsquo;t graded — the sandbox failed to run it. This
                    is a system-side issue, not feedback on your fix. Try submitting
                    again.
                  </p>
                  {sandboxError && (
                    <pre className={styles.sandboxErrorDetail}>{sandboxError}</pre>
                  )}
                </div>
              )}

              {/* Individual test results */}
              {tests.length > 0 && (
                <div className={`${styles.testResults} stagger`}>
                  {tests.map((t, i) => (
                    <div
                      key={i}
                      className={`${styles.testItem} ${
                        t.passed ? styles.testPassed : styles.testFailed
                      }`}
                    >
                      <span className={styles.testIcon}>
                        {t.passed ? "✅" : "❌"}
                      </span>
                      <div className={styles.testInfo}>
                        <span className={styles.testName}>{t.name}</span>
                        {t.message && (
                          <span className={styles.testMessage}>{t.message}</span>
                        )}
                      </div>
                    </div>
                  ))}
                </div>
              )}

              {/* AI Mentor feedback */}
              {result.aiFeedback && (
                <div className={styles.aiFeedback}>
                  <div className={styles.aiHeader}>
                    <span>🤖</span>
                    <span>AI Mentor</span>
                  </div>
                  <div
                    className={styles.aiContent}
                    dangerouslySetInnerHTML={{
                      __html: result.aiFeedback
                        .replace(
                          /### (.*)/g,
                          '<h3 class="' + styles.aiH3 + '">$1</h3>'
                        )
                        .replace(
                          /\*\*(.*?)\*\*/g,
                          "<strong>$1</strong>"
                        )
                        .replace(
                          /\*(.*?)\*/g,
                          "<em>$1</em>"
                        )
                        .replace(/\n/g, "<br/>"),
                    }}
                  />
                </div>
              )}
            </div>
          )}
        </div>
      </div>
    </main>
  );
}
