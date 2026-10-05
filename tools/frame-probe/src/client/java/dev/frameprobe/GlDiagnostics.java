package dev.frameprobe;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL43;
import org.lwjgl.opengl.GLDebugMessageCallback;
import org.slf4j.LoggerFactory;

/** Opt-in synchronous callback to identify the Java caller of GL errors. */
final class GlDiagnostics {
    private static GLDebugMessageCallback callback;
    private static int errors;
    static void register() {
        ClientTickEvents.START_CLIENT_TICK.register(client -> {
            if(client.world==null || callback!=null)return;
            callback=GLDebugMessageCallback.create((source,type,id,severity,length,message,user)->{
                if(type==GL43.GL_DEBUG_TYPE_ERROR && ++errors<=8)
                    LoggerFactory.getLogger("GL Diagnostics").error("GL trace id="+id+" "+GLDebugMessageCallback.getMessage(length,message),new Throwable("Synchronous GL call stack"));
            });
            GL11.glEnable(GL43.GL_DEBUG_OUTPUT);
            GL11.glEnable(GL43.GL_DEBUG_OUTPUT_SYNCHRONOUS);
            GL43.glDebugMessageCallback(callback,0L);
            LoggerFactory.getLogger("GL Diagnostics").info("Synchronous GL diagnostics enabled (QA only)");
        });
    }
}
