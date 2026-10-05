package dev.prismlights.client.mixin;

import dev.prismlights.ShaderPatcher;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;

@Pseudo
@Mixin(targets="me.jellysquid.mods.sodium.client.gl.shader.ShaderWorkarounds",remap=false)
public abstract class SodiumSourceMixin {
    @ModifyVariable(method="safeShaderSource",at=@At("HEAD"),argsOnly=true,ordinal=0,remap=false,require=0)
    private static CharSequence prism$terrainLighting(CharSequence source) { return ShaderPatcher.patch(source.toString()); }
}
