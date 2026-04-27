# API Gateway — Dashboard Integration Guide

## Infrastructure

| Service   | URL                    | Notes                              |
|-----------|------------------------|------------------------------------|
| Gateway 1 | http://localhost:8080  | Primary                            |
| Gateway 2 | http://localhost:8082  | Identical config, shared Redis     |
| Backend   | http://localhost:8081  | Not called directly by dashboard   |
| Redis     | localhost:6379         | Shared state between both gateways |

CORS is enabled for `http://localhost:3000` — all gateway endpoints are
callable directly from the browser with no proxy needed.

---

## Authentication

| Endpoint group | Header required                 |
|----------------|---------------------------------|
| `/metrics/*`   | None                            |
| `/health`      | None                            |
| `/admin/*`     | `X-Admin-Key: admin-secret-key` |

---

## Available API Keys (for demo/testing)

| Key                    | Algorithm | Limit | Scope              |
|------------------------|-----------|-------|--------------------|
| `dev-key-token`        | token     | 3     | client             |
| `dev-key-fixed`        | fixed     | 3     | client             |
| `dev-key-sliding`      | sliding   | 3     | client             |
| `dev-key-leaky`        | leaky     | 3     | client             |
| `dev-key-dynamic`      | AIMD      | 10    | client (adjusts!)  |
| `dev-key-business`     | token     | 6     | client             |
| `key-acme-dashboard`   | token     | 10    | tenant-acme/dashboard |
| `key-acme-api`         | fixed     | 20    | tenant-acme/api    |
| `key-beta-dashboard`   | sliding   | 15    | tenant-beta/dashboard |
| `key-beta-api`         | leaky     | 25    | tenant-beta/api    |
| `key-enterprise-dashboard` | fixed | 20   | tenant-enterprise/dashboard |
| `key-enterprise-api`   | token     | 50    | tenant-enterprise/api |

---

## Rate Limiting Algorithms

There are 5 algorithms. The dashboard should label each client with its
algorithm name — the behavior is fundamentally different.

| Algorithm | How it works | Key characteristic to show |
|-----------|--------------|---------------------------|
| `token`   | Bucket refills at fixed rate (5 tokens / 120s) | Requests succeed in bursts, then slow |
| `fixed`   | Hard counter per 10s window, resets cleanly | Resets every 10 seconds |
| `sliding` | Weighted blend of current + previous window | Smooth, no hard reset — see note below |
| `leaky`   | Queue drains at fixed rate, excess dropped | Even spacing between allowed requests |
| `dynamic` | **AIMD — adjusts limit automatically** | Limit goes up/down in real time |

### AIMD (Dynamic) Algorithm — special case

`dev-key-dynamic` uses AIMD (Additive Increase Multiplicative Decrease),
the same algorithm TCP uses for congestion control.

**How it adjusts (runs every 10 seconds):**
- If p50 latency > 500ms → limit is **halved** (× 0.5, minimum 1)
- If p50 latency < 500ms and > 0 → limit **increases by 1**
- Base/max limit is 10

**What the dashboard should show:**
- The *current effective limit* — read `X-RateLimit-Limit` from any
  response header, or from `/metrics/clients` → statusCodes showing
  when 429s started (the limit dropped)
- A live chart of the limit over time would be the most compelling
  visualization — poll `/metrics/clients` every 5s and track
  `blockedCount` vs `totalRequests` for `dev-key-dynamic`

**Sliding window note:**
After a window expires, previous requests fade gradually (not a hard
reset). Don't expect a clean zero after 30 seconds — the previous window
still contributes with a weight. Sleep 60+ seconds for a truly clean slate.

---

## Two Separate Risk Systems

The project has **two independent risk concepts** — do not confuse them.

### 1. Rate Limit Risk (from MetricsService)
How close a client is to hitting their rate limit.
- Source: `GET /metrics/clients` → `riskScore` field
- Source: `GET /metrics/risk`
- Scale: 0% (no requests) → 100% (at or over limit)
- Resets when the gateway restarts (in-memory)

### 2. Abuse Risk (from RiskScoreService)
How close a client is to being **permanently blocked** by the abuse filter.
Based on consecutive failure count, not request count.
- Levels: `NONE`, `LOW`, `MEDIUM`, `HIGH`
- HIGH = one more failure away from being banned
- Forwarded to the backend on every request as:
  - `X-Gateway-Risk-Level` — NONE/LOW/MEDIUM/HIGH
  - `X-Gateway-Risk-Percent` — 0–100
  - `X-Gateway-Client-IP` — resolved client IP
- **No dedicated endpoint exposes this yet** — it's only visible in
  backend logs. If you want to show it on the dashboard, ask the team
  to add `GET /metrics/abuse-risk` to MetricsController.

---

## Metrics Endpoints

### `GET /metrics`
Top-level gateway snapshot. Good for summary cards.

```json
{
  "totalRequests": 100,
  "blockedRequests": 20,
  "allowedRequests": 80,
  "perKey": { "dev-key-token": 30, "dev-key-fixed": 70 },
  "riskScores": { "dev-key-token": 45, "dev-key-fixed": 100 },
  "statusCodes": { "200": 80, "429": 15, "403": 5 }
}
```

---

### `GET /metrics/clients` — most important for dashboard
Per-client breakdown. Best source for a live table showing every key.

```json
{
  "dev-key-token": {
    "totalRequests": 30,
    "blockedCount": 5,
    "latency": { "p50": 4, "p95": 12, "p99": 20 },
    "statusCodes": { "200": 25, "429": 5 },
    "riskScore": "45%"
  },
  "dev-key-dynamic": {
    "totalRequests": 40,
    "blockedCount": 0,
    "latency": { "p50": 320, "p95": 480, "p99": 510 },
    "statusCodes": { "200": 40 },
    "riskScore": "30%"
  }
}
```

**Poll every 5 seconds.**

---

### `GET /metrics/status`
Status code breakdown. Good for a 2xx/4xx/5xx breakdown chart.

```json
{
  "gateway": { "200": 80, "400": 2, "401": 3, "403": 5, "429": 10 },
  "perKey": {
    "dev-key-token": { "200": 25, "429": 5 },
    "dev-key-fixed":  { "200": 55, "403": 3 }
  }
}
```

**Poll every 5 seconds.**

---

### `GET /metrics/gateway`
Side-by-side comparison of both gateways. Data comes from MetricsForwarder
which posts every **10 seconds** — no point polling faster.

```json
{
  "gateway-1": {
    "totalRequests": 60,
    "blockedRequests": 10,
    "allowedRequests": 50,
    "statusCodes": { "200": 50, "429": 10 }
  },
  "gateway-2": {
    "totalRequests": 40,
    "blockedRequests": 10,
    "allowedRequests": 30,
    "statusCodes": { "200": 30, "429": 10 }
  }
}
```

**Poll every 10–15 seconds.**

---

### `GET /metrics/timeseries?range=week`
Historical request counts stored in Redis. Persists across restarts.

Query params:
- `range` — `hour`, `day`, `week` (default: `week`)
- `from` — epoch milliseconds (optional)
- `to` — epoch milliseconds (optional)

> **CRITICAL — timestamps are epoch milliseconds:**
> ```js
> // JavaScript
> new Date(timestamp)
>
> // Chart.js — use directly as x-axis value
> { x: timestamp, y: count }
>
> // Grafana — divide by 1000 (expects seconds, not milliseconds)
> timestamp / 1000
> ```

**Poll every 30 seconds.**

---

### `GET /metrics/risk`
Rate limit risk scores for all clients as percentage strings.

```json
{
  "dev-key-token": "45%",
  "dev-key-fixed": "100%",
  "dev-key-dynamic": "30%"
}
```

---

### `GET /metrics/latency?apiKey=dev-key-token`
Latency percentiles for one specific client.

```json
{
  "apiKey": "dev-key-token",
  "percentiles": { "p50": 4, "p95": 12, "p99": 20 },
  "statusCodes": { "200": 25, "429": 5 },
  "riskScore": "45%"
}
```

---

### `GET /metrics/suspicious`
IPs flagged as bots (over 50 requests from that IP across any key).

```json
["10.0.0.1", "192.168.1.5"]
```

### `GET /metrics/suspicious/risk`
Flagged IPs grouped by severity. Thresholds: LOW > 50, MEDIUM > 100, HIGH > 200.

```json
{
  "HIGH":   ["10.0.0.1"],
  "MEDIUM": ["192.168.1.5"],
  "LOW":    []
}
```

---

### `GET /metrics/logs`
Last 100 requests. Good for a live log feed.

```json
[
  {
    "timestamp": "2026-04-22T10:00:00Z",
    "apiKey": "dev-key-token",
    "ip": "10.0.0.1",
    "path": "/api/test123/notes",
    "decision": "ALLOWED",
    "reason": "ok",
    "algorithm": "token",
    "latencyMs": 5,
    "gatewayId": "gateway-1",
    "requestId": "a3f2c1d4-..."
  }
]
```

`decision` values: `ALLOWED`, `BLOCKED`
`reason` values: `ok`, `rate_limit_exceeded`, `invalid_or_missing_key`,
`abuse_detected`, `suspected_bot`

### `GET /metrics/logs/filter`
Filter by any combination (all params optional):
```
GET /metrics/logs/filter?decision=BLOCKED&reason=rate_limit_exceeded&apiKey=dev-key-fixed
```

### `GET /metrics/logs/export/json` and `/export/csv`
Download buttons — returns file attachment.

---

## Health Endpoint

### `GET /health`
Returns `200` if healthy, `207` if degraded.

```json
{
  "status": "UP",
  "service": "API Gateway",
  "gatewayId": "gateway-1",
  "redis": "UP",
  "backend": "UP",
  "uptimeSeconds": 3420
}
```

**Poll every 30 seconds.**

---

## Admin Endpoints
All require `X-Admin-Key: admin-secret-key` header.

### `GET /admin/config`
Full current config — tenants, apps, algorithms, limits.

### `POST /admin/config`
Live config change — syncs to both gateways via Redis pub/sub instantly.

```json
{
  "tenant": "tenant-acme",
  "app": "dashboard",
  "algorithm": "sliding",
  "limit": 5
}
```

`app` is optional — omit for tenant-level change.
Valid algorithms: `token`, `fixed`, `sliding`, `leaky`, `dynamic`

### `GET /admin/config/audit`
Last 100 config changes across both gateways, newest first. Persists in Redis.

```json
[
  {
    "timestamp": "2026-04-22T10:00:00Z",
    "tenant": "tenant-acme",
    "app": "dashboard",
    "oldAlgorithm": "token",
    "newAlgorithm": "sliding",
    "oldLimit": 10,
    "newLimit": 5,
    "gatewayId": "gateway-1"
  }
]
```

### `POST /admin/reset/bot`
Clears bot IP counts and flagged IPs from Redis.

### `POST /admin/reset/blocked`
Clears the abuse blocklist from Redis.

### `POST /admin/reset/all`
Both of the above. **Always run before a demo section.**

---

## Response Headers on Every Request

| Header               | Present    | Value                              |
|----------------------|------------|------------------------------------|
| `X-Request-ID`       | Always     | UUID — matches `requestId` in logs |
| `X-RateLimit-Limit`  | Always     | Current enforced limit             |
| `X-RateLimit-Algorithm` | Always  | Algorithm name (`token`, `aimd`, etc.) |
| `Retry-After`        | 429 only   | `10` (seconds)                     |

For AIMD clients, `X-RateLimit-Limit` changes over time — this is the
live adjusted limit, not the base limit.

---

## Error Response Format (gateway-level errors)

```json
{
  "status": 429,
  "error": "Too Many Requests",
  "message": "Request could not be processed at this time.",
  "path": "/api/test123/notes",
  "requestId": "a3f2c1d4-..."
}
```

`requestId` matches `X-Request-ID` header and the log entry for that request.

---

## Data Persistence — what survives a gateway restart

| Data                    | Storage    | Survives restart? |
|-------------------------|------------|-------------------|
| Request counts          | In-memory  | No                |
| Per-key status codes    | In-memory  | No                |
| Latency percentiles     | In-memory  | No                |
| Bot IP counts           | Redis      | Yes               |
| Abuse blocklist         | Redis      | Yes (with TTL)    |
| Rate limit state        | Redis      | Yes               |
| Timeseries history      | Redis      | Yes               |
| Config audit log        | Redis      | Yes               |
| Config (algo/limit)     | Redis + properties | Yes      |

---

## Known Behaviours to Handle

**Bot detection is per IP, not per key.**
50 requests from one IP across any key triggers a 403 for ALL keys from
that IP. The dashboard should make this visually distinct from a 429
(rate limit) — they look similar but mean different things.

**Abuse blocklist has automatic TTL.**
A blocked client is automatically unblocked after the block duration
(default ~5 minutes). No manual action needed — but the dashboard
should show this expiry if possible.

**AIMD limit is live.**
`dev-key-dynamic`'s limit fluctuates every 10 seconds based on p50
latency. The most interesting dashboard view for this key is a line
chart of `X-RateLimit-Limit` over time — show it going down under load
and recovering when latency drops.

**`/metrics/gateway` lags by up to 10 seconds.**
MetricsForwarder posts every 10s. The data you see is never more than
10 seconds stale — communicate this to users (e.g. "updated 3s ago").

**Sliding window does not hard-reset.**
After 30 seconds, the previous window fades but doesn't zero out
immediately. Don't show a "window reset" indicator for sliding window
clients — it's misleading.

---

## Dashboard Priority — what to build first

**Tier 1 — Core monitoring (build first):**
1. Live request counter — total / allowed / blocked (from `/metrics`)
2. Per-client table — key, algorithm, requests, blocked, risk score
   (from `/metrics/clients`, poll every 5s)
3. Health status bar — gateway-1, gateway-2, Redis, backend
   (from `/health` on both ports, poll every 30s)

**Tier 2 — Security panels:**
4. Status code breakdown chart — 200 / 4xx / 5xx
   (from `/metrics/status`)
5. Suspicious IPs panel grouped by HIGH / MEDIUM / LOW
   (from `/metrics/suspicious/risk`)
6. Live log feed with filter controls
   (from `/metrics/logs` and `/metrics/logs/filter`)

**Tier 3 — Advanced / demo features:**
7. Timeseries chart (from `/metrics/timeseries`)
8. Per-gateway comparison — gateway-1 vs gateway-2 side by side
   (from `/metrics/gateway`)
9. AIMD live limit chart — show limit adjusting in real time
   (from `/metrics/clients`, track `dev-key-dynamic`)
10. Config audit log table (from `/admin/config/audit`)
11. Admin panel — change algorithm/limit, reset buttons
