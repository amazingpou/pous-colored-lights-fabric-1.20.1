package dev.prismlights.client.mixin;

import dev.prismlights.client.GpuLightVolume;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets="me.jellysquid.mods.sodium.client.gl.shader.GlProgram",remap=false)
public abstract class SodiumProgramMixin {
    @Unique private boolean prism$firstBind=true;
    @Inject(method="bind",at=@At("HEAD"),remap=false,require=0)
    private void prism$firstProgramBind(CallbackInfo ci) {
        // Iris 1.7 redirects Sodium's glUseProgram through GlStateManager.
        // Its global binding hook therefore runs before our bind tail. Clear
        // reused locations first, using this object's handle (not the previous
        // GL_CURRENT_PROGRAM), so the hook cannot upload into an old layout.
        if(prism$firstBind) {
            GpuLightVolume.forgetProgram(((SodiumGlObjectAccess)(Object)this).prism$handle());
            prism$firstBind=false;
        }
    }
    @Inject(method="bind",at=@At("TAIL"),remap=false,require=0)
    private void prism$terrainUniforms(CallbackInfo ci) {
        GpuLightVolume.bindCurrentProgram();
    }
}
