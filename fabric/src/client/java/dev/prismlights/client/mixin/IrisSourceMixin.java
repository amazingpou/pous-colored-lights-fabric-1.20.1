package dev.prismlights.client.mixin;

import dev.prismlights.ShaderPatcher;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;

@Pseudo
@Mixin(targets="net.irisshaders.iris.gl.shader.ShaderWorkarounds",remap=false)
public abstract class IrisSourceMixin {
    @ModifyVariable(method="safeShaderSource",at=@At("HEAD"),argsOnly=true,ordinal=0,remap=false,require=0)
    private static CharSequence prism$materialLighting(CharSequence source) { return ShaderPatcher.patch(source.toString()); }
}
