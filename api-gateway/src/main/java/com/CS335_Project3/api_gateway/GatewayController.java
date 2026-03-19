package com.CS335_Project3.api_gateway;

import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

@RestController
@RequestMapping("/api/{guid}/notes")
public class GatewayController {

    private final String BACKEND_URL = "http://localhost:8081/api/";

    @GetMapping
    public String getNotes(@PathVariable String guid) {
        RestTemplate restTemplate = new RestTemplate();
        return restTemplate.getForObject(BACKEND_URL + guid + "/notes", String.class);
    }

    @PostMapping
    public String createNote(@PathVariable String guid, @RequestBody String body) {
        RestTemplate restTemplate = new RestTemplate();
        return restTemplate.postForObject(BACKEND_URL + guid + "/notes", body, String.class);
    }

    @PutMapping("/{id}")
    public String updateNote(@PathVariable String guid, @PathVariable String id, @RequestBody String body) {
        RestTemplate restTemplate = new RestTemplate();
        restTemplate.put(BACKEND_URL + guid + "/notes/" + id, body);
        return "Updated";
    }

    @DeleteMapping("/{id}")
    public String deleteNote(@PathVariable String guid, @PathVariable String id) {
        RestTemplate restTemplate = new RestTemplate();
        restTemplate.delete(BACKEND_URL + guid + "/notes/" + id);
        return "Deleted";
    }
}