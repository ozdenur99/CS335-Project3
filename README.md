# CS335 Microsoft Project 3:
## Rate Limiting and Abuse Detection API Gateway

> NOT FINAL / TO BE UPDATED (last update: Mar 18th 2026)

---

## Project Overview
Added by Mateo

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

> Always start the **Backend first** on `port 8081`, then the **Gateway** on `port 8080`. The Gateway needs the Backend running before it can forward requests
=========================================================
## Using the API Gateway
Added by Cathy

All requests to the gateway **must include** an `X-API-Key` header.

### Valid dev keys (local development only)
| Key | Purpose |
|-----|---------|
| `dev-key-alpha` | Local dev / testing |
| `dev-key-beta`  | Local dev / testing |

### Example request (via curl)
```bash
curl http://localhost:8080/api/test123/notes \
  -H "X-API-Key: dev-key-alpha"
```
=========================================================


The below has many overlap with the above section
Suggest to delete or modify them later.
=========================================================
# CS335 readme for Backend + API Gateway System
Added by Avneet

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