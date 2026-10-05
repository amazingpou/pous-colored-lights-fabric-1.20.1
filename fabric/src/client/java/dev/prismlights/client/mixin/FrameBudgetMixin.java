package dev.prismlights.client.mixin;

import dev.prismlights.client.PrismClient;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Additional world views share this frame's remaining lighting allowance. */
@Mixin(MinecraftClient.class)
abstract class FrameBudgetMixin {
    @Inject(method="render",at=@At("HEAD"))
    private void prism$beginFrame(boolean tick,CallbackInfo ci){PrismClient.beginFrame();}
}
