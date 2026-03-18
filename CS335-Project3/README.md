# CS335 Backend + API Gateway System

This project contains a Spring Boot Backend Service and a custom API Gateway built using Spring MVC.

The system follows a microservices-style architecture where:
- Backend handles business logic (notes API)
- Gateway acts as the entry point and forwards requests

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

cd CS335_project/CS335-Project3

--------------------------------------------------

TERMINAL 1 — START BACKEND

cd backend-service
mvn clean install
mvn spring-boot:run

Expected Output:

Tomcat started on port 8081  
Started BackendServiceApplication  

Test Backend:

Open browser:
http://localhost:8081/hello

Expected:
Backend is working!

--------------------------------------------------

TERMINAL 2 — START API GATEWAY

(Open new terminal)

cd ~/CS335_project/CS335-Project3/api-gateway
mvn clean install
mvn spring-boot:run

Expected Output:

Tomcat started on port 8080  
Started ApiGatewayApplication  

Test Gateway:

http://localhost:8080/gateway/hello

Expected:
Backend is working!

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

Expected Output:
{"id":"1","content":"My first note"}

--------------------------------------------------

3. UPDATE NOTE

curl -X PUT http://localhost:8081/api/test123/notes/1 \
-H "Content-Type: application/json" \
-d '{"id":"1","content":"Updated note"}'

Expected Output:
{"id":"1","content":"Updated note"}

--------------------------------------------------

4. DELETE NOTE

curl -X DELETE http://localhost:8081/api/test123/notes/1

Expected Output:
Deleted

--------------------------------------------------

USING API GATEWAY

Instead of calling backend directly, use:

http://localhost:8080/gateway/hello

--------------------------------------------------

HOW IT WORKS

1. Client sends request to Gateway  
2. Gateway forwards request using RestTemplate  
3. Backend processes request  
4. Response returned through Gateway  

--------------------------------------------------

TECHNOLOGIES USED

- Java  
- Spring Boot (3.5.11)  
- Maven  
- REST APIs  

--------------------------------------------------

COMMON ISSUES

Port conflict → Ensure backend = 8081, gateway = 8080  
404 error → Check controller mapping  
500 error → Backend not running or wrong URL  
Connection refused → Start backend first  

--------------------------------------------------

FINAL CHECKLIST

Backend running on 8081  
Gateway running on 8080  
/hello works on backend  
/gateway/hello works on gateway  

--------------------------------------------------

PROJECT STATUS

Backend Service — Working  
API Gateway — Working  
End-to-End Communication — Working  

--------------------------------------------------

