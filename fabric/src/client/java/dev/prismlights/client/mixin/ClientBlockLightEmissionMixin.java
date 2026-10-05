package dev.prismlights.client.mixin;

import dev.prismlights.client.LightTransitionGate;
import net.minecraft.block.BlockState;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.world.chunk.light.ChunkBlockLightProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChunkBlockLightProvider.class)
abstract class ClientBlockLightEmissionMixin {
    @Inject(method="getLightSourceLuminance",at=@At("RETURN"),cancellable=true)
    private void prism$holdEmission(long pos,BlockState state,CallbackInfoReturnable<Integer> ci) {
        if(((LightProviderAccess)this).prism$chunkProvider().getWorld() instanceof ClientWorld world)
            ci.setReturnValue(LightTransitionGate.luminance(world,pos,ci.getReturnValueI()));
    }
}
