package dev.prismlights.client.mixin;

import dev.prismlights.PrismLights;
import dev.prismlights.client.LauncherRecoil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientPlayerInteractionManager.class)
public abstract class LauncherUseMixin {
    @Inject(method = "interactItem", at = @At("RETURN"))
    private void prism$acceptedTrigger(PlayerEntity player, Hand hand, CallbackInfoReturnable<ActionResult> result) {
        if (player == MinecraftClient.getInstance().player
                && player.getStackInHand(hand).isOf(PrismLights.FIREWORK_LAUNCHER)
                && result.getReturnValue().isAccepted()) LauncherRecoil.trigger(hand);
    }
}
