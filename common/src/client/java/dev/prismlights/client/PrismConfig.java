package dev.prismlights.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.prismlights.platform.PlatformServices;
import org.slf4j.LoggerFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import com.google.gson.JsonParser;
import dev.prismlights.LightSourceLimit;

public final class PrismConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = PlatformServices.get().configDirectory().resolve("prism-lights.json");
    public boolean enabled = true;
    public boolean artisticMixing = true;
    public int radius = 15;
    public int maxLights = LightSourceLimit.MAX;
    public float strength = 0.85f;
    public int configVersion = LightSourceLimit.CONFIG_VERSION;
    public static PrismConfig load() {
        PrismConfig config = new PrismConfig();
        if (Files.exists(PATH)) {
            try {
                String json=Files.readString(PATH);
                var root=JsonParser.parseString(json).getAsJsonObject();
                PrismConfig loaded = GSON.fromJson(root, PrismConfig.class);
                if (loaded != null) {
                    int oldVersion=root.has("configVersion")?root.get("configVersion").getAsInt():1;
                    if(oldVersion<2 && loaded.radius==9) loaded.radius=15;
                    loaded.maxLights=LightSourceLimit.migrate(loaded.maxLights,oldVersion);
                    loaded.configVersion=LightSourceLimit.CONFIG_VERSION;
                    config = loaded;
                }
            } catch (Exception error) { LoggerFactory.getLogger("Prism Lights").warn("Cannot read configuration; using defaults", error); }
        }
        config.radius = Math.max(3, Math.min(16, config.radius));
        config.maxLights = Math.max(1, Math.min(LightSourceLimit.MAX, config.maxLights));
        config.strength = Float.isFinite(config.strength) ? Math.max(0.1f, Math.min(1.5f, config.strength)) : 0.85f;
        return config;
    }
    public void save() {
        try { Files.createDirectories(PATH.getParent()); Files.writeString(PATH, GSON.toJson(this)); }
        catch (Exception error) { LoggerFactory.getLogger("Prism Lights").warn("Cannot save configuration", error); }
    }
}
