package dev.frameprobe.mixin;
import dev.frameprobe.FrameProbe;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(MinecraftClient.class)
abstract class FrameTimingMixin {
    @Inject(method="render",at=@At("HEAD"))
    private void frameprobe$begin(boolean tick,CallbackInfo ci) { FrameProbe.frameBegin(); }
    @Inject(method="render",at=@At("RETURN"))
    private void frameprobe$end(boolean tick,CallbackInfo ci) { FrameProbe.frameEnd(); }
}
