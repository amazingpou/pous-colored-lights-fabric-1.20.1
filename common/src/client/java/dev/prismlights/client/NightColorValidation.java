package dev.prismlights.client;

import dev.prismlights.platform.PlatformServices;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.ScreenshotRecorder;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;
import org.slf4j.LoggerFactory;
import java.nio.ByteBuffer;
import java.nio.file.*;
import java.util.Optional;

/** Opt-in rendered-pixel regression, confined to the separate QA world. */
public final class NightColorValidation {
    private static final boolean ACTIVE=PlatformServices.get().isDevelopmentEnvironment()
        && Boolean.getBoolean("prism.nightColorTest");
    private static final boolean IRIS=PlatformServices.get().isModLoaded("iris");
    private static final String[] PACKS=ColorResponseValidation.renderers();
    private static final String[] MODES={"night-on","night-off","day-on","day-off"};
    private static final String[] MATERIALS={"grass","concrete","armor","sheep"};
    private static final double[][] POINTS={{-2.5,146.01,2.5},{3.5,146.01,2.5},
        {-3.5,147.3,5.5},{2.5,147.0,5.5}};
    private static final ByteBuffer PIXELS=BufferUtils.createByteBuffer(7*7*4);
    private static int ticks,lastCapture=-1;
    private NightColorValidation() {}
    public static void prepareCamera(MinecraftClient client) {
        if(!ACTIVE || client.player==null) return;
        client.player.setYaw(0); client.player.prevYaw=0;
        client.player.setPitch(35); client.player.prevPitch=35;
    }
    public static void tick(MinecraftClient client) {
        if(!ACTIVE || client.world==null || client.player==null || client.getServer()==null) return;
        client.options.pauseOnLostFocus=false;
        client.options.hudHidden=true;
        if(client.isPaused()) return;
        int current=++ticks;
        if(current==40) {
            String player=client.player.getName().getString();
            commands(client,"gamemode creative "+player,"gamerule doMobSpawning false",
                "gamerule doDaylightCycle false","weather clear","time set midnight",
                // Remove the elevated density/wall QA fixtures: they would
                // otherwise shade this material scene during the day.
                "fill -16 199 -8 16 211 20 air","fill -16 240 -8 16 253 20 air",
                "fill -12 145 -8 12 158 18 air","fill -12 145 -8 0 145 18 grass_block",
                "fill 1 145 -8 12 145 18 white_concrete","setblock 0 149 -7 barrier",
                "setblock -3 146 3 prism_lights:blue_prism","setblock 3 146 3 prism_lights:blue_prism",
                "kill @e[tag=prism_night_qa]",
                "summon armor_stand -3.5 146 5.5 {Tags:[\"prism_night_qa\"],NoGravity:1b,Invulnerable:1b,ShowArms:1b,ArmorItems:[{id:\"minecraft:iron_boots\",count:1},{id:\"minecraft:iron_leggings\",count:1},{id:\"minecraft:iron_chestplate\",count:1},{id:\"minecraft:iron_helmet\",count:1}]}",
                "summon sheep 2.5 146 5.5 {Tags:[\"prism_night_qa\"],NoAI:1b,Invulnerable:1b,Silent:1b}",
                "tp "+player+" 0 150 -7 0 35");
            try { Files.writeString(client.runDirectory.toPath().resolve("night-colors.csv"),
                "renderer,mode,material,x,y,r,g,b,sky\n"); }
            catch(Exception error) { throw new IllegalStateException(error); }
        }
        if(current<60) return;
        int phase=(current-60)/100;
        int count=IRIS?PACKS.length*4:4;
        if(phase>=count) { log("Night color probe COMPLETE"); client.scheduleStop(); return; }
        int mode=phase%4;
        if((current-60)%100==0) {
            if(mode==0 && IRIS) reload(PACKS[phase/4]);
            commands(client,"time set "+(mode<2?"midnight":"noon"));
        }
        GpuLightVolume.setEnabled(mode%2==0);
    }
    public static void frame(MinecraftClient client,Matrix4f view,Matrix4f projection) {
        if(!ACTIVE || client.player==null || ticks<60 || ticks==lastCapture || (ticks-60)%100!=85) return;
        lastCapture=ticks;
        int phase=(ticks-60)/100;
        String renderer=IRIS?PACKS[phase/4].replace(".zip",""):"Vanilla";
        String mode=MODES[phase%4];
        if(IRIS) verifyPack(PACKS[phase/4]);
        var fb=client.getFramebuffer();
        int oldRead=GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,fb.fbo);
        var camera=client.gameRenderer.getCamera().getPos();
        try(LightingValidation.PixelPackState ignored=new LightingValidation.PixelPackState()) {
            StringBuilder rows=new StringBuilder();
            for(int p=0;p<POINTS.length;p++) {
                double[] point=POINTS[p];
                Vector4f clip=new Vector4f((float)(point[0]-camera.x),(float)(point[1]-camera.y),(float)(point[2]-camera.z),1);
                view.transform(clip); projection.transform(clip);
                int x=(int)((clip.x/clip.w*0.5+0.5)*fb.textureWidth);
                int y=(int)((clip.y/clip.w*0.5+0.5)*fb.textureHeight);
                if(clip.w<=0 || x<3 || y<3 || x>=fb.textureWidth-3 || y>=fb.textureHeight-3)
                    throw new AssertionError("Material probe outside camera: "+MATERIALS[p]);
                PIXELS.clear(); GL11.glReadPixels(x-3,y-3,7,7,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,PIXELS);
                double r=0,g=0,b=0;
                for(int i=0;i<49;i++){r+=PIXELS.get(i*4)&255;g+=PIXELS.get(i*4+1)&255;b+=PIXELS.get(i*4+2)&255;}
                int sky=client.world.getLightLevel(net.minecraft.world.LightType.SKY,
                    net.minecraft.util.math.BlockPos.ofFloored(point[0],point[1],point[2]));
                if(sky!=15) throw new AssertionError("Material fixture is shaded: "+MATERIALS[p]+" sky="+sky);
                rows.append(renderer).append(',').append(mode).append(',').append(MATERIALS[p]).append(',')
                    .append(x).append(',').append(fb.textureHeight-1-y).append(',')
                    .append(r/49).append(',').append(g/49).append(',').append(b/49).append(',').append(sky).append('\n');
            }
            Files.writeString(client.runDirectory.toPath().resolve("night-colors.csv"),rows,
                StandardOpenOption.APPEND);
        } catch(Exception error) { throw new IllegalStateException("Rendered night probe failed",error); }
        finally { GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,oldRead); }
        ScreenshotRecorder.saveScreenshot(client.runDirectory,renderer+"-"+mode+".png",fb,message->log(message.getString()));
        log("Captured "+renderer+" "+mode+" camera="+camera+" sky="+client.world.getSkyAngle(1));
    }
    static void reload(String pack) {
        try {
            Class<?> iris=Class.forName("net.irisshaders.iris.Iris");
            Object config=iris.getMethod("getIrisConfig").invoke(null);
            if(!pack.equals("Sodium")) config.getClass().getMethod("setShaderPackName",String.class).invoke(config,pack);
            config.getClass().getMethod("setShadersEnabled",boolean.class).invoke(config,!pack.equals("Sodium"));
            config.getClass().getMethod("save").invoke(config); iris.getMethod("reload").invoke(null);
        } catch(ReflectiveOperationException error) {throw new IllegalStateException(error);}
    }
    static void verifyPack(String pack) {
        try {
            Class<?> iris=Class.forName("net.irisshaders.iris.Iris");
            Optional<?> error=(Optional<?>)iris.getMethod("getStoredError").invoke(null);
            Class<?> api=Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            boolean active=(Boolean)api.getMethod("isShaderPackInUse").invoke(api.getMethod("getInstance").invoke(null));
            String actual=(String)iris.getMethod("getCurrentPackName").invoke(null);
            if(error.isPresent() || active==pack.equals("Sodium") || (active && !actual.equals(pack)))
                throw new AssertionError("Wrong renderer or compile failure: "+actual+" "+error);
        } catch(ReflectiveOperationException error) {throw new IllegalStateException(error);}
    }
    private static void commands(MinecraftClient client,String... commands) {
        client.getServer().execute(()->{
            var server=client.getServer(); if(server==null)return;
            for(String command:commands) server.getCommandManager().executeWithPrefix(server.getCommandSource(),command);
        });
    }
    private static void log(String text) {LoggerFactory.getLogger("Prism Lights").info(text);}
}
