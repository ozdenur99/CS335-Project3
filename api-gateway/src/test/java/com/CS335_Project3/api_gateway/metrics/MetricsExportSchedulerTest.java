package com.CS335_Project3.api_gateway.metrics;

import com.CS335_Project3.api_gateway.logging.RequestLogger;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MetricsExportSchedulerTest {

    @TempDir
    Path tempDir;

    @Test
    void writesHourlyExportFile() throws Exception {
        MetricsService metricsService = mock(MetricsService.class);
        RequestLogger requestLogger = mock(RequestLogger.class);

        when(metricsService.getSnapshot()).thenReturn(Map.of("totalRequests", 1));
        when(metricsService.getStatusDistribution(60)).thenReturn(Map.of("distribution", Map.of("200", 1)));
        when(metricsService.getAlgorithmBlockingComparison(60)).thenReturn(Map.of("blockedByAlgorithm", Map.of("token", 0)));
        when(metricsService.getRiskLeaderboard(60, 50)).thenReturn(Map.of("items", java.util.List.of()));
        when(metricsService.getEventFeed(1440, 10000, null, null, null, null, null, null, null))
                .thenReturn(Map.of("items", java.util.List.of()));
        when(requestLogger.getLogs()).thenReturn(java.util.List.of());

        MetricsExportScheduler scheduler = new MetricsExportScheduler(metricsService, requestLogger, new ObjectMapper());
        ReflectionTestUtils.setField(scheduler, "exportPath", tempDir.toString());

        scheduler.exportHourlySnapshot();

        long files = Files.list(tempDir).count();
        org.junit.jupiter.api.Assertions.assertTrue(files >= 1);
    }
}

