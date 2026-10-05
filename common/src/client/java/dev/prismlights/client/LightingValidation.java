package dev.prismlights.client;

import dev.prismlights.platform.PlatformServices;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.util.ScreenshotRecorder;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;
import org.slf4j.LoggerFactory;
import java.nio.ByteBuffer;
import java.nio.file.*;

/** Opt-in developer integration test in an isolated copied world. Read actual
 * rendered pixels over time, and capture entities lit by the native material path. */
public final class LightingValidation {
    private static final boolean ACTIVE=PlatformServices.get().isDevelopmentEnvironment() && Boolean.getBoolean("prism.lightingTest");
    private static int ticks,frame;
    private static final ByteBuffer pixels=BufferUtils.createByteBuffer(9*9*4);
    private static ByteBuffer scenePixels;
    private static double lastSceneMean=-1;
    private static int jumps;
    public static void prepareCamera(MinecraftClient client) {
        if(!ACTIVE || client.player==null) return;
        // Mouse input is applied before renderWorld, between client ticks.
        // Freeze here too so screenshots at 60 FPS share the same camera.
        client.player.setYaw(0); client.player.prevYaw=0;
        client.player.setPitch(15); client.player.prevPitch=15;
    }
    public static void tick(MinecraftClient client) {
        if(!ACTIVE || client.world==null || client.player==null || client.getServer()==null) return;
        // Keep the probe independent of cursor motion in another desktop app.
        client.player.setYaw(0); client.player.prevYaw=0;
        client.player.setPitch(15); client.player.prevPitch=15;
        if(++ticks==40) GpuUploadValidation.run();
        if(ticks==60) {
            client.options.setPerspective(Perspective.FIRST_PERSON);
            String name=client.player.getName().getString();
            client.getServer().execute(()->{
                var server=client.getServer(); var commands=server.getCommandManager(); var source=server.getCommandSource();
                commands.executeWithPrefix(source,"tp "+name+" 0 -60 0 0 15");
                commands.executeWithPrefix(source,"gamemode creative "+name);
                commands.executeWithPrefix(source,"gamerule doMobSpawning false");
                commands.executeWithPrefix(source,"execute at "+name+" run kill @e[type=creeper,distance=..32]");
                commands.executeWithPrefix(source,"execute at "+name+" run kill @e[type=zombie,distance=..32]");
                commands.executeWithPrefix(source,"execute at "+name+" run kill @e[type=skeleton,distance=..32]");
                commands.executeWithPrefix(source,"execute as "+name+" at @s run function prism_lights:demo");
                commands.executeWithPrefix(source,"time set midnight");
                commands.executeWithPrefix(source,"gamerule doDaylightCycle false");
                commands.executeWithPrefix(source,"gamerule doMobLoot false");
                commands.executeWithPrefix(source,"gamerule doEntityDrops false");
                commands.executeWithPrefix(source,"kill @e[tag=prism_validation]");
                commands.executeWithPrefix(source,"kill @e[type=item]");
                commands.executeWithPrefix(source,"execute at "+name+" run summon armor_stand ~3 ~ ~3 {Tags:[\"prism_validation\"],NoGravity:1b,Invulnerable:1b,ShowArms:1b,ArmorItems:[{id:\"minecraft:iron_boots\",count:1},{id:\"minecraft:iron_leggings\",count:1},{id:\"minecraft:iron_chestplate\",count:1},{id:\"minecraft:iron_helmet\",count:1}]}");
                commands.executeWithPrefix(source,"execute at "+name+" run summon sheep ~2 ~ ~4 {Tags:[\"prism_validation\"],NoAI:1b,Invulnerable:1b,Silent:1b}");
            });
        }
    }
    public static void frame(MinecraftClient client) {
        if(!ACTIVE || ticks<100 || GpuLightVolume.current()==null) return;
        int current=frame++;
        var framebuffer=client.getFramebuffer();
        int oldRead=GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,framebuffer.fbo);
        try (PixelPackState ignored=new PixelPackState()) {
            int x=(int)(framebuffer.textureWidth*0.22), y=(int)(framebuffer.textureHeight*0.28);
            pixels.clear(); GL11.glReadPixels(x,y,9,9,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,pixels);
            long red=0,green=0,blue=0;
            for(int i=0;i<81;i++){red+=pixels.get(i*4)&255;green+=pixels.get(i*4+1)&255;blue+=pixels.get(i*4+2)&255;}
            String row=current+","+red/81.0+","+green/81.0+","+blue/81.0+"\n";
            Files.writeString(Path.of("lighting-frames.csv"),row,StandardOpenOption.CREATE,StandardOpenOption.APPEND);
            int width=framebuffer.textureWidth,height=framebuffer.textureHeight;
            if(scenePixels==null || scenePixels.capacity()!=width*height*4) scenePixels=BufferUtils.createByteBuffer(width*height*4);
            scenePixels.clear(); GL11.glReadPixels(0,0,width,height,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,scenePixels);
            StringBuilder grid=new StringBuilder();
            double sceneMean=0;
            for(int gy=0;gy<9;gy++) for(int gx=0;gx<16;gx++) {
                int cx=(gx*2+1)*width/32,cy=(gy*2+1)*height/18;
                long r=0,g=0,b=0;
                for(int dy=-4;dy<=4;dy++) for(int dx=-4;dx<=4;dx++) {
                    int i=((cy+dy)*width+cx+dx)*4;
                    r+=scenePixels.get(i)&255;g+=scenePixels.get(i+1)&255;b+=scenePixels.get(i+2)&255;
                }
                grid.append(current).append(',').append(gy*16+gx).append(',').append(r/81.0).append(',').append(g/81.0).append(',').append(b/81.0).append('\n');
                sceneMean+=(r+g+b)/(81.0*3*144);
            }
            if(current>=130 && current<290 && lastSceneMean>=0 && Math.abs(sceneMean-lastSceneMean)>10 && jumps++<10) {
                ScreenshotRecorder.saveScreenshot(client.runDirectory,"lighting-jump-"+current+".png",framebuffer,t->{});
                LoggerFactory.getLogger("Prism Lights").info("Scene lighting jump frame {}: {} -> {}",current,lastSceneMean,sceneMean);
            }
            lastSceneMean=sceneMean;
            Files.writeString(Path.of("lighting-grid.csv"),grid,StandardOpenOption.CREATE,StandardOpenOption.APPEND);
            var camera=client.gameRenderer.getCamera();
            Files.writeString(Path.of("lighting-camera.csv"),current+","+camera.getYaw()+","+camera.getPitch()+","+camera.getPos().x+","+camera.getPos().y+","+camera.getPos().z+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);
        } catch(Exception e) {throw new IllegalStateException("Lighting integration probe failed",e);}
        finally {GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,oldRead);}
        if(current==120) ScreenshotRecorder.saveScreenshot(client.runDirectory,"native-material-entities.png",framebuffer,t->LoggerFactory.getLogger("Prism Lights").info(t.getString()));
        if(current==0 || current==30 || current==60 || current==90 || current==130 || current==170 || current==210 || current==250) ScreenshotRecorder.saveScreenshot(client.runDirectory,"temporal-"+current+".png",framebuffer,t->{});
        if(current==300) {
            String name=client.player.getName().getString();
            client.options.setPerspective(Perspective.THIRD_PERSON_BACK);
            client.getServer().execute(()->client.getServer().getCommandManager().executeWithPrefix(client.getServer().getCommandSource(),"tp "+name+" 4.5 -60 3 0 15"));
        }
        if(current==420) ScreenshotRecorder.saveScreenshot(client.runDirectory,"native-material-player.png",framebuffer,t->LoggerFactory.getLogger("Prism Lights").info(t.getString()));
        if(current==600) client.scheduleStop();
    }
    static final class PixelPackState implements AutoCloseable {
        private static final int[] NAMES={GL11.GL_PACK_ALIGNMENT,GL11.GL_PACK_ROW_LENGTH,
            GL12.GL_PACK_IMAGE_HEIGHT,GL11.GL_PACK_SKIP_PIXELS,GL11.GL_PACK_SKIP_ROWS,
            GL12.GL_PACK_SKIP_IMAGES,GL11.GL_PACK_SWAP_BYTES,GL11.GL_PACK_LSB_FIRST};
        private final int[] previous=new int[NAMES.length];
        private final int buffer=GL11.glGetInteger(GL21.GL_PIXEL_PACK_BUFFER_BINDING);
        PixelPackState() {
            for(int i=0;i<NAMES.length;i++){previous[i]=GL11.glGetInteger(NAMES[i]);GL11.glPixelStorei(NAMES[i],i==0?4:0);}
            GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER,0);
        }
        @Override public void close() {
            for(int i=0;i<NAMES.length;i++) GL11.glPixelStorei(NAMES[i],previous[i]);
            GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER,buffer);
        }
    }
}
