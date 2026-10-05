package dev.prismlights.client;

import dev.prismlights.*;
import dev.prismlights.platform.PlatformServices;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;
import org.slf4j.LoggerFactory;
import java.nio.ByteBuffer;
import java.nio.file.*;
import java.util.HashSet;

/** Real framebuffer/weapon QA, opt-in and confined to the isolated world. */
public final class FireworkValidation {
    private static final boolean ACTIVE=(PlatformServices.get().isDevelopmentEnvironment() || Boolean.getBoolean("prism.qaRuntime")) && Boolean.getBoolean("prism.fireworkTest");
    private static final boolean IRIS=PlatformServices.get().isModLoaded("iris");
    private static final String[] PACKS=ColorResponseValidation.renderers();
    private static final String[] MODES={"baseline","flight-blue","flight-purple","burst-blue","over-cap-lights","rgb-off","launcher"};
    private static final int START=240,PERIOD=100;
    private static final ByteBuffer PIXELS=BufferUtils.createByteBuffer(5*5*4);
    private static int ticks,lastCapture=-1;
    private static long firstBuilds,firstUploads;
    private static int staticTextureBefore;
    public static void prepareCamera(MinecraftClient client) {
        if(!ACTIVE || client.player==null) return;
        client.player.setYaw(0);client.player.prevYaw=0;client.player.setPitch(35);client.player.prevPitch=35;
    }
    public static void tick(MinecraftClient client) {
        if(!ACTIVE || client.world==null || client.player==null || client.getServer()==null) return;
        if(Boolean.getBoolean("prism.qaRuntime") && !client.runDirectory.getName().startsWith("run-optimization-validation-"))throw new IllegalStateException("Isolated firework QA copy required");
        client.options.pauseOnLostFocus=false;client.options.hudHidden=true;
        if(client.isPaused()) return;
        int current=++ticks;
        if(current==40) {
            commands(client,"gamemode creative Player","gamerule doMobSpawning false","gamerule doDaylightCycle false",
                "weather clear","time set midnight","fill -16 270 -8 16 283 20 air",
                "fill -16 270 -8 0 270 20 grass_block","fill 1 270 -8 16 270 20 white_concrete",
                "fill -16 271 14 16 278 14 white_concrete","setblock 0 276 -7 barrier",
                "tp Player 0 277 -7 0 35");
            try {Files.writeString(client.runDirectory.toPath().resolve("firework-colors.csv"),
                "renderer,mode,material,r,g,b,mobileCount,maskBuilds,uploads,maxBuildMs,staticTexture,p50BuildMs,p95BuildMs\n");}
            catch(Exception error){throw new IllegalStateException(error);}
        }
        if(current<START) return;
        int phase=(current-START)/PERIOD, mode=phase%MODES.length, step=(current-START)%PERIOD;
        if(phase>=PACKS.length*MODES.length) {
            if(step<40) return;
            if(MobileLights.count()!=0) throw new AssertionError("Mobile light leaked after final shot");
            if(!FireworkLifecycleValidation.complete()) throw new AssertionError("Server lifecycle QA did not finish");
            log("Firework rendered probe COMPLETE");client.scheduleStop();return;
        }
        client.options.hudHidden=mode!=6;
        if(step==0) {
            if(mode==0 && IRIS) NightColorValidation.reload(PACKS[phase/MODES.length]);
            firstBuilds=MobileLights.maskBuilds();firstUploads=MobileLights.uploads();
        }
        GpuLightVolume.setEnabled(mode!=5);
        if(step==65 && lastCapture!=phase) throw new AssertionError("Missed rendered capture: "+PACKS[phase/MODES.length]+" "+MODES[mode]);
        if(mode==0 && step==20) GpuUploadValidation.run();
        if(step==45 && mode!=0) {
            staticTextureBefore=GpuLightVolume.currentTexture();
            client.getServer().execute(()->{
            var world=client.getServer().getOverworld();
            if(mode==6) {
                var player=client.getServer().getPlayerManager().getPlayer(client.player.getUuid());
                player.setStackInHand(Hand.MAIN_HAND,new ItemStack(PrismLights.FIREWORK_LAUNCHER));
                var before=world.getEntitiesByType(PrismLights.FIREWORK_ENTITY,e->true).size();
                var result=PrismLights.FIREWORK_LAUNCHER.use(world,player,Hand.MAIN_HAND);
                var repeated=PrismLights.FIREWORK_LAUNCHER.use(world,player,Hand.MAIN_HAND);
                if(!result.getResult().isAccepted() || repeated.getResult().isAccepted()
                    || world.getEntitiesByType(PrismLights.FIREWORK_ENTITY,e->true).size()!=before+1)
                    throw new AssertionError("Launcher/cooldown duplicated or lost a shot");
                log("Launcher right-click and cooldown PASS");return;
            }
            int quantity=mode==4?MobileLights.LIMIT+1:1;
            var usedColors=new HashSet<Integer>();
            for(int i=0;i<quantity;i++) {
                var shot=new PrismFireworkEntity(PrismLights.FIREWORK_ENTITY,world);
                int color=mode==2?0x8024FF:mode==4?net.minecraft.util.math.MathHelper.hsvToRgb(i/(float)quantity,0.95f,1f):0x054DFF;
                usedColors.add(color);shot.setColor(color);
                if(mode==3) {shot.setPosition(0,273.5,4.5);shot.setVelocity(0,-1,0);}
                else {shot.setPosition((i%4)*0.35,272.5,2.5+(i/4)*0.35);shot.setVelocity(0,0,0.15);}
                if(!world.spawnEntity(shot)) throw new AssertionError("QA shot could not spawn");
            }
            if(mode==4 && usedColors.size()!=quantity) throw new AssertionError("Color variety lost");
            });
        }
        if(mode==6 && step==70) client.getServer().execute(()->{
            var world=client.getServer().getOverworld();
            var player=client.getServer().getPlayerManager().getPlayer(client.player.getUuid());
            var previous=new HashSet<Integer>();
            for(var entity:world.getEntitiesByType(PrismLights.FIREWORK_ENTITY,e->true)) previous.add(entity.getId());
            for(int i=0;i<8;i++) {
                player.getItemCooldownManager().remove(PrismLights.FIREWORK_LAUNCHER);
                if(!PrismLights.FIREWORK_LAUNCHER.use(world,player,Hand.MAIN_HAND).getResult().isAccepted())
                    throw new AssertionError("Random shot rejected");
            }
            var colors=new HashSet<Integer>();
            for(var entity:world.getEntitiesByType(PrismLights.FIREWORK_ENTITY,e->!previous.contains(e.getId()))) {
                colors.add(entity.color());
                var component=entity.getStack().getSubNbt("Explosion");
                if(component==null || component.getIntArray("Colors").length!=1 || component.getIntArray("Colors")[0]!=entity.color()) throw new AssertionError("Firework VFX color mismatch");
            }
            if(colors.size()<2) throw new AssertionError("Launcher does not vary colors");
            log("Launcher random-color/VFX identity PASS: "+colors.size()+" colors in 8 shots");
        });
        if(step==35 && phase>0 && MobileLights.count()!=0) throw new AssertionError("Expired mobile source remained: "+MODES[mode]);
    }
    public static void frame(MinecraftClient client,Matrix4f view,Matrix4f projection) {
        if(!ACTIVE || client.player==null || ticks<START) return;
        int phase=(ticks-START)/PERIOD,mode=phase%MODES.length,step=(ticks-START)%PERIOD;
        int desired=mode==3?49:mode==4?57:55;
        if(phase==lastCapture || step<desired || step>desired+5 || phase>=PACKS.length*MODES.length) return;
        // Wait for authoritative burst metadata and its rebuilt mask. A fast
        // client can otherwise photograph its predicted flight inside the
        // floor one frame before the server's collision phase arrives.
        if(mode==3 && !MobileLights.readyBurst(0x054DFF)) return;
        lastCapture=phase;String pack=PACKS[phase/MODES.length],renderer=pack.replace(".zip","");
        if(IRIS) NightColorValidation.verifyPack(pack);
        if(mode!=0 && mode!=5 && MobileLights.count()==0) throw new AssertionError("No mobile light for "+MODES[mode]);
        if(mode==4 && MobileLights.count()!=MobileLights.LIMIT) throw new AssertionError("Expected "+MobileLights.LIMIT+" mobile lights, got "+MobileLights.count());
        if(mode!=0 && GpuLightVolume.currentTexture()!=staticTextureBefore)
            throw new AssertionError("Mobile movement recreated static lamp texture");
        var fb=client.getFramebuffer();var camera=client.gameRenderer.getCamera().getPos();
        int oldRead=GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,fb.fbo);
        try(LightingValidation.PixelPackState ignored=new LightingValidation.PixelPackState()) {
            StringBuilder rows=new StringBuilder();
            for(int p=0;p<2;p++) {
                double x=p==0?-2.5:3.5,y=271.01,z=4.5;
                var clip=new Vector4f((float)(x-camera.x),(float)(y-camera.y),(float)(z-camera.z),1);
                view.transform(clip);projection.transform(clip);
                int sx=(int)((clip.x/clip.w*0.5+0.5)*fb.textureWidth),sy=(int)((clip.y/clip.w*0.5+0.5)*fb.textureHeight);
                if(clip.w<=0 || sx<2 || sy<2 || sx>=fb.textureWidth-2 || sy>=fb.textureHeight-2)
                    throw new AssertionError("Firework pixel probe outside view");
                var end=new Vec3d(x,270.99,z);
                var hit=client.world.raycast(new net.minecraft.world.RaycastContext(camera,end,
                    net.minecraft.world.RaycastContext.ShapeType.VISUAL,net.minecraft.world.RaycastContext.FluidHandling.NONE,client.player));
                if(!hit.getBlockPos().equals(net.minecraft.util.math.BlockPos.ofFloored(end)))
                    throw new AssertionError("Floor probe is occluded");
                PIXELS.clear();GL11.glReadPixels(sx-2,sy-2,5,5,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,PIXELS);
                double r=0,g=0,b=0;for(int i=0;i<25;i++){r+=PIXELS.get(i*4)&255;g+=PIXELS.get(i*4+1)&255;b+=PIXELS.get(i*4+2)&255;}
                rows.append(renderer).append(',').append(MODES[mode]).append(',').append(p==0?"grass":"concrete").append(',')
                    .append(r/25).append(',').append(g/25).append(',').append(b/25).append(',').append(MobileLights.count()).append(',')
                    .append(MobileLights.maskBuilds()-firstBuilds).append(',').append(MobileLights.uploads()-firstUploads).append(',')
                    .append(MobileLights.maxBuildNanos()/1e6).append(',').append(GpuLightVolume.currentTexture()).append(',')
                    .append(MobileLights.buildPercentile(0.5)).append(',').append(MobileLights.buildPercentile(0.95)).append('\n');
            }
            Files.writeString(client.runDirectory.toPath().resolve("firework-colors.csv"),rows,StandardOpenOption.APPEND);
        } catch(Exception error){throw new IllegalStateException("Firework rendered probe failed",error);}
        finally{GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,oldRead);}
        ScreenshotRecorder.saveScreenshot(client.runDirectory,"firework-"+renderer+"-"+MODES[mode]+".png",fb,message->log(message.getString()));
        if(mode==4) {
            MobileLights.verifyAtlas();log("Mobile atlas exact GPU readback PASS ("+MobileLights.LIMIT+" masks)");
            GpuUploadValidation.run();log("Mobile/static material bindings with "+MobileLights.LIMIT+" sources PASS");
        }
        log("Firework capture "+renderer+" "+MODES[mode]+" sources="+MobileLights.count());
    }
    private static void commands(MinecraftClient client,String... commands) {
        client.getServer().execute(()->{var server=client.getServer();for(String command:commands)
            server.getCommandManager().executeWithPrefix(server.getCommandSource(),command);});
    }
    private static int lastHandCapture=-1;
    public static void afterFrame(MinecraftClient client) {
        if(!ACTIVE || ticks<START) return;
        int phase=(ticks-START)/PERIOD,mode=phase%MODES.length,step=(ticks-START)%PERIOD;
        if(mode!=6 || step!=55 || phase>=PACKS.length*MODES.length || lastHandCapture==ticks) return;
        lastHandCapture=ticks;
        ScreenshotRecorder.saveScreenshot(client.runDirectory,"launcher-preview-"+PACKS[phase/MODES.length].replace(".zip","")+".png",
            client.getFramebuffer(),message->log(message.getString()));
    }
    private static void log(String text){LoggerFactory.getLogger("Prism Lights").info(text);}
}
