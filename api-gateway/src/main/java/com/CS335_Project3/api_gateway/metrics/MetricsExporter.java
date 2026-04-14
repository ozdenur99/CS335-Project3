package com.CS335_Project3.api_gateway.metrics;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

//@Component makes Spring create one single instance shared across the whole app
//automatically exports metrics to a JSON file every hour
@Component
public class MetricsExporter {

    private final MetricsService metricsService;
    private final ObjectMapper mapper;

    public MetricsExporter(MetricsService metricsService) {
        this.metricsService = metricsService;
        this.mapper = new ObjectMapper();
        //register JavaTimeModule so LocalDateTime serializes correctly
        this.mapper.registerModule(new JavaTimeModule());
    }

    //runs every hour automatically (3600000 ms = 1 hr)
    //dumps the current metrics snapshot to a JSON file named with the timestamp
    //e.g. metrics/2026-04-12-14-00.json
    //this means data survives container restarts since it is saved to disk
    @Scheduled(fixedRate = 600000)
    public void exportMetrics() {
        try {
            //create the metrics folder if it doesnt exist yet
            File folder = new File("metrics");
            if (!folder.exists()) {
                folder.mkdirs();
            }

            //build the filename using the current timestamp
            String timestamp = LocalDateTime.now()
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm"));
            File outputFile = new File("metrics/" + timestamp + ".json");

            //get the current metrics snapshot and write it to the file
            Map<String, Object> snapshot = metricsService.getSnapshot();
            mapper.writerWithDefaultPrettyPrinter().writeValue(outputFile, snapshot);

        } catch (Exception e) {
            //silently ignore file write errors so the gateway keeps running
        }
    }
}