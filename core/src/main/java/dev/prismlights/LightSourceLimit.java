package dev.prismlights;

/** Static emitter defaults and backward-compatible configuration migration. */
public final class LightSourceLimit {
    public static final int MAX = 72;
    public static final int MOBILE_MAX = 8;
    public static final int CONFIG_VERSION = 5;
    private LightSourceLimit() {}

    public static int migrate(int configured, int version) {
        if (version < 3 && configured == 24) configured = 64;
        if (version < 4 && configured == 64) configured = MAX;
        return Math.max(1, Math.min(MAX, configured));
    }
}
