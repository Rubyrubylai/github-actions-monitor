package dev.ruby.model;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MonitorStateTest {

    private MonitorState state;

    @BeforeEach
    void setUp() {
        state = new MonitorState();
    }

    @Test
    void setLastRunTime_shouldUpdateTime() {
        Instant newTime = Instant.parse("2024-06-15T12:00:00Z");
        state.setLastRunTime(newTime);

        assertEquals(newTime, state.getLastRunTime());
    }

    @Test
    void isNewEvent_withNewKey_shouldReturnTrueAndStoreKey() {
        String key = "run_123_STARTED";
        Instant time = Instant.now();

        assertTrue(state.isNewEvent(key, time));
        assertTrue(state.getAlreadySeenKeys().containsKey(key));
        assertEquals(time, state.getAlreadySeenKeys().get(key));
    }

    @Test
    void isNewEvent_withExistingKey_shouldReturnFalse() {
        String key = "run_123_STARTED";
        Instant time = Instant.now();

        state.isNewEvent(key, time);
        assertFalse(state.isNewEvent(key, time.plusSeconds(10)));
    }

    @Test
    void cleanupOldKeys_shouldRemoveKeysOlderThanThreshold() {
        Instant oldTime = Instant.now().minus(Duration.ofDays(10));
        Instant recentTime = Instant.now().minus(Duration.ofDays(1));

        state.isNewEvent("old_key", oldTime);
        state.isNewEvent("recent_key", recentTime);

        state.cleanupOldKeys(Duration.ofDays(7));

        assertFalse(state.getAlreadySeenKeys().containsKey("old_key"));
        assertTrue(state.getAlreadySeenKeys().containsKey("recent_key"));
    }

    @Test
    void cleanupOldKeys_shouldKeepAllKeysWithinThreshold() {
        Instant time1 = Instant.now().minus(Duration.ofDays(1));
        Instant time2 = Instant.now().minus(Duration.ofDays(2));

        state.isNewEvent("key1", time1);
        state.isNewEvent("key2", time2);

        state.cleanupOldKeys(Duration.ofDays(7));

        assertEquals(2, state.getAlreadySeenKeys().size());
    }
}
