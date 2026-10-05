package dev.frameprobe;

import dev.frameprobe.mixin.PauseMenuAccess;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.util.math.BlockPos;
import org.slf4j.LoggerFactory;
import java.lang.reflect.*;
import java.nio.file.Files;
import java.util.*;

/** Natural pulse latency in a disposable native client; never forces completion. */
final class PulseProbe {
    static final boolean ACTIVE=Boolean.getBoolean("frameprobe.pulses");
    private static BooleanProperty activeProperty;
    private static final String[] PHASES={"single-1t","single-2t","single-4t","single-8t","single-16t",
        "alternating16-1t","alternating16-2t","alternating16-4t","alternating16-8t","alternating16-16t"};
    private static final int[] PERIODS={1,2,4,8,16,1,2,4,8,16};
    private static final BlockPos[] LAMPS=new BlockPos[16];
    private static final StringBuilder events=new StringBuilder("phase,event,mask,milliseconds,missed,propagationBuilds,measured\n");
    private static final StringBuilder frames=new StringBuilder("phase,milliseconds\n");
    private static final StringBuilder summary=new StringBuilder("phase,changes,reached,missed,builds,frames\n");
    private static final boolean PIXELS=Boolean.getBoolean("frameprobe.pulsePixels");
    private static final String COLOR=System.getProperty("frameprobe.pulseColor","blue");
    private static final boolean ISOLATED=Boolean.getBoolean("frameprobe.pulseIsolated");
    private static final StringBuilder nativeRows=new StringBuilder("phase,desired,published,blockLight,r,g,b,milliseconds\n");
    private static Field view,projection;
    private static Constructor<?> pixelState;
    private static final java.nio.ByteBuffer pixels=org.lwjgl.BufferUtils.createByteBuffer(5*5*4);
    private static Object engine,lastVolume,lastPublished;
    private static Field published,builds;
    private static Method current,sourcePosition;
    private static volatile boolean sceneReady;
    private static boolean setup,layoutScheduled,finished,closing,transition,transitionMeasured;
    private static int phase=-1,ticks,clientMask=-1,publishedMask=-1,changes,reached,missed,frameCount;
    private static long start,phaseStart,changedAt,previousFrame,buildsBefore,lastClock;
    static {for(int i=0;i<16;i++)LAMPS[i]=new BlockPos(994+(i%4)*4,-59,1008+(i/4)*4);}
    static String label(){return phase<0?"pulse-warmup":PHASES[phase];}
    static void register(){ClientTickEvents.END_CLIENT_TICK.register(PulseProbe::tick);}
    private static long builds(){try{return builds.getLong(engine);}catch(Exception e){throw new IllegalStateException(e);}}
    private static void command(MinecraftClient client,String text){
        var server=client.getServer();server.getCommandManager().executeWithPrefix(server.getCommandSource(),text);
    }
    private static void tick(MinecraftClient client){
        if(finished || client.world==null || client.player==null || client.getServer()==null)return;
        if(!client.runDirectory.getName().startsWith("run-frame-validation-pulses-"))throw new IllegalStateException("Disposable pulse world required");
        if(closing){
            ProfileProbe.finish();finished=true;
            ((PauseMenuAccess)client.currentScreen).frameprobe$saveAndQuit();
            LoggerFactory.getLogger("Pulse Probe").info("PULSE PROBE COMPLETE");client.scheduleStop();return;
        }
        client.options.pauseOnLostFocus=false;client.options.hudHidden=true;
        if(client.isPaused())return;
        if(!setup){
            setup=true;start=System.nanoTime();
            try{
                var api=Class.forName("dev.prismlights.client.PrismClient").getDeclaredMethod("lightField");api.setAccessible(true);engine=api.invoke(null);
                published=engine.getClass().getDeclaredField("publishedSources");published.setAccessible(true);
                builds=engine.getClass().getDeclaredField("propagationBuilds");builds.setAccessible(true);
                current=Class.forName("dev.prismlights.client.GpuLightVolume").getMethod("current");
                if(PIXELS){
                    var gpu=Class.forName("dev.prismlights.client.GpuLightVolume");
                    view=gpu.getDeclaredField("inverseView");projection=gpu.getDeclaredField("inverseProjection");view.setAccessible(true);projection.setAccessible(true);
                    pixelState=Class.forName("dev.prismlights.client.LightingValidation$PixelPackState").getDeclaredConstructor();pixelState.setAccessible(true);
                }
                activeProperty=(BooleanProperty)Class.forName("dev.prismlights.PrismBlock").getField("ACTIVE").get(null);
                var cfg=engine.getClass().getDeclaredField("config");cfg.setAccessible(true);cfg.get(engine).getClass().getField("enabled").setBoolean(cfg.get(engine),true);
                ProfileProbe.start(client.runDirectory.toPath());
            }catch(Exception e){throw new IllegalStateException(e);}
            client.getServer().execute(()->{
                for(String c:new String[]{"gamemode creative Player","time set midnight","weather clear",
                    "gamerule doDaylightCycle false","gamerule doMobSpawning false","tp Player 1000 -54 999 0 15"})command(client,c);
                var player=client.getServer().getPlayerManager().getPlayer(client.player.getUuid());player.getAbilities().flying=true;player.sendAbilitiesUpdate();
            });return;
        }
        if(!layoutScheduled && System.nanoTime()-start>5_000_000_000L){
            layoutScheduled=true;client.getServer().execute(()->{
                for(String c:new String[]{"fill 979 -59 993 1021 -49 1027 air","fill 979 -60 993 1021 -60 1027 white_concrete"})command(client,c);
                if(!Set.of("blue","green").contains(COLOR))throw new AssertionError("Unsupported pulse fixture color");
                for(var p:LAMPS)command(client,"setblock "+p.getX()+" "+p.getY()+" "+p.getZ()+" prism_lights:"+COLOR+"_redstone_prism[active=false]");
                // Constant anchors keep both configurations inside a stable RGB volume.
                if(!ISOLATED)for(String c:new String[]{"setblock 989 -59 1003 prism_lights:green_prism[active=true]",
                    "setblock 1011 -59 1025 prism_lights:green_prism[active=true]"})command(client,c);
                sceneReady=true;
            });return;
        }
        if(!sceneReady || System.nanoTime()-start<30_000_000_000L)return;
        if(client.player.getPos().squaredDistanceTo(1000.5,-54,999.5)>4)throw new AssertionError("Pulse camera moved during measurement");
        if(phase<0)begin(0,client);
        if(++ticks%PERIODS[phase]==0){
            boolean turn=(ticks/PERIODS[phase])%2==0;int currentPhase=phase;
            client.getServer().execute(()->{
                for(int i=0;i<(currentPhase<5?1:16);i++){
                    boolean on=currentPhase<5?turn:((i&1)==0)==turn;var p=LAMPS[i];
                    client.getServer().getOverworld().setBlockState(p.down(),on?Blocks.REDSTONE_BLOCK.getDefaultState():Blocks.WHITE_CONCRETE.getDefaultState(),3);
                }
            });
        }
        if(System.nanoTime()-phaseStart>8_000_000_000L){
            if(transition && transitionMeasured)missed++;
            summary.append(label()).append(',').append(changes).append(',').append(reached).append(',').append(missed).append(',').append(builds()-buildsBefore).append(',').append(frameCount).append('\n');
            LoggerFactory.getLogger("Pulse Probe").info("PULSE phase={} changes={} reached={} missed={} builds={}",label(),changes,reached,missed,builds()-buildsBefore);
            if(changes<3 || frameCount<100)throw new AssertionError("Pulse scene not exercised: "+label());
            if(phase+1<PHASES.length)begin(phase+1,client);
            else{
                try{Files.writeString(client.runDirectory.toPath().resolve("pulses.csv"),events);Files.writeString(client.runDirectory.toPath().resolve("pulse-frames.csv"),frames);Files.writeString(client.runDirectory.toPath().resolve("pulse-summary.csv"),summary);Files.writeString(client.runDirectory.toPath().resolve("pulse-native.csv"),nativeRows);}
                catch(Exception e){throw new IllegalStateException(e);}
                closing=true;client.setScreen(new GameMenuScreen(true));
            }
        }
    }
    private static void begin(int next,MinecraftClient client){
        phase=next;ticks=0;changes=reached=missed=frameCount=0;transition=false;clientMask=-1;
        phaseStart=System.nanoTime();previousFrame=0;buildsBefore=builds();
        client.getServer().execute(()->{for(var p:LAMPS)client.getServer().getOverworld().setBlockState(p.down(),Blocks.WHITE_CONCRETE.getDefaultState(),3);});
    }
    static void rendered(){
        if(!ACTIVE || phase<0 || finished || closing)return;
        var client=MinecraftClient.getInstance();if(client.world==null)return;
        long now=System.nanoTime();
        if(client.isPaused()){if(lastClock!=0){long pause=now-lastClock;start+=pause;phaseStart+=pause;changedAt+=pause;}lastClock=now;previousFrame=0;return;}
        lastClock=now;boolean measuring=now-phaseStart>1_000_000_000L && now-phaseStart<7_000_000_000L;
        if(measuring && previousFrame!=0)frames.append(label()).append(',').append((now-previousFrame)/1e6).append('\n');previousFrame=now;frameCount++;
        int desired=0;
        for(int i=0;i<16;i++){var s=client.world.getBlockState(LAMPS[i]);if(s.contains(activeProperty) && s.get(activeProperty))desired|=1<<i;}
        if(desired!=clientMask){
            if(transition){if(transitionMeasured)missed++;events.append(label()).append(",superseded,").append(clientMask).append(',').append((now-changedAt)/1e6).append(",1,").append(builds()).append(',').append(transitionMeasured).append('\n');}
            clientMask=desired;changedAt=now;transition=true;transitionMeasured=measuring;if(measuring)changes++;
            events.append(label()).append(",client-state,").append(desired).append(",0,0,").append(builds()).append(',').append(measuring).append('\n');
        }
        try{
            Object volume=current.invoke(null);
            Object sourceList=published.get(engine);
            if(volume!=lastVolume || sourceList!=lastPublished){
                lastVolume=volume;lastPublished=sourceList;publishedMask=0;
                if(volume!=null)for(Object source:(List<?>)published.get(engine)){
                    if(sourcePosition==null){sourcePosition=source.getClass().getDeclaredMethod("pos");sourcePosition.setAccessible(true);}
                    var pos=(BlockPos)sourcePosition.invoke(source);
                    for(int i=0;i<16;i++)if(pos.equals(LAMPS[i]))publishedMask|=1<<i;
                }
            }
        }catch(Exception e){throw new IllegalStateException(e);}
        if(measuring){
            double r=-1,g=-1,b=-1;
            if(PIXELS){
                var fb=client.getFramebuffer();var camera=client.gameRenderer.getCamera().getPos();
                var clip=new org.joml.Vector4f((float)(994.5-camera.x),(float)(-58.99-camera.y),(float)(1010.5-camera.z),1);
                int old=org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL30.GL_READ_FRAMEBUFFER_BINDING);
                org.lwjgl.opengl.GL30.glBindFramebuffer(org.lwjgl.opengl.GL30.GL_READ_FRAMEBUFFER,fb.fbo);
                try(AutoCloseable ignored=(AutoCloseable)pixelState.newInstance()){
                    new org.joml.Matrix4f((org.joml.Matrix4f)view.get(null)).invert().transform(clip);
                    new org.joml.Matrix4f((org.joml.Matrix4f)projection.get(null)).invert().transform(clip);
                    int sx=(int)((clip.x/clip.w*.5+.5)*fb.textureWidth),sy=(int)((clip.y/clip.w*.5+.5)*fb.textureHeight);
                    if(clip.w<=0 || sx<2 || sy<2 || sx>=fb.textureWidth-2 || sy>=fb.textureHeight-2)throw new AssertionError("Pulse point outside framebuffer");
                    pixels.clear();org.lwjgl.opengl.GL11.glReadPixels(sx-2,sy-2,5,5,org.lwjgl.opengl.GL11.GL_RGBA,org.lwjgl.opengl.GL11.GL_UNSIGNED_BYTE,pixels);
                    r=g=b=0;for(int i=0;i<25;i++){r+=pixels.get(4*i)&255;g+=pixels.get(4*i+1)&255;b+=pixels.get(4*i+2)&255;}r/=25;g/=25;b/=25;
                }catch(Exception e){throw new IllegalStateException(e);}finally{org.lwjgl.opengl.GL30.glBindFramebuffer(org.lwjgl.opengl.GL30.GL_READ_FRAMEBUFFER,old);}
            }
            nativeRows.append(label()).append(',').append(clientMask).append(',').append(publishedMask).append(',').append(client.world.getLightLevel(net.minecraft.world.LightType.BLOCK,new BlockPos(994,-59,1010))).append(',').append(r).append(',').append(g).append(',').append(b).append(',').append((now-phaseStart)/1e6).append('\n');
        }
        if(transition && publishedMask==clientMask){
            if(transitionMeasured)reached++;transition=false;events.append(label()).append(",rgb-published,").append(clientMask).append(',').append((now-changedAt)/1e6).append(",0,").append(builds()).append(',').append(transitionMeasured).append('\n');
        }
    }
}
