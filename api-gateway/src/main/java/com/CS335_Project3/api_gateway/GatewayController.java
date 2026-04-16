package com.CS335_Project3.api_gateway;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.beans.factory.annotation.Value;

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
    public String getNotes(@PathVariable String guid) {
        return restTemplate.getForObject(BACKEND_URL + guid + "/notes", String.class);
    }

    @GetMapping("/smart")
    public String getSmartNotes(@PathVariable String guid) {
        return restTemplate.getForObject(BACKEND_URL + guid + "/notes/smart", String.class);
    }
    

    @PostMapping
    public String createNote(@PathVariable String guid, @RequestBody String body) {

        //create a headers object
        HttpHeaders headers = new HttpHeaders();

        //Tell the backend that the body of the object is JSON
        //To ensure backend can read it while sending with POSTMAN
        headers.setContentType(MediaType.APPLICATION_JSON);

        //Wrap the body with the headers
        //this will let us send the JSON data and its content type with it
        HttpEntity<String> request = new HttpEntity<>(body,headers);

        // Forward POST request to backend
        // Example:
        // gateway receives:  POST /api/test123/notes
        // gateway forwards: POST http://localhost:8081/api/test123/notes
        return restTemplate.postForObject(BACKEND_URL + guid + "/notes", request, String.class);
    }

    @PutMapping("/{id}")
    public String updateNote(@PathVariable String guid, @PathVariable String id, @RequestBody String body) {
        //same as POST - set request headers
         HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        //Wrap body and json together
        HttpEntity<String> request = new HttpEntity<>(body,headers);


        // Forward PUT request to backend
        // Example:
        // gateway receives:  PUT /api/test123/notes/1
        // gateway forwards: PUT http://localhost:8081/api/test123/notes/1
        restTemplate.put(BACKEND_URL + guid + "/notes/" + id, request);
        return "Updated";
    }

    @DeleteMapping("/{id}")
    public String deleteNote(@PathVariable String guid, @PathVariable String id) {
        restTemplate.delete(BACKEND_URL + guid + "/notes/" + id);
        return "Deleted";
    }
}