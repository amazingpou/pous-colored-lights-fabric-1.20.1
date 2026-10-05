package dev.prismlights;

import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.LanternBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.util.math.BlockPos;

public final class ColoredLanternBlock extends LanternBlock implements BlockEntityProvider, ColoredLightSource {
    private final int color;
    public ColoredLanternBlock(Settings settings, int color) {
        super(settings);
        this.color = color;
    }
    @Override public int prismColor() { return color; }
    @Override public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new PrismBlockEntity(pos, state);
    }
}
