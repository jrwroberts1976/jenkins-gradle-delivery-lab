package uk.co.jrwroberts.homelabdefender;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppTest {
    private final GameEngine game = new GameEngine();

    @Test
    void acceptsTheBestActionForAnIncident() {
        var resolution = game.resolve("attack", "block").orElseThrow();

        assertTrue(resolution.successful());
    }

    @Test
    void rejectsAnUnsafeActionForAnIncident() {
        var resolution = game.resolve("backup", "restart").orElseThrow();

        assertFalse(resolution.successful());
    }
}
