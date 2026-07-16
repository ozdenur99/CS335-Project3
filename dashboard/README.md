# Observability Dashboard

A single-file web dashboard for the API Gateway. It consumes the gateway's
existing `/metrics/*`, `/health`, and `/admin/*` REST endpoints directly from
the browser and covers every metric the project produces.

## Why a React-style web app (and not Grafana / Prometheus)

| Option | Verdict | Reasoning |
|--------|---------|-----------|
| **Custom web app** | ✅ **chosen** | The gateway already exposes rich **JSON** endpoints and CORS is pre-wired for `http://localhost:3000`. Zero backend changes. It's also the only option that can do the **interactive** parts of this project — live config changes (`POST /admin/config`), reset buttons, log filtering, CSV/JSON export, and the live AIMD limit chart. The team's own `docs/01-architecture/dashboard-guide.md` is written as a spec for exactly this. |
| Grafana | second-best | Great for the timeseries panel, and a `dashboard.json` already exists. But with the Infinity/JSON datasource it's clumsy for tables, live log feeds, and cannot POST config changes or trigger resets. Needs a running Grafana instance. |
| Prometheus | weakest fit | Our endpoints are **custom JSON, not Prometheus exposition format**. Using Prometheus would mean re-instrumenting the gateway with Micrometer (`/actuator/prometheus`) and would lose per-request labels (log feed, per-client status, audit log). Pure numeric time-series only. |

This dashboard is intentionally **dependency-light**: one HTML file, Chart.js from
a CDN, no build step. Drop it behind any static server, or serve it from Spring
Boot's `static/` folder.

## Running it

The gateway's CORS policy only allows the origin `http://localhost:3000`, so the
dashboard must be served on **port 3000**:

```bash
cd dashboard
python3 -m http.server 3000
# then open http://localhost:3000
```

Make sure the gateway stack is up first (`docker compose up`), so that:

- Gateway 1 → `http://localhost:8080`
- Gateway 2 → `http://localhost:8082`
- Backend → `http://localhost:8081`
- Redis → `localhost:6379`

Gateway URLs, the admin key, and the refresh interval are all editable at runtime
via the **⚙ Settings** panel (stored in `localStorage`).

## What it shows (every core metric)

**Core monitoring**
- System health strip — gateway-1, gateway-2, Redis, backend + uptime (`/health`, both ports)
- Summary tiles — total / allowed / blocked / block-rate / active clients (`/metrics`)
- Per-client table — key, algorithm, tenant, requests, blocked, block-rate bar, p50/p95/p99, rate-limit risk (`/metrics/clients`)

**Traffic & security**
- Status-code doughnut (`/metrics/status`)
- Allowed vs blocked per tenant (derived from `/metrics/clients`)
- Latency by algorithm — p50/p95/p99 grouped bars
- Suspicious IPs by HIGH / MEDIUM / LOW (`/metrics/suspicious/risk`)
- Live request log with decision/reason/key filters + JSON/CSV export (`/metrics/logs`)

**Historical & distributed**
- Request timeseries with hour/day/week toggle (`/metrics/timeseries`)
- Gateway-1 vs gateway-2 comparison (`/metrics/gateway`)
- **AIMD live limit chart** for `dev-key-dynamic` — accumulates snapshots as it polls
- Config audit log (`/admin/config/audit`)

**Runtime control**
- Change rate-limit policy — tenant/app/algorithm/limit (`POST /admin/config`)
- Demo reset buttons — bot / blocklist / all (`POST /admin/reset/*`)

## Extras

- Light / dark theme toggle (validated, colorblind-safe palette)
- Pause/resume polling
- Graceful degradation — panels whose endpoint is unreachable show an inline
  state instead of breaking the page

## Notes

- Two independent risk concepts, surfaced distinctly: **rate-limit risk** (% toward
  the limit, in the table) vs **abuse risk** (consecutive-failure ban). Bot detection
  is per-IP; a 403 (bot/abuse) is styled differently from a 429 (rate limit).
- `/metrics/gateway` lags up to ~10s (MetricsForwarder posts every 10s), so it polls
  every 12s. Timeseries polls every 30s; the fast panels every 5s.
