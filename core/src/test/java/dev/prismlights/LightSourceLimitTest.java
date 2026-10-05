package dev.prismlights;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class LightSourceLimitTest {
    @Test void legacyDefaultsUpgrade() {
        assertEquals(72, LightSourceLimit.migrate(24, 1));
        assertEquals(72, LightSourceLimit.migrate(24, 2));
        assertEquals(72, LightSourceLimit.migrate(64, 3));
    }
    @Test void customLowerLimitsSurviveMigration() {
        assertEquals(32, LightSourceLimit.migrate(32, 1));
        assertEquals(24, LightSourceLimit.migrate(24, 3));
        assertEquals(48, LightSourceLimit.migrate(48, 3));
        assertEquals(64, LightSourceLimit.migrate(64, 4));
    }
    @Test void boundsAndCurrentDefaultAreEnforced() {
        assertEquals(1, LightSourceLimit.migrate(-10, 4));
        assertEquals(72, LightSourceLimit.migrate(200, 4));
        assertEquals(72, LightSourceLimit.migrate(96, 4));
        assertEquals(72, LightSourceLimit.migrate(96, 5));
        assertEquals(72, LightSourceLimit.migrate(72, 5));
    }
}
