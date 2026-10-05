package dev.prismlights;

import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.TorchBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import org.joml.Vector3f;

public final class ColoredTorchBlock extends TorchBlock implements BlockEntityProvider, ColoredLightSource {
    private final int color;
    public ColoredTorchBlock(Settings settings, int color) {
        super(settings, ParticleTypes.FLAME);
        this.color = color;
    }
    @Override public int prismColor() { return color; }
    @Override public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new PrismBlockEntity(pos, state);
    }
    @Override public void randomDisplayTick(BlockState state, World world, BlockPos pos, Random random) {
        if (random.nextInt(3) != 0) return;
        world.addParticle(new DustParticleEffect(particleColor(color), 1.0f),
            pos.getX() + 0.5, pos.getY() + 0.72, pos.getZ() + 0.5, 0, 0.01, 0);
    }
    static Vector3f particleColor(int color) {
        return new Vector3f(((color >>> 16) & 255) / 255f, ((color >>> 8) & 255) / 255f, (color & 255) / 255f);
    }
}
