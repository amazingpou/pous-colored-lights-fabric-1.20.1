package dev.prismlights.client.mixin;
import dev.prismlights.client.SkyExposure;
import net.minecraft.client.world.ClientChunkManager;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.world.LightType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** Coalesces actual skylight changes; no per-frame world scans. */
@Mixin(ClientChunkManager.class)
abstract class ClientSkyUpdateMixin {
    @Inject(method="onLightUpdate",at=@At("TAIL"))
    private void prism$skyChanged(LightType type,ChunkSectionPos section,CallbackInfo ci) {SkyExposure.changed(type,section);dev.prismlights.client.LightTransitionGate.lightChanged(type,section);}
}
