# Deploying IncidentX

## The constraint that shapes everything

**Vercel can only host `incidentx-web` (the Next.js frontend).** It cannot run `incidentx-api`
(Spring Boot) or the Docker-based sandbox — Vercel functions are short-lived and serverless,
with no Docker-in-Docker access and no support for a persistent JVM process. `SandboxService`
shells out to `docker run` for every submission by default, so it needs either a host with a
real Docker daemon, or `SANDBOX_MODE=node` (see below) to skip Docker entirely.

**Render's standard web service (what this repo is set up for) does not expose a Docker daemon
to your app container either**, so `docker run` won't work there out of the box. To make this
work on Render, `SandboxService` now supports a `SANDBOX_MODE=node` fallback: instead of
spawning `docker run`, it runs the same `runner.js` directly with the Node binary that's baked
into the API's own Docker image (see `incidentx-api/Dockerfile`). Isolation is weaker than the
Docker path (process-level limits only, no container) — acceptable for a project like this, but
worth knowing. `render.yaml` in the repo root sets `SANDBOX_MODE=node` automatically. If you'd
rather keep full container isolation, deploy `incidentx-api` to a host that gives you a real
Docker daemon (Fly.io Machines or a VPS) and leave `SANDBOX_MODE` unset/`docker`.

So this is a two-host deployment:

| Piece | Host | Why |
|---|---|---|
| `incidentx-web` | Vercel | Standard Next.js app, zero-config |
| `incidentx-api` + Postgres + sandbox | Railway, Render, Fly.io, or a VPS | Needs a long-running JVM process and Docker access |

Pick a backend host that explicitly gives your app access to a Docker daemon (Fly.io Machines
or a plain VPS with Docker installed are the most reliable; confirm before committing if using
a PaaS's standard container service, since some sandbox untrusted `docker run` from inside your
own container).

## 1. Deploy the backend to Render first

You need the backend's public URL before the frontend can be configured, so do this first.

**Option A — `render.yaml` (Blueprint)**

1. Push this repo to GitHub.
2. In Render: New → Blueprint → pick the repo. Render reads `render.yaml` at the repo root and
   creates a free Postgres instance (`incidentx-db`) plus a Docker web service (`incidentx-api`,
   built from `incidentx-api/Dockerfile`).
3. Once the Postgres instance is up, open its "Connect" tab and copy the host/database/user/
   password, then set on the `incidentx-api` service (Environment tab):

   | Variable | Value |
   |---|---|
   | `DATABASE_URL` | `jdbc:postgresql://<host>/<database>` (JDBC format — Render's own `postgres://...` connection string won't work as-is) |
   | `DATABASE_USERNAME` | from the Connect tab |
   | `DATABASE_PASSWORD` | from the Connect tab |
   | `FRONTEND_URL` | `https://your-app.vercel.app` (fill in once you have it from step 2 below; a placeholder is fine for the first deploy) |
   | `CORS_ALLOWED_ORIGINS` | same as `FRONTEND_URL`, comma-separated if you add more origins |

   `SANDBOX_MODE=node`, `SESSION_COOKIE_SAME_SITE=None`, and `SESSION_COOKIE_SECURE=true` are
   already set by `render.yaml`.

**Option B — manual Web Service**

1. Provision a Render Postgres instance.
2. New → Web Service → pick the repo → Runtime: **Docker** → Dockerfile Path:
   `incidentx-api/Dockerfile` → Docker Build Context: `incidentx-api`.
3. Set the same environment variables as the table above, plus `SANDBOX_MODE=node`,
   `SESSION_COOKIE_SAME_SITE=None`, `SESSION_COOKIE_SECURE=true`.
4. Optional: `GITHUB_CLIENT_ID`/`GITHUB_CLIENT_SECRET` and `GOOGLE_CLIENT_ID`/
   `GOOGLE_CLIENT_SECRET` for real OAuth login (the mock-login / "Launch Practice Mode" flow
   works fine without them). `AI_API_KEY` for real AI mentor feedback (otherwise mock feedback
   is used).

Either way, confirm it's up once deployed: `curl https://your-backend.onrender.com/health` →
`{"status":"UP"}`.

## 2. Deploy the frontend to Vercel

1. Import the GitHub repo into Vercel.
2. **Set the project's Root Directory to `incidentx-web`** (Project Settings → General →
   Root Directory) — this is a monorepo, Vercel needs to know which folder is the actual
   Next.js app. No `vercel.json` is needed; the standard Next.js zero-config build handles it.
3. Set the environment variable (see `incidentx-web/.env.example`):

   | Variable | Value |
   |---|---|
   | `NEXT_PUBLIC_API_URL` | `https://your-backend.onrender.com` (no trailing slash) |

4. Deploy. Vercel runs `npm run build` in `incidentx-web` automatically.
5. Go back to Render and update `FRONTEND_URL` / `CORS_ALLOWED_ORIGINS` on `incidentx-api` to
   your real Vercel URL, then redeploy the backend so CORS/OAuth picks it up.

## 3. If OAuth login (GitHub/Google) is wired up

Update the OAuth app's callback URL in the GitHub/Google developer console to point at your
deployed backend: `https://your-backend-host/login/oauth2/code/github` (and `/google`).

If you skip this, the mock-login flow (`POST /api/auth/mock-login`) still works end-to-end —
it's what the "Launch Practice Mode" button on the login page uses, no OAuth credentials needed.

## Local dev is unaffected

All of the above are env vars with defaults that match the existing local setup
(`docker-compose.yml` + `localhost:3000` + `localhost:8080` + `SameSite=Lax`), so nothing
changes for `npm run dev` / `./mvnw spring-boot:run` locally.
