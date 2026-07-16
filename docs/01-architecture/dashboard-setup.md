# Dashboard setup — Grafana + Postman

The observability dashboard is **Grafana** (reading the gateway's JSON metric
endpoints through the Infinity datasource). The interactive controls Grafana
can't do — live config changes and demo resets — are driven from a **Postman**
collection. No hand-coded frontend.

## Why this split

| Job | Tool | Why |
|-----|------|-----|
| Charts, tables, timeseries, risk, health | Grafana + Infinity | Reads the existing `/metrics/*` and `/health` JSON directly. Grafana is read-only. |
| Live `POST /admin/config`, `POST /admin/reset/*` | Postman | Writes — outside Grafana's scope. |
| Fill the dashboard with traffic | Locust (`:8089`) or Postman runner | — |

## 1. Start everything

```bash
docker compose up --build
```

This brings up: `backend` (8081), `gateway1` (8080), `gateway2` (8082),
`redis` (6379), `grafana` (3000), `locust` (8089). Grafana pre-installs the
Infinity plugin (`GF_PLUGINS_PREINSTALL`) and runs with anonymous admin access.

## 2. Grafana

Open **http://localhost:3000**.

- **Datasources** are auto-provisioned from
  `grafana/provisioning/datasources/infinity.yaml` — two Infinity datasources,
  `gateway-1` (uid `dfk5lf85xepz4f`, base `http://gateway1:8080`) and
  `gateway-2` (uid `afk5lqu9tk3k0d`, base `http://gateway2:8080`). The dashboard
  binds to those UIDs, so no manual datasource mapping is needed.
- **Import the dashboard:** Dashboards → New → **Import** → upload
  `dashboard.json` → select the two datasources if prompted.

> If a panel shows "datasource not found", confirm the provisioned datasource
> UIDs match the ones above (Connections → Data sources). If relative URLs don't
> resolve, open each datasource and confirm its base URL / allowed host.

### Panels

Already in the dashboard: total / blocked / allowed requests, per-key totals
(gateway-1 & 2), per-key risk, load-distribution pie, latency timeseries,
latency percentiles, response-status distribution per algorithm (all 5:
token / fixed / sliding / leaky / dynamic), suspicious/risk IPs, blocked-request
table, endpoints, backend health.

Added in this change:
- **Request timeseries (history)** — `/metrics/timeseries`, Redis-persisted
  request / allowed / blocked counts over time.
- **Abuse risk levels (per client)** — `/metrics/abuse-risk`, the second risk
  system (NONE / LOW / MEDIUM / HIGH by consecutive failures). Colour the
  `Risk level` column via a value mapping if you want the red/amber/green cue.

## 3. Postman — live config & resets

Import `postman/API-Gateway.postman_collection.json`. Set the collection
variables if your ports differ (`gw1`, `gw2`, `adminKey`, `apiKey`).

- **Admin — live config** → `POST /admin/config` changes a tenant/app policy
  (algorithm + limit); it syncs to **both** gateways instantly via Redis
  pub/sub. `GET /admin/config` shows current policies; `GET /admin/config/audit`
  the change history. All require the `X-Admin-Key` header (preset from the
  `adminKey` variable).
- **Admin — reset** → clear bot detection / blocklist / both before a demo.
- **Generate traffic** → fire `/api/test/notes` with different `X-API-KEY`
  values (use the Runner) to populate the dashboard, or just run Locust.

After a config change, watch the effect land in Grafana (per-key limits, block
rates) and in the audit history.

## Endpoint reference

Full endpoint contract and response shapes: `dashboard-guide.md` in this folder.
The one addition is `GET /metrics/abuse-risk`, which returns an array:

```json
[ { "apiKey": "dev-key-fixed", "level": "HIGH", "percent": 100 } ]
```
