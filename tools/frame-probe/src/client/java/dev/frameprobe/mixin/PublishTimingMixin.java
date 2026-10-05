package dev.frameprobe.mixin;
import dev.frameprobe.FrameProbe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Pseudo
@Mixin(targets="dev.prismlights.client.GpuLightVolume",remap=false)
abstract class PublishTimingMixin {
    @Inject(method="publish",at=@At("HEAD"))
    private static void frameprobe$publish(CallbackInfo ci) { FrameProbe.published(); }
}
