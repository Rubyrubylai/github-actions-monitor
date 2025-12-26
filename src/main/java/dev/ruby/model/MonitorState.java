package dev.ruby.model;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public class MonitorState {
    public Instant lastRunTime;
    public Map<String, Instant> alreadySeenKeys = new HashMap<>();

    public MonitorState() {
        this.alreadySeenKeys = new HashMap<>();
    }

    public void cleanupOldKeys(Duration ageLimit) {
        Instant threshold = Instant.now().minus(ageLimit);
        this.alreadySeenKeys.entrySet().removeIf(entry -> entry.getValue().isBefore(threshold));
    }
}
