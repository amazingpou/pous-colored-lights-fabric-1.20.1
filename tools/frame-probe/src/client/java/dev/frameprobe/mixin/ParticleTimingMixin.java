package dev.frameprobe.mixin;
import dev.frameprobe.FireworkProbe;
import net.minecraft.client.particle.ParticleManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(ParticleManager.class)
abstract class ParticleTimingMixin {
    @Inject(method="tick",at=@At("HEAD")) private void tickBegin(CallbackInfo ci){FireworkProbe.tickBegin();}
    @Inject(method="tick",at=@At("RETURN")) private void tickEnd(CallbackInfo ci){FireworkProbe.tickEnd();}
    @Inject(method="renderParticles",at=@At("HEAD")) private void renderBegin(CallbackInfo ci){FireworkProbe.renderBegin();}
    @Inject(method="renderParticles",at=@At("RETURN")) private void renderEnd(CallbackInfo ci){FireworkProbe.renderEnd();}
}
