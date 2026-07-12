# IncidentX — Product Requirements Document (PRD)

**Status:** Draft v1
**Owner:** Parth Gupta
**Last updated:** July 2026

---

## 1. Problem Statement

Developers learn to debug production systems the hard way — by breaking things in real production, under real pressure, for the first time, with no safety net. There's no equivalent of a chess.com or a LeetCode for *incident response*: realistic logs, stack traces, and metrics, in a sandboxed environment, scored deterministically, with a rating that actually means something.

IncidentX is that platform.

## 2. Vision

A competitive debugging arena where developers diagnose and fix realistic production incidents — inside isolated sandboxes, graded by hidden test suites, rated via Glicko-2, and coached (never judged) by an AI mentor. Skill is proven by passing hidden tests, not by an AI's opinion of your code.

## 3. Goals / Non-Goals

**Goals (v1):**
- Prove the core loop works and is fun: get an incident → investigate → fix → get graded → see your rating move.
- Make the sandbox genuinely safe — this is the product's credibility, not a footnote.
- Ship something a judge/user can complete start-to-finish in under 15 minutes.

**Non-Goals (v1):**
- Not trying to support every language on day one (Node.js only, per roadmap).
- Not trying to fully solve AI-assisted cheating — mitigate, don't pretend to eliminate.
- Not building Tournament mode or full achievement systems yet — those are v2+.

## 4. Target Users

- **Primary:** Intermediate-to-senior developers who want to sharpen production-debugging instincts competitively (job prep, portfolio, genuine skill-building).
- **Secondary:** Bootcamp grads / junior devs using Practice mode as structured learning.
- **Tertiary:** Hiring teams who might eventually use ranked stats as a signal (future, not v1).

## 5. Core Features

### 5.1 MVP (build this first)
| Feature | Description |
|---|---|
| Auth | OAuth2 login via GitHub/Google |
| Practice Mode | Single-player, untimed, no rating impact |
| Incident Delivery | Pre-seeded incidents: logs, metrics, stack trace, source code |
| Sandbox Execution | Isolated, resource-limited execution of submitted fixes |
| Deterministic Grading | Hidden test suite pass/fail, no AI in the scoring path |
| Basic AI Mentor | Post-submission explanation + hints (hints cost points) |
| Player Profile | Basic stats: attempts, pass rate, categories attempted |

### 5.2 Full Vision (post-MVP)
| Feature | Description |
|---|---|
| Ranked Mode | Glicko-2 rated matches |
| Matchmaking + Lobby | Realtime via WebSocket/STOMP |
| Custom Match / Tournament / Daily Challenge | Additional modes |
| Replay Timeline | Full investigation + submission history playback |
| Achievements & Category Mastery | Long-term progression system |
| Multi-language incidents | Java, Python, Go, Docker, Kubernetes, distributed systems, security |

**Why this split:** every mode past Practice depends on realtime infra, matchmaking, and a trustworthy rating system. Building those before the core "solve an incident" loop is proven and fun is how you end up demoing plumbing instead of a product.

## 6. System Architecture

**Frontend:** Next.js + React
**Backend:** Spring Boot 3 (Java 21)
**Auth:** Spring Security OAuth2 (Google/GitHub)
**Database:** PostgreSQL via Spring Data JPA (Hibernate)
**Realtime:** Spring WebSocket + STOMP (post-MVP, needed for lobby/matchmaking/leaderboard)
**Sandbox:** Containerized execution (Docker per submission — see §8, this replaces the raw `ProcessBuilder` approach from the original spec) with no network access, execution timeout, and CPU/RAM limits
**Storage:** S3-compatible object storage for repos/submissions/logs; Postgres for metadata + pointers
**Rating:** Glicko-2 (post-MVP)
**AI:** Claude/OpenAI — feedback, hints, learning recommendations only. Never scoring.

## 7. Match Flow (Full Vision)

Login → Choose mode → Matchmaking → Variant assignment → Investigation → Optional hints → Submission → Sandbox execution → Hidden test evaluation → Score calculation → Rating update → AI feedback → Replay

**MVP flow (simplified):** Login → Pick incident (Practice) → Investigation → Submission → Sandbox execution → Hidden test evaluation → AI feedback

## 8. Security & Fairness

- **Sandbox isolation:** Docker container per submission (not bare `ProcessBuilder`) — no host filesystem access, no network, hard CPU/memory/time limits, non-root execution user.
- **Grading integrity:** Hidden, randomized test cases prevent hardcoded/memorized solutions. AI excluded from the scoring path entirely.
- **Anti-cheat (partial, honest about limits):** External AI use during a match cannot be fully prevented. Mitigations: hint-usage penalties baked into rating, and (post-MVP) investigation-step telemetry from the replay timeline as a soft signal, not a ban-hammer. This is explicitly a mitigation, not a solve.

## 9. Success Metrics (MVP)

- A user can go from login to graded submission in one sitting, with zero manual intervention.
- Sandbox handles a malicious/broken submission (infinite loop, fork bomb, filesystem write attempt) without crashing the host.
- AI mentor feedback is judged "useful" in informal user testing (qualitative, v1).

## 10. Open Risks / Questions

1. **Sandbox complexity is the real MVP bottleneck** — budget the most build time here, not on UI polish.
2. **Incident content creation is manual and slow** — need at least 5–8 well-tuned Node.js incidents before this is demoable; content pipeline is a hidden cost the original spec doesn't budget for.
3. **Glicko-2 needs match volume to mean anything** — with a small early user base, ratings will be noisy. Consider showing "provisional" rating status early on.
4. Deadline/timeline not yet confirmed — architecture above assumes real runway; if this is hackathon-speed, MVP scope needs to shrink further (see next conversation turn).

---
*This PRD supersedes any earlier IncidentX planning. Scope, stack, and grading philosophy reflect the new spec, not prior versions.*
