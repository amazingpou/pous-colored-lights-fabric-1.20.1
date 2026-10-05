package dev.prismlights.client;

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.block.Block;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.world.chunk.WorldChunk;

/** Client-only loader bindings; no client classes leak into the server platform. */
public interface ClientPlatform {
    void registerFireworkRenderer();
    void registerCandleFlame(net.minecraft.particle.DefaultParticleType type,int color);
    void cutout(Block block);
    KeyBinding registerKey(KeyBinding key);
    void onEntityLoad(BiConsumer<Entity, ClientWorld> listener);
    void onEntityUnload(BiConsumer<Entity, ClientWorld> listener);
    void onChunkLoad(BiConsumer<ClientWorld, WorldChunk> listener);
    void onChunkUnload(BiConsumer<ClientWorld, WorldChunk> listener);
    void onClientTick(Consumer<MinecraftClient> listener);
}
