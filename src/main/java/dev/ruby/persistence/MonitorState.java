package dev.ruby.persistence;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class MonitorState {
    private Instant lastRunTime;
    private final Map<String, Instant> alreadySeenKeys;

    @JsonCreator
    public MonitorState(
            @JsonProperty("lastRunTime") Instant lastRunTime,
            @JsonProperty("alreadySeenKeys") Map<String, Instant> alreadySeenKeys) {
        this.lastRunTime = (lastRunTime != null) ? lastRunTime : Instant.now();
        this.alreadySeenKeys = (alreadySeenKeys != null) ? alreadySeenKeys : new HashMap<>();
    }

    public MonitorState() {
        this(Instant.now(), new HashMap<>());
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
