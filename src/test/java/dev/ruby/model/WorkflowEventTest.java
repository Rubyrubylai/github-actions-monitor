package dev.ruby.model;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

class WorkflowEventTest {

    @Test
    void constructor_shouldGenerateUniqueKey() {
        Instant time = Instant.parse("2024-01-15T10:30:00Z");
        RunContext ctx = new RunContext("main", "abc1234");

        WorkflowEvent event = new WorkflowEvent("123", time, WorkflowLevel.RUN, EventStatus.STARTED, ctx, "Build");

        assertEquals("123_2024-01-15T10:30:00Z_RUN_STARTED", event.getKey());
    }

    @Test
    void getTime_shouldReturnEventTime() {
        Instant time = Instant.parse("2024-01-15T10:30:00Z");
        RunContext ctx = new RunContext("main", "abc1234");

        WorkflowEvent event = new WorkflowEvent("123", time, WorkflowLevel.JOB, EventStatus.SUCCESS, ctx, "Test");

        assertEquals(time, event.getTime());
    }
}
