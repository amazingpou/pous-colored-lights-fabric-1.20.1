package dev.frameprobe;

import dev.frameprobe.mixin.PauseMenuAccess;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.util.math.*;
import net.minecraft.world.LightType;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;
import org.slf4j.LoggerFactory;
import java.lang.reflect.*;
import java.nio.ByteBuffer;
import java.nio.file.Files;

/** Sixteen isolated lamps: colored / neutral / unlit radial framebuffer probes. */
final class HaloProbe {
    static final boolean ACTIVE=Boolean.getBoolean("frameprobe.haloStudy");
    private static final String[] COLORS="white orange magenta light_blue yellow lime pink gray light_gray cyan purple blue brown green red black".split(" ");
    private static final String[] MODES={"colored","neutral","unlit"};
    private static final ByteBuffer pixels=BufferUtils.createByteBuffer(5*5*4);
    private static final StringBuilder rows=new StringBuilder("color,mode,material,distance,r,g,b,blockLight,coverage,neutralMix\n");
    private static Object config;
    private static Field enabled,view,projection;
    private static Method current,sample;
    private static Constructor<?> packState;
    private static volatile boolean ready;
    private static boolean setup,layout,closing,done,capture;
    private static int phase=-1;
    private static long start,phaseStart;
    static void register(){ClientTickEvents.END_CLIENT_TICK.register(HaloProbe::tick);}
    private static void command(MinecraftClient c,String text){var s=c.getServer();s.getCommandManager().executeWithPrefix(s.getCommandSource(),text);}
    private static void tick(MinecraftClient c){
        if(done || c.world==null || c.player==null || c.getServer()==null)return;
        if(!c.runDirectory.getName().startsWith("run-frame-validation-halo-"))throw new IllegalStateException("Disposable halo world required");
        c.options.pauseOnLostFocus=false;c.options.hudHidden=true;
        if(closing){done=true;((PauseMenuAccess)c.currentScreen).frameprobe$saveAndQuit();LoggerFactory.getLogger("Halo Probe").info("HALO STUDY COMPLETE rows={}",16*3*2*17);c.scheduleStop();return;}
        if(c.isPaused())return;
        if(!setup){
            setup=true;start=System.nanoTime();
            try{
                var api=Class.forName("dev.prismlights.client.PrismClient").getDeclaredMethod("lightField");api.setAccessible(true);var engine=api.invoke(null);
                var f=engine.getClass().getDeclaredField("config");f.setAccessible(true);config=f.get(engine);enabled=config.getClass().getField("enabled");
                var gpu=Class.forName("dev.prismlights.client.GpuLightVolume");current=gpu.getMethod("current");view=gpu.getDeclaredField("inverseView");projection=gpu.getDeclaredField("inverseProjection");view.setAccessible(true);projection.setAccessible(true);
                sample=Class.forName("dev.prismlights.LightVolume").getMethod("sample",double.class,double.class,double.class);
                packState=Class.forName("dev.prismlights.client.LightingValidation$PixelPackState").getDeclaredConstructor();packState.setAccessible(true);
            }catch(Exception e){throw new IllegalStateException(e);}
            c.getServer().execute(()->{
                for(String text:new String[]{"gamemode creative Player","time set midnight","weather clear","gamerule doDaylightCycle false","gamerule doMobSpawning false","tp Player 1000.5 -46 990.5 0 35"})command(c,text);
                var p=c.getServer().getPlayerManager().getPlayer(c.player.getUuid());p.getAbilities().flying=true;p.sendAbilitiesUpdate();
            });return;
        }
        if(!layout && System.nanoTime()-start>5_000_000_000L){
            layout=true;c.getServer().execute(()->{
                for(int x=977>>4;x<=1023>>4;x++)for(int z=1003>>4;z<=1035>>4;z++)c.getServer().getOverworld().getChunk(x,z);
                for(String text:new String[]{"fill 977 -59 1003 1023 -49 1035 air","fill 977 -60 1003 1000 -60 1035 white_concrete","fill 1001 -60 1003 1023 -60 1035 grass_block"})command(c,text);
                ready=true;
            });return;
        }
        if(!ready || System.nanoTime()-start<20_000_000_000L)return;
        if(phase<0)begin(0,c);
        if(!capture && System.nanoTime()-phaseStart>2_000_000_000L)capture=true;
    }
    private static void begin(int next,MinecraftClient c){
        phase=next;phaseStart=System.nanoTime();capture=false;
        try{enabled.setBoolean(config,next%3==0);}catch(Exception e){throw new IllegalStateException(e);}
        c.getServer().execute(()->command(c,"setblock 1000 -59 1018 prism_lights:"+COLORS[next/3]+"_prism[active="+(next%3!=2)+"]"));
    }
    static void rendered(){
        if(!ACTIVE || !capture || done || closing)return;
        var c=MinecraftClient.getInstance();if(c.world==null || c.isPaused())return;
        String color=COLORS[phase/3],mode=MODES[phase%3];var fb=c.getFramebuffer();var camera=c.gameRenderer.getCamera().getPos();
        int old=GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,fb.fbo);
        try(AutoCloseable ignored=(AutoCloseable)packState.newInstance()){
            var v=new Matrix4f((Matrix4f)view.get(null)).invert();var p=new Matrix4f((Matrix4f)projection.get(null)).invert();Object volume=current.invoke(null);
            for(int side:new int[]{-1,1})for(int distance=1;distance<=17;distance++){
                double x=1000.5+side*distance,y=-58.99,z=1018.5;
                var clip=new Vector4f((float)(x-camera.x),(float)(y-camera.y),(float)(z-camera.z),1);v.transform(clip);p.transform(clip);
                int sx=(int)((clip.x/clip.w*.5+.5)*fb.textureWidth),sy=(int)((clip.y/clip.w*.5+.5)*fb.textureHeight);
                if(clip.w<=0 || sx<2 || sy<2 || sx>=fb.textureWidth-2 || sy>=fb.textureHeight-2)throw new AssertionError("Halo point outside framebuffer");
                var target=new Vec3d(x,y,z);var hit=c.world.raycast(new net.minecraft.world.RaycastContext(camera,target.add(target.subtract(camera).normalize().multiply(.1)),net.minecraft.world.RaycastContext.ShapeType.OUTLINE,net.minecraft.world.RaycastContext.FluidHandling.NONE,c.player));
                if(!hit.getBlockPos().equals(BlockPos.ofFloored(x,-60,z)))throw new AssertionError("Halo probe occluded: "+hit.getBlockPos());
                pixels.clear();GL11.glReadPixels(sx-2,sy-2,5,5,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,pixels);
                double[] rgb=new double[3];for(int n=0;n<25;n++)for(int ch=0;ch<3;ch++)rgb[ch]+=pixels.get(n*4+ch)&255;
                float coverage=volume==null?0:((float[])sample.invoke(volume,x,y,z))[3];
                rows.append(color).append(',').append(mode).append(',').append(side<0?"concrete":"grass").append(',').append(distance);
                for(double channel:rgb)rows.append(',').append(channel/25);
                rows.append(',').append(c.world.getLightLevel(LightType.BLOCK,BlockPos.ofFloored(x,y,z))).append(',').append(coverage).append(',').append(Math.pow(1-Math.min(1,Math.max(0,coverage)),3)).append('\n');
            }
            if(color.equals("blue") || color.equals("green") || color.equals("purple") || color.equals("red"))ScreenshotRecorder.saveScreenshot(c.runDirectory,"halo-"+color+"-"+mode+".png",fb,message->{});
            LoggerFactory.getLogger("Halo Probe").info("HALO color={} mode={} PASS",color,mode);
            if(phase==47){Files.writeString(c.runDirectory.toPath().resolve("halo-pixels.csv"),rows);closing=true;c.setScreen(new GameMenuScreen(true));}
            else begin(phase+1,c);
        }catch(Exception e){throw new IllegalStateException(e);}finally{GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,old);}
    }
}
