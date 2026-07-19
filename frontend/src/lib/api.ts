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

// Free-tier hosts (e.g. Render) spin the backend down after inactivity and take 30-60s to wake
// on the next request, so the very first fetch after a lull throws a plain network error rather
// than returning a real response. Without a retry, page loads silently render with no data and
// the user has to manually refresh a few times until the backend has finished waking up. This
// only retries network-level failures (the fetch throwing) — HTTP error statuses like 401 are
// real answers from an awake server and are returned as-is for the caller to handle. Only use
// this for idempotent GETs on initial page load, never for POSTs like submissions or logout.
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
      return await apiFetch(path, options);
    } catch (err) {
      lastError = err;
      if (attempt < maxAttempts) {
        onRetry?.(attempt, maxAttempts);
        await new Promise((resolve) => setTimeout(resolve, delayMs));
      }
    }
  }
  throw lastError;
}
