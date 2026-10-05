package dev.prismlights;

import dev.prismlights.platform.ContentPlatform;
import net.minecraft.block.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import org.slf4j.LoggerFactory;

/** Opt-in dedicated-server save/restart smoke test; no client types. */
final class CandleServerValidation {
    private static int ticks;
    private static boolean restart;
    static void register(ContentPlatform platform) {
        if(Boolean.getBoolean("prism.candleServerTest"))platform.onServerTick(CandleServerValidation::tick);
    }
    private static void tick(MinecraftServer server) {
        int now=++ticks;
        if(now==30) {
            require(java.nio.file.Path.of("").toAbsolutePath().getFileName().toString().matches("run-candle-server-validation(?:-opt)?"),"isolated server directory");
            for(String function:new String[]{"demo","showcase"})
                require(server.getCommandFunctionManager().getFunction(PrismLights.id(function)).isPresent(),"native function parsing "+function);
            LoggerFactory.getLogger("Prism Lights").info("Backport functions QA: demo/showcase parsed by native command dispatcher PASS");
            var world=server.getOverworld();world.getChunk(2,0);world.getChunk(3,0);
            restart=world.getBlockState(new BlockPos(32,100,2)).getBlock() instanceof ColoredCandleBlock;
            int i=0;
            for(var entry:PrismLights.NEW_BLOCKS.entrySet()) {
                if(!(entry.getValue() instanceof ColoredCandleBlock candle))continue;
                var pos=new BlockPos(32+i++,100,2);var desired=candle.getDefaultState().with(CandleBlock.CANDLES,4).with(CandleBlock.LIT,true);
                if(restart)require(world.getBlockState(pos)==desired && world.getBlockEntity(pos) instanceof PrismBlockEntity,"persisted lit group "+entry.getKey());
                else {world.setBlockState(pos.down(),Blocks.STONE.getDefaultState());world.setBlockState(pos,desired);}
                require(world.getBlockEntity(pos) instanceof PrismBlockEntity,"emitter block entity");
                require(desired.getLuminance()==12 && candle.prismEmission(desired)==12,"four candle light");
                var drops=Block.getDroppedStacks(desired,world,pos,world.getBlockEntity(pos));
                require(drops.size()==1 && drops.get(0).getCount()==4 && drops.get(0).isOf(candle.asItem()),"dedicated candle count loot");
                require(server.getRecipeManager().get(PrismLights.id(entry.getKey())).isPresent(),"dedicated candle recipe");
            }
            require(i==16,"sixteen colors");
            LoggerFactory.getLogger("Prism Lights").info("Candle dedicated QA: {} PASS, 16 states/entities/recipes/loot",restart?"save/restart":"initial placement");
        }
        if(now==100)server.stop(false);
    }
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}
