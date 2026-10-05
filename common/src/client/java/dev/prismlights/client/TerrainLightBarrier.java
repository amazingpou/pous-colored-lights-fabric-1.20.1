package dev.prismlights.client;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Queue;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.Long2ReferenceMap;
import dev.prismlights.platform.PlatformServices;
import net.minecraft.client.MinecraftClient;
import org.slf4j.LoggerFactory;

/** Optional renderer bridge: a worker queue being empty does not imply upload.
 * Sodium stays optional; reflective members are resolved once, never per section. */
final class TerrainLightBarrier {
    private static boolean resolved,available,warned;
    private static Method instance,pending,task,built,flags;
    private static Field manager,sections,queues;
    private static long scheduled;
    private TerrainLightBarrier() {}
    static boolean complete(LongSet affected) {
        if(!resolved)resolve();
        if(available)try{
            var renderer=instance.invoke(null);if(renderer==null)return true;
            var owner=manager.get(renderer);if(owner==null)return true;
            var loaded=(Long2ReferenceMap<?>)sections.get(owner);
            for(long key:affected){
                var section=loaded.get(key);
                // An unbuilt section has no stale light baked into a mesh. A
                // running job can already have captured the old light, however.
                if(section!=null && (task.invoke(section)!=null ||
                    (hasMesh(section) && pending.invoke(section)!=null)))return false;
            }
            return true;
        }catch(ReflectiveOperationException | LinkageError failure){available=false;warn(failure);}
        return MinecraftClient.getInstance().worldRenderer.isTerrainRenderComplete();
    }
    /** Submit at most two otherwise deferred, already-built sections through
     * Sodium's own queues/budgets. Never creates meshes for unseen empty space. */
    @SuppressWarnings("unchecked")
    static void schedule(Object owner,LongSet affected) {
        if(!resolved)resolve();
        if(!available)return;
        try{
            var loaded=(Long2ReferenceMap<?>)sections.get(owner);
            var lists=(Map<Object,Queue<Object>>)queues.get(owner);
            int added=0;
            for(long key:affected){
                var section=loaded.get(key);
                if(section==null || !hasMesh(section) || task.invoke(section)!=null)continue;
                var type=pending.invoke(section);if(type==null)continue;
                var queue=lists.get(type);
                if(queue!=null && !queue.contains(section)){queue.add(section);scheduled++;if(++added==2)break;}
            }
        }catch(ReflectiveOperationException | LinkageError failure){available=false;warn(failure);}
    }
    private static boolean hasMesh(Object section)throws ReflectiveOperationException {
        return Boolean.TRUE.equals(built.invoke(section)) && ((Integer)flags.invoke(section))!=0;
    }
    private static void resolve() {
        resolved=true;
        if(!PlatformServices.get().isModLoaded("sodium"))return;
        try{
            var renderer=Class.forName("me.jellysquid.mods.sodium.client.render.SodiumWorldRenderer");
            var section=Class.forName("me.jellysquid.mods.sodium.client.render.chunk.RenderSection");
            instance=renderer.getMethod("instanceNullable");
            manager=renderer.getDeclaredField("renderSectionManager");manager.setAccessible(true);
            sections=manager.getType().getDeclaredField("sectionByPosition");sections.setAccessible(true);
            queues=manager.getType().getDeclaredField("rebuildLists");queues.setAccessible(true);
            built=section.getMethod("isBuilt");flags=section.getMethod("getFlags");
            pending=section.getMethod("getPendingUpdate");task=section.getMethod("getBuildCancellationToken");available=true;
        }catch(ReflectiveOperationException | LinkageError failure){warn(failure);}
    }
    private static void warn(Throwable failure) {
        if(warned)return;warned=true;
        LoggerFactory.getLogger("Prism Lights").warn("Renderer mesh completion bridge unavailable; redstone transition synchronization needs validation for this renderer version",failure);
    }
}
