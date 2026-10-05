package dev.prismlights;

/** Quick kick followed by a smooth, bounded return over 0.3 seconds. */
public final class LauncherRecoilCurve {
    public static final float DURATION_TICKS = 6;
    private LauncherRecoilCurve() {}

    public static float sample(float elapsedTicks) {
        if (!Float.isFinite(elapsedTicks) || elapsedTicks <= 0 || elapsedTicks >= DURATION_TICKS) return 0;
        if (elapsedTicks < 1) return smoothstep(elapsedTicks);
        return 1 - smoothstep((elapsedTicks - 1) / (DURATION_TICKS - 1));
    }

    private static float smoothstep(float value) { return value * value * (3 - 2 * value); }
}
