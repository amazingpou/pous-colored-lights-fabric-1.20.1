package dev.prismlights.client.mixin;

import dev.prismlights.client.LauncherRecoil;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HeldItemRenderer.class)
public abstract class LauncherHeldItemMixin {
    // Transform inside Vanilla's hand push/pop, after its equip/bob transforms.
    @Inject(method = "renderFirstPersonItem", at = @At(value = "INVOKE", target =
        "Lnet/minecraft/client/render/item/HeldItemRenderer;renderItem(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/render/model/json/ModelTransformationMode;ZLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V"))
    private void prism$recoil(AbstractClientPlayerEntity player, float tickDelta, float pitch,
                              Hand hand, float swing, ItemStack stack, float equip,
                              MatrixStack matrices, VertexConsumerProvider vertices, int light, CallbackInfo ci) {
        LauncherRecoil.apply(player, hand, stack, tickDelta, matrices);
    }
}
