package dev.frameprobe.mixin;
import dev.frameprobe.ProfileProbe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Pseudo @Mixin(targets="dev.prismlights.client.MobileLights",remap=false)
abstract class MobileWorkProfileMixin {
    @Inject(method="advanceUntil",at=@At("HEAD")) private static void begin(CallbackInfo ci){ProfileProbe.begin("mobile-work");}
    @Inject(method="advanceUntil",at=@At("RETURN")) private static void end(CallbackInfo ci){ProfileProbe.end();}
    @Inject(method="upload",at=@At("HEAD")) private static void uploadBegin(CallbackInfo ci){ProfileProbe.begin("mobile-upload");}
    @Inject(method="upload",at=@At("RETURN")) private static void uploadEnd(CallbackInfo ci){ProfileProbe.end();}
    @Inject(method="tick",at=@At("HEAD")) private static void tickBegin(CallbackInfo ci){ProfileProbe.begin("mobile-selection");}
    @Inject(method="tick",at=@At("RETURN")) private static void tickEnd(CallbackInfo ci){ProfileProbe.end();}
}
