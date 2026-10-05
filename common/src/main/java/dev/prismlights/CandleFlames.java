package dev.prismlights;

import net.minecraft.particle.ParticleTypes;
import net.minecraft.particle.DefaultParticleType;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;

/** Matches AbstractCandleBlock's display distribution; only SMALL_FLAME changes. */
final class CandleFlames {
    private CandleFlames() {}
    static void display(World world,BlockPos pos,Vec3d offset,Random random,DefaultParticleType flame) {
        double x=pos.getX()+offset.x,y=pos.getY()+offset.y,z=pos.getZ()+offset.z;
        float chance=random.nextFloat();
        if(chance<.3f) {
            world.addParticle(ParticleTypes.SMOKE,x,y,z,0,0,0);
            if(chance<.17f)world.playSound(x+.5,y+.5,z+.5,SoundEvents.BLOCK_CANDLE_AMBIENT,
                SoundCategory.BLOCKS,1+random.nextFloat(),random.nextFloat()*.7f+.3f,false);
        }
        world.addParticle(flame,x,y,z,0,0,0);
    }
}
