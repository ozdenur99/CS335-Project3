
## **Date March 25th, 2026**
Attendees: Theo, Mateo, Ozdenur, Sean, Avneet, Vlad, and Cathy. 
Location: Microsoft Dublin Office
1. For abuse detection, try to simulate spike, mimic 
2. GA4 finger: is spring boot compitable to it? 
   Spring boot: add hooks, some extensions can make GA4finger happen, still it’s optional stretch feature.
3. For client, mentor Theo mentioned another client, not postman, more advanced.
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
![alt text](image.png)
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