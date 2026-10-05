package dev.prismlights.client;

import dev.prismlights.LightVolume;
import dev.prismlights.ColoredLightSource;
import dev.prismlights.platform.PlatformServices;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.util.math.BlockPos;
import org.slf4j.LoggerFactory;

/** Opt-in opaque-roof probe, run only in the copied validation world. */
public final class OcclusionValidation {
    private static final boolean ACTIVE = PlatformServices.get().isDevelopmentEnvironment()
        && Boolean.getBoolean("prism.occlusionTest");
    private static int ticks, lastCapture = -1;

    private OcclusionValidation() {}

    public static void prepareCamera(MinecraftClient client) {
        if (!ACTIVE || client.player == null) return;
        client.player.setYaw(0); client.player.prevYaw = 0;
        client.player.setPitch(90); client.player.prevPitch = 90;
    }

    public static void tick(MinecraftClient client) {
        if (!ACTIVE || client.world == null || client.player == null || client.getServer() == null) return;
        client.options.pauseOnLostFocus=false;
        if(client.isPaused()) return;
        int current = ++ticks;
        if (current == 60) {
            client.options.setPerspective(Perspective.FIRST_PERSON);
            commands(client,
                "gamemode spectator Player659",
                "gamerule doDaylightCycle false",
                "weather clear",
                "time set midnight",
                "tp Player659 0 131 4 0 90",
                "fill -12 120 -12 12 132 12 air",
                "fill -4 121 0 4 123 8 white_concrete",
                "fill -3 122 1 3 122 7 air",
                "setblock 0 122 4 prism_lights:red_prism");
        }
        if (current == 120) {
            LightVolume volume = GpuLightVolume.current();
            if (volume == null || !client.world.getBlockState(new BlockPos(0, 123, 4)).isOf(Blocks.WHITE_CONCRETE)
                || !(client.world.getBlockState(new BlockPos(0, 122, 4)).getBlock() instanceof ColoredLightSource))
                throw new IllegalStateException("Occlusion probe scene did not load");
            float top = volume.sample(0.5, 124.0, 4.5)[3];
            float source = volume.sample(0.5, 122.5, 4.5)[3];
            LoggerFactory.getLogger("Prism Lights").info("Occlusion probe top alpha={} source alpha={}", top, source);
            if (source < 0.1f || top > 0.02f)
                throw new IllegalStateException("Opaque roof leaked RGB or source did not illuminate");
        }
        if (current == 160) GpuLightVolume.setEnabled(false);
        if (current == 230) client.scheduleStop();
    }

    public static void frame(MinecraftClient client) {
        if (!ACTIVE || client.player == null || ticks == lastCapture) return;
        String name = switch (ticks) {
            case 140 -> "occlusion-colored.png";
            case 190 -> "occlusion-disabled.png";
            default -> null;
        };
        if (name == null) return;
        lastCapture = ticks;
        ScreenshotRecorder.saveScreenshot(client.runDirectory, name, client.getFramebuffer(),
            message -> LoggerFactory.getLogger("Prism Lights").info(message.getString()));
    }

    private static void commands(MinecraftClient client, String... commands) {
        client.getServer().execute(() -> {
            var server = client.getServer();
            if (server == null) return;
            for (String command : commands)
                server.getCommandManager().executeWithPrefix(server.getCommandSource(), command);
        });
    }
}
