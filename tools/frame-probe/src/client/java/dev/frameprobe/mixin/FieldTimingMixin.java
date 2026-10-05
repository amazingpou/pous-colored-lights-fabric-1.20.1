package dev.frameprobe.mixin;
import dev.frameprobe.FrameProbe;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Pseudo
@Mixin(targets="dev.prismlights.client.LightField",remap=false)
abstract class FieldTimingMixin {
    @Inject(method="tick",at=@At("HEAD"))
    private void frameprobe$begin(MinecraftClient client,CallbackInfo ci) { FrameProbe.fieldBegin(); }
    @Inject(method="tick",at=@At("RETURN"))
    private void frameprobe$end(MinecraftClient client,CallbackInfo ci) { FrameProbe.fieldEnd(); }
    // Baseline composes synchronously in publish; optimized completes in finish.
    // Counting calls to GPU publish alone misses its unchanged-data early return.
    @Inject(method={"publish","finish"},at=@At("RETURN"),require=0)
    private void frameprobe$composed(CallbackInfo ci) { FrameProbe.composed(); }
}
