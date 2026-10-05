package dev.prismlights.fabric;

import dev.prismlights.PrismLights;
import dev.prismlights.client.ClientPlatform;
import dev.prismlights.client.PrismClient;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.block.Block;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.entity.FlyingItemEntityRenderer;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.world.chunk.WorldChunk;

public final class PrismFabricClient implements ClientModInitializer, ClientPlatform {
    @Override public void onInitializeClient() { PrismClient.initialize(this); }
    @Override public void registerCandleFlame(net.minecraft.particle.DefaultParticleType type,int color) {
        net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry.getInstance().register(type,sprites->{
            var vanilla=new net.minecraft.client.particle.FlameParticle.SmallFactory(sprites);
            return (effect,world,x,y,z,vx,vy,vz)->{
                var particle=vanilla.createParticle(effect,world,x,y,z,vx,vy,vz);
                particle.setColor(((color>>>16)&255)/255f,((color>>>8)&255)/255f,(color&255)/255f);
                return particle;
            };
        });
    }
    @Override public void registerFireworkRenderer() {
        EntityRendererRegistry.register(PrismLights.FIREWORK_ENTITY, context -> new FlyingItemEntityRenderer<>(context, 0.75f, true));
    }
    @Override public void cutout(Block block) { BlockRenderLayerMap.INSTANCE.putBlock(block, RenderLayer.getCutout()); }
    @Override public KeyBinding registerKey(KeyBinding key) { return KeyBindingHelper.registerKeyBinding(key); }
    @Override public void onEntityLoad(BiConsumer<Entity, ClientWorld> listener) { ClientEntityEvents.ENTITY_LOAD.register(listener::accept); }
    @Override public void onEntityUnload(BiConsumer<Entity, ClientWorld> listener) { ClientEntityEvents.ENTITY_UNLOAD.register(listener::accept); }
    @Override public void onChunkLoad(BiConsumer<ClientWorld, WorldChunk> listener) { ClientChunkEvents.CHUNK_LOAD.register(listener::accept); }
    @Override public void onChunkUnload(BiConsumer<ClientWorld, WorldChunk> listener) { ClientChunkEvents.CHUNK_UNLOAD.register(listener::accept); }
    @Override public void onClientTick(Consumer<MinecraftClient> listener) { ClientTickEvents.END_CLIENT_TICK.register(listener::accept); }
}
