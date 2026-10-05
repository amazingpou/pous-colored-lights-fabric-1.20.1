package dev.prismlights;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.util.math.BlockPos;

public final class PrismBlockEntity extends BlockEntity {
    public PrismBlockEntity(BlockPos pos, BlockState state) { super(PrismLights.LIGHT_ENTITY, pos, state); }
}
