package dev.prismlights.client.mixin;

import dev.prismlights.client.LightTransitionGate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets="me.jellysquid.mods.sodium.client.render.chunk.RenderSectionManager",remap=false)
abstract class SodiumTransitionBuildMixin {
    @Inject(method="updateChunks",at=@At("HEAD"),remap=false,require=0)
    private void prism$finishAffectedTerrain(boolean immediate,CallbackInfo ci) {
        LightTransitionGate.scheduleTerrain(this);
    }
}
