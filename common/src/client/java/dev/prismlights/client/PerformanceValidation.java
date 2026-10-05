package dev.prismlights.client;

import dev.prismlights.PrismBlock;
import dev.prismlights.PrismFireworkEntity;
import dev.prismlights.PrismLights;
import dev.prismlights.platform.PlatformServices;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import org.slf4j.LoggerFactory;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;

/** Opt-in timings in a separate copy of the showcase. No synchronous GPU probes during sampling. */
public final class PerformanceValidation {
    public static final boolean ACTIVE=PlatformServices.get().isDevelopmentEnvironment()
        && Boolean.getBoolean("prism.performanceTest");
    private static final String[] PHASES={"static-on","static-off","far-chunk","near-chunk",
        "lamp-toggle","walk-on","walk-off","fireworks-16"};
    private static final int WARMUP=160,PERIOD=120;
    private static int ticks,phase=-1,step;
    private static long previousFrame;
    private static final StringBuilder rows=new StringBuilder("phase,kind,milliseconds,allocatedBytes,propagationBuilds,texture\n");
    private static final com.sun.management.ThreadMXBean threads=ACTIVE
        && ManagementFactory.getThreadMXBean() instanceof com.sun.management.ThreadMXBean bean?bean:null;
    private static long allocatedBefore;
    private static boolean samplingTick;
    private PerformanceValidation() {}
    public static long allocated() { return threads==null?0:threads.getThreadAllocatedBytes(Thread.currentThread().getId()); }
    public static void beforeTick(MinecraftClient client,LightField field,PrismConfig config) {
        samplingTick=false;
        if(!ACTIVE || client.world==null || client.player==null || client.getServer()==null || client.isPaused()) return;
        client.options.pauseOnLostFocus=false;client.options.hudHidden=true;
        int current=++ticks;
        if(current==20) {
            if(Files.exists(Path.of(client.runDirectory.toString(),"performance.csv")))
                throw new IllegalStateException("Archive previous profiling output before rerunning");
            client.getServer().execute(()->{
                var server=client.getServer();var commands=server.getCommandManager();var source=server.getCommandSource();
                for(String command:new String[]{"gamemode creative Player","time set midnight","weather clear",
                        "gamerule doDaylightCycle false","gamerule doMobSpawning false","tp Player 0 -49 -36 0 17"})
                    commands.executeWithPrefix(source,command);
                var player=server.getPlayerManager().getPlayer(client.player.getUuid());
                player.getAbilities().flying=true;player.sendAbilitiesUpdate();
            });
        }
        if(current<WARMUP) return;
        phase=(current-WARMUP)/PERIOD;step=(current-WARMUP)%PERIOD;
        if(phase>=PHASES.length) {
            try { Files.writeString(client.runDirectory.toPath().resolve("performance.csv"),rows); }
            catch(Exception error){throw new IllegalStateException(error);}
            log("Performance probe COMPLETE");client.scheduleStop();return;
        }
        config.enabled=phase!=1 && phase!=6;
        if(step==0) {
            previousFrame=0;
            client.getServer().execute(()->client.getServer().getCommandManager().executeWithPrefix(
                client.getServer().getCommandSource(),"tp Player 0 -49 -36 0 17"));
            log("Performance phase "+PHASES[phase]);
        }
        if(step%10==0 && (phase==2 || phase==3)) {
            int x=phase==2?-6:0,z=phase==2?-7:1;
            if(!client.world.isChunkLoaded(x,z)) throw new AssertionError("Profiling chunk not loaded");
            var chunk=client.world.getChunk(x,z);
            // Exercise the light engine's chunk event path without removing server terrain.
            field.unload(client.world,chunk);field.load(client.world,chunk);
        }
        boolean lampActive=step%20==0;
        if(phase==4 && step%10==0) client.getServer().execute(()->{
            var world=client.getServer().getOverworld();var pos=new BlockPos(-15,-56,22);
            world.setBlockState(pos,world.getBlockState(pos).with(PrismBlock.ACTIVE,lampActive),3);
        });
        if((phase==5 || phase==6) && step%5==0) {
            double x=step<60?step*2:(120-step)*2;
            client.getServer().execute(()->client.getServer().getCommandManager().executeWithPrefix(
                client.getServer().getCommandSource(),"tp Player "+x+" -49 -36 0 17"));
        }
        if(phase==7 && step%35==0) client.getServer().execute(()->{
            var world=client.getServer().getOverworld();
            for(int i=0;i<16;i++) {
                var shot=new PrismFireworkEntity(PrismLights.FIREWORK_ENTITY,world);
                shot.setColor(net.minecraft.util.math.MathHelper.hsvToRgb(i/16f,0.95f,1));
                shot.setPosition((i%4)*0.2,-48,-32+(i/4)*0.2);shot.setVelocity(0,-0.08,0.15);
                world.spawnEntity(shot);
            }
        });
        if(step==110 && phase!=4) log("Field digest "+PHASES[phase]+" "+digest());
        allocatedBefore=allocated();
        samplingTick=true;
    }
    public static void measuredTick(long start,long buildsBefore) {
        if(!ACTIVE || !samplingTick || phase<0 || phase>=PHASES.length || step<20) return;
        samplingTick=false;
        long elapsed=System.nanoTime()-start,bytes=allocated()-allocatedBefore;
        rows.append(PHASES[phase]).append(",field,").append(elapsed/1e6).append(',').append(bytes).append(',')
            .append(PrismClient.lightField().propagationBuilds()-buildsBefore).append(',')
            .append(GpuLightVolume.currentTexture()).append('\n');
    }
    public static void prepareCamera(MinecraftClient client) {
        if(!ACTIVE || client.player==null) return;
        client.player.setYaw(0);client.player.prevYaw=0;client.player.setPitch(17);client.player.prevPitch=17;
    }
    public static void frame() {
        if(!ACTIVE || phase<0 || phase>=PHASES.length) return;
        if(MinecraftClient.getInstance().isPaused()) {previousFrame=0;return;}
        // Snapshot hashing is verification work, not part of rendering cost.
        if(step>=105 && step<=112) {previousFrame=0;return;}
        long now=System.nanoTime();
        if(previousFrame!=0 && step>=20) rows.append(PHASES[phase]).append(",frame,").append((now-previousFrame)/1e6)
            .append(",0,0,0\n");
        previousFrame=now;
    }
    private static String digest() {
        var volume=GpuLightVolume.current();if(volume==null) return "empty";
        try {
            MessageDigest digest=MessageDigest.getInstance("SHA-256");
            for(float value:volume.rgba) {
                int bits=Float.floatToRawIntBits(value);
                digest.update((byte)(bits>>>24));digest.update((byte)(bits>>>16));
                digest.update((byte)(bits>>>8));digest.update((byte)bits);
            }
            return volume.x+","+volume.y+","+volume.z+","+volume.width+","+volume.height+","+volume.depth+":"
                +java.util.HexFormat.of().formatHex(digest.digest());
        } catch(Exception error){throw new IllegalStateException(error);}
    }
    private static void log(String text){LoggerFactory.getLogger("Prism Lights").info(text);}
}
