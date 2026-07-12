# Deploying IncidentX

## The constraint that shapes everything

**Vercel can only host `incidentx-web` (the Next.js frontend).** It cannot run `incidentx-api`
(Spring Boot) or the Docker-based sandbox — Vercel functions are short-lived and serverless,
with no Docker-in-Docker access and no support for a persistent JVM process. `SandboxService`
literally shells out to `docker run` for every submission, so the backend needs a host that
gives it a real Docker daemon.

So this is a two-host deployment:

| Piece | Host | Why |
|---|---|---|
| `incidentx-web` | Vercel | Standard Next.js app, zero-config |
| `incidentx-api` + Postgres + sandbox | Railway, Render, Fly.io, or a VPS | Needs a long-running JVM process and Docker access |

Pick a backend host that explicitly gives your app access to a Docker daemon (Fly.io Machines
or a plain VPS with Docker installed are the most reliable; confirm before committing if using
a PaaS's standard container service, since some sandbox untrusted `docker run` from inside your
own container).

## 1. Deploy the backend first

You need the backend's public URL before the frontend can be configured, so do this first.

1. Provision a Postgres database on your chosen host (or any managed Postgres).
2. Build the sandbox image on that host: `docker build -t incidentx-sandbox-node ./sandbox`
   (this must happen wherever `incidentx-api` actually runs, since it calls `docker run
   incidentx-sandbox-node` directly).
3. Deploy `incidentx-api` (e.g. `./mvnw spring-boot:run` in production mode, or build a jar
   with `./mvnw package` and run `java -jar target/*.jar`).
4. Set these environment variables on the backend host:

   | Variable | Example | Notes |
   |---|---|---|
   | `PORT` | (usually set automatically by the host) | Falls back to 8080 |
   | `DATABASE_URL` | `jdbc:postgresql://host:5432/incidentx` | Must be JDBC format — if your host gives you a `postgres://user:pass@host/db` URI, rewrite it to `jdbc:postgresql://host/db` and set `DATABASE_USERNAME`/`DATABASE_PASSWORD` separately |
   | `DATABASE_USERNAME` | `postgres` | |
   | `DATABASE_PASSWORD` | `...` | |
   | `FRONTEND_URL` | `https://your-app.vercel.app` | Where OAuth2 login redirects back to after success |
   | `CORS_ALLOWED_ORIGINS` | `https://your-app.vercel.app` | Comma-separated if you need more than one (e.g. add `http://localhost:3000` while testing) |
   | `SESSION_COOKIE_SAME_SITE` | `None` | Required for cross-site cookies (frontend and backend are different domains) |
   | `SESSION_COOKIE_SECURE` | `true` | Required alongside `SameSite=None` — needs HTTPS, which your host should provide by default |
   | `GITHUB_CLIENT_ID` / `GITHUB_CLIENT_SECRET` | | Only needed if you want real GitHub OAuth login; otherwise the mock-login flow still works |
   | `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET` | | Same, for Google |

5. Confirm it's up: `curl https://your-backend-host/health` → `{"status":"UP"}`.

## 2. Deploy the frontend to Vercel

1. Import the GitHub repo into Vercel.
2. **Set the project's Root Directory to `incidentx-web`** (Project Settings → General →
   Root Directory) — this is a monorepo, Vercel needs to know which folder is the actual
   Next.js app. No `vercel.json` is needed; the standard Next.js zero-config build handles it.
3. Set the environment variable:

   | Variable | Value |
   |---|---|
   | `NEXT_PUBLIC_API_URL` | `https://your-backend-host` (no trailing slash) |

4. Deploy. Vercel runs `npm run build` in `incidentx-web` automatically.

## 3. If OAuth login (GitHub/Google) is wired up

Update the OAuth app's callback URL in the GitHub/Google developer console to point at your
deployed backend: `https://your-backend-host/login/oauth2/code/github` (and `/google`).

If you skip this, the mock-login flow (`POST /api/auth/mock-login`) still works end-to-end —
it's what the "Launch Practice Mode" button on the login page uses, no OAuth credentials needed.

## Local dev is unaffected

All of the above are env vars with defaults that match the existing local setup
(`docker-compose.yml` + `localhost:3000` + `localhost:8080` + `SameSite=Lax`), so nothing
changes for `npm run dev` / `./mvnw spring-boot:run` locally.
