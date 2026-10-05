package dev.prismlights;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** The existing prism shape with a power-controlled light state. */
public final class RedstonePrismBlock extends PrismBlock {
    public RedstonePrismBlock(Settings settings, int color) {
        super(settings, color);
        setDefaultState(getDefaultState().with(ACTIVE, false));
    }

    @Override public BlockState getPlacementState(ItemPlacementContext context) {
        return getDefaultState().with(ACTIVE, context.getWorld().isReceivingRedstonePower(context.getBlockPos()));
    }

    @Override public void neighborUpdate(BlockState state, World world, BlockPos pos, Block sourceBlock,
                                             BlockPos sourcePos, boolean notify) {
        if (!world.isClient) {
            boolean powered = world.isReceivingRedstonePower(pos);
            if (powered != state.get(ACTIVE)) world.setBlockState(pos, state.with(ACTIVE, powered), Block.NOTIFY_ALL);
        }
    }

    @Override public ActionResult onUse(BlockState state, World world, BlockPos pos,
                                            PlayerEntity player, net.minecraft.util.Hand hand, BlockHitResult hit) {
        return ActionResult.PASS;
    }
}
