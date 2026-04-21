package com.CS335_Project3.api_gateway;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.http.HttpMethod;
import java.util.Map;

@RestController
@RequestMapping("/api/{guid}/notes")
public class GatewayController {

    // private final String BACKEND_URL = "http://localhost:8081/api/";

    // Docker backend url
    @Value("${backend.url}")
    private String BACKEND_URL;

    private final RestTemplate restTemplate;

    @Autowired
    public GatewayController(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @GetMapping
    // wrap createNote method in try-catch to handle backend timeouts,
    // forward GET request to backend and return response directly to client
    public ResponseEntity<?> getNotes(@PathVariable String guid) {
        try {
            Object result = restTemplate.getForObject(BACKEND_URL + guid + "/notes", Object.class);
            return ResponseEntity.ok(result);
        } catch (ResourceAccessException e) {
            return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT).body("Gateway Timeout");
        }
    }

    @PostMapping
    public ResponseEntity<?> createNote(@PathVariable String guid, @RequestBody Map<String, Object> body) {
        try {
            // create a headers object
            HttpHeaders headers = new HttpHeaders();

            // Tell the backend that the body of the object is JSON
            // To ensure backend can read it while sending with POSTMAN
            headers.setContentType(MediaType.APPLICATION_JSON);

            // Wrap the body with the headers
            // this will let us send the JSON data and its content type with it
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

            // Forward POST request to backend
            // Example:
            // gateway receives: POST /api/test123/notes
            // gateway forwards: POST http://localhost:8081/api/test123/notes

            // wrap getNotes method in try-catch to handle backend timeouts,
            Object result = restTemplate.postForObject(BACKEND_URL + guid + "/notes", request, Object.class);
            return ResponseEntity.ok(result);
        } catch (ResourceAccessException e) {
            return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT).body("Gateway Timeout");
        } catch (org.springframework.web.client.HttpStatusCodeException e) {
            return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT).body("Gateway Timeout");
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateNote(@PathVariable String guid, @PathVariable String id,
            @RequestBody Map<String, Object> body) {
        try {
            // same as POST - set request headers
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            // Wrap body and json together
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

            // Forward PUT request to backend
            // Example:
            // gateway receives: PUT /api/test123/notes/1
            // gateway forwards: PUT http://localhost:8081/api/test123/notes/1

            // restTemplate does not have a built-in method for PUT that
            // returns a response body, so we use exchange() instead
            ResponseEntity<Object> response = restTemplate.exchange(
                    BACKEND_URL + guid + "/notes/" + id,
                    HttpMethod.PUT, request, Object.class);
            return ResponseEntity.ok(response.getBody());
        } catch (ResourceAccessException e) {
            return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT).body("Gateway Timeout");
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteNote(@PathVariable String guid, @PathVariable String id) {
        try {
            restTemplate.delete(BACKEND_URL + guid + "/notes/" + id);
            return ResponseEntity.ok("Deleted");
        } catch (ResourceAccessException e) {
            return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT).body("Gateway Timeout");
        }
    }
}