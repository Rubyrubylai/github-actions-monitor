package dev.ruby;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.time.Instant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.ruby.model.MonitorState;

class StateManagerTest {

    private static final String TEST_REPO = "test-owner-test-repo";
    private StateManager stateManager;
    private File stateFile;

    @BeforeEach
    void setUp() {
        stateManager = new StateManager(TEST_REPO);
        stateFile = new File(TEST_REPO + "-workflow-state.json");
    }

    @AfterEach
    void tearDown() {
        if (stateFile.exists()) {
            stateFile.delete();
        }
    }

    @Test
    void load_withNoExistingFile_shouldReturnNewState() {
        MonitorState state = stateManager.load();

        assertNotNull(state);
        assertNotNull(state.getLastRunTime());
        assertTrue(state.getAlreadySeenKeys().isEmpty());
    }

    @Test
    void save_shouldPersistState() {
        MonitorState state = new MonitorState();
        Instant testTime = Instant.now().minusSeconds(24 * 60 * 60);
        state.setLastRunTime(testTime);
        state.isNewEvent("test_key", Instant.now());

        stateManager.save(state);

        assertTrue(stateFile.exists());
    }

    @Test
    void loadAfterSave_shouldRestoreState() {
        MonitorState originalState = new MonitorState();
        Instant testTime = Instant.now().minusSeconds(24 * 60 * 60);
        originalState.setLastRunTime(testTime);
        originalState.isNewEvent("test_key_1", Instant.now());
        originalState.isNewEvent("test_key_2", Instant.now());

        stateManager.save(originalState);
        MonitorState loadedState = stateManager.load();

        assertEquals(testTime, loadedState.getLastRunTime());
        assertTrue(loadedState.getAlreadySeenKeys().containsKey("test_key_1"));
        assertTrue(loadedState.getAlreadySeenKeys().containsKey("test_key_2"));
    }

    @Test
    void save_shouldCleanupOldKeys() {
        MonitorState state = new MonitorState();
        // add an old key (10 days ago)
        state.getAlreadySeenKeys().put("old_key", Instant.now().minusSeconds(10 * 24 * 60 * 60));
        // add a recent key
        state.isNewEvent("recent_key", Instant.now());

        stateManager.save(state);
        MonitorState loadedState = stateManager.load();

        assertFalse(loadedState.getAlreadySeenKeys().containsKey("old_key"));
        assertTrue(loadedState.getAlreadySeenKeys().containsKey("recent_key"));
    }

    @Test
    void differentRepos_shouldUseDifferentFiles() {
        StateManager manager1 = new StateManager("repo1");
        StateManager manager2 = new StateManager("repo2");

        File file1 = new File("repo1-workflow-state.json");
        File file2 = new File("repo2-workflow-state.json");

        try {
            MonitorState state1 = new MonitorState();
            state1.isNewEvent("key1", Instant.now());
            manager1.save(state1);

            MonitorState state2 = new MonitorState();
            state2.isNewEvent("key2", Instant.now());
            manager2.save(state2);

            MonitorState loaded1 = manager1.load();
            MonitorState loaded2 = manager2.load();

            assertTrue(loaded1.getAlreadySeenKeys().containsKey("key1"));
            assertFalse(loaded1.getAlreadySeenKeys().containsKey("key2"));

            assertTrue(loaded2.getAlreadySeenKeys().containsKey("key2"));
            assertFalse(loaded2.getAlreadySeenKeys().containsKey("key1"));
        } finally {
            file1.delete();
            file2.delete();
        }
    }
}
