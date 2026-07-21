export const API_BASE = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080";

export async function apiFetch(path: string, options: RequestInit = {}) {
  const res = await fetch(`${API_BASE}${path}`, {
    credentials: "include",
    headers: {
      "Content-Type": "application/json",
      ...options.headers,
    },
    ...options,
  });
  return res;
}

// Render's edge proxy can return a 502/503/504 for a request that arrives while the origin is
// still cold-starting — the TCP/TLS handshake succeeds so fetch() resolves normally (it does not
// throw), it just carries a gateway-error status. A retry loop that only reacts to fetch()
// throwing treats that response as final, so one of several parallel requests silently "loses"
// while its siblings succeed a few hundred ms later once the origin is warm — which is exactly
// what produced the dashboard's inconsistent user/progress state (see dashboard/page.tsx). Retry
// on both: the fetch throwing (DNS/connection failure) and these specific gateway statuses. Real
// answers from an awake server (200, 401, 404, ...) are returned as-is for the caller to handle.
// Only use this for idempotent GETs on initial page load, never for POSTs like submissions or logout.
const GATEWAY_RETRYABLE_STATUSES = new Set([502, 503, 504]);

// A second, separately-diagnosed failure mode: production's database (Supabase, on a direct,
// non-pooled connection) intermittently rejects a fraction of connection attempts under load,
// which the backend can only surface as a generic 500 (there's no specific status for "the DB
// connection pool couldn't get a connection this time"). Confirmed by direct repeated testing
// against production that this is fast (~1s, not a timeout) and resolves on the very next
// request — the real fix is switching the backend to Supabase's connection pooler, but until
// that's done, a short retry here measurably reduces how often a real user hits it. Deliberately
// a much shorter delay than the cold-start case above: this condition clears in under a second
// in practice, not tens of seconds, and a persistent 500 (an actual bug, not this known
// condition) still surfaces to the caller once maxAttempts is exhausted.
const TRANSIENT_DB_ERROR_STATUS = 500;
const TRANSIENT_DB_RETRY_DELAY_MS = 400;

export async function apiFetchWithRetry(
  path: string,
  options: RequestInit = {},
  onRetry?: (attempt: number, maxAttempts: number) => void,
  maxAttempts = 8,
  delayMs = 5000
) {
  let lastError: unknown;
  for (let attempt = 1; attempt <= maxAttempts; attempt++) {
    let wait = delayMs;
    try {
      const res = await apiFetch(path, options);
      if (res.status === TRANSIENT_DB_ERROR_STATUS) {
        wait = TRANSIENT_DB_RETRY_DELAY_MS;
      } else if (!GATEWAY_RETRYABLE_STATUSES.has(res.status)) {
        return res;
      }
      if (attempt === maxAttempts) return res;
    } catch (err) {
      lastError = err;
      if (attempt === maxAttempts) throw lastError;
    }
    onRetry?.(attempt, maxAttempts);
    await new Promise((resolve) => setTimeout(resolve, wait));
  }
  throw lastError;
}
