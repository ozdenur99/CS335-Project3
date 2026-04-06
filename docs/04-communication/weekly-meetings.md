## **Date April 2nd, 2026**
Attendees: Theo, Mateo, Ozdenur, Sean, Avneet, Vlad, and Cathy. 
Theme: Demo and Optimization

1. Overview
This document outlines planned enhancements, priorities, and demo strategy. The focus is
to improve realism, flexibility, and demo clarity.
------------------------------------------------------------
2. Core Feature Enhancements
2.1 Multiple Rate Limiting Algorithms
Support multiple algorithms:
- Token Bucket (existing)
- Fixed Window
- Sliding Window
Use a common interface (RateLimiterStrategy).
Assign algorithms per client (e.g. Client A = Token Bucket, Client B = Fixed Window).
Optional: Adaptive/expandable buckets based on previous traffic.
2.2 Client-Aware Logging & Tracking
Log per client:
- API key / client ID
- Endpoint
- Status (200, 401, 429, 403)
- Decision (allowed / blocked)
- Timestamp
Store logs in memory initially, optional file/database later.
Allow filtering by client or status.
Simulate multiple clients using different API keys.
2.3 Abuse Detection Improvements
Add:
- Blocklist (instant block)
- Allow list (never block certain clients)
- Configurable thresholds (e.g. window size)
- Escalation: repeated 429 → block → 403
Advanced: Sync blocked clients across gateways (e.g. Redis).
2.4 Configuration via Properties
Move all important variables to configuration:
- Rate limits
- Thresholds
- Durations
- Window sizes
2.5 Frontend Dashboard
Single-page dashboard:
- Request simulator
- Response viewer
- Metrics panel
- Logs panel
Optional: graphs for requests over time and allowed vs blocked.
------------------------------------------------------------
3. Optional Enhancements
- Redis for shared state and distributed rate limiting
- Containerisation for realistic deployment
------------------------------------------------------------
4. Demo Strategy

4.1 Flow:
-- 1. Normal usage → 200
-- 2. Rate limiting → 429
-- 3. Continued abuse → 403
-- 4. Compare algorithms across clients
-- 5. Show logs and metrics in real time

4.2 Multiple Client Simulation
- Use different API keys
- Assign different algorithms and limits
------------------------------------------------------------
5. Presentation Plan
Total: 10 minutes (8 + 2 Q&A)
Suggested structure:
- Problem (1 min)
- Architecture (1–2 min)
- Rate limiting (2–3 min)
- Abuse detection (2 min)
- Demo (2–3 min)
Use 2 speakers for better pacing.
------------------------------------------------------------
6. Timeline
Code Freeze: 30th April
Before:
- Multiple algorithms
- Abuse detection complete
- Config setup
- Basic dashboard
After:
- Demo polish
- Practice
------------------------------------------------------------
7. Key Engineering Principles
- Separation of concerns
- Configurability
- Observability
- Scalability (Redis, multi-gateway)
- Algorithm trade-offs
------------------------------------------------------------
8. Summary
A configurable API gateway that supports multiple rate limiting strategies, detects abuse,
and provides real-time observability.



## **Date March 25th, 2026**
Attendees: Theo, Mateo, Ozdenur, Sean, Avneet, Vlad, and Cathy. 
Location: Microsoft Dublin Office
1. For abuse detection, try to simulate spike,
2. JA4 finger: is spring boot compitable to it? 
   Spring boot: add hooks, some extensions can make GA4finger happen, still it’s optional stretch feature.
3. For client, mentor Theo mentioned another client, Bruno.
4. Recommended stretches: dashboard. Vlad will work on this.
5. Demo: gemini  some ai tools   +  recording + ppt, live demo is not required. Demo video. how long is recommended? is 5mins good? to be confirmed, not so urgent.



## **Date March 19th, 2026**

### Mentor Abhinaw's Feedback

1. work on functions first
2. will tell us Microsoft visit time and presentation time

### Team Delivery

1. Demo backend 
2. Talk about our willingness to speed up project a bit in recent weeks
3. Display gantt chart with task assignments and progress planning


## **Date March 12th, 2026**

### Mentor Abhinaw's Feedback
1. write user stories
2. give feedback on gantt chart, backend might need 2 people
3. start coding

### Team Delivery
1. Problem
Backend APIs need protection from excessive, malformed, or abusive requests.
2. Solution
A gateway sits between client and backend, validating keys, enforcing rate limits, detecting abuse, logging and metrics.
3. Tech Stack
 Java 21, Spring Boot 3, Maven, Postman, VS Code
4. MVP Scope
Current cope: 
    
    backend, API key validation, fixed-window rate limiting, basic abuse detection, logging and metrics.
    
    ### **Minimum success criteria**
    
    By the end, your system should:
    - Accept requests through the gateway
    - Forward allowed requests to the backend
    - Block excess requests with 429 Too Many Requests
    - Log what happened and why
    
    ### **Stretch (optional, not required)**
    - Observability dashboard
    - Token bucket algorithm
    - Containerize gateway + backend (Docker)
    - Distributed counters using Redis
    - Configurable policies
    - Locust (loading test)
    - Tenant and app/client scoped rate‑limit buckets
    - JA4 Fingerprints
    
5. Known Limitations of In-memory counters
This rate limiter stores the counters in memory meaning when the gateway restarts, the
counters reset.
Further down the line, if we have time, we can use Redis potentially to fix this.

6. Architecture sketch
https://excalidraw.com/#room=f78d67fb86f551429f25,RjABcy6uMKNo5UiRu7y7tg


## **Date March 5th, 2026**

### Action Items from Mentor Theo Input:
**Weeks 1: Understand & Scope**
**Goal:** Clarity before code
- Restate the problem in your own words
- Sketch system diagram on paper
- Decide language, framework, and scope
- Define “done” for your MVP
- Github repository
Deliverable: short design note + architecture sketch