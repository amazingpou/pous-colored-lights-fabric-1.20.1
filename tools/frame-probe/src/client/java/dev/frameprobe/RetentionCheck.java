package dev.frameprobe;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import org.slf4j.LoggerFactory;
import java.util.*;

/** Independent opt-in guard: finished fields must not retain obsolete path generations. */
final class RetentionCheck {
    private static int snapshots,maximum,cycles;
    private RetentionCheck() {}
    static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client->{
            if(client.world==null)return;
            try {
                var api=Class.forName("dev.prismlights.client.PrismClient").getDeclaredMethod("lightField");
                api.setAccessible(true);var engine=api.invoke(null);if(engine==null)return;
                if(read(engine,"pendingSources")!=null || (boolean)read(engine,"dirty"))return;
                var bounds=(Map<?,?>)read(engine,"pathBounds");var paths=(Map<?,?>)read(engine,"propagationCache");
                var live=Collections.newSetFromMap(new IdentityHashMap<Object,Boolean>());live.addAll(paths.values());
                snapshots++;maximum=Math.max(maximum,bounds.size());
                if(bounds.size()>paths.size() || bounds.keySet().stream().anyMatch(path->!live.contains(path)))
                    throw new AssertionError("Obsolete path generations retained after completed field: bounds="+bounds.size()+" live="+paths.size());
                // Unlike an unload that publishes an empty field before reload,
                // atomic callbacks regenerate equal paths without dropping the
                // old bounds first. This is the measured near-chunk workload.
                if(Boolean.getBoolean("frameprobe.retentionStress") && cycles<8 && !bounds.isEmpty()) {
                    var chunk=client.world.getChunk(0,1);
                    for(String event:new String[]{"unload","load"})for(var method:engine.getClass().getMethods())
                        if(method.getName().equals(event))method.invoke(engine,client.world,chunk);
                    cycles++;
                }
            }catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(client->{
            if(snapshots<3)throw new AssertionError("No completed snapshots checked");
            if(Boolean.getBoolean("frameprobe.retentionStress") && cycles<8)throw new AssertionError("Atomic callbacks not exercised");
            LoggerFactory.getLogger("Frame Probe").info("RETENTION CHECK PASS completedSnapshots={} maxBounds={} atomicCycles={}",snapshots,maximum,cycles);
        });
    }
    private static Object read(Object owner,String name)throws ReflectiveOperationException {
        var field=owner.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(owner);
    }
}
