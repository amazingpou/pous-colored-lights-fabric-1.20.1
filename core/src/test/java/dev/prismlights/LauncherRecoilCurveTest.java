package dev.prismlights;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LauncherRecoilCurveTest {
    @Test void boundedKickReturnsToRestBeforeCooldownEnds() {
        assertEquals(0, LauncherRecoilCurve.sample(-1));
        assertEquals(0, LauncherRecoilCurve.sample(0));
        assertEquals(1, LauncherRecoilCurve.sample(1));
        assertEquals(0, LauncherRecoilCurve.sample(6));
        assertEquals(0, LauncherRecoilCurve.sample(8));
        assertEquals(0, LauncherRecoilCurve.sample(Float.NaN));
        float previous=0;
        for (int i=1; i<=100; i++) {
            float value=LauncherRecoilCurve.sample(i/100f);
            assertTrue(value>=previous && value<=1);
            previous=value;
        }
        for (int i=101; i<=600; i++) {
            float value=LauncherRecoilCurve.sample(i/100f);
            assertTrue(value<=previous && value>=0);
            previous=value;
        }
    }
}
