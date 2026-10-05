package dev.prismlights.client.mixin;

import dev.prismlights.client.GpuLightVolume;
import net.minecraft.client.gl.ShaderProgram;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ShaderProgram.class)
public abstract class ShaderProgramMixin {
    @Inject(method="bind",at=@At("HEAD"))
    private void prism$beginMaterial(CallbackInfo ci) {GpuLightVolume.beginVanillaBind();}
    @Inject(method="bind",at=@At("TAIL"))
    private void prism$materialLight(CallbackInfo ci) { GpuLightVolume.endVanillaBind(); }
}
