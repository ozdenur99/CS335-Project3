
API-Gateway URL: http://localhost:8080
Backend URL: http://localhost:8081

Details:
Route: GET /api/test123/notes
Required Headers: X-API-Key

Response Samples:
200 OK: Successful JSON.
401 Unauthorized: Missed or wrong key.
429 Too Many Requests: Too many requests.
403 Forbidden: IP has been blocked.