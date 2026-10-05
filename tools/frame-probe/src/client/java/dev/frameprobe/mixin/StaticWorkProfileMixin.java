package dev.frameprobe.mixin;
import dev.frameprobe.ProfileProbe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Pseudo @Mixin(targets="dev.prismlights.client.LightField",remap=false)
abstract class StaticWorkProfileMixin {
    @Inject(method="advanceUntil",at=@At("HEAD")) private void begin(CallbackInfo ci){ProfileProbe.begin("static-work");}
    @Inject(method="advanceUntil",at=@At("RETURN")) private void end(CallbackInfo ci){ProfileProbe.end();}
    @Inject(method="finish",at=@At("HEAD")) private void finishBegin(CallbackInfo ci){ProfileProbe.begin("static-finish");}
    @Inject(method="finish",at=@At("RETURN")) private void finishEnd(CallbackInfo ci){ProfileProbe.end();}
}
