package dev.prismlights;

/** Physical hand offsets remain separated even when looking straight up/down. */
public final class LauncherShotLayout {
    public record Shot(double offsetX, double offsetZ, float yaw) {}
    private LauncherShotLayout() {}

    public static Shot forHand(float yaw, boolean mainArmRight, boolean offHand, boolean dual) {
        int side = mainArmRight != offHand ? 1 : -1;
        double radians = Math.toRadians(yaw);
        return new Shot(-Math.cos(radians) * side * 0.28,
            -Math.sin(radians) * side * 0.28, yaw + (dual ? side * 5.0f : 0));
    }
}
