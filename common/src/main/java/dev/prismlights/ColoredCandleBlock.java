package dev.prismlights;

import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.CandleBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.particle.DefaultParticleType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;

/** Native candle stacking, waterlogging, ignition and support; RGB only while lit. */
public final class ColoredCandleBlock extends CandleBlock implements BlockEntityProvider, ColoredLightSource {
    private final int color;
    private final DefaultParticleType flame;
    public ColoredCandleBlock(Settings settings,int color,DefaultParticleType flame) {
        super(settings);this.color=color;this.flame=flame;
    }
    @Override public int prismColor() {return color;}
    @Override public boolean prismActive(BlockState state) {return state.get(LIT) && !state.get(WATERLOGGED);}
    @Override public int prismEmission(BlockState state) {return state.getLuminance();}
    @Override public BlockEntity createBlockEntity(BlockPos pos,BlockState state) {return new PrismBlockEntity(pos,state);}
    @Override public void randomDisplayTick(BlockState state,World world,BlockPos pos,Random random) {
        if(state.get(LIT))for(var offset:getParticleOffsets(state))
            CandleFlames.display(world,pos,offset,random,flame);
    }
}
