package dev.prismlights;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LauncherShotLayoutTest {
    @Test void dualOriginsAndAnglesAreDistinctAtAllCardinalYaws() {
        for(float yaw:new float[]{0,90,180,270,-90}) {
            var main=LauncherShotLayout.forHand(yaw,true,false,true);
            var off=LauncherShotLayout.forHand(yaw,true,true,true);
            assertEquals(0.56,Math.hypot(main.offsetX()-off.offsetX(),main.offsetZ()-off.offsetZ()),1e-9);
            // Minecraft's positive yaw turns toward the physical right arm.
            assertEquals(yaw+5,main.yaw(),1e-6);
            assertEquals(yaw-5,off.yaw(),1e-6);
            assertEquals(10,main.yaw()-off.yaw(),1e-6);
        }
    }
    @Test void leftDominantPlayerMirrorsHandsRatherThanWorldAxes() {
        assertEquals(LauncherShotLayout.forHand(37,true,false,true),LauncherShotLayout.forHand(37,false,true,true));
        assertEquals(LauncherShotLayout.forHand(37,true,true,true),LauncherShotLayout.forHand(37,false,false,true));
    }
    @Test void singleLauncherRetainsAimedDirection() {
        assertEquals(27,LauncherShotLayout.forHand(27,true,false,false).yaw());
        assertEquals(27,LauncherShotLayout.forHand(27,false,true,false).yaw());
    }
}
