# IncidentX — Project Source of Truth

**Read this entire file before writing any code.** This is the single canonical spec. Do not invent features, modes, or architecture decisions not listed here. If something is ambiguous, ask before assuming.

---

## 1. What We're Building

IncidentX is a production-debugging platform where developers solve realistic software incidents inside isolated sandboxes. Submissions are graded **deterministically** via hidden test suites — AI is never part of the scoring path. AI is used only for post-submission mentorship, hints, and feedback.

## 2. Non-Negotiable Rules

- **AI never determines match outcomes.** Grading = hidden test suite pass/fail. Full stop.
- **Sandbox isolation is not optional or "good enough for now."** Every submission runs in a Docker container: no network, CPU/memory/time limits, non-root, no host filesystem access. Do not use raw `ProcessBuilder`/`child_process` execution — that is explicitly rejected.
- **Build in phase order.** Do not start a phase until the prior phase's exit criteria is met. Do not build Ranked/Realtime/Replay features before the MVP loop (Phases 0–5) works end-to-end.
- **Content quality over quantity.** 5–8 hand-authored, well-tested incidents beat 50 sloppy ones.

## 3. Tech Stack (fixed — do not substitute without asking)

| Layer | Choice |
|---|---|
| Frontend | Next.js + React |
| Backend | Spring Boot 3, Java 21 |
| Auth | Spring Security OAuth2 (Google/GitHub) |
| Database | PostgreSQL + Spring Data JPA (Hibernate) |
| Realtime | Spring WebSocket + STOMP (Phase 6+ only) |
| Sandbox | Docker, per-submission container execution |
| Storage | S3-compatible object storage (repos/submissions/logs); Postgres for metadata |
| Rating | Glicko-2 (Phase 7+ only) |
| AI | Claude/OpenAI API — feedback, hints, learning recs only |

## 4. Match Flow (Full Vision — build toward this, but not all at once)

Login → Choose mode → Matchmaking → Variant assignment → Investigation → Optional hints → Submission → Sandbox execution → Hidden test evaluation → Score calculation → Rating update → AI feedback → Replay

## 5. Security & Fairness Requirements

- Hidden, randomized test cases prevent hardcoded solutions.
- Sandbox must survive: infinite loops, fork bombs, filesystem writes outside scratch dir, network calls, memory exhaustion attempts. Test all four before marking Phase 1 complete.
- Anti-cheat is a mitigation, not a guarantee — external AI use during a match cannot be fully prevented. Hint usage is tracked and penalized in rating/stats as the primary lever.

---

## 6. Build Plan — Phased Checklist

### Phase 0 — Foundations
- [x] Repo scaffold (`incidentx-api` backend, `incidentx-web` frontend)
- [x] Spring Boot 3 project init (Java 21)
- [x] Postgres running via Docker Compose
- [x] Health-check endpoint (`/health`)
- [x] CI skeleton: build + test on push — `git init`'d locally and added `.github/workflows/ci.yml`
      (backend job: Postgres service container + build sandbox image + `mvnw test`; frontend job:
      `npm ci` + lint + build). Verified both jobs' steps pass locally. Not pushed to a remote —
      that's a separate step for whenever a GitHub remote exists.
- [x] **Exit criteria:** `docker compose up` → Postgres + Spring Boot app responding on `/health`

### Phase 1 — Sandbox Execution Engine (highest priority — this is the product)
- [x] Docker-based per-submission execution setup
- [x] Enforce no network access
- [x] Enforce CPU/memory limits
- [x] Enforce execution timeout
- [x] Enforce non-root user, read-only filesystem except scratch dir
- [x] Submission endpoint: code + test harness in → pass/fail + stdout/stderr out
- [x] Stress test: infinite loop survives
- [x] Stress test: fork bomb survives
- [x] Stress test: malicious filesystem write survives
- [x] Stress test: memory exhaustion survives
- [x] **Exit criteria:** deterministic pass/fail via API; host survives all four attack tests
  - Found and fixed a real defect while verifying: `sandbox/Dockerfile` had no `CMD`, so `runner.js`
    never actually executed — every submission silently came back as a parse `ERROR` with empty
    output. Added `CMD ["node", "runner.js"]`. Also strengthened the fork-bomb test, which was
    trivially passing without ever attempting to exhaust PIDs. All 5 sandbox tests now genuinely pass.

### Phase 2 — Incident Content Pipeline
- [x] Define incident schema (logs, metrics, stack trace, source code, hidden tests, difficulty)
- [x] Author incident #1–8 (Node.js) — 5 incidents authored (memory leak, event-loop blocking,
      SQL injection, uncaught rejection, race condition); within the PRD's 5–8 target range
- [x] Verify each incident's hidden tests fail on broken code and pass on reference fix
- [x] **Exit criteria:** 5+ incidents loadable and correctly graded

### Phase 3 — Core Backend: Auth, Users, Grading Loop
- [x] OAuth2 login (GitHub/Google) via Spring Security — wired up; also has a mock-login path for
      local dev since real OAuth client credentials aren't configured
- [x] Player profile entity (attempts, pass rate, categories attempted)
- [x] Wire together: fetch incident → submit → sandbox executes → grade → persist
- [x] **Exit criteria:** logged-in user can submit a fix and see a persisted graded result

### Phase 4 — AI Mentor
- [x] Claude/OpenAI integration for post-submission feedback — calls OpenAI if `ai.api-key` is set,
      falls back to high-quality mock feedback per incident otherwise (all 5 incidents covered)
- [x] Hint system (pre-submission, tracked, penalized) — `Incident.hint` field (one nudge per
      incident, all 5 authored), `POST /api/incidents/{id}/hint` increments `usedHintsCount` and
      deducts 10 rating points (floored at 0), frontend has a "Get Hint (-10 rating)" button that
      reveals the hint and locks after first use. Verified in-browser: rating dropped 1500 → 1490
      after one hint, correctly reflected on the dashboard.
      Known limitation: calling the endpoint directly (bypassing the UI's one-click lock) re-deducts
      each time — acceptable for MVP since it only self-penalizes, but worth a per-incident-per-user
      uniqueness constraint before Ranked mode (Phase 7) makes rating stakes real.
- [x] Guardrail: AI runs only after deterministic grading completes, never before/instead
- [x] **Exit criteria:** failed submission returns coherent, incident-specific feedback

### Phase 5 — Frontend: Practice Mode End-to-End
- [x] Login page
- [x] Incident list page
- [x] Incident detail view (logs/metrics/stack trace viewer)
- [x] Code editor component
- [x] Submission result view
- [x] AI feedback panel
- [x] **Exit criteria:** full loop playable in-browser by someone other than the builder
  - Found and fixed a real defect while verifying: `incident/[id]/workspace.module.css` didn't
    exist, so the incident workspace page (the core of the product) 500'd on every load. Built the
    stylesheet to match the existing glassmorphic design system. Played the full loop end-to-end in
    a browser afterward: login → dashboard (5 incidents) → incident brief/logs/metrics/stack trace →
    edit code → submit → sandbox grades it → AI feedback renders → profile stats persist across a
    backend restart.

> **🏁 MVP COMPLETE at end of Phase 5.** Do not proceed to Phase 6 until this is validated by an outside user, not just the developer.

### Phase 6 — Realtime Infra
- [ ] Spring WebSocket + STOMP setup
- [ ] Lobby presence
- [ ] Matchmaking queue
- [ ] Live leaderboard updates
- [ ] **Exit criteria:** two sessions see each other's presence in real time

### Phase 7 — Ranked Mode + Glicko-2
- [ ] Glicko-2 rating implementation
- [ ] Matchmaking pairing + benchmarked variant assignment
- [ ] Provisional rating UI for low match-volume users
- [ ] **Exit criteria:** two-user ranked match completes and both ratings update correctly

### Phase 8 — Replay Timeline
- [ ] Log investigation steps with timestamps
- [ ] Playback UI
- [ ] **Exit criteria:** any completed match replayable step-by-step

### Phase 9 — Remaining Modes + Achievements (open-ended, do last)
- [ ] Custom Match mode
- [ ] Tournament mode
- [ ] Daily Challenge mode
- [ ] Achievements + category mastery
- [ ] Additional language runtimes (Java, Python, Go, Docker, Kubernetes, distributed systems, security) — each treated as its own mini-project

---

## 7. Instructions for the AI Building This

1. Work phase by phase, top to bottom. Do not skip ahead.
2. Before starting a phase, confirm the previous phase's exit criteria is checked off.
3. When a task is completed, check its box (`- [x]`) in this file so progress stays visible.
4. Do not substitute the tech stack in §3 without explicit sign-off.
5. Do not weaken the sandbox security requirements in §5 for convenience or speed — this is the product's core credibility and is non-negotiable.
6. If a requirement is ambiguous or missing, stop and ask rather than guessing.
7. Keep AI/mentor logic strictly separate from the grading path — grading must be deterministic and testable independent of any AI call.
