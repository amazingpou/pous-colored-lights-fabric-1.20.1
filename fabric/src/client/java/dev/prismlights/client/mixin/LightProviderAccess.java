package dev.prismlights.client.mixin;
import net.minecraft.world.chunk.ChunkProvider;
import net.minecraft.world.chunk.light.ChunkLightProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(ChunkLightProvider.class)
public interface LightProviderAccess {
    @Accessor("chunkProvider") ChunkProvider prism$chunkProvider();
}
