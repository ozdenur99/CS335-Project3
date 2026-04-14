## **Date April 9th, 2026**
### Mentors Feedback
**Redis-Backed Distributed Rate Limiting with Dashboard**

**Key Deliverables:**
- ✅ Phase 1: Redis foundation (merged)
- 🔴 Phase 2-3: 4 rate-limit algorithms (token, fixed, sliding, leaky) → Redis
- 🔴 Phase 4: Runtime-configurable policies (tenant/app/client hierarchy)
- 🔴 Phase 5: Comprehensive metrics (latency, status codes, auto-export)
- 🔴 Phase 6: 2 gateway instances (prove distributed Redis)
- 🔴 Phase 7: Interactive multi-panel dashboard
- 🟡 B6: Locust load testing (realistic data)

### **1. AUTO-EXPORT & PERSISTENT LOGS (Theo Dumitrescu)**

**Quote:**

> "So and now you shut down the server do you have an automatic export that is periodically stored locally for or not... Probably that's the best next thing to follow up on... The main thing is to save them automatically on the files."
> 

**What Theo wants:**

- Automatic periodic export of request metrics/logs to files
- Data survives container restart
- Historical records for dashboard (not single session)
- Mentor Priority: Very high — "best next thing to follow up on"

**Why:**

> "it'll be better to focus on that one... in the last weeks we can talk about the last minute improvement... if we are going to have the dashboard, then at least even starting from the next meeting, we can talk a bit how to organize the demo"
> 

**Current state:** Metrics only in JVM memory (lost on restart)

**Technology needed:**

- Scheduled task (Spring `@Scheduled` annotation)
- File I/O to write JSON snapshots hourly
- Redis persistence (AOF or RDB)
- Hourly snapshot exports

---

### **2. DASHBOARD AGGREGATION & STORYTELLING (Theo Dumitrescu)**

**Quote on data aggregation:**

> "So you need to kind of build the logic to kinda count the request per client and also the overall request count... you need to think about how do you process into aggregate it to show the information... you want to see the trend of how the features are working, right?"
> 

**Quote on graphs:**

> "How many requests overall they get... the gateway scene that is coming in total across the clients and then maybe you want to have a view that is going to be like I want to actually see the request of all very specific client by the API... you create the same kind of time graph, but it's only based on that client IP right?"
> 

**Quote on HTTP status codes (crucial for error diagnosis):**

> "We need to also have it like in the dashboard to see OK the service suddenly starting to throw way more errors... How many are 200? How many are 500? just to see the over time like in the increase of error or like successful request and what on average?"
> 

**Quote on algorithms performance:**

> "For the algorithms it's more like what algorithms are how often they are detecting the blocking right or how much... how you want to even see for example for the client... how much the client is about to get blocked or considered suspicious"
> 

**Quote on storytelling through data:**

> "So you might want to just throw in a box of the dashboard to say these clients are not yet suspicious, but are close to becoming a suspicious mark... think about it in this way, like OK with this data that we have, what is actually meaningful for me to understand what's happening in the system overall"
> 

**What Theo wants:**

- Specific graphs for specific information (not overloaded single view)
- Format: diverse, graphs, text, pies etc
- Per-client breakdown with drill-down capability
- HTTP status codes breakdown: 200 vs 400 vs 429 vs 403 vs 500 distribution
- Latency trends over time
- Algorithm blocking frequency comparison
- Risk score visualization for clients approaching limits
- Comprehensive data aggregation (per-client, overall, time-based)
- **Dashboard as storytelling:** "See spike in blocks? Look at 5xx errors. Ah, backend issue, not abuse."

**Current State:**

- `MetricsService.java`: Basic AtomicInteger counters only
- No time-series data
- No status code tracking
- No latency tracking
- No drill-down capability

**Technology needed:**

- Redis Sorted Sets (timestamps + data)
- Data aggregation logic (counts per minute/hour)
- Dashboard charts: line graphs, pie charts, bar charts, tables
- Interactive table with drill-down UI (click client → see details)

---

### **3. CONFIGURABLE POLICIES (Runtime-changeable) (Theo Dumitrescu + Abhinaw Tiwari)**

**Theo quote on hierarchy:**

> "you configure the tenant to be is configured for like the token bracket... but then the application ID is actually configured on the sliding window... How are both configurations working together? What's the priority ordering?"
> 

**Theo quote on config UI (simple approach):**

> "you can just download it and then you upload your data and then upload it back... it really the whole file into just download and upload every time... Don't consider like to build UX like the normal like input form with each field and manually type all of them in the UX percent."
> 

**Abhinaw quote - demo moment:**

> "You could go one step further into making a single page Web page to be able to Change the config from the UI... That would immediately reflect in the metrics showing up in dashboard and if you can show this together, that could be a great story for the demo... The first point would be let them fill the gaps in metric side and then you externalize the configs"
> 

**What they want:**

- Algorithm selection at TENANT level (not just API key)
- Algorithm selection at APP level (within tenant)
- Priority order: App policy > Tenant policy > Client/API key > Global default
- API endpoint to GET/POST config (JSON upload/download)
- Runtime-changeable config stored in Redis (not hardcoded in code/properties, so no restart needed)
- **Demo moment:** Change config live → see metrics update in real-time without restart, see both gateways apply it instantly
- Both gateways share same Redis config

**Current State:**

- Config hardcoded in `application.properties` (startup-only)
- Algorithm locked to API key in `RateLimiter.registerClientPolicies()`
- No app-level algorithm support
- No runtime changes

**Technology needed:**

- ConfigController with runtime GET/POST **policy API**
- Redis Hash storage for **runtime** policies
- **`RateLimiter.reloadConfig()` + `@PostConstruct`**
- Config reload logic (no restart)
- JSON parser (Jackson)
- Both gateways share same Redis config

---

### **4. DISTRIBUTED SYSTEM PROOF (Implied by mentors)**

**Why 2 gateways matter:**

Mentors kept asking about configuration at different layers (tenant vs app) and how it "scales." This implies distributed thinking.

**What they want:**

- Prove Redis works across multiple gateway instances
- Both gateways share same rate limit counters (not separate HashMaps)
- Config change applies to both instantly
- Single source of truth (Redis)

**Current state:** 1 gateway only

**Technology needed:**

- docker-compose with 2 gateway services
- Both pointing to same Redis
- Coordinated state via Redis (no in-memory silos)

---

### **5. REDIS ARCHITECTURE DECISION (Abhinaw Tiwari)**

**Quote:**

> "If it's just for API keys, you don't really need Redis... Unless there is a need of it there is no point bonus point for fancy components... Unless there is a need of it,you don't need to go. But it won't be an architectural advice right now."
> 

**Translation:** Redis is justified only if:

- ✅ Distributed counters (multiple gateways)
- ✅ Persistent metrics (survive restart)
- ✅ Runtime config changes
- ❌ NOT just for storing static API keys

**Your use case:** All three ✅, so Redis is correct choice.

---

### **6. NATURAL PROGRESSION: METRICS → DASHBOARD (Abhinaw Tiwari)**

**Direct Quotes:**

> "Dashboard is a natural progression from where? From metrics that has generating even the APS that he has written could be used in the dashboard"
> 

> "So what naturally comes from the metrics that you are producing is all the fields that he has, fields and values. So what algorithm are you using? What IP? Was there what? What came into suspicion and everything that you already have in metrics"
> 

> "1st we need to focus on metrics like the log features and all this should display on dashboard."
> 

**What They Want:**

- Build metrics first (comprehensive)
- Dashboard uses existing metric fields naturally
- Don't force unrelated features into dashboard
- Focus on what you're already generating

**Current Implementation:** Start with metrics, then dashboard.

---

### **7. CONFIG UI ENHANCEMENT (Abhinaw Tiwari)**

**Direct Quotes:**

> "You could go one step further into making a single page Web page to be able to Change the config from the UI... That would immediately reflect in the metrics showing up in dashboard and if you can show this together, that could be a great story for the demo."
> 

> "The first point would be let them fill the gaps in metric side and then you externalize the configs and then we can see if we can Go towards making this story for the demo."
> 

**What They Want:**

- First: Finalize metrics
- Second: Externalize config (JSON upload/download)
- Third (Stretch): Add UI to change config live + see dashboard update

**Implementation Priority:**

1. Phase 1: ConfigController endpoints + JSON
2. Phase 2: (If time) Simple web UI to upload config

---

## **CURRENT STATE vs. TARGET STATE**

| Component | Current | Target | Status |
| --- | --- | --- | --- |
| **Algorithms** | 3 (token, fixed, sliding) | 4 + leaky bucket | ⏳ Leaky in progress |
| **Tenants** | 2 (acme, beta) | 3 (acme, beta, enterprise) | ⏳ Pending |
| **Apps per tenant** | 1 (dashboard under acme) | 2 per tenant | ⏳ Pending |
| **API key clients** | 4 | 6 (add enterprise tier) | ⏳ Pending |
| **Gateways** | 1 | 2 | ⏳ Pending |
| **Redis** | Foundation ✅ | Full integration | ✅ Done |
| **Metrics** | In-memory AtomicInteger | Redis Sorted Sets + export | ⏳ Critical |
| **Config** | Hardcoded | Runtime-changeable | ⏳ Critical |
| **Dashboard** | None | Multi-panel interactive | ⏳ Critical |
| **Latency tracking** | None | Per-request + aggregated | ⏳ Critical |
| **Status code tracking** | None | 200/429/403/400/500 breakdown | ⏳ Critical |
| **Auto-export** | None | Hourly JSON snapshots | ⏳ Critical |

**Legend:** ✅ Done | ⏳ In Progress | 🔴 Critical | 🟡 Medium | 🟢 Optional

---


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