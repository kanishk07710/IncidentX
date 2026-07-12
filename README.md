# IncidentX

⚡ Debug. Compete. Prove It.

A competitive production-debugging arena. Diagnose and fix realistic Node.js incidents —
logs, metrics, stack traces — inside an isolated Docker sandbox, graded deterministically by
hidden test suites, with an AI mentor for post-submission feedback. AI never touches the
grading path; passing is decided by hidden tests, full stop.

## How it works

1. Log in (mock login for local dev, or GitHub/Google OAuth2).
2. Pick an incident from the practice list — each ships with logs, a metrics timeline, a stack
   trace, and starter code.
3. Investigate, optionally reveal a hint (costs rating points), and edit the code.
4. Submit. Your fix runs inside a throwaway Docker container — no network, capped CPU/memory,
   a hard timeout, non-root, read-only filesystem outside a scratch dir — against a hidden test
   suite.
5. Get a pass/fail per test plus AI mentor feedback explaining what went wrong (or right).

## Project layout

```
incidentx-api/   Spring Boot 3 (Java 21) backend — auth, incidents, grading, AI mentor
incidentx-web/   Next.js frontend
sandbox/         Docker image the backend spins up per submission (Node.js test runner)
docker-compose.yml   Local Postgres
```

## Running locally

Prerequisites: Java 21, Node 20+, Docker Desktop.

```bash
# 1. Postgres
docker compose up -d

# 2. Sandbox image (the backend calls `docker run` against this per submission)
docker build -t incidentx-sandbox-node ./sandbox

# 3. Backend — http://localhost:8080
cd incidentx-api
./mvnw spring-boot:run

# 4. Frontend — http://localhost:3000
cd incidentx-web
npm install
npm run dev
```

Open `http://localhost:3000`, use "Launch Practice Mode" with any username (mock login — no
OAuth setup needed for local dev), and pick an incident.

## Stack

Next.js · Spring Boot 3 / Java 21 · PostgreSQL + Spring Data JPA · Spring Security OAuth2 ·
Docker (per-submission sandbox) · Claude/OpenAI (mentor feedback only, falls back to
high-quality mock feedback if no API key is set)

## Deploying

Vercel can only host the frontend — the backend needs Docker access for the sandbox, so it
lives on a separate host. See [DEPLOYMENT.md](DEPLOYMENT.md) for the full two-host setup and
required environment variables.

## Docs

- [project (1).md](project%20(1).md) — canonical spec and phased build checklist
- [IncidentX_PRD (1).md](IncidentX_PRD%20(1).md) — product requirements
- [IncidentX_Build_Plan (1).md](IncidentX_Build_Plan%20(1).md) — build plan and sequencing
