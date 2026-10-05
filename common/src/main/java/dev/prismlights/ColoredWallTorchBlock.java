package dev.prismlights;

import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.WallTorchBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;

public final class ColoredWallTorchBlock extends WallTorchBlock implements BlockEntityProvider, ColoredLightSource {
    private final int color;
    public ColoredWallTorchBlock(Settings settings, int color) {
        super(settings, ParticleTypes.FLAME);
        this.color = color;
    }
    @Override public int prismColor() { return color; }
    @Override public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new PrismBlockEntity(pos, state);
    }
    @Override public void randomDisplayTick(BlockState state, World world, BlockPos pos, Random random) {
        if (random.nextInt(3) != 0) return;
        Direction facing = state.get(FACING);
        world.addParticle(new DustParticleEffect(ColoredTorchBlock.particleColor(color), 1.0f),
            pos.getX() + 0.5 - facing.getOffsetX() * 0.27, pos.getY() + 0.72,
            pos.getZ() + 0.5 - facing.getOffsetZ() * 0.27, 0, 0.01, 0);
    }
}
