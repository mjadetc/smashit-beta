# Deploying SmasHIT (beta)

```
 phone / laptop
      │  https://<your-app>.vercel.app          (one site, so cookies just work)
      ▼
   Vercel ── serves the React app (frontend/)
      │  /api/... is forwarded (frontend/vercel.json "rewrites")
      ▼
   Render ── runs Spring Boot from backend/Dockerfile
      │
      ▼
   Neon ──── PostgreSQL (data + login sessions)
```

The browser only ever talks to Vercel, so the login cookie is a normal first-party
cookie. Free plans: Render sleeps after 15 idle minutes (the next request takes about
a minute), Neon's free database doesn't expire.

## 1. Database: Neon

1. Sign up at neon.tech and create a project (region: Singapore, closest to the Philippines).
2. Open **Connect**, pick the **Java** (JDBC) option, and note three values:
   - `DB_URL` = `jdbc:postgresql://<host>/<database>?sslmode=require`
   - `DB_USERNAME`
   - `DB_PASSWORD`

Flyway creates all tables on the backend's first start.

## 2. Backend: Render

1. Push your code to GitHub.
2. On render.com: **New → Web Service**, connect the repository.
3. Settings:
   - **Name**: `smashit-beta-api` (gives `https://smashit-beta-api.onrender.com`)
   - **Language**: Docker
   - **Root Directory**: `backend`
   - **Region**: Singapore
   - **Instance type**: Free
   - **Health Check Path** (under Advanced): `/api/auth/csrf`
4. **Environment variables**:

   | Name | Value |
   |---|---|
   | `DB_URL` | from Neon |
   | `DB_USERNAME` | from Neon |
   | `DB_PASSWORD` | from Neon |
   | `COOKIE_SECURE` | `true` |
   | `SHOW_SQL` | `false` |
   | `CORS_ALLOWED_ORIGINS` | your Vercel address (step 3), e.g. `https://smashit-beta.vercel.app` |

   (Render sets `PORT` by itself.)
5. Create the service. When the log says `Started BackendApplication`, open
   `https://<service>.onrender.com/api/auth/csrf`. An empty page means it works.

## 3. Frontend: Vercel

1. Check that `frontend/vercel.json` points at your Render address
   (`https://smashit-beta-api.onrender.com`). If Render gave you a different
   address, put that one in, then commit and push.
2. On vercel.com: **Add New → Project**, import the repository.
   - **Project Name**: `smashit-beta` (gives `https://smashit-beta.vercel.app`)
   - **Root Directory**: `frontend` (Vercel then detects Vite by itself)
3. Deploy, and note the address (`https://smashit-beta.vercel.app`).
4. Back on Render, set `CORS_ALLOWED_ORIGINS` to exactly that address (no `/` at the
   end). Render restarts the backend.

## 4. Check

Open the Vercel address on your phone: register, create an event, add players, score
a match. Close the browser, open it again: you're still logged in.

## When something goes wrong

| What you see | Why | Fix |
|---|---|---|
| The first request hangs for about a minute | Render's free plan was asleep | Normal. Open the app a few minutes before the session. |
| "Something went wrong (error 403)" on every button | The Vercel address isn't in `CORS_ALLOWED_ORIGINS` | Copy it exactly, `https://`, no `/` at the end. |
| 404 when you refresh a page like /events/5 | `vercel.json` is missing or not in `frontend/` | Check the Root Directory and the file. |
| Logged out after every request | `COOKIE_SECURE=true` but the page uses `http://` | Always use the `https://` address. |
| "Can't reach the server" | The `vercel.json` destination is wrong | It must be the Render address + `/api/:path*`. |
