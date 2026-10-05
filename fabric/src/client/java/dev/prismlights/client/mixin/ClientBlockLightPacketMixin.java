package dev.prismlights.client.mixin;

import dev.prismlights.client.LightTransitionGate;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.world.LightType;
import net.minecraft.world.chunk.ChunkNibbleArray;
import net.minecraft.world.chunk.light.LightingProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ClientPlayNetworkHandler.class)
abstract class ClientBlockLightPacketMixin {
    @Redirect(method="updateLighting",at=@At(value="INVOKE",target="Lnet/minecraft/world/chunk/light/LightingProvider;enqueueSectionData(Lnet/minecraft/world/LightType;Lnet/minecraft/util/math/ChunkSectionPos;Lnet/minecraft/world/chunk/ChunkNibbleArray;)V"))
    private void prism$deferBlocklight(LightingProvider provider,LightType type,ChunkSectionPos section,ChunkNibbleArray data) {
        if(!LightTransitionGate.defer(provider,type,section,data))provider.enqueueSectionData(type,section,data);
    }
}
