# incidentx-web

The Next.js frontend for IncidentX. See the [repo root README](../README.md) for what the
project is and how to run the full stack, and [DEPLOYMENT.md](../DEPLOYMENT.md) for deploying
this to Vercel.

## Local dev

```bash
npm install
npm run dev
```

Requires `incidentx-api` running at `http://localhost:8080` (the default — see
[.env.example](.env.example) to point at a different backend via `NEXT_PUBLIC_API_URL`).

## Structure

```
src/app/                Pages (login, dashboard, incident workspace)
src/lib/api.ts          Fetch wrapper — reads NEXT_PUBLIC_API_URL
```
