package dev.ruby;

import java.io.File;
import java.io.IOException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

public class StateManager {
    private final File stateFile = new File("workflow-state.json");
    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    public MonitorState load() {
        if (!stateFile.exists())
            return new MonitorState();

        try {
            return mapper.readValue(stateFile, MonitorState.class);
        } catch (IOException e) {
            System.err.println("Cannot load file: " + e.getMessage());
            return new MonitorState();
        }
    }

    public void save(MonitorState state) {
        try {
            mapper.writerWithDefaultPrettyPrinter().writeValue(stateFile, state);
        } catch (IOException e) {
            System.err.println("Save error " + e.getMessage());
        }
    }
}
