package dev.ruby.model;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public class MonitorState {
    private Instant lastRunTime;
    private final Map<String, Instant> alreadySeenKeys;

    public MonitorState() {
        this.lastRunTime = Instant.now();
        this.alreadySeenKeys = new HashMap<>();
    }

    public Instant getLastRunTime() {
        return lastRunTime;
    }

    public void setLastRunTime(Instant lastRunTime) {
        this.lastRunTime = lastRunTime;
    }

    public boolean isNewEvent(String key, Instant time) {
        if (alreadySeenKeys.containsKey(key)) {
            return false;
        }
        alreadySeenKeys.put(key, time);
        return true;
    }

    public Map<String, Instant> getAlreadySeenKeys() {
        return alreadySeenKeys;
    }

    public void cleanupOldKeys(Duration ageLimit) {
        Instant threshold = Instant.now().minus(ageLimit);
        this.alreadySeenKeys.entrySet().removeIf(entry -> entry.getValue().isBefore(threshold));
    }
}
