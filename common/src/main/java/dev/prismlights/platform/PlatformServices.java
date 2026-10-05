package dev.prismlights.platform;

import java.nio.file.Path;
import java.util.Objects;

/** Loader environment, installed once before content or client initialization. */
public final class PlatformServices {
    public interface Environment {
        Path configDirectory();
        boolean isModLoaded(String id);
        boolean isDevelopmentEnvironment();
    }

    private static Environment environment;
    private PlatformServices() {}

    public static void install(Environment implementation) {
        if (environment != null) throw new IllegalStateException("Platform already initialized");
        environment = Objects.requireNonNull(implementation);
    }

    public static Environment get() {
        if (environment == null) throw new IllegalStateException("Loader must initialize the platform first");
        return environment;
    }
}
