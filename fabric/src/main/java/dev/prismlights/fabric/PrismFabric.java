package dev.prismlights.fabric;

import dev.prismlights.PrismLights;
import dev.prismlights.PrismBlockEntity;
import dev.prismlights.PrismFireworkEntity;
import dev.prismlights.platform.ContentPlatform;
import dev.prismlights.platform.PlatformServices;
import java.nio.file.Path;
import java.util.function.Consumer;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Block;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.item.ItemGroup;
import net.minecraft.entity.EntityType;
import net.minecraft.registry.Registry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;

public final class PrismFabric implements ModInitializer, ContentPlatform {
    @Override public void onInitialize() {
        PlatformServices.install(this);
        PrismLights.initialize(this);
    }
    @Override public Path configDirectory() { return FabricLoader.getInstance().getConfigDir(); }
    @Override public boolean isModLoaded(String id) { return FabricLoader.getInstance().isModLoaded(id); }
    @Override public boolean isDevelopmentEnvironment() { return FabricLoader.getInstance().isDevelopmentEnvironment(); }
    @Override public <T, V extends T> V register(Registry<T> registry, Identifier id, V value) { return Registry.register(registry, id, value); }
    @Override public BlockEntityType<PrismBlockEntity> lightEntityType(Block[] blocks) {
        return FabricBlockEntityTypeBuilder.create(PrismBlockEntity::new, blocks).build();
    }
    @Override public EntityType<PrismFireworkEntity> fireworkEntityType(EntityType.Builder<PrismFireworkEntity> builder) { return builder.build("prism_lights:prism_firework"); }
    @Override public ItemGroup.Builder itemGroupBuilder() { return FabricItemGroup.builder(); }
    @Override public net.minecraft.particle.DefaultParticleType simpleParticleType() {
        return net.fabricmc.fabric.api.particle.v1.FabricParticleTypes.simple();
    }
    @Override public void onServerTick(Consumer<MinecraftServer> listener) { ServerTickEvents.END_SERVER_TICK.register(listener::accept); }
}
