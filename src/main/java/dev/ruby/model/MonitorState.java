package dev.ruby.model;

import java.time.Instant;
import java.util.HashSet;

public class MonitorState {
    public Instant lastRunTime;
    public HashSet<String> alreadySeenKeys;

    public MonitorState() {
        this.alreadySeenKeys = new HashSet<>();
    }
}
