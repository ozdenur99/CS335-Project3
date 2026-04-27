# Dashboard Integration Guide

## Setup

| Service   | URL                        |
|-----------|----------------------------|
| Gateway 1 | http://localhost:8080      |
| Gateway 2 | http://localhost:8082      |
| Backend   | http://localhost:8081      |

**CORS is enabled for `http://localhost:3000`** — you can call any gateway endpoint directly from the browser with no proxy needed.

---

## Authentication

| Endpoint group       | Header required                        |
|----------------------|----------------------------------------|
| `/metrics/*`         | None — open access                     |
| `/health`            | None — open access                     |
| `/admin/*`           | `X-Admin-Key: admin-secret-key`        |
| `/api/*`             | `X-API-Key: <key>` (not for dashboard) |

---

## Metrics Endpoints

### `GET /metrics`
Overall gateway summary. Good for top-level counters.

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

### `GET /metrics/clients`
Per-client breakdown — most useful for a live dashboard table.

```json
{
  "dev-key-token": {
    "totalRequests": 30,
    "blockedCount": 5,
    "latency": { "p50": 4, "p95": 12, "p99": 20 },
    "statusCodes": { "200": 25, "429": 5 },
    "riskScore": "45%"
  }
}
```

**Poll every 5 seconds.**

---

### `GET /metrics/status`
Status code breakdown at gateway level and per client. Good for a 4xx/5xx chart.

```json
{
  "gateway": { "200": 80, "400": 2, "429": 15, "403": 3 },
  "perKey": {
    "dev-key-token": { "200": 25, "429": 5 },
    "dev-key-fixed": { "200": 55, "403": 3 }
  }
}
```

**Poll every 5 seconds.**

---

### `GET /metrics/gateway`
Per-gateway snapshot — shows both gateway-1 and gateway-2 side by side.
Data comes from `MetricsForwarder` which posts every **10 seconds**.

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
    "allowedRequests": 30
  }
}
```

**Poll every 10–15 seconds** (no point polling faster than MetricsForwarder's interval).

---

### `GET /metrics/timeseries?range=week`
Historical request counts stored in Redis. Persists across gateway restarts.

**Query params:**
- `range` — `hour`, `day`, `week` (default: `week`)
- `from` — epoch milliseconds (optional)
- `to` — epoch milliseconds (optional)

Returns a list of `{ timestamp, count }` objects.

> **IMPORTANT — timestamps are epoch milliseconds:**
> ```js
> // JavaScript
> new Date(timestamp)
>
> // Grafana — divide by 1000 (Grafana expects seconds)
> timestamp / 1000
> ```

**Poll every 30 seconds** (slower changing data).

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

### `GET /metrics/risk`
Risk score for every client (how close they are to hitting rate limit).

```json
{
  "dev-key-token": "45%",
  "dev-key-fixed": "100%"
}
```

---

### `GET /metrics/suspicious`
Set of IPs currently flagged as bots (over 50 requests from that IP).

```json
["10.0.0.1", "192.168.1.5"]
```

---

### `GET /metrics/suspicious/risk`
Suspicious IPs grouped by severity. Thresholds: LOW > 50, MEDIUM > 100, HIGH > 200 requests.

```json
{
  "HIGH": ["10.0.0.1"],
  "MEDIUM": ["192.168.1.5"],
  "LOW": []
}
```

---

### `GET /metrics/logs`
Last 100 requests logged by the gateway. Each entry:

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

`decision` is always `"ALLOWED"` or `"BLOCKED"`.
`reason` values: `ok`, `rate_limit_exceeded`, `invalid_or_missing_key`, `abuse_detected`, `suspected_bot`.

---

### `GET /metrics/logs/filter`
Filter logs by any combination of fields.

**Query params (all optional):**
- `decision` — `ALLOWED` or `BLOCKED`
- `reason` — e.g. `rate_limit_exceeded`
- `apiKey` — e.g. `dev-key-token`
- `algorithm` — e.g. `token`

```
GET /metrics/logs/filter?decision=BLOCKED&reason=rate_limit_exceeded
```

---

### `GET /metrics/logs/export/json`
Downloads all logs as a `.json` file (attachment).

### `GET /metrics/logs/export/csv`
Downloads all logs as a `.csv` file (attachment).

---

## Health Endpoint

### `GET /health`
Shows live status of Redis and the backend. Returns `200` if healthy, `207` if degraded.

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
All require header: `X-Admin-Key: admin-secret-key`

### `GET /admin/config`
Current rate limit config for all tenants and apps.

### `POST /admin/config`
Change algorithm or limit for a tenant/app at runtime. Syncs to both gateways via Redis pub/sub instantly.

```json
{
  "tenant": "tenant-acme",
  "app": "dashboard",
  "algorithm": "sliding",
  "limit": 5
}
```

Available algorithms: `token`, `fixed`, `sliding`, `leaky`, `dynamic`

### `GET /admin/config/audit`
Last 100 config changes, newest first. Persists in Redis across restarts. Includes changes from both gateways.

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
Clears bot detection data (IP counts + flagged IPs in Redis).

### `POST /admin/reset/blocked`
Clears the abuse blocklist.

### `POST /admin/reset/all`
Clears both. **Run this before every demo section.**

---

## Response Headers on Every Request

| Header | Value |
|--------|-------|
| `X-Request-ID` | UUID — matches `requestId` in logs |
| `X-RateLimit-Limit` | Resolved rate limit for this client |
| `X-RateLimit-Algorithm` | Algorithm currently enforced |
| `Retry-After` | Seconds to wait — **only on 429 responses** |

---

## Error Response Format

All gateway-level errors (401, 403, 429) return:

```json
{
  "status": 429,
  "error": "Too Many Requests",
  "message": "Request could not be processed at this time.",
  "path": "/api/test123/notes",
  "requestId": "a3f2c1d4-..."
}
```

The `requestId` matches the `X-Request-ID` header and the `requestId` field in `/metrics/logs`.

---

## Known Behaviours to Handle

**1. In-memory vs Redis data**
- `/metrics/clients`, `/metrics/status`, `/metrics/risk`, `/metrics/logs` — **in-memory**, reset on gateway restart
- `/metrics/timeseries` — **Redis**, survives restarts
- `/admin/config/audit` — **Redis**, survives restarts

**2. `/metrics/gateway` lag**
Data updates every 10 seconds via MetricsForwarder. Don't poll faster than that.

**3. Bot detection is per IP, not per key**
One IP sending 50 total requests (across any API key) triggers the 403 bot block for ALL keys from that IP. The dashboard should show this clearly — it's a feature, not a bug.

**4. Rate limiting is per key, not per IP**
Two clients using the same API key from different IPs share the same rate limit counter. Two clients using different API keys from the same IP have independent counters.

**5. Sliding window does not hard-reset**
After a window expires the previous count fades gradually using a weight. Don't expect a clean zero after `windowSizeMs` milliseconds.
