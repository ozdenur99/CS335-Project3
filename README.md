# CS335 Microsoft Project 3:
## Rate Limiting and Abuse Detection API Gateway

> NOT FINAL / TO BE UPDATED (last update: April 9th 2026)

---

## Project Overview
> Added by Mateo

This project implements a **Rate Limiting and Abuse Detection API Gateway**. Which is the single point of access for clients wanting to use backend services. The gateway handles incoming requests, routes valid ones to the appropriate service and protects backend services by controlling access frequency and detecting user misuse

| | |
|---|---|
| **Language** | Java 21 |
| **Framework** | Spring Boot 3.5.11 |
| **Web Layer** | Spring MVC (via spring-boot-starter-web) |
| **Build tool** | Maven Wrapper exclusively |
| **Testing** | JUnit 5 (via spring-boot-starter-test) |
| **State** | In-memory: ConcurrentHashMap for MVP (no database) |
| **API Client** | Acts as a postman for all manual and automated testing |

---
For more details, please refer to our Docs.
## Documentation
- [Architecture Decisions (ADRs)](./docs/01-architecture/decisions.md)
- [Testing Results](./docs/03-testing)
- [Algorithms Logic Specs](./docs/02-algorithms)
- [Weekly Meeting Minutes](./docs/04-communication/weekly-meetings.md)
- [API Specification](./docs/01-architecture/endpoints.md)

## Architecture

[ Client ]  ──►  [API Gateway: 8080]  ──►  [Backend Service: 8081]


The API Gateway acts as a "middleman" between clients and a simple CRUD backend service, enforcing rate limits and detecting abuse patterns to protect the backend from excessive or malicious traffic

<img width="1488" height="1988" alt="Untitled-2026-03-19-1406" src="https://github.com/user-attachments/assets/f588bc33-5b06-4c89-afc3-1ba90c084f85" />

Link to detailed Architecture Diagram: https://excalidraw.com/#room=f78d67fb86f551429f25,RjABcy6uMKNo5UiRu7y7tg

---

## Key Features

### Gateway Components

| Component | Behaviour |
|-----------|-----------|
| **API Key Validation** | Rejects requests with missing or invalid `X-API-Key` headers → `401 Unauthorized` |
| **Rate Limiting** | In-memory counters limits the requests per client (`X` req/min) → `429 Too Many Requests` |
| **Abuse Detection** | Detects spikes and repeated failures and it temporarily or permanently blocks them → `403 Forbidden` |
| **Request Logging** | Captures the timestamp, API key, path, decision, and reason for every request |
| **Request Forwarding** | Passes the validated requests to the backend service on `port 8081` |

### HTTP Status Codes

| Code | Meaning |
|------|---------|
| `401 Unauthorized` | Invalid or missing API Key |
| `429 Too Many Requests` | Rate limit exceeded |
| `403 Forbidden` | Temporarily blocked due to abuse |

### Request Path

Every request passes through the Gateway filter chain in this order. The Backend never receives unauthenticated or rate-limited requests


1. Client (aka Postman)  →  sends request with X-API-Key to `port 8080`
2. ApiKeyFilter      →  invalid/missing key? returns `401 Unauthorized`
3. RateLimitFilter   →  limit exceeded? returns `429 Too Many Requests`
4. AbuseFilter       →  spiked/repeated failures? returns `429 Too Many Requests` or `403 Forbidden`
5. RequestLogger     →  logs: timestamp, key, path, decision, reason
6. Gateway           →  forwards request to Backend `port 8081`
7. Backend           →  returns response -> Gateway passes it back unchanged

---

## Backend Service

The backend relies fully on the gateway. It has no built-in authentication or rate limiting algorithm

```
POST   /api/{guid}/notes        →  Create a note
GET    /api/{guid}/notes        →  List all notes
PUT    /api/{guid}/notes/{id}   →  Update a note
DELETE /api/{guid}/notes/{id}   →  Delete a note
```

> **Note:** `{guid}` represents a user/tenant scope, allowing the same backend to serve multiple clients

---

## Rate Limiting Algorithm

Acts as a traffic controller for the API: setting and monitoring the frequency of client requests

* **Prevents DoS/DDoS Attacks:** caps requests from a single source, defending against attempts to overwhelm the system
* **Ensures Fair Use:** prevents any single client from monopolizing resources
* **Protects Backend Services:** acts as a buffer against sudden traffic surges

---

### Port Configuration

Both services run simultaneously and must use different ports, if they conflict, neither will start

| Service | Port | Config location |
|---------|------|-----------------|
| API Gateway | `8080` | `api-gateway/src/main/resources/application.properties` |
| Backend Service | `8081` | `backend-service/src/main/resources/application.properties` |

=========================================================
## Using the API Gateway
> Added by Cathy

All requests to the gateway **must include** an `X-API-Key` header.

### Valid dev keys (local development only)
| Key | Purpose |
|-----|---------|
| `dev-key-token` | Local dev / testing |
| `dev-key-fixed`  | Local dev / testing |
| `dev-key-sliding`  | Local dev / testing |
| `dev-key-business`  | Local dev / testing |

### Example request (via curl)
```bash
curl http://localhost:8080/api/test123/notes \
  -H "X-API-Key: dev-key-token"
```
=========================================================


> The below has many overlap with the above section. Suggest to delete or modify them later.
=========================================================
# Backend + API Gateway System
> Added by Avneet

This project contains a Spring Boot Backend Service and a custom API Gateway built using Spring MVC.

The system follows a microservices-style architecture where:
- Backend handles business logic (notes API)
- API Gateway acts as the entry point and forwards requests using RestTemplate (manual routing)

--------------------------------------------------

ARCHITECTURE

Client (Browser / Postman)
        ↓
API Gateway (Port 8080)
        ↓
Backend Service (Port 8081)
        ↓
Response returned

--------------------------------------------------

SERVICES & PORTS

Backend Service → http://localhost:8081  
API Gateway → http://localhost:8080  

--------------------------------------------------

REQUIREMENTS
- Java 21
- Maven
--------------------------------------------------

HOW TO RUN THE PROJECT
Step 0 — Open Project
cd CS335_project
--------------------------------------------------

TERMINAL 1 — START BACKEND

cd backend-service
mvn clean install
mvn spring-boot:run

Expected Output:

Tomcat started on port 8081  
Started BackendServiceApplication  

Test Backend:

http://localhost:8081/health  

Expected:
Backend is running  

Optional:
http://localhost:8081/hello  

--------------------------------------------------

TERMINAL 2 — START API GATEWAY

(Open new terminal)

cd api-gateway
mvn clean install
mvn spring-boot:run

Expected Output:

Tomcat started on port 8080  
Started ApiGatewayApplication  

--------------------------------------------------

IMPORTANT

- Always start Backend first  
- Then start Gateway  
- Keep both terminals running  

--------------------------------------------------

API ENDPOINTS (BACKEND)

All note operations require a GUID (example: test123)

--------------------------------------------------

1. GET ALL NOTES

curl http://localhost:8081/api/test123/notes

Expected Output:
[]  
OR  
[{"id":"1","content":"My first note"}]

--------------------------------------------------

2. CREATE NOTE

curl -X POST http://localhost:8081/api/test123/notes \
-H "Content-Type: application/json" \
-d '{"id":"1","content":"My first note"}'

--------------------------------------------------

3. UPDATE NOTE

curl -X PUT http://localhost:8081/api/test123/notes/1 \
-H "Content-Type: application/json" \
-d '{"id":"1","content":"Updated note"}'

--------------------------------------------------

4. DELETE NOTE

curl -X DELETE http://localhost:8081/api/test123/notes/1

--------------------------------------------------

USING API GATEWAY (MAIN ENTRY POINT)

Instead of calling backend directly, use:

http://localhost:8080/api/test123/notes  

--------------------------------------------------

HOW IT WORKS

1. Client sends request to API Gateway (port 8080)  
2. GatewayController receives the request  
3. Gateway forwards request to backend using RestTemplate  
4. Backend processes request  
5. Response is returned through Gateway  

---

## Logging & Metrics
> Added by Mateo

It Records every request passing through the gateway and tracks whether it was allowed or blocked. Results are viewable at `/metrics` for live counts and `/metrics/logs` for the last 100 requests

It sits at the outermost layer of the filter chain (`@Order(1)`) and wraps all requests that come through the gateway. It captures: timestamp, API key, client IP, path, decision (ALLOWED/BLOCKED), reason, and which rate limiting algorithm was used. It also includes bot detection which automatically flags IPs that exceed 50 requests (number of requests can be changed)

---


### Files
| File | Description |
|------|-------------|
| `LogEntry.java` | Data model for a single request log record |
| `RequestLogger.java` | Thread-safe queue that stores up to 100 log entries |
| `MetricsService.java` | Tracks total, blocked, and per-key request counts |
| `MetricsController.java` | REST endpoints to expose metrics, logs, filters, and exports |
| `LoggingFilter.java` | Filter chain wrapper that records every request outcome |
| `BotDetector.java` | Flags IPs that exceed the suspicious request threshold |

---
## Logging & Metrics TEST Instructions:

### Starting the Services

Both services must be running simultaneously. Always start the backend first.

**Start the Backend**
```bash
cd backend-service
./mvnw spring-boot:run
```
Wait until you see:
```
Tomcat started on port 8081
```

**Start the API Gateway**
```bash
cd api-gateway
./mvnw spring-boot:run
```
Wait until you see:
```
Tomcat started on port 8080
```

---

## Valid API Keys

| Key | Algorithm | Limit |
|-----|-----------|-------|
| `dev-key-token` | Token Bucket | 3 req/window |
| `dev-key-fixed` | Fixed Window | 3 req/window |
| `dev-key-sliding` | Sliding Window | 3 req/window |
| `dev-key-business` | Token Bucket | 10 req/window |

> **Note:** Limits are temporarily set to 3 for testing. Will be restored to 5 before final submission.

---

## Sending Test Requests

### PowerShell

If PowerShell shows a security warning, press **A (Yes to All)** to continue.

**Valid Request (expect 200)**
```powershell
curl -Uri "http://localhost:8080/api/test123/notes" -Headers @{"X-API-Key"="dev-key-token"}
```
Expected result: request passes through to the backend and returns `[]`

---

**Invalid API Key (expect 401)**
```powershell
curl -Uri "http://localhost:8080/api/test123/notes" -Headers @{"X-API-Key"="bad-key"}
```
Expected result:
```json
{"status":401,"error":"Unauthorized","message":"Request could not be authorised.","path":"/api/test123/notes"}
```

---

**Burst Requests to Trigger Rate Limit (expect 429 after 3rd request)**
```powershell
1..8 | ForEach-Object {
    try {
        $r = Invoke-WebRequest -Uri "http://localhost:8080/api/test123/notes" -Headers @{"X-API-Key"="dev-key-fixed"}
        Write-Host "Request $_`: $($r.StatusCode)"
    } catch {
        $code = $_.Exception.Response.StatusCode.value__
        Write-Host "Request $_`: $code"
    }
}
```
Expected result: first 3 requests return 200, remaining return 429.

---

**Abuse Detection (expect 403)**
```powershell
1..12 | ForEach-Object {
    try {
        $r = Invoke-WebRequest -Uri "http://localhost:8080/api/test/notes" -Headers @{"X-API-Key"="dev-key-sliding"}
        Write-Host "Request $_`: $($r.StatusCode)"
    } catch {
        $code = $_.Exception.Response.StatusCode.value__
        Write-Host "Request $_`: $code"
    }
}
```
Expected result: returns 403 around request 12 once the abuse detection threshold is hit.

---

**Bot Detection (expect IP flagged after 50+ requests)**
```powershell
1..55 | ForEach-Object {
    try {
        Invoke-WebRequest -Uri "http://localhost:8080/api/test123/notes" -Headers @{"X-API-Key"="dev-key-token"} | Out-Null
    } catch {}
    Write-Host "Request $_"
}
```
Then check `http://localhost:8080/metrics/suspicious` (should show bot IP)

---

### IntelliJ Terminal

The same PowerShell commands above work in the IntelliJ terminal. Open it via the **Terminal** tab at the bottom of IntelliJ and run the commands directly.

Alternatively use curl on Mac or Linux:
```bash
curl http://localhost:8080/api/test123/notes -H "X-API-Key: dev-key-token"
```

---

### Postman (VS Code)

1. Open Postman and create a new request
2. Set the method to **GET**
3. Set the URL to `http://localhost:8080/api/test123/notes`
4. Go to the **Headers** tab and add:
   - Key: `X-API-Key`
   - Value: `dev-key-token`
5. Click **Send**

To test different scenarios change the key value:
- Valid: `dev-key-token`, `dev-key-fixed`, `dev-key-sliding`, `dev-key-business`
- Invalid: any other value e.g. `bad-key`

To trigger the rate limit click Send rapidly more than 3 times with the same key.

---

## 4. Checking Metrics and Logs

All endpoints below require no API key.

| Endpoint | Description |
|----------|-------------|
| `GET /metrics` | Live count of total, blocked, allowed, and per-key requests |
| `GET /metrics/logs` | Last 100 requests with full details including IP and algorithm |
| `GET /metrics/logs/filter` | Filter logs by decision, reason, apiKey, or algorithm |
| `GET /metrics/logs/export/json` | Download all logs as a JSON file |
| `GET /metrics/logs/export/csv` | Download all logs as a CSV file |
| `GET /metrics/suspicious` | List of IPs flagged as potential bots |
| `GET /metrics/dashboard/overall-trend?minutes=60` | Overall request count trend (time series) |
| `GET /metrics/dashboard/client-trend?apiKey=...&ip=...&minutes=60` | Per-client request trend (drill-down ready) |
| `GET /metrics/dashboard/status-trend?minutes=60` | Time-series status split (200/400/401/403/429/500) |
| `GET /metrics/dashboard/status-distribution?minutes=60` | Status code distribution for selected window |
| `GET /metrics/dashboard/latency-trend?minutes=60` | Latency trend (avg/p50/p95) |
| `GET /metrics/dashboard/algorithm-blocking?minutes=60` | Blocking frequency grouped by algorithm and reason |
| `GET /metrics/dashboard/risk-leaderboard?minutes=30&limit=10` | Clients near suspicious/blocking limits |
| `GET /metrics/dashboard/client-detail?apiKey=...&ip=...` | Client drill-down with recent events |

### Example `/metrics` response
```json
{
  "totalRequests": 8,
  "blockedRequests": 2,
  "allowedRequests": 6,
  "perKey": {
    "dev-key-token": 3,
    "dev-key-fixed": 4,
    "MISSING": 1
  }
}
```

### Example `/metrics/logs` response
```json
[
  {
    "timestamp": "2026-04-07T14:02:11",
    "apiKey": "dev-key-token",
    "ip": "127.0.0.1",
    "path": "/api/test123/notes",
    "decision": "ALLOWED",
    "reason": "ok",
    "algorithm": "token"
  },
  {
    "timestamp": "2026-04-07T14:02:12",
    "apiKey": "MISSING",
    "ip": "127.0.0.1",
    "path": "/api/test123/notes",
    "decision": "BLOCKED",
    "reason": "invalid_or_missing_key",
    "algorithm": "token"
  }
]
```

### Checking logs & metrics
```
http://localhost:8080/metrics
http://localhost:8080/metrics/logs
http://localhost:8080/dashboard.html
```
To check any suspicious bot IPs
```
http://localhost:8080/metrics/suspicious
```

### Filtering logs
Check which requests were BLOCKED, ALLOWED, for what reason where they allowed or blocked, what type of dev-key was used, what type of rate-limitng algorithm was used

```
http://localhost:8080/metrics/logs/filter?decision=BLOCKED
http://localhost:8080/metrics/logs/filter?decision=ALLOWED

http://localhost:8080/metrics/logs/filter?reason=ok
http://localhost:8080/metrics/logs/filter?reason=rate_limit_exceeded
http://localhost:8080/metrics/logs/filter?reason=invalid_or_missing_key
http://localhost:8080/metrics/logs/filter?reason=abuse_detected

http://localhost:8080/metrics/logs/filter?apiKey=dev-key-token
http://localhost:8080/metrics/logs/filter?apiKey=dev-key-business
http://localhost:8080/metrics/logs/filter?apiKey=dev-key-fixed
http://localhost:8080/metrics/logs/filter?apiKey=dev-key-sliding

http://localhost:8080/metrics/logs/filter?algorithm=token
http://localhost:8080/metrics/logs/filter?algorithm=business
http://localhost:8080/metrics/logs/filter?algorithm=fixed
http://localhost:8080/metrics/logs/filter?algorithm=sliding
```

Multiple filters can be combined:
```
http://localhost:8080/metrics/logs/filter?decision=BLOCKED&algorithm=token
```

---

## 5. Exporting Logs

```
http://localhost:8080/metrics/logs/export/json
```
Downloads a `logs.json` file with all current log entries.

```
http://localhost:8080/metrics/logs/export/csv
```
Downloads a `logs.csv` file that can be opened in Excel or any spreadsheet app.

---

## 6. Dashboard View (Storytelling)

Open:
```
http://localhost:8080/dashboard.html
```

Dashboard includes:
- Overall request trend graph
- Status distribution pie + status trend graph (200/400/401/403/429/500)
- Latency trend graph (avg/p50/p95)
- Blocking by algorithm bar chart
- Risk leaderboard and clickable per-client drill-down
- Story box that helps distinguish backend issues (5xx) vs abuse pressure (403/429)

---

> Brief Overview
--------------------------------------------------

TECHNOLOGIES USED

- Java  
- Spring Boot (3.5.11)  
- Maven  
- REST APIs  

--------------------------------------------------

COMMON ISSUES

Port conflict → Ensure backend = 8081, gateway = 8080  
404 error → Check correct endpoint (/api/...)  
500 error → Backend not running  
Connection refused → Start backend first  

--------------------------------------------------

FINAL CHECKLIST

Backend running on 8081  
Gateway running on 8080  
/health works on backend  
/api/test123/notes works on gateway  

--------------------------------------------------

PROJECT STATUS

Backend Service — Working  
API Gateway — Working  
Manual Routing — Implemented  
End-to-End Communication — Working  
=======
