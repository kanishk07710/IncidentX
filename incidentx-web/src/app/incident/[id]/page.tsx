"use client";
import { useEffect, useState, use } from "react";
import { useRouter } from "next/navigation";
import { apiFetch } from "@/lib/api";
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
  const [hint, setHint] = useState<HintResponse | null>(null);
  const [hintLoading, setHintLoading] = useState(false);
  const router = useRouter();

  useEffect(() => {
    async function load() {
      try {
        const res = await apiFetch(`/api/incidents/${id}`);
        if (res.status === 401) {
          router.push("/");
          return;
        }
        if (res.ok) {
          const data: IncidentDetail = await res.json();
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
        }
      } catch {
        // ignore
      } finally {
        setLoading(false);
      }
    }
    load();
  }, [id, router]);

  async function handleSubmit() {
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
        const data: SubmissionResult = await res.json();
        setResult(data);
      } else {
        setResult({
          id: 0,
          status: "ERROR",
          results: '{"error": "Submission failed. Check authentication."}',
          aiFeedback: "Could not process submission.",
        });
      }
    } catch {
      setResult({
        id: 0,
        status: "ERROR",
        results: '{"error": "Network error"}',
        aiFeedback: "Could not connect to server.",
      });
    } finally {
      setSubmitting(false);
    }
  }

  async function handleGetHint() {
    if (hint || hintLoading) return;
    setHintLoading(true);
    try {
      const res = await apiFetch(`/api/incidents/${id}/hint`, { method: "POST" });
      if (res.ok) {
        const data: HintResponse = await res.json();
        setHint(data);
      }
    } catch {
      // ignore — hint is a nice-to-have, not the grading path
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

  if (loading) {
    return (
      <main className={styles.main}>
        <div className={styles.loadingContainer}>
          <div className="spinner" />
          <p>Loading incident...</p>
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
                <div className={styles.hintBanner}>
                  <span className={styles.hintIcon}>💡</span>
                  <span>{hint.hint}</span>
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
                      : styles.statusFailed
                  }`}
                >
                  {result.status === "PASSED"
                    ? "✅ ALL TESTS PASSED"
                    : result.status === "TIMEOUT"
                    ? "⏱️ EXECUTION TIMED OUT"
                    : "❌ TESTS FAILED"}
                </div>
              </div>

              {/* Individual test results */}
              {tests.length > 0 && (
                <div className={styles.testResults}>
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
