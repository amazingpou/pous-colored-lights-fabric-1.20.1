package dev.prismlights.client.mixin;

import com.mojang.blaze3d.platform.GlStateManager;
import dev.prismlights.ShaderPatcher;
import dev.prismlights.client.GpuLightVolume;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.List;

@Mixin(GlStateManager.class)
public abstract class GlStateManagerMixin {
    @ModifyVariable(method="glShaderSource",at=@At("HEAD"),argsOnly=true,ordinal=0,remap=false)
    private static List<String> prism$lightingSource(List<String> sources) {
        return List.of(ShaderPatcher.patch(String.join("\n",sources)));
    }
    @Inject(method="_glUseProgram",at=@At("TAIL"),remap=false)
    private static void prism$bind(int program,CallbackInfo ci) { GpuLightVolume.bindProgram(program); }
    @Inject(method="glLinkProgram",at=@At("TAIL"),remap=false)
    private static void prism$link(int program,CallbackInfo ci) { GpuLightVolume.forgetProgram(program); }
}
