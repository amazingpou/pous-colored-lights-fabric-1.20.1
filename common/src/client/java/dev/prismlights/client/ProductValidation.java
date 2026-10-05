package dev.prismlights.client;

import dev.prismlights.platform.PlatformServices;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.LightType;
import org.slf4j.LoggerFactory;

/** Opt-in visual probe. Only operates in the existing isolated validation world. */
public final class ProductValidation {
    private static final boolean ACTIVE = PlatformServices.get().isDevelopmentEnvironment()
        && Boolean.getBoolean("prism.productTest");
    private static int ticks, lastCapture = -1;
    private ProductValidation() {}

    public static void prepareCamera(MinecraftClient client) {
        if (!ACTIVE || client.player == null) return;
        client.player.setYaw(0); client.player.prevYaw = 0;
        client.player.setPitch(18); client.player.prevPitch = 18;
    }

    public static void tick(MinecraftClient client) {
        if (!ACTIVE || client.world == null || client.player == null || client.getServer() == null) return;
        client.options.pauseOnLostFocus=false;
        if(client.isPaused()) return;
        int current = ++ticks;
        if (current == 60) commands(client,
            "gamemode creative Player659",
            "gamerule doDaylightCycle false",
            "weather clear",
            "time set noon",
            "fill -10 74 -10 10 86 14 air",
            "fill -10 73 -10 10 73 14 grass_block",
            "fill -10 73 6 10 73 14 white_concrete",
            "setblock -5 74 3 prism_lights:blue_prism",
            "setblock -1 74 3 prism_lights:yellow_prism",
            "setblock 2 74 3 prism_lights:red_prism",
            "setblock 6 74 3 prism_lights:blue_prism",
            "setblock -7 74 5 prism_lights:green_torch",
            "setblock 8 74 5 prism_lights:magenta_lantern",
            "setblock 0 74 10 prism_lights:purple_redstone_prism",
            "tp Player659 0 76 -7 0 18");
        if (current == 150) GpuLightVolume.setEnabled(false);
        if (current == 190) {
            commands(client,"setblock 0 74 11 redstone_block");
            GpuLightVolume.setEnabled(true);
        }
        if (current == 240) commands(client,"setblock 0 74 11 air","time set midnight");
        if (current >= 150 && current < 190) GpuLightVolume.setEnabled(false);
        if (current == 121 || current == 181 || current == 221 || current == 271)
            LoggerFactory.getLogger("Prism Lights").info("Product probe tick {} redstone state {} skyAngle {} skyLevel {}",
                current, client.world.getBlockState(new BlockPos(0,74,10)), client.world.getSkyAngle(1.0f),
                client.world.getLightLevel(LightType.SKY,new BlockPos(0,74,0)));
        if (current == 310) commands(client,
            "fill -10 74 -10 10 75 14 air",
            "setblock -2 74 3 prism_lights:blue_prism",
            "setblock 2 74 3 prism_lights:yellow_prism");
        if (current == 380) commands(client,"setblock 2 74 3 prism_lights:red_prism");
        if (current == 440) commands(client,
            "fill -10 74 -10 10 75 14 air",
            "setblock -2 74 3 prism_lights:green_torch",
            "setblock 2 74 3 prism_lights:magenta_lantern",
            "setblock 0 74 8 stone",
            "setblock 0 74 7 prism_lights:cyan_wall_torch[facing=north]",
            "tp Player659 0 75 -2 0 12");
        if (current == 560) client.scheduleStop();
    }

    public static void frame(MinecraftClient client) {
        if (!ACTIVE || ticks == lastCapture || client.player == null) return;
        String name = switch (ticks) {
            case 120 -> "product-day-colored.png";
            case 145 -> "product-day-colored-settled.png";
            case 180 -> "product-day-vanilla-light.png";
            case 220 -> "product-redstone-on.png";
            case 270 -> "product-night-redstone-off.png";
            case 350 -> "product-blue-yellow.png";
            case 420 -> "product-red-blue.png";
            case 520 -> "product-torches-lantern.png";
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
