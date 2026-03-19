# CS335 Backend + API Gateway System

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

- Java 17+
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

