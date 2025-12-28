package dev.ruby.model;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class EventStatusTest {
    @Test
    void isFinished_shouldReturnTrueForTerminalStates() {
        assertTrue(EventStatus.SUCCESS.isFinished());
        assertTrue(EventStatus.FAILURE.isFinished());
        assertTrue(EventStatus.CANCELLED.isFinished());
        assertTrue(EventStatus.SKIPPED.isFinished());
    }

    @Test
    void isFinished_shouldReturnFalseForNonTerminalStates() {
        assertFalse(EventStatus.QUEUED.isFinished());
        assertFalse(EventStatus.STARTED.isFinished());
        assertFalse(EventStatus.UNKNOWN.isFinished());
    }
}
