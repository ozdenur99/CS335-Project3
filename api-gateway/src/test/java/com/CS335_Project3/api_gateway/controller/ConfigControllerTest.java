package com.CS335_Project3.api_gateway.controller;

import com.CS335_Project3.api_gateway.config.RuntimeRateLimitConfigService;
import com.CS335_Project3.api_gateway.config.TenantRateLimitConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ConfigController.class)
class ConfigControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private RuntimeRateLimitConfigService runtimeRateLimitConfigService;

    @Test
    void downloadsConfigJson() throws Exception {
        when(runtimeRateLimitConfigService.getEffectiveConfigJson()).thenReturn("{\"defaultLimit\":5}");

        mockMvc.perform(get("/config/rate-limit/download"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=rate-limit-config.json"));
    }

    @Test
    void uploadsConfigJson() throws Exception {
        TenantRateLimitConfig config = new TenantRateLimitConfig();
        config.setDefaultLimit(9);
        when(runtimeRateLimitConfigService.saveConfig(any(TenantRateLimitConfig.class))).thenReturn(config);

        mockMvc.perform(post("/config/rate-limit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(config)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.defaultLimit").value(9));
    }
}
