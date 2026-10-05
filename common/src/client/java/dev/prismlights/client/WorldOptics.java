package dev.prismlights.client;

import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;

/** Client-thread geometry shared by static propagation and mobile connectivity.
 * Entries live only in loaded chunks and are invalidated before lighting updates. */
final class WorldOptics {
    private static final int BLOCKED=1, FLUID=2;
    private static ClientWorld world;
    private static final Long2ObjectOpenHashMap<Long2ByteOpenHashMap> chunks=new Long2ObjectOpenHashMap<>();
    private WorldOptics() {}
    static void world(ClientWorld next) {
        if(world!=next) {world=next;chunks.clear();}
    }
    static void chunk(ClientWorld next,ChunkPos position) {
        world(next);chunks.remove(position.toLong());
    }
    static void changed(BlockPos position) {
        // Neighbor-sensitive shapes may depend on adjacent cells; clearing the
        // affected chunks also preserves that dependency without a global scan.
        for(int z=(position.getZ()-1)>>4;z<=(position.getZ()+1)>>4;z++)
            for(int x=(position.getX()-1)>>4;x<=(position.getX()+1)>>4;x++) chunks.remove(ChunkPos.toLong(x,z));
    }
    static int cell(ClientWorld next,BlockPos position) {
        world(next);
        long chunk=ChunkPos.toLong(position.getX()>>4,position.getZ()>>4);
        var cells=chunks.get(chunk);
        if(cells==null) {cells=new Long2ByteOpenHashMap();cells.defaultReturnValue((byte)-1);chunks.put(chunk,cells);}
        long key=position.asLong();int cached=cells.get(key);
        if(cached>=0)return cached;
        var state=next.getBlockState(position);
        int flags=(state.isOpaqueFullCube(next,position) || state.getOpacity(next,position)>=15?BLOCKED:0)
            | (state.getFluidState().isEmpty()?0:FLUID);
        cells.put(key,(byte)flags);return flags;
    }
    static boolean blocked(int cell) {return (cell&BLOCKED)!=0;}
    static int cost(int cell) {return (cell&FLUID)==0?1:2;}
}
