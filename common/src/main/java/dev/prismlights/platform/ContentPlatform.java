package dev.prismlights.platform;

import dev.prismlights.PrismBlockEntity;
import dev.prismlights.PrismFireworkEntity;
import java.util.function.Consumer;
import net.minecraft.block.Block;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.item.ItemGroup;
import net.minecraft.entity.EntityType;
import net.minecraft.registry.Registry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;

/** Registration and lifecycle operations provided by the active loader adapter. */
public interface ContentPlatform extends PlatformServices.Environment {
    <T, V extends T> V register(Registry<T> registry, Identifier id, V value);
    BlockEntityType<PrismBlockEntity> lightEntityType(Block[] blocks);
    EntityType<PrismFireworkEntity> fireworkEntityType(EntityType.Builder<PrismFireworkEntity> builder);
    ItemGroup.Builder itemGroupBuilder();
    net.minecraft.particle.DefaultParticleType simpleParticleType();
    void onServerTick(Consumer<MinecraftServer> listener);
}
