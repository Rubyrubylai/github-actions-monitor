package dev.ruby.persistence;

import java.io.File;
import java.io.IOException;
import java.time.Duration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

public class StateStore {
    private final Duration DEFAULT_RETENTION_PERIOD = Duration.ofDays(7);
    private final File stateFile;

    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    public StateStore(String repo) {
        this.stateFile = new File(repo + "-workflow-state.json");
    }

    public MonitorState load() {
        if (!this.stateFile.exists())
            return new MonitorState();

        try {
            return mapper.readValue(this.stateFile, MonitorState.class);
        } catch (IOException e) {
            System.err.println("Cannot load file: " + e.getMessage());
            return new MonitorState();
        }
    }

    public void save(MonitorState state) {
        try {
            state.cleanupOldKeys(DEFAULT_RETENTION_PERIOD);
            mapper.writerWithDefaultPrettyPrinter().writeValue(this.stateFile, state);
        } catch (IOException e) {
            System.err.println("Save error " + e.getMessage());
        }
    }
}
