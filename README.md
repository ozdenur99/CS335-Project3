# CS335: Microsoft Project 3: Design / Implementation of a Rate Limiting and Abuse Detection API Gateway 
# (NOT FINAL / TO BE UPDATED)

# Project Overview
To create an Rate Limiting and Abuse Detection API Gateway, which will be the single point of access for clients wanting to use backend services. The gateway handles requests from clients and routes them to the appropriate service if the request is valid. The gateway will also protect these backend services by controlling how often they are accessed and spotting any misuse

Core Framework: Java21, Spring Boot 3, [...]

# Basic Architecture
[Client] → [API Gateway] → [Backend Service]

The API gateway acts as a middleman between clients and a simple CRUD backend service, enforcing rate limits and detecting abuse patterns to protect the backend from excessive or malicious traffic

# KEY FEATURES

# Gateway Components
- API Key Validation: Rejects requests with missing/invalid X-API-Key headers (returns 401 Unauthorized)
  
- Per-Client Rate Limiting: In-memory counters set different limits per client (X req/min). If the limit is exceeded, returns 429 Too Many Requests

- Abuse Detection: Identifies suspicious patterns like request spikes or repeated malformed/invalid data and temporarily/fully blocks them (403 Forbidden)
  
- Request Logging: Captures timestamp, API key, path, decision and reason for every request
  
- Request Forwarding: Passes validated requests to the backend service

# HTTP Status:
- _401 Unauthorized_: Invalid/missing API Key
  
- _429 Too Many Requests_:	Rate Limit Exceeded
  
- _403 Forbidden	Temporarily_: tEMPORARILY blocked due to abuse

If the request passes the validation, rate limiting and abuse checks. The gateway forwards it to the backend service

# Backend Service (with CRUD-style Rest API)
Backend fully reliant on the gateway with no built-in authentication or rate limiting:
e.g.
- POST   /api/{guid}/notes          -> Creates note

- GET    /api/{guid}/notes          -> Lists notes

- PUT    /api/{guid}/notes/{id}     -> Updates note

- DELETE /api/{guid}/notes/{id}     -> Deletes note

_{guid} represents a user/tenant scope, which allows the same backend to serve for multiple clients_

# Rate Limiting
Rate limiting works simlarly to a traffic controller for the API by setting, limiting and monitoring the frequency of client requests as well as:

- Preventing Denial of Service (DDoS) Attacks: 
By capping requests from a single   source, rate limiters defend against attempts to overwhelm your system

- Prioritizing Fair Use: 
It prevents any single client from monopolizing resources, ensuring equitable access for all users

- Protecting the Backend Services from overloading: 
It acts as a buffer, protecting databases and microservices from being flooded by sudden traffic surges

# Stretch/Extra Features to consider
The main focus is getting the MVP (Minimum Viable Product) fully working, but there are also other features to consider if they’re able to be worked on later on:
- Token‑bucket rate limiting

- Profile clients using JA4 fingerprints

- Distributed counters using Redis

- Web‑based admin dashboard

- Configurable policies loaded at runtime

- Docker‑based deployment and CI pipeline

# Extra Resources
- Architecture Diagram (TO BE UPDATED): https://excalidraw.com/#room=f78d67fb86f551429f25,RjABcy6uMKNo5UiRu7y7tg
