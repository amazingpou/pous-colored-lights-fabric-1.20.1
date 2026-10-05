package dev.prismlights;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PipelineTrackerTest {
    @Test void reloadWhileEnabledInvalidatesResources() {
        var tracker=new PipelineTracker();
        Object manager=new Object(), first=new Object(), second=new Object();
        assertFalse(tracker.update(manager,first,0,true));
        assertFalse(tracker.update(manager,first,0,true));
        assertTrue(tracker.update(manager,second,1,true));
        assertFalse(tracker.update(manager,second,1,true));
        assertTrue(tracker.update(manager,second,2,true));
    }
    @Test void dimensionSwitchToggleAndManagerReplacementAreDetected() {
        var tracker=new PipelineTracker();
        Object manager=new Object(), first=new Object(), second=new Object();
        tracker.update(manager,first,0,true);
        assertTrue(tracker.update(manager,second,0,true));
        assertTrue(tracker.update(manager,second,0,false));
        assertTrue(tracker.update(new Object(),second,0,false));
        assertTrue(tracker.update(null,null,0,false));
        assertFalse(tracker.update(null,null,0,false));
    }
}
