CS335 Backend Service

This project contains the Spring Boot backend API for the CS335 application. It provides REST endpoints to create, read, update, and delete notes.

The backend runs locally at:
http://localhost:8080

Requirements
Before running the backend, make sure the following are installed:
Java 17+
Maven

How to Run the Backend

Step 0 — Clone the repository

bash
git clone 
cd CS335_project

Step 1 — Open a terminal and navigate to the backend folder

bash
cd backend-service

Step 2 — Start the Spring Boot server

bash
mvn spring-boot:run

Expected Output

You should see something similar to:

Tomcat started on port 8080
Started BackendServiceApplication

This means the backend server is now running on:
http://localhost:8080

⚠️ Important
Keep this terminal running. Do not close it while testing the API.

Step 3 — Check if the Backend is Running

Open a browser and go to:
http://localhost:8080/health

Expected Response

Backend is running

If you see this message, the backend is working correctly.

Step 4 — Open a Second Terminal

While the backend server is running, open another terminal window or tab.

Mac shortcut:
Cmd + T

All API commands below should be run in this second terminal.

API Endpoints

All note operations require a GUID (a unique identifier for a user or session).

Example GUID used below:
test123

1️⃣ Get All Notes

Fetch all notes for the GUID.

bash
curl http://localhost:8080/api/test123/notes

Expected Output

[]

If notes exist:

[{“id”:“1”,“content”:“My first note”}]

You can also check this in the browser by opening:
http://localhost:8080/api/test123/notes

2️⃣ Create a Note

Create a new note.

bash
curl -X POST http://localhost:8080/api/test123/notes 
-H “Content-Type: application/json” 
-d ‘{“id”:“1”,“content”:“My first note”}’

Expected Output

{“id”:“1”,“content”:“My first note”}

You will also see logs in the first terminal where the backend is running.

After creating a note, you can verify it by opening:
http://localhost:8080/api/test123/notes

3️⃣ Update a Note

Update the content of an existing note.

bash
curl -X PUT http://localhost:8080/api/test123/notes/1 
-H “Content-Type: application/json” 
-d ‘{“id”:“1”,“content”:“Updated note”}’

Expected Output

{“id”:“1”,“content”:“Updated note”}

To verify the update, open:
http://localhost:8080/api/test123/notes

4️⃣ Delete a Note

Delete an existing note.

bash
curl -X DELETE http://localhost:8080/api/test123/notes/1

Expected Output

Deleted

To confirm deletion, open:
http://localhost:8080/api/test123/notes

You should see:

[]

Quick Testing Flow

Run the commands in this order.

Create a note

bash
curl -X POST http://localhost:8080/api/test123/notes 
-H “Content-Type: application/json” 
-d ‘{“id”:“1”,“content”:“My first note”}’

Get notes

bash
curl http://localhost:8080/api/test123/notes

Update note

bash
curl -X PUT http://localhost:8080/api/test123/notes/1 
-H “Content-Type: application/json” 
-d ‘{“id”:“1”,“content”:“Updated note”}’

Delete note

bash
curl -X DELETE http://localhost:8080/api/test123/notes/1

Technologies Used

• Java
• Spring Boot
• Maven
• REST APIs






