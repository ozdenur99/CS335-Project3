package com.CS335_Project3.api_gateway.logging;

import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

//@Component makes it run once only, making all filters share the same log list
@Component
public class RequestLogger {
    //we use a ConcurrentLinkedQueue to add entries simultaneously
    //because multiple HTTP requests can arrive at the same time
    private final Queue<LogEntry> logs = new ConcurrentLinkedQueue<>();

    //every request is processed and shows the 4 string fields
    //and creates a new LogEntry with the timestamp automatically set
    public void log(String apiKey, String path, String decision, String reason) {
        logs.add(new LogEntry(apiKey, path, decision, reason));
    }

    //returns last 100 log entries as a List
    public List<LogEntry> getLogs() {
        List<LogEntry> all = new ArrayList<>(logs); // copy queue into a list
        int size = all.size();

        if (size <= 100) {
            return all;
        }
        //if its >100, shows the most recent 100
        return all.subList(size - 100, size);
    }

    //returns total number of logs stored
    //MetricsService uses it to report the total request count
    public int getTotalCount() {
        return logs.size();
    }
}