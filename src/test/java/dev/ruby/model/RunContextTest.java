package dev.ruby.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import org.junit.jupiter.api.Test;

class RunContextTest {

    @Test
    void shortSha_withValidSha_shouldReturnFirst7Characters() {
        RunContext ctx = new RunContext("main", "abc1234567890");

        assertEquals("abc1234", ctx.shortSha());
    }

    @Test
    void shortSha_withShortSha_shouldReturnAsIs() {
        RunContext ctx = new RunContext("main", "abc");

        assertEquals("abc", ctx.shortSha());
    }

    @Test
    void shortSha_withNullSha_shouldReturnNull() {
        RunContext ctx = new RunContext("main", null);

        assertNull(ctx.shortSha());
    }
}
