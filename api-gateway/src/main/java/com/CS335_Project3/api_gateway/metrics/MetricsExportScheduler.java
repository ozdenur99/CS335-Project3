package com.CS335_Project3.api_gateway.metrics;

import com.CS335_Project3.api_gateway.logging.RequestLogger;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class MetricsExportScheduler {

    private static final Logger log = LoggerFactory.getLogger(MetricsExportScheduler.class);
    private static final DateTimeFormatter FILE_TS =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC);

    private final MetricsService metricsService;
    private final RequestLogger requestLogger;
    private final ObjectMapper objectMapper;

    @Value("${metrics.export.path:/var/lib/api-gateway/metrics-exports}")
    private String exportPath;

    public MetricsExportScheduler(MetricsService metricsService,
                                  RequestLogger requestLogger,
                                  ObjectMapper objectMapper) {
        this.metricsService = metricsService;
        this.requestLogger = requestLogger;
        this.objectMapper = objectMapper.copy().registerModule(new JavaTimeModule());
    }

    @Scheduled(cron = "${metrics.export.cron:0 0 * * * *}")
    public void exportHourlySnapshot() {
        try {
            Path dir = Path.of(exportPath);
            Files.createDirectories(dir);

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("generatedAt", Instant.now().toString());
            payload.put("snapshot", metricsService.getSnapshot());
            payload.put("statusDistribution1h", metricsService.getStatusDistribution(60));
            payload.put("algorithmBlocking1h", metricsService.getAlgorithmBlockingComparison(60));
            payload.put("riskLeaderboard1h", metricsService.getRiskLeaderboard(60, 50));
            payload.put("recentEvents24h", metricsService.getEventFeed(1440, 10000, null, null, null, null, null, null, null));
            payload.put("inMemoryRecentLogs", requestLogger.getLogs());

            String fileName = "metrics-" + FILE_TS.format(Instant.now()) + ".json";
            Path target = dir.resolve(fileName);
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(target.toFile(), payload);
        } catch (Exception ex) {
            // keep gateway path non-failing if export encounters file-system issues
            log.warn("Failed to export metrics snapshot to {}", exportPath, ex);
        }
    }
}
