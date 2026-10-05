package dev.frameprobe;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import org.slf4j.LoggerFactory;

/** Independent opt-in fixture: rebuild meshes after the roof's SKY data settles. */
final class SceneStabilizer {
    static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client->{
            if(client.world==null || client.player==null || client.isPaused())return;
            if(!Boolean.getBoolean("prism.qaRuntime") || !client.runDirectory.getName().startsWith("run-candle-validation-"))
                throw new IllegalStateException("Disposable candle QA required");
            try {
                var field=Class.forName("dev.prismlights.client.CandleRevisionValidation").getDeclaredField("ticks");field.setAccessible(true);
                int tick=field.getInt(null);
                if(tick>=160 && (tick-160)%100==40 && ((tick-160)/100%8==0 || (tick-160)/100%8==6)) {
                    client.worldRenderer.reload();
                    LoggerFactory.getLogger("Frame Probe").info("CANDLE CONTROL rebuilt terrain after SKY settling: tick={}",tick);
                }
            } catch(ReflectiveOperationException error){throw new IllegalStateException(error);}
        });
    }
}
