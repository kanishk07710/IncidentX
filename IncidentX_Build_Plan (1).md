# IncidentX — Build Plan

**Sequencing principle:** de-risk the hardest, most product-defining piece first (the sandbox). Everything else is comparatively conventional CRUD/webapp work. Build order below assumes no hard deadline — if you get one, tell me and I'll compress it.

---

## Phase 0 — Foundations (½–1 day)
- Repo scaffold: monorepo or split repos (backend `incidentx-api`, frontend `incidentx-web`).
- Spring Boot 3 project init (Java 21), Postgres running locally via Docker Compose.
- Basic health-check endpoint, CI skeleton (GitHub Actions: build + test on push).
- **Exit criteria:** `docker compose up` gives you a running Postgres + a Spring Boot app that responds on `/health`.

## Phase 1 — Sandbox Execution Engine (the hard part — 3–5 days)
This is the actual product. Everything else is scaffolding around this.
- Docker-based per-submission execution (not `ProcessBuilder`): each submission runs in a throwaway container.
- Enforce: no network access, CPU/memory limits, execution timeout, non-root user, read-only filesystem except a scratch dir.
- Input: submitted code + test harness. Output: pass/fail per hidden test + stdout/stderr, captured safely.
- Stress-test it yourself: infinite loop, fork bomb, `require('fs').rmSync('/', {recursive:true})`, giant memory allocation. If the host survives all four, you're clear to move on.
- **Exit criteria:** you can POST a code submission to an endpoint and get back deterministic pass/fail results, and the sandbox survives a deliberately malicious submission.

## Phase 2 — Incident Content Pipeline (parallel-able with Phase 1, 2–3 days)
- Define an incident schema: logs, metrics, stack trace, source code, hidden test suite, difficulty rating.
- Hand-author 5–8 Node.js incidents. Don't automate content generation yet — quality over volume for v1.
- **Exit criteria:** 5+ incidents stored and loadable, each with a working hidden test suite that correctly fails broken code and passes the reference fix.

## Phase 3 — Core Backend: Auth, Users, Grading Loop (2–3 days)
- OAuth2 login (GitHub/Google) via Spring Security.
- Player profile entity: basic stats (attempts, pass rate).
- Wire Phase 1 (sandbox) + Phase 2 (incidents) into a real submission flow: fetch incident → submit code → sandbox executes → grade → persist result.
- **Exit criteria:** a logged-in user can pick an incident, submit a fix, and see pass/fail persisted to their profile.

## Phase 4 — AI Mentor (1–2 days)
- Claude/OpenAI integration for post-submission feedback (explain why tests failed, suggest debugging approach).
- Hint system: pre-submission hints available, tracked and penalized in later stats.
- Guardrail: AI output never touches the grading path — it only runs *after* deterministic grading completes.
- **Exit criteria:** after a failed submission, user gets a coherent, incident-specific explanation, not generic advice.

## Phase 5 — Frontend: Practice Mode End-to-End (2–3 days)
- Next.js pages: login, incident list, incident detail (logs/metrics/stack trace viewer), code editor, submission result view, AI feedback panel.
- Connect to backend REST endpoints from Phases 1–4.
- **Exit criteria:** full MVP loop playable in a browser — login → pick incident → investigate → submit → see graded result + AI feedback.

**🏁 MVP COMPLETE HERE.** This is a demoable, honest product. Everything below is Full Vision — don't start it until the above is solid and you've actually had a few people play through it.

---

## Phase 6 — Realtime Infra (3–4 days)
- Spring WebSocket + STOMP for lobby presence, matchmaking queue, live leaderboard updates.
- **Exit criteria:** two browser sessions can see each other's presence in a lobby in real time.

## Phase 7 — Ranked Mode + Glicko-2 (2–3 days)
- Implement Glicko-2 rating updates post-match.
- Matchmaking: pair users, assign a benchmarked-difficulty variant.
- Provisional rating UI for users with low match volume (ratings are noisy early — don't hide that from users).
- **Exit criteria:** two users can complete a ranked match and both ratings update correctly.

## Phase 8 — Replay Timeline (2 days)
- Log investigation steps (which logs viewed, which files opened, submission attempts) with timestamps.
- Playback UI on the frontend.
- **Exit criteria:** any completed match can be replayed step-by-step.

## Phase 9 — Remaining Modes + Achievements (open-ended)
- Custom Match, Tournament, Daily Challenge.
- Achievements, category mastery tracking.
- Multi-language incident support (Java, Python, Go, Docker, Kubernetes, distributed systems, security) — each of these is realistically its own mini-project (new sandbox runtime, new incident content), don't underestimate this.

---

## Total Estimate (MVP: Phases 0–5)
Roughly **11–17 focused days** if solo and Phase 1 goes smoothly. If Phase 1 fights you — and sandboxing usually does — budget more there and don't compress it just to hit a number. A leaky sandbox isn't a v2 fix, it's a rebuild.

## The One Rule
Do not start Phase 6 until Phase 5's exit criteria is genuinely met by someone other than you clicking through it. You've shipped enough hackathon projects that looked done in your head and weren't. This time, prove the loop before you build the tower on top of it.
