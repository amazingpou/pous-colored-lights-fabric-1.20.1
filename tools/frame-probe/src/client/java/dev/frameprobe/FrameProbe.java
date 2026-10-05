package dev.frameprobe;

import dev.frameprobe.mixin.PauseMenuAccess;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.util.ScreenshotRecorder;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.nio.file.Files;
import org.slf4j.LoggerFactory;

/** Fixed-camera on/off timings in a disposable world, without touching our JAR. */
public final class FrameProbe implements ClientModInitializer {
    public static final boolean ACTIVE=Boolean.getBoolean("frameprobe.active");
    private static final boolean DYNAMIC=Boolean.getBoolean("frameprobe.dynamic");
    private static final String[] PHASES=DYNAMIC?new String[]{"static-on","static-off","far-chunk","near-chunk","lamp-toggle","fireworks-16"}:new String[]{"rgb-on","rgb-off"};
    private static final long WARMUP=20_000_000_000L,PERIOD=DYNAMIC?20_000_000_000L:30_000_000_000L;
    private static final StringBuilder rows=new StringBuilder("phase,kind,milliseconds\n");
    private static long start,phaseStart,previousFrame,frameStart,fieldStart,publishes,worldFrames,compositions;
    private static int phase=-1;
    private static boolean done,closing,cameraReady;
    private static Object config,engine;
    private static int phaseTicks;
    private static Field enabled;
    private static java.lang.reflect.Method currentVolume;
    private static Object observedVolume;
    private static float yaw,pitch;
    private static long gcBeforeTime,gcBeforeCount;
    private static final StringBuilder gcRows=new StringBuilder("phase,gcCount,gcMillis,publishes,worldFrames,compositions\n");
    private static String label() {return PHASES[phase];}
    static String phaseLabel(){return PulseProbe.ACTIVE?PulseProbe.label():FireworkProbe.ACTIVE?FireworkProbe.label():phase<0?"warmup":label();}
    private static long gcTime() {return ManagementFactory.getGarbageCollectorMXBeans().stream().mapToLong(b->b.getCollectionTime()).sum();}
    private static long gcCount() {return ManagementFactory.getGarbageCollectorMXBeans().stream().mapToLong(b->b.getCollectionCount()).sum();}
    @Override public void onInitializeClient() {
        if(Boolean.getBoolean("frameprobe.glDiagnostics"))GlDiagnostics.register();
        if(Boolean.getBoolean("frameprobe.transitionLifecycle"))TransitionLifecycleProbe.register();
        if(PulseProbe.ACTIVE)PulseProbe.register();
        if(FireworkProbe.ACTIVE)FireworkProbe.register();
        if(HaloProbe.ACTIVE)HaloProbe.register();
        if(Boolean.getBoolean("frameprobe.retentionCheck"))RetentionCheck.register();
        if(Boolean.getBoolean("frameprobe.candleStabilize"))SceneStabilizer.register();
        if(!ACTIVE)return;
        ProfileProbe.start(MinecraftClient.getInstance().runDirectory.toPath());
        LoggerFactory.getLogger("Frame Probe").info("FRAME PROBE initialized mod={}",FabricLoader.getInstance().getModContainer("prism_lights").orElseThrow().getMetadata().getVersion());
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
        WorldRenderEvents.END.register(context->{if(phase>=0)worldFrames++;});
    }
    private void tick(MinecraftClient client) {
        if(!ACTIVE || done)return;
        if(!client.runDirectory.getName().startsWith("run-frame-validation-"))throw new IllegalStateException("Disposable frame world required");
        if(closing) {
            ProfileProbe.finish();
            done=true;
            ((PauseMenuAccess)client.currentScreen).frameprobe$saveAndQuit();
            LoggerFactory.getLogger("Frame Probe").info("FRAME PROBE COMPLETE server={} world={}",client.getServer(),client.world);
            client.scheduleStop();return;
        }
        if(client.world==null || client.player==null || client.getServer()==null)return;
        client.options.pauseOnLostFocus=false;
        client.options.hudHidden=true;
        if(start==0) {
            try {
                var method=Class.forName("dev.prismlights.client.PrismClient").getDeclaredMethod("lightField");method.setAccessible(true);
                engine=method.invoke(null);var field=engine.getClass().getDeclaredField("config");field.setAccessible(true);
                config=field.get(engine);enabled=config.getClass().getField("enabled");enabled.setBoolean(config,true);
                currentVolume=Class.forName("dev.prismlights.client.GpuLightVolume").getMethod("current");
            } catch(Exception error){throw new IllegalStateException(error);}
            yaw=0;pitch=7;start=System.nanoTime();
            client.getServer().execute(()->{
                var server=client.getServer();
                for(String command:new String[]{"gamemode creative Player","time set midnight","weather clear","gamerule doDaylightCycle false","gamerule doMobSpawning false","tp Player 0.0 -55.0 7.0 0 7"})server.getCommandManager().executeWithPrefix(server.getCommandSource(),command);
                var player=server.getPlayerManager().getPlayer(client.player.getUuid());
                player.getAbilities().flying=true;player.sendAbilitiesUpdate();
                cameraReady=true;
            });
            LoggerFactory.getLogger("Frame Probe").info("FRAME PROBE world ready position={} yaw={} pitch={}",client.player.getPos(),yaw,pitch);
        }
        long now=System.nanoTime();if(now-start<WARMUP || !cameraReady)return;
        try {
            Object current=currentVolume.invoke(null);
            if(phase>=0 && current!=observedVolume)publishes++;
            observedVolume=current;
        } catch(Exception error){throw new IllegalStateException(error);}
        if(phase<0) {
            if(client.player.getPos().squaredDistanceTo(0,-55,7)>.2)throw new IllegalStateException("Camera not at gallery");
            LoggerFactory.getLogger("Frame Probe").info("FRAME PROBE scene position={} skip={} loaded={} screen={} hit={}",client.player.getPos(),client.skipGameRender,client.world!=null,client.currentScreen,client.crosshairTarget);
            ScreenshotRecorder.saveScreenshot(client.runDirectory,"frame-scene-on.png",client.getFramebuffer(),message->{});
            begin(0,now);
        }
        if(DYNAMIC && phase>=0 && ++phaseTicks%10==0) {
            if(phase==2 || phase==3) {
                var chunk=client.world.getChunk(phase==2?-6:0,phase==2?-7:1);
                try {
                    for(String event:new String[]{"unload","load"})for(var method:engine.getClass().getMethods())
                        if(method.getName().equals(event))method.invoke(engine,client.world,chunk);
                } catch(Exception error){throw new IllegalStateException(error);}
            }
            if(phase==4 || phase==5) {
                int at=phaseTicks;int currentPhase=phase;
                client.getServer().execute(()->{
                    var server=client.getServer();var commands=server.getCommandManager();var source=server.getCommandSource();
                    if(currentPhase==4)commands.executeWithPrefix(source,"setblock -15 -56 22 prism_lights:white_prism[active="+(at%20==0)+"]");
                    else for(int i=0;i<16;i++)commands.executeWithPrefix(source,
                        "summon prism_lights:prism_firework "+(-7+i)+" -54 13 {PrismColor:"+(i%2==0?255:16711935)+",Motion:[0.0d,0.02d,0.2d]}");
                });
            }
        }
        if(now-phaseStart>PERIOD) {
            if(DYNAMIC && (phase==3 || phase==4) && compositions<4)
                throw new IllegalStateException("Rebuild benchmark did not complete fields: "+label()+" count="+compositions);
            gcRows.append(label()).append(',').append(gcCount()-gcBeforeCount).append(',').append(gcTime()-gcBeforeTime).append(',').append(publishes).append(',').append(worldFrames).append(',').append(compositions).append('\n');
            LoggerFactory.getLogger("Frame Probe").info("FRAME PROBE phase complete {} compositions={} GPUchanges={}",label(),compositions,publishes);
            if(worldFrames<100)throw new IllegalStateException("World was not rendered");
            if(phase+1<PHASES.length)begin(phase+1,now);
            else {
                try {Files.writeString(client.runDirectory.toPath().resolve("frames.csv"),rows);Files.writeString(client.runDirectory.toPath().resolve("gc.csv"),gcRows);}
                catch(Exception error){throw new IllegalStateException(error);}
                closing=true;phase=-1;client.setScreen(new GameMenuScreen(true));
            }
        }
    }
    private static void begin(int next,long now) {
        phase=next;phaseTicks=0;phaseStart=now;previousFrame=0;publishes=0;worldFrames=0;compositions=0;
        gcBeforeTime=gcTime();gcBeforeCount=gcCount();
        try {enabled.setBoolean(config,next!=1);}catch(Exception error){throw new IllegalStateException(error);}
        LoggerFactory.getLogger("Frame Probe").info("FRAME PROBE phase {}",label());
    }
    public static void frameBegin() {
        ProfileProbe.frame();
        if(PulseProbe.ACTIVE || FireworkProbe.ACTIVE || HaloProbe.ACTIVE) {
            var c=MinecraftClient.getInstance();
            for(var key:new net.minecraft.client.option.KeyBinding[]{c.options.forwardKey,c.options.backKey,c.options.leftKey,c.options.rightKey,c.options.jumpKey,c.options.sneakKey,c.options.sprintKey})key.setPressed(false);
            if(c.player!=null){float fixedPitch=HaloProbe.ACTIVE?35:15;c.player.setYaw(0);c.player.prevYaw=0;c.player.setPitch(fixedPitch);c.player.prevPitch=fixedPitch;}
        }
        if(!ACTIVE || start==0 || done)return;
        var client=MinecraftClient.getInstance();
        for(var key:new net.minecraft.client.option.KeyBinding[]{client.options.forwardKey,client.options.backKey,client.options.leftKey,client.options.rightKey,client.options.jumpKey,client.options.sneakKey,client.options.sprintKey})key.setPressed(false);
        if(client.player!=null){client.player.setYaw(yaw);client.player.prevYaw=yaw;client.player.setPitch(pitch);client.player.prevPitch=pitch;}
        long now=System.nanoTime();frameStart=now;
        if(phase>=0 && now-phaseStart>2_000_000_000L && previousFrame!=0 && !client.isPaused())
            rows.append(label()).append(",interval,").append((now-previousFrame)/1e6).append('\n');
        previousFrame=now;
    }
    public static void frameEnd() {
        PulseProbe.rendered();
        FireworkProbe.rendered();
        HaloProbe.rendered();
        if(ACTIVE && phase>=0 && System.nanoTime()-phaseStart>2_000_000_000L && frameStart!=0)
            rows.append(label()).append(",render,").append((System.nanoTime()-frameStart)/1e6).append('\n');
    }
    public static void fieldBegin() {if(ACTIVE)fieldStart=System.nanoTime();}
    public static void fieldEnd() {
        if(ACTIVE && phase>=0 && System.nanoTime()-phaseStart>2_000_000_000L)
            rows.append(label()).append(",field,").append((System.nanoTime()-fieldStart)/1e6).append('\n');
    }
    // Legacy hook retained for binary compatibility. Observe completed volume
    // identities instead: the revisions have different publish overloads.
    public static void published() {}
    public static void composed() {if(ACTIVE && phase>=0)compositions++;}
}
