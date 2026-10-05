package dev.prismlights.client;

import dev.prismlights.platform.PlatformServices;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;

/** Builds the opt-in filming world once, using its own external datapack. */
public final class ShowcaseSetup {
    private static final boolean ACTIVE = PlatformServices.get().isDevelopmentEnvironment()
            && Boolean.getBoolean("prism.showcaseBuild");
    private static int ticks;
    private static boolean built;
    private static volatile boolean ready;

    static boolean ready() { return ready; }

    private ShowcaseSetup() {}

    public static void tick(MinecraftClient client) {
        if (!ACTIVE || client.world == null || client.player == null || client.getServer() == null) return;
        if (built) {
            if (!ready && ticks > 1200) {
                org.slf4j.LoggerFactory.getLogger("Prism Lights").error("Showcase construction timed out; inspect datapack command errors");
                client.scheduleStop();
                return;
            }
            if (!ready && ticks++ % 40 == 0) client.getServer().execute(() -> {
                ready = client.getServer().getDataCommandStorage().get(new Identifier("showcase", "state")).getBoolean("built");
            });
            ShowcaseValidation.tick(client);
            return;
        }
        if (++ticks < 60) return;
        built = true;
        String name = client.player.getName().getString();
        client.getServer().execute(() -> {
            var server = client.getServer();
            if (server == null) return;
            if (!server.getSaveProperties().getLevelName().equals("Pou's Colored Lights - Showcase"))
                throw new IllegalStateException("Showcase builder must only run in its separate world");
            var commands = server.getCommandManager();
            var source = server.getCommandSource();
            commands.executeWithPrefix(source, "gamemode creative " + name);
            commands.executeWithPrefix(source, "gamerule doMobSpawning false");
            commands.executeWithPrefix(source, "gamerule doDaylightCycle false");
            commands.executeWithPrefix(source, "weather clear");
            commands.executeWithPrefix(source, "time set midnight");
            commands.executeWithPrefix(source, "function showcase:build");
        });
    }
}
