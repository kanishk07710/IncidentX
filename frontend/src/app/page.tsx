"use client";
import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { apiFetch, API_BASE } from "@/lib/api";
import styles from "./page.module.css";

const DEMO_HANDLE = "debug_master_42";
const STORAGE_KEY = "incidentx_last_handle";

export default function LoginPage() {
  const [username, setUsername] = useState("");
  const [securityKey, setSecurityKey] = useState("");
  const [showKey, setShowKey] = useState(false);
  const [keyFocused, setKeyFocused] = useState(false);
  const [keyHovered, setKeyHovered] = useState(false);
  const [capsLockOn, setCapsLockOn] = useState(false);
  const [remember, setRemember] = useState(true);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [shake, setShake] = useState(false);
  const router = useRouter();

  // Prefill last-used handle, and silently redirect if a session already exists.
  useEffect(() => {
    let cancelled = false;

    async function init() {
      const saved = window.localStorage.getItem(STORAGE_KEY);
      if (saved && !cancelled) setUsername(saved);

      try {
        const res = await apiFetch("/api/auth/me");
        if (res.ok && !cancelled) router.replace("/dashboard");
      } catch {
        // backend unreachable or not logged in — stay on the login screen
      }
    }

    init();
    return () => {
      cancelled = true;
    };
  }, [router]);

  function triggerShake() {
    setShake(true);
    setTimeout(() => setShake(false), 400);
  }

  function fillDemoHandle() {
    setUsername(DEMO_HANDLE);
    setError("");
  }

  function trackCapsLock(e: React.KeyboardEvent<HTMLInputElement>) {
    if (e.getModifierState) setCapsLockOn(e.getModifierState("CapsLock"));
  }

  async function handleMockLogin(e: React.FormEvent) {
    e.preventDefault();
    const handle = username.trim();

    if (!handle) {
      setError("Enter a developer handle to continue.");
      triggerShake();
      return;
    }
    if (handle.length < 2) {
      setError("Handle must be at least 2 characters.");
      triggerShake();
      return;
    }

    setLoading(true);
    setError("");

    try {
      const res = await apiFetch("/api/auth/mock-login", {
        method: "POST",
        body: JSON.stringify({ username: handle }),
      });

      if (res.ok) {
        if (remember) {
          window.localStorage.setItem(STORAGE_KEY, handle);
        } else {
          window.localStorage.removeItem(STORAGE_KEY);
        }
        router.push("/dashboard");
      } else {
        setError("Login failed. Please try again.");
        triggerShake();
      }
    } catch {
      setError("Could not connect to the server.");
      triggerShake();
    } finally {
      setLoading(false);
    }
  }

  const showPeek = (keyHovered || keyFocused) && securityKey.length > 0 && !showKey;

  return (
    <main className={styles.shell}>
      {/* Left — brand / visual panel */}
      <section className={styles.visualPanel}>
        <div className={styles.orbA} />
        <div className={styles.orbB} />
        <div className={styles.orbC} />
        <div className={styles.starfield} />

        <div className={styles.visualContent}>
          <div className={styles.brandRow}>
            <span className={styles.logoMark}>⚡</span>
            <span className={styles.brandName}>
              Incident<span className={styles.brandAccent}>X</span>
            </span>
          </div>

          <h1 className={styles.headline}>
            Debug. Compete.
            <br />
            Prove It.
          </h1>
          <p className={styles.subHeadline}>
            Diagnose and fix realistic production incidents inside isolated
            Docker sandboxes. Graded deterministically. No AI in the scoring
            path.
          </p>

          <ul className={styles.featureList}>
            <li>
              <span className={styles.featureIcon}>🔒</span> Docker Sandboxed
            </li>
            <li>
              <span className={styles.featureIcon}>🧪</span> Deterministic
              Grading
            </li>
            <li>
              <span className={styles.featureIcon}>🤖</span> AI Mentor
            </li>
            <li>
              <span className={styles.featureIcon}>📊</span> ELO Rating
            </li>
          </ul>

          <div className={styles.statusRow}>
            <span className={styles.statusDot} />
            Systems Online
          </div>
        </div>
      </section>

      {/* Right — login form panel */}
      <section className={styles.formPanel}>
        <div
          className={`glass-card ${styles.loginCard} ${shake ? styles.shake : ""}`}
        >
          <h2 className={styles.cardTitle}>Enter the Arena</h2>
          <p className={styles.cardSubtitle}>Sign in to start debugging</p>

          <div className={styles.demoHint}>
            <span className={styles.demoHintIcon}>🎮</span>
            <div className={styles.demoHintText}>
              <strong>Practice Mode</strong> — no password required, any
              handle works.
            </div>
            <button
              type="button"
              className={styles.demoHintBtn}
              onClick={fillDemoHandle}
            >
              Try &ldquo;{DEMO_HANDLE}&rdquo;
            </button>
          </div>

          <form onSubmit={handleMockLogin} className={styles.form} noValidate>
            <div className={styles.inputGroup}>
              <label htmlFor="username" className={styles.label}>
                Developer Handle
              </label>
              <div className={styles.inputWrap}>
                <span className={styles.inputIcon}>👤</span>
                <input
                  id="username"
                  type="text"
                  value={username}
                  onChange={(e) => {
                    setUsername(e.target.value);
                    setError("");
                  }}
                  placeholder="e.g. debug_master_42"
                  className={styles.input}
                  autoFocus
                  autoComplete="username"
                  maxLength={32}
                />
              </div>
            </div>

            <div className={styles.inputGroup}>
              <div className={styles.labelRow}>
                <label htmlFor="securityKey" className={styles.label}>
                  Security Key
                </label>
                <span className={styles.optionalTag}>
                  optional · practice mode
                </span>
              </div>
              <div
                className={styles.inputWrap}
                onMouseEnter={() => setKeyHovered(true)}
                onMouseLeave={() => setKeyHovered(false)}
              >
                <span className={styles.inputIcon}>🔒</span>
                <input
                  id="securityKey"
                  type={showKey ? "text" : "password"}
                  value={securityKey}
                  onChange={(e) => setSecurityKey(e.target.value)}
                  onFocus={() => setKeyFocused(true)}
                  onBlur={() => setKeyFocused(false)}
                  onKeyDown={trackCapsLock}
                  onKeyUp={trackCapsLock}
                  placeholder="not required for practice mode"
                  className={styles.input}
                  autoComplete="off"
                />
                <button
                  type="button"
                  className={styles.eyeBtn}
                  onClick={() => setShowKey((s) => !s)}
                  aria-label={showKey ? "Hide security key" : "Show security key"}
                  aria-pressed={showKey}
                  tabIndex={-1}
                >
                  {showKey ? "🙈" : "👁️"}
                </button>

                {showPeek && (
                  <div className={styles.peekTooltip} aria-hidden="true">
                    {securityKey}
                  </div>
                )}
              </div>
              {capsLockOn && keyFocused && (
                <p className={styles.capsWarning}>⚠ Caps Lock is on</p>
              )}
            </div>

            <label className={styles.rememberRow}>
              <input
                type="checkbox"
                checked={remember}
                onChange={(e) => setRemember(e.target.checked)}
              />
              Remember my handle on this device
            </label>

            {error && (
              <p className={styles.error} role="alert">
                {error}
              </p>
            )}

            <button
              type="submit"
              className={`btn btn-primary ${styles.loginBtn}`}
              disabled={loading || !username.trim()}
            >
              {loading ? (
                <>
                  <span className="spinner" style={{ width: 16, height: 16 }} />
                  Connecting...
                </>
              ) : (
                <>🚀 Launch Practice Mode</>
              )}
            </button>
          </form>

          <div className={styles.divider}>
            <span>or connect with</span>
          </div>

          <div className={styles.oauthButtons}>
            <a
              href={`${API_BASE}/oauth2/authorization/github`}
              className={`btn btn-secondary ${styles.oauthBtn}`}
            >
              <svg width="18" height="18" viewBox="0 0 24 24" fill="currentColor">
                <path d="M12 0c-6.626 0-12 5.373-12 12 0 5.302 3.438 9.8 8.207 11.387.599.111.793-.261.793-.577v-2.234c-3.338.726-4.033-1.416-4.033-1.416-.546-1.387-1.333-1.756-1.333-1.756-1.089-.745.083-.729.083-.729 1.205.084 1.839 1.237 1.839 1.237 1.07 1.834 2.807 1.304 3.492.997.107-.775.418-1.305.762-1.604-2.665-.305-5.467-1.334-5.467-5.931 0-1.311.469-2.381 1.236-3.221-.124-.303-.535-1.524.117-3.176 0 0 1.008-.322 3.301 1.23.957-.266 1.983-.399 3.003-.404 1.02.005 2.047.138 3.006.404 2.291-1.552 3.297-1.23 3.297-1.23.653 1.653.242 2.874.118 3.176.77.84 1.235 1.911 1.235 3.221 0 4.609-2.807 5.624-5.479 5.921.43.372.823 1.102.823 2.222v3.293c0 .319.192.694.801.576 4.765-1.589 8.199-6.086 8.199-11.386 0-6.627-5.373-12-12-12z" />
              </svg>
              GitHub
            </a>
            <a
              href={`${API_BASE}/oauth2/authorization/google`}
              className={`btn btn-secondary ${styles.oauthBtn}`}
            >
              <svg width="18" height="18" viewBox="0 0 24 24" fill="currentColor">
                <path d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92a5.06 5.06 0 0 1-2.2 3.32v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.1z" />
                <path d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z" />
                <path d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.07H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.93l2.85-2.22.81-.62z" />
                <path d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.07l3.66 2.84c.87-2.6 3.3-4.53 6.16-4.53z" />
              </svg>
              Google
            </a>
          </div>

          <p className={styles.fineprint}>
            This is a practice sandbox — mock logins don&rsquo;t require a real
            password and no credentials are stored beyond your chosen handle.
          </p>
        </div>
      </section>
    </main>
  );
}
