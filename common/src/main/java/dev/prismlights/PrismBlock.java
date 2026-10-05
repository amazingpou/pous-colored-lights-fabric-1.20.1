package dev.prismlights;

import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class PrismBlock extends Block implements BlockEntityProvider, ColoredLightSource {
    public static final BooleanProperty ACTIVE = BooleanProperty.of("active");
    public final int color;
    public PrismBlock(Settings settings, int color) {
        super(settings);
        this.color = color;
        setDefaultState(getStateManager().getDefaultState().with(ACTIVE, true));
    }
    @Override public int prismColor() { return color; }
    @Override public boolean prismActive(BlockState state) { return state.get(ACTIVE); }
    @Override protected void appendProperties(StateManager.Builder<Block, BlockState> builder) { builder.add(ACTIVE); }
    @Override public BlockEntity createBlockEntity(BlockPos pos, BlockState state) { return new PrismBlockEntity(pos, state); }
    @Override public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, net.minecraft.util.Hand hand, BlockHitResult hit) {
        if (!player.getMainHandStack().isEmpty()) return ActionResult.PASS;
        if (!world.isClient) world.setBlockState(pos, state.cycle(ACTIVE), Block.NOTIFY_ALL);
        return ActionResult.success(world.isClient);
    }
}
