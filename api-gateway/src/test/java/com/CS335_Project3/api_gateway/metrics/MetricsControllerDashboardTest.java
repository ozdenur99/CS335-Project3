package com.CS335_Project3.api_gateway.metrics;

import com.CS335_Project3.api_gateway.logging.RequestLogger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MetricsController.class)
class MetricsControllerDashboardTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MetricsService metricsService;

    @MockBean
    private RequestLogger requestLogger;

    @MockBean
    private BotDetector botDetector;

    @Test
    void returnsOverallTrendPayload() throws Exception {
        when(metricsService.getOverallTrend(60)).thenReturn(Map.of(
                "windowMinutes", 60,
                "points", List.of(Map.of("timestamp", "2026-01-01T00:00:00Z", "count", 5))
        ));

        mockMvc.perform(get("/metrics/dashboard/overall-trend").param("minutes", "60"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.windowMinutes").value(60))
                .andExpect(jsonPath("$.points[0].count").value(5));
    }

    @Test
    void returnsRiskLeaderboardPayload() throws Exception {
        when(metricsService.getRiskLeaderboard(eq(30), eq(10))).thenReturn(Map.of(
                "windowMinutes", 30,
                "items", List.of(Map.of("client", "dev-key-token@127.0.0.1", "riskScore", 72.5))
        ));

        mockMvc.perform(get("/metrics/dashboard/risk-leaderboard")
                        .param("minutes", "30")
                        .param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].client").value("dev-key-token@127.0.0.1"))
                .andExpect(jsonPath("$.items[0].riskScore").value(72.5));
    }

    @Test
    void keepsExistingSuspiciousEndpoint() throws Exception {
        when(botDetector.getSuspiciousIps()).thenReturn(Set.of("127.0.0.1"));

        mockMvc.perform(get("/metrics/suspicious"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value("127.0.0.1"));
    }
}
