package dev.prismlights.client;

import dev.prismlights.LightVolume;
import dev.prismlights.platform.PlatformServices;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.util.math.BlockPos;
import org.slf4j.LoggerFactory;

/** Opt-in regression, only in the isolated occlusion development world. */
public final class CompatibilityValidation {
    private static final boolean ACTIVE=PlatformServices.get().isDevelopmentEnvironment()
        && Boolean.getBoolean("prism.compatibilityTest");
    private static int ticks;
    private static long builds, changes, bindings;
    private static String expectedPack;
    private static boolean expectedEnabled;
    private static boolean iris=PlatformServices.get().isModLoaded("iris");

    private CompatibilityValidation() {}

    public static void tick(MinecraftClient client) {
        if(!ACTIVE || client.world==null || client.player==null || client.getServer()==null) return;
        // Client END_TICK still fires when the integrated world is paused.
        // A background QA window must not advance ahead of its block packets.
        client.options.pauseOnLostFocus=false;
        if(client.isPaused()) return;
        int current=++ticks;
        if(current==60) commands(client,
            "gamemode spectator Player659", "gamerule doDaylightCycle false", "time set midnight", "weather clear",
            "tp Player659 0 128 26 180 30",
            "fill -12 120 -12 30 133 30 air",
            "fill -6 121 14 6 123 18 white_concrete",
            "fill -5 122 16 5 122 16 air",
            "setblock -4 122 16 prism_lights:red_prism",
            "setblock 0 122 16 prism_lights:black_prism[active=false]",
            "setblock 20 122 16 prism_lights:blue_prism");
        if(current==120) {
            assertCoverage(client,false);
            builds=PrismClient.lightField().propagationBuilds();
            commands(client,"setblock 0 122 16 air");
        }
        if(current==150) {
            assertCoverage(client,true);
            assertLocalRebuild();
            builds=PrismClient.lightField().propagationBuilds();
            commands(client,"setblock 0 122 16 prism_lights:black_prism[active=false]");
        }
        if(current==180) {
            assertCoverage(client,false);
            assertLocalRebuild();
            GpuUploadValidation.run();
            log("Opaque source cache regression PASS: closed/open/closed; only nearby source rebuilt");
            if(iris) {
                changes=PrismClient.pipelineChanges;
                reload("ComplementaryReimagined_r5.9.3.zip",true);
            }
        }
        if(current==210 && iris) {
            assertReload(); capture(client,"compat-initial.png");
            changes=PrismClient.pipelineChanges;
            reload(null,true);
        }
        if(current==240 && iris) {
            assertReload();
            changes=PrismClient.pipelineChanges;
            reload("BSL_v10.1.8.zip",true);
        }
        if(current==270 && iris) {
            assertReload(); capture(client,"compat-bsl.png");
            changes=PrismClient.pipelineChanges;
            reload("ComplementaryUnbound_r5.9.3.zip",true);
        }
        if(current==300 && iris) {
            assertReload(); capture(client,"compat-unbound.png");
            changes=PrismClient.pipelineChanges;
            reload("ComplementaryReimagined_r5.9.3.zip",true);
        }
        if(current==330 && iris) {
            assertReload(); capture(client,"compat-complementary.png");
            changes=PrismClient.pipelineChanges;
            reload(null,false);
        }
        if(current==360 && iris) {
            assertReload(); capture(client,"compat-sodium.png");
            log("Iris lifecycle regression PASS: actual pack/API state checked; repeated reload, BSL/Reimagined/Unbound switch, shaders off");
        }
        if(current==390) { log("Compatibility regression COMPLETE"); client.scheduleStop(); }
    }

    private static void assertCoverage(MinecraftClient client,boolean open) {
        boolean gateOpen=client.world.getBlockState(new BlockPos(0,122,16)).isAir();
        LightVolume volume=GpuLightVolume.current();
        float coverage=volume==null?0:volume.sample(3.5,122.5,16.5)[3];
        if(gateOpen!=open || (open?coverage<0.05f:coverage>0.001f))
            throw new AssertionError("Opaque-source cache: open="+open+", gate="+gateOpen+", alpha="+coverage);
        log("Gate open="+open+" target coverage="+coverage);
    }
    private static void assertLocalRebuild() {
        long count=PrismClient.lightField().propagationBuilds()-builds;
        if(count!=1) throw new AssertionError("Expected one nearby propagation rebuild; got "+count);
    }
    private static void assertReload() {
        if(PrismClient.pipelineChanges<=changes || GpuLightVolume.current()==null)
            throw new AssertionError("Pipeline reload not detected or RGB volume lost");
        try {
            Class<?> irisClass=Class.forName("net.irisshaders.iris.Iris");
            Object error=irisClass.getMethod("getStoredError").invoke(null);
            if(((java.util.Optional<?>)error).isPresent()) throw new AssertionError("Iris shader compile error: "+error);
            Class<?> api=Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            boolean enabled=(Boolean)api.getMethod("isShaderPackInUse").invoke(api.getMethod("getInstance").invoke(null));
            String actual=(String)irisClass.getMethod("getCurrentPackName").invoke(null);
            if(enabled!=expectedEnabled || (enabled && !actual.equals(expectedPack)))
                throw new AssertionError("Wrong shader state: "+actual+", enabled="+enabled);
            if(GpuLightVolume.activeBindings()<=bindings)
                throw new AssertionError("No RGB material program bound after pipeline transition");
            log("Verified renderer: "+actual+", enabled="+enabled+", RGB material binds="+(GpuLightVolume.activeBindings()-bindings));
        } catch(ReflectiveOperationException error) { throw new IllegalStateException(error); }
        log("Pipeline transition detected; RGB volume present");
    }
    private static void reload(String pack,boolean enabled) {
        try {
            Class<?> irisClass=Class.forName("net.irisshaders.iris.Iris");
            Object config=irisClass.getMethod("getIrisConfig").invoke(null);
            if(pack!=null) config.getClass().getMethod("setShaderPackName",String.class).invoke(config,pack);
            config.getClass().getMethod("setShadersEnabled",boolean.class).invoke(config,enabled);
            // Iris.reload() re-reads configuration from disk.
            config.getClass().getMethod("save").invoke(config);
            expectedPack=pack==null?expectedPack:pack;
            expectedEnabled=enabled;
            bindings=GpuLightVolume.activeBindings();
            irisClass.getMethod("reload").invoke(null);
            log("Reload requested: pack="+pack+", enabled="+enabled);
        } catch(ReflectiveOperationException error) { throw new IllegalStateException("Cannot execute Iris development probe",error); }
    }
    private static void capture(MinecraftClient client,String name) {
        ScreenshotRecorder.saveScreenshot(client.runDirectory,name,client.getFramebuffer(),
            message -> log(message.getString()));
    }
    private static void commands(MinecraftClient client,String... commands) {
        client.getServer().execute(() -> {
            var server=client.getServer();
            if(server==null) return;
            for(String command:commands) server.getCommandManager().executeWithPrefix(server.getCommandSource(),command);
        });
    }
    private static void log(String text) { LoggerFactory.getLogger("Prism Lights").info(text); }
}
