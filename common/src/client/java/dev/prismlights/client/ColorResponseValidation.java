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
import java.util.*;

/** Density/time/hue regression; only edits the isolated QA world when opted in. */
public final class ColorResponseValidation {
    private static final boolean ACTIVE=PlatformServices.get().isDevelopmentEnvironment()
        && Boolean.getBoolean("prism.colorResponseTest");
    private static final boolean IRIS=PlatformServices.get().isModLoaded("iris");
    private static final String[] ALL_PACKS={"BSL_v10.1.8.zip","ComplementaryUnbound_r5.9.3.zip",
        "ComplementaryReimagined_r5.9.3.zip","Sodium"};
    private static final String[] NAMES={"wall-blue","wall-blue-blue","wall-blue-purple","wall-purple-blue",
        "morning-single","morning-dense","noon-dense","morning-off","noon-off",
        "night-dense","shade-dense","shade-off"};
    private static final int PERIOD=90;
    private static final int FIRST_MODE=Boolean.getBoolean("prism.responseDaylightOnly")?4:0;
    private static final int MODES_PER_PACK=NAMES.length-FIRST_MODE;
    private static final ByteBuffer PIXELS=BufferUtils.createByteBuffer(5*5*4);
    private static final String[] PACKS=renderers();
    private static int ticks,lastCapture=-1;
    private ColorResponseValidation() {}
    static String[] renderers() {
        if(!IRIS) return new String[]{"Vanilla"};
        String requested=System.getProperty("prism.responseRenderer","");
        int selected=switch(requested){case "bsl"->0;case "unbound"->1;case "reimagined"->2;case "sodium"->3;default->-1;};
        return selected<0?ALL_PACKS:new String[]{ALL_PACKS[selected]};
    }
    public static void prepareCamera(MinecraftClient client) {
        if(!ACTIVE || client.player==null || ticks<60) return;
        int mode=((ticks-60)/PERIOD)%MODES_PER_PACK+FIRST_MODE;
        float pitch=mode<4?0:35;
        client.player.setYaw(0); client.player.prevYaw=0;
        client.player.setPitch(pitch); client.player.prevPitch=pitch;
    }
    public static void tick(MinecraftClient client) {
        if(!ACTIVE || client.world==null || client.player==null || client.getServer()==null) return;
        client.options.pauseOnLostFocus=false; client.options.hudHidden=true;
        if(client.isPaused()) return;
        int current=++ticks;
        if(current==40) {
            commands(client,"gamemode creative Player659","gamerule doMobSpawning false",
                "gamerule doDaylightCycle false","weather clear",
                // The fireworks QA platform sits above the daylight fixture.
                "fill -16 270 -8 16 283 20 air",
                "fill -16 199 -8 16 211 18 air","fill -12 199 -8 12 199 8 white_concrete",
                "fill -12 200 6 12 209 6 white_concrete","fill -12 200 -8 -12 209 6 white_concrete",
                "fill 12 200 -8 12 209 6 white_concrete","setblock 0 201 -5 barrier",
                "fill -16 240 -8 16 253 20 air","fill -16 240 -8 16 240 20 grass_block",
                "fill 7 240 -8 16 240 20 white_concrete","setblock 0 246 -7 barrier");
            try {Files.writeString(client.runDirectory.toPath().resolve("color-response.csv"),
                "renderer,mode,point,x,y,r,g,b,sky,dayAngle,alpha,visible\n");}
            catch(Exception error){throw new IllegalStateException(error);}
        }
        if(current<60) return;
        int phase=(current-60)/PERIOD;
        if(phase>=PACKS.length*MODES_PER_PACK){log("Color response probe COMPLETE");client.scheduleStop();return;}
        int mode=phase%MODES_PER_PACK+FIRST_MODE;
        if((current-60)%PERIOD==0) {
            if(mode==FIRST_MODE && IRIS) NightColorValidation.reload(PACKS[phase/MODES_PER_PACK]);
            if(mode==0) {
                commands(client,"fill -12 200 -8 11 208 5 air","setblock 0 201 -5 barrier",
                    "fill -16 241 -8 16 245 20 air","time set midnight",
                    "setblock -3 203 5 prism_lights:blue_prism","tp Player659 0 202 -5 0 0");
            }
            if(mode==1) commands(client,"setblock 3 203 5 prism_lights:blue_prism");
            if(mode==2) commands(client,"setblock 3 203 5 prism_lights:purple_prism");
            if(mode==3) commands(client,"setblock -3 203 5 prism_lights:purple_prism",
                "setblock 3 203 5 prism_lights:blue_prism");
            if(mode==4) commands(client,"setblock -3 203 5 air","setblock 3 203 5 air",
                "fill -16 241 -8 16 248 20 air","setblock 0 246 -7 barrier",
                "setblock -1 241 6 prism_lights:blue_prism","time set 1000","tp Player659 0 247 -7 0 35");
            if(mode==5) {
                List<String> lights=new ArrayList<>(); lights.add("setblock -1 241 6 air");
                for(int x=-10;x<=11;x+=3) for(int z=2;z<=11;z+=3)
                    lights.add("setblock "+x+" 241 "+z+" prism_lights:blue_prism");
                commands(client,lights.toArray(String[]::new));
            }
            if(mode==6 || mode==8) commands(client,"time set noon");
            if(mode==7) commands(client,"time set 1000");
            if(mode==9) commands(client,"time set midnight");
            if(mode==10) commands(client,"time set noon","fill -16 247 -4 -1 247 18 white_concrete",
                "setblock 0 241 -7 barrier","tp Player659 0 242 -7 0 35");
        }
        GpuLightVolume.setEnabled(mode!=7 && mode!=8 && mode!=11);
    }
    public static void frame(MinecraftClient client,Matrix4f view,Matrix4f projection) {
        if(!ACTIVE || ticks<60 || client.player==null || ticks==lastCapture || (ticks-60)%PERIOD!=75) return;
        lastCapture=ticks;
        int phase=(ticks-60)/PERIOD, mode=phase%MODES_PER_PACK+FIRST_MODE;
        String pack=PACKS[phase/MODES_PER_PACK], renderer=pack.replace(".zip","");
        if(IRIS) NightColorValidation.verifyPack(pack);
        var fb=client.getFramebuffer(); var camera=client.gameRenderer.getCamera().getPos();
        int oldRead=GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,fb.fbo);
        try(LightingValidation.PixelPackState ignored=new LightingValidation.PixelPackState()) {
            StringBuilder rows=new StringBuilder();
            int points=mode<4?25:6;
            for(int p=0;p<points;p++) {
                double x=mode<4?-6+p*0.5:new double[]{-5.5,-2.5,0.5,3.5,6.5,9.5}[p];
                double y=mode<4?205:241.01, z=mode<4?5.99:0.5;
                Vector4f clip=new Vector4f((float)(x-camera.x),(float)(y-camera.y),(float)(z-camera.z),1);
                view.transform(clip);projection.transform(clip);
                int sx=(int)((clip.x/clip.w*0.5+0.5)*fb.textureWidth);
                int sy=(int)((clip.y/clip.w*0.5+0.5)*fb.textureHeight);
                if(clip.w<=0 || sx<2 || sy<2 || sx>=fb.textureWidth-2 || sy>=fb.textureHeight-2)
                    throw new AssertionError("Response probe outside view: "+p);
                PIXELS.clear();GL11.glReadPixels(sx-2,sy-2,5,5,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,PIXELS);
                double r=0,g=0,b=0;
                for(int i=0;i<25;i++){r+=PIXELS.get(i*4)&255;g+=PIXELS.get(i*4+1)&255;b+=PIXELS.get(i*4+2)&255;}
                var pos=net.minecraft.util.math.BlockPos.ofFloored(x,y,z);
                int sky=client.world.getLightLevel(net.minecraft.world.LightType.SKY,pos);
                var volume=GpuLightVolume.current(); float alpha=volume==null?0:volume.sample(x,y,z)[3];
                var end=new net.minecraft.util.math.Vec3d(x,mode<4?y:y-0.02,mode<4?z+0.02:z);
                var hit=client.world.raycast(new net.minecraft.world.RaycastContext(camera,end,
                    net.minecraft.world.RaycastContext.ShapeType.VISUAL,
                    net.minecraft.world.RaycastContext.FluidHandling.NONE,client.player));
                boolean visible=hit.getType()==net.minecraft.util.hit.HitResult.Type.BLOCK
                    && (mode<4
                        ? hit.getBlockPos().getZ()==6
                            && client.world.getBlockState(hit.getBlockPos()).isOf(net.minecraft.block.Blocks.WHITE_CONCRETE)
                        : hit.getBlockPos().equals(net.minecraft.util.math.BlockPos.ofFloored(end)));
                rows.append(renderer).append(',').append(NAMES[mode]).append(',').append(p).append(',')
                    .append(sx).append(',').append(fb.textureHeight-1-sy).append(',')
                    .append(r/25).append(',').append(g/25).append(',').append(b/25).append(',')
                    .append(sky).append(',').append(client.world.getSkyAngle(1)).append(',').append(alpha).append(',').append(visible).append('\n');
            }
            Files.writeString(client.runDirectory.toPath().resolve("color-response.csv"),rows,StandardOpenOption.APPEND);
        } catch(Exception error){throw new IllegalStateException("Response probe failed",error);}
        finally{GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,oldRead);}
        ScreenshotRecorder.saveScreenshot(client.runDirectory,"response-"+renderer+"-"+NAMES[mode]+".png",fb,message->log(message.getString()));
        log("Response capture "+renderer+" "+NAMES[mode]+" camera="+camera);
    }
    private static void commands(MinecraftClient client,String... commands) {
        client.getServer().execute(()->{
            var server=client.getServer();if(server==null)return;
            for(String command:commands) server.getCommandManager().executeWithPrefix(server.getCommandSource(),command);
        });
    }
    private static void log(String text){LoggerFactory.getLogger("Prism Lights").info(text);}
}
