package dev.ruby.model;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class EventStatusTest {

    @ParameterizedTest
    @CsvSource({
            "queued, , QUEUED",
            "requested, , QUEUED",
            "waiting, , QUEUED",
            "pending, , QUEUED",
            "in_progress, , STARTED",
            "completed, success, SUCCESS",
            "completed, failure, FAILURE",
            "completed, timed_out, FAILURE",
            "completed, cancelled, CANCELLED",
            "completed, skipped, SKIPPED",
            "completed, , SUCCESS",
            "unknown_status, , UNKNOWN",
    })
    void map_shouldReturnCorrectStatus(String status, String conclusion, EventStatus expected) {
        assertEquals(expected, EventStatus.map(status, conclusion));
    }

    @Test
    void map_shouldBeCaseInsensitive() {
        assertEquals(EventStatus.QUEUED, EventStatus.map("QUEUED", null));
        assertEquals(EventStatus.STARTED, EventStatus.map("IN_PROGRESS", null));
        assertEquals(EventStatus.SUCCESS, EventStatus.map("COMPLETED", "SUCCESS"));
    }

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
