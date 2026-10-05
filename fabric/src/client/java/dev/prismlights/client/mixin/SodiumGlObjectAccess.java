package dev.prismlights.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Optional bridge to the declaring class of Sodium 0.5's inherited handle. */
@Pseudo
@Mixin(targets="me.jellysquid.mods.sodium.client.gl.GlObject",remap=false)
public interface SodiumGlObjectAccess {
    @Invoker(value="handle",remap=false)
    int prism$handle();
}
