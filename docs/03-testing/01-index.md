
To be confimed, not the final version

# Testing Strategy & Environment Standards

## 1. Global Setup & Requirements
- **Endpoints**: Gateway (`:8080`) -> Backend (`:8081`). **Backend must be running first.**
- **State Reset**: We use In-Memory storage. **Restart the Gateway** to clear blacklists/rate limits between tests.
- **Tools**: Use **Postman** for single requests and **CLI (PowerShell/Bash)** for high-speed spikes.

---

## 2. Unit Testing (Isolated Logic)
Verify individual classes using Mocks. No live servers required.

**Windows (PowerShell)**
```powershell
cd backend-service
.\mvnw.cmd clean test

**macOS / Linux (Terminal)**
# Grant execution rights if needed: chmod +x mvnw
cd backend-service
./mvnw clean test

---
## 3. Integration Testing 
Verifies the Gateway's ability to proxy requests to the Backend. This requires three active terminal sessions.

Step 1: Start Backend (Terminal A)
Windows: cd backend-service; .\mvnw.cmd spring-boot:run
Mac/Linux: cd backend-service; ./mvnw spring-boot:run

Step 2: Start Gateway (Terminal B)
Windows: cd api-gateway; .\mvnw.cmd spring-boot:run
Mac/Linux: cd api-gateway; ./mvnw spring-boot:run

Step 3: Verification (Terminal C or Postman)
Universal (cURL):
Bash
curl http://localhost:8080/api/test123/notes -H "X-API-Key: dev-key-alpha"


## 4. Command Cheat Sheet
| Task | Windows (PowerShell) | macOS / Linux (Bash) |
| :--- | :--- | :--- |
| **Clean & Compile** | `.\mvnw.cmd clean compile` | `./mvnw clean compile` |
| **Run All Tests** | `.\mvnw.cmd clean test` | `./mvnw clean test` |
| **Run Specific Test** | `.\mvnw.cmd test -Dtest=Name` | `./mvnw test -Dtest=Name` |
| **Launch Application** | `.\mvnw.cmd spring-boot:run` | `./mvnw spring-boot:run` |

---