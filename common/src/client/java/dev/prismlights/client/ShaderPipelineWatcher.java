package dev.prismlights.client;

import java.lang.reflect.Method;
import dev.prismlights.PipelineTracker;
import dev.prismlights.platform.PlatformServices;
import org.slf4j.LoggerFactory;

/** Optional Iris bridge kept reflective so Iris remains an optional dependency. */
public final class ShaderPipelineWatcher {
    private static final PipelineTracker tracker=new PipelineTracker();
    private static boolean resolved, warned, pipelineBridge;
    private static Method getInstance, isShaderPackInUse, getManager, getPipeline, getGeneration;

    private ShaderPipelineWatcher() {}

    public static boolean changed() {
        if(!resolved) resolve();
        if(getInstance==null || isShaderPackInUse==null) return false;
        try {
            boolean active=(Boolean)isShaderPackInUse.invoke(getInstance.invoke(null));
            Object manager=null, pipeline=null;
            int generation=0;
            if(pipelineBridge) {
                try {
                    manager=getManager.invoke(null);
                    if(manager!=null) {
                        pipeline=getPipeline.invoke(manager);
                        generation=(Integer)getGeneration.invoke(manager);
                    }
                } catch(ReflectiveOperationException | LinkageError failure) {
                    pipelineBridge=false;
                    manager=null; pipeline=null; generation=0;
                    warn(failure);
                }
            }
            return tracker.update(manager,pipeline,generation,active);
        } catch(ReflectiveOperationException | LinkageError failure) {
            getInstance=null;
            warn(failure);
            return false;
        }
    }

    private static void resolve() {
        resolved=true;
        if(!PlatformServices.get().isModLoaded("iris")) return;
        try {
            Class<?> api=Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            getInstance=api.getMethod("getInstance");
            isShaderPackInUse=api.getMethod("isShaderPackInUse");
            Class<?> iris=Class.forName("net.irisshaders.iris.Iris");
            getManager=iris.getMethod("getPipelineManager");
            Class<?> manager=getManager.getReturnType();
            getPipeline=manager.getMethod("getPipelineNullable");
            getGeneration=manager.getMethod("getVersionCounterForSodiumShaderReload");
            pipelineBridge=true;
        } catch(ReflectiveOperationException | LinkageError failure) {
            // Preserve the public toggle API if future Iris internals change.
            pipelineBridge=false;
            warn(failure);
        }
    }

    private static void warn(Throwable failure) {
        if(warned) return;
        warned=true;
        LoggerFactory.getLogger("Prism Lights").warn("Iris pipeline observation unavailable; reload compatibility needs validation for this Iris version",failure);
    }
}
