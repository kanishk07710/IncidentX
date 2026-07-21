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
const RETRYABLE_STATUSES = new Set([502, 503, 504]);

export async function apiFetchWithRetry(
  path: string,
  options: RequestInit = {},
  onRetry?: (attempt: number, maxAttempts: number) => void,
  maxAttempts = 8,
  delayMs = 5000
) {
  let lastError: unknown;
  for (let attempt = 1; attempt <= maxAttempts; attempt++) {
    try {
      const res = await apiFetch(path, options);
      if (!RETRYABLE_STATUSES.has(res.status) || attempt === maxAttempts) {
        return res;
      }
    } catch (err) {
      lastError = err;
      if (attempt === maxAttempts) throw lastError;
    }
    onRetry?.(attempt, maxAttempts);
    await new Promise((resolve) => setTimeout(resolve, delayMs));
  }
  throw lastError;
}
