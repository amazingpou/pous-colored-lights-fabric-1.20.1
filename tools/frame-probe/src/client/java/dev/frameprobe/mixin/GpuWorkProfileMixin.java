package dev.frameprobe.mixin;
import dev.frameprobe.ProfileProbe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
@Pseudo @Mixin(targets="dev.prismlights.client.GpuLightVolume",remap=false)
abstract class GpuWorkProfileMixin {
    @Inject(method="advanceStaged",at=@At("HEAD")) private static void begin(CallbackInfoReturnable<Boolean> ci){ProfileProbe.begin("rgb-upload");}
    @Inject(method="advanceStaged",at=@At("RETURN")) private static void end(CallbackInfoReturnable<Boolean> ci){ProfileProbe.end();}
}
