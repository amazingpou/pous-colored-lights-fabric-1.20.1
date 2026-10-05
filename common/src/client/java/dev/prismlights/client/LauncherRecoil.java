package dev.prismlights.client;

import dev.prismlights.LauncherRecoilCurve;
import dev.prismlights.PrismLights;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import org.joml.Quaternionf;

/** Independent recoil for each equipped launcher; both hands may fire together. */
public final class LauncherRecoil {
    private static AbstractClientPlayerEntity owner;
    private static final int[] firedAges = {-1, -1};
    private static final long[] handFrames = new long[2];
    private static long renderedFrames;
    private static final Quaternionf ROTATION = new Quaternionf();
    private LauncherRecoil() {}

    public static void trigger(Hand hand) {
        var player = MinecraftClient.getInstance().player;
        if (player == null) return;
        if(owner!=player) java.util.Arrays.fill(firedAges,-1);
        owner = player;
        firedAges[hand.ordinal()] = player.age;
        if(player.getMainHandStack().isOf(PrismLights.FIREWORK_LAUNCHER)
                && player.getOffHandStack().isOf(PrismLights.FIREWORK_LAUNCHER)) {
            firedAges[0]=firedAges[1]=player.age;
        }
    }

    public static void tick(MinecraftClient client) {
        if(owner==null) return;
        if(owner!=client.player || client.world==null) {owner=null;java.util.Arrays.fill(firedAges,-1);return;}
        for(Hand hand:Hand.values()) {
            int index=hand.ordinal();
            if(firedAges[index]>=0 && (owner.age-firedAges[index]>=LauncherRecoilCurve.DURATION_TICKS
                    || !owner.getStackInHand(hand).isOf(PrismLights.FIREWORK_LAUNCHER))) firedAges[index]=-1;
        }
        if(firedAges[0]<0 && firedAges[1]<0) owner=null;
    }

    public static void apply(AbstractClientPlayerEntity player, Hand hand, ItemStack stack,
                             float tickDelta, MatrixStack matrices) {
        int firedAge=firedAges[hand.ordinal()];
        if (owner != player || firedAge<0 || !stack.isOf(PrismLights.FIREWORK_LAUNCHER)) return;
        float recoil = LauncherRecoilCurve.sample(player.age - firedAge + tickDelta);
        if (recoil == 0) return;
        Arm arm = hand == Hand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
        int side = arm == Arm.RIGHT ? 1 : -1;
        matrices.translate(side * 0.008 * recoil, 0.035 * recoil, 0.16 * recoil);
        float radians = (float)(Math.PI / 180) * recoil;
        // Barrel points along -Z: positive X rotation lifts its muzzle.
        matrices.multiply(ROTATION.rotationXYZ(9 * radians, -side * 0.8f * radians, side * 0.6f * radians));
        renderedFrames++;
        handFrames[hand.ordinal()]++;
    }

    static long renderedFrames() { return renderedFrames; }
    static long renderedFrames(Hand hand) { return handFrames[hand.ordinal()]; }
}
