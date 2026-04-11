## API Gateway Endpoints

Gateway base URL: `http://localhost:8080`  
Backend base URL: `http://localhost:8081`

---

### Core proxy route

- `GET /api/{guid}/notes`
- Required header: `X-API-Key`
- Optional headers: `X-Tenant-Id`, `X-App-Id`

Common status codes:
- `200` success
- `401` invalid/missing API key
- `429` rate limit exceeded
- `403` blocked/abuse detection

---

### Metrics + Dashboard APIs

- `GET /metrics`
- `GET /metrics/logs`
- `GET /metrics/logs/filter`
- `GET /metrics/logs/export/json`
- `GET /metrics/logs/export/csv`
- `GET /metrics/suspicious`

Dashboard endpoints:
- `GET /metrics/dashboard/overall-trend?minutes=60`
- `GET /metrics/dashboard/client-trend?apiKey=...&ip=...&minutes=60`
- `GET /metrics/dashboard/status-trend?minutes=60`
- `GET /metrics/dashboard/status-distribution?minutes=60`
- `GET /metrics/dashboard/latency-trend?minutes=60`
- `GET /metrics/dashboard/algorithm-blocking?minutes=60`
- `GET /metrics/dashboard/risk-leaderboard?minutes=30&limit=20`
- `GET /metrics/dashboard/client-detail?apiKey=...&ip=...&minutes=60&limit=50`
- `GET /metrics/dashboard/events?minutes=30&limit=100&tenantId=...&appId=...&apiKey=...&ip=...&statusCode=...&algorithm=...&decision=...`

---

### Runtime Config APIs (no restart required)

- `GET /config/rate-limit`
- `GET /config/rate-limit/download`
- `POST /config/rate-limit` (JSON body)
- `POST /config/rate-limit/upload` (raw JSON body)

Policy priority:
1. App policy
2. Tenant policy
3. Client policy
4. Global default

