package com.CS335_Project3.api_gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestTemplate;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration test for GatewayController.
 * Tests that the gateway properly forwards requests to the backend service
 * after passing through API Key authentication.
 */
@SpringBootTest
@AutoConfigureMockMvc
class GatewayControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RestTemplate restTemplate;

    @Test
    void getNotes_withValidKey_shouldForwardToBackend() throws Exception {
        String guid = "test-guid-123";
        String mockResponse = "[{\"id\":1,\"content\":\"Test Note\"}]";

        // Mock the backend response
        when(restTemplate.getForObject(
            "http://localhost:8081/api/" + guid + "/notes",
            String.class
        )).thenReturn(mockResponse);

        mockMvc.perform(get("/api/" + guid + "/notes")
            .header("X-API-Key", "dev-key-alpha"))
            .andExpect(status().isOk())
            .andExpect(content().string(mockResponse));
    }

    @Test
    void createNote_withoutApiKey_shouldReturn401() throws Exception {
        String guid = "test-guid-123";

        mockMvc.perform(post("/api/" + guid + "/notes")
            .contentType("application/json")
            .content("{\"content\":\"New Note\"}"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error").value("Unauthorized"));
    }

    @Test
    void createNote_withInvalidKey_shouldReturn401() throws Exception {
        String guid = "test-guid-123";

        mockMvc.perform(post("/api/" + guid + "/notes")
            .header("X-API-Key", "wrong-key")
            .contentType("application/json")
            .content("{\"content\":\"New Note\"}"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error").value("Unauthorized"));
    }

    @Test
    void updateNote_withValidKey_shouldForwardToBackend() throws Exception {
        String guid = "test-guid-123";
        String noteId = "note-1";

        mockMvc.perform(put("/api/" + guid + "/notes/" + noteId)
            .header("X-API-Key", "dev-key-alpha")
            .contentType("application/json")
            .content("{\"content\":\"Updated Note\"}"))
            .andExpect(status().isOk())
            .andExpect(content().string("Updated"));
    }

    @Test
    void deleteNote_withValidKey_shouldForwardToBackend() throws Exception {
        String guid = "test-guid-123";
        String noteId = "note-1";

        mockMvc.perform(delete("/api/" + guid + "/notes/" + noteId)
            .header("X-API-Key", "dev-key-beta"))
            .andExpect(status().isOk())
            .andExpect(content().string("Deleted"));
    }
}
