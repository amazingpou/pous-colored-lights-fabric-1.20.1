package dev.prismlights.client;

import dev.prismlights.ColoredLightSource;
import dev.prismlights.LightNibbleClamp;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.block.BlockState;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.world.LightType;
import net.minecraft.world.chunk.ChunkNibbleArray;
import net.minecraft.world.chunk.light.LightingProvider;

/** Client-only barrier for native blocklight arriving before its RGB generation.
 * Sky packets and unrelated sections pass through. Server logic is untouched. */
public final class LightTransitionGate {
    private static ClientWorld world;
    private static final Long2IntOpenHashMap sources=new Long2IntOpenHashMap();
    private static final LongOpenHashSet sections=new LongOpenHashSet();
    private static final LongOpenHashSet removals=new LongOpenHashSet();
    private static final Long2ObjectOpenHashMap<ChunkNibbleArray> packets=new Long2ObjectOpenHashMap<>();
    private static boolean enabled;
    private static long deferredPackets,releases;
    private static long nativeEpoch,renderedEpoch=-1;
    private LightTransitionGate() {}
    static void world(ClientWorld next) {
        if(world==next)return;
        world=next;sources.clear();sections.clear();packets.clear();removals.clear();enabled=false;nativeEpoch=0;renderedEpoch=-1;
    }
    static void configure(boolean next) {
        if(!next && enabled)release();
        enabled=next && !Boolean.getBoolean("prism.disableTransitionGate");
    }
    static void changed(BlockPos pos,BlockState before,BlockState after) {
        if(!enabled || world==null)return;
        if(!(before.getBlock() instanceof ColoredLightSource) && !(after.getBlock() instanceof ColoredLightSource))return;
        if(before.getLuminance()==after.getLuminance())return;
        nativeEpoch++;
        if(sources.size()>=256 || sections.size()>=4096)release();
        long key=pos.asLong();
        if(after.getLuminance()>before.getLuminance()){
            // Several candle additions may arrive before the first generation
            // publishes. Keep the lowest held emission instead of advancing it.
            sources.put(key,Math.min(before.getLuminance(),sources.getOrDefault(key,before.getLuminance())));
            removals.remove(key);
        }
        else {sources.putIfAbsent(key,before.getLuminance());removals.add(key);}
        // Native light attenuates by one per cell, independent of the RGB radius.
        int radius=15;
        for(int x=(pos.getX()-radius)>>4;x<=(pos.getX()+radius)>>4;x++)
            for(int y=(pos.getY()-radius)>>4;y<=(pos.getY()+radius)>>4;y++)
                for(int z=(pos.getZ()-radius)>>4;z<=(pos.getZ()+radius)>>4;z++)
                    sections.add(ChunkSectionPos.asLong(x,y,z));
    }
    public static int luminance(ClientWorld owner,long pos,int actual) {
        if(owner!=world || !enabled || !sources.containsKey(pos))return actual;
        return Math.min(actual,sources.get(pos));
    }
    public static boolean defer(LightingProvider provider,LightType type,ChunkSectionPos section,ChunkNibbleArray data) {
        if(!enabled || world==null || type!=LightType.BLOCK || provider!=world.getChunkManager().getLightingProvider()
            || !sections.contains(section.asLong()))return false;
        packets.put(section.asLong(),data);deferredPackets++;
        var old=provider.get(LightType.BLOCK).getLightSection(section);
        var held=new ChunkNibbleArray(LightNibbleClamp.decreasesOnly(data.asByteArray(),old==null?null:old.asByteArray()));
        provider.enqueueSectionData(type,section,held);
        return true;
    }
    public static void lightChanged(LightType type,ChunkSectionPos section) {
        if(type==LightType.BLOCK && sections.contains(section.asLong()))nativeEpoch++;
    }
    public static void scheduleTerrain(Object manager) {
        if(enabled && !removals.isEmpty())TerrainLightBarrier.schedule(manager,sections);
    }
    public static void rendered() {
        if(world!=null && !removals.isEmpty() && !world.getChunkManager().getLightingProvider().get(LightType.BLOCK).hasUpdates()
            && TerrainLightBarrier.complete(sections))renderedEpoch=nativeEpoch;
    }
    static boolean readyToPublish() {
        if(world==null || removals.isEmpty())return true;
        var provider=world.getChunkManager().getLightingProvider();
        if(provider.get(LightType.BLOCK).hasUpdates())return false;
        for(long key:removals){
            var pos=BlockPos.fromLong(key);var state=world.getBlockState(pos);
            if(state.getLuminance()==0 && state.isOpaqueFullCube(world,pos)){
                if(provider.get(LightType.BLOCK).getLightLevel(pos)!=0)return false;
                var queued=packets.get(ChunkSectionPos.from(pos).asLong());
                if(queued!=null && queued.get(pos.getX()&15,pos.getY()&15,pos.getZ()&15)!=0)return false;
            }
        }
        return renderedEpoch==nativeEpoch && TerrainLightBarrier.complete(sections);
    }
    static void published() { release(); }
    private static void release() {
        if(world==null){sources.clear();sections.clear();packets.clear();removals.clear();return;}
        var provider=world.getChunkManager().getLightingProvider();
        // Remove the barrier before native propagation reads the sources again.
        var positions=sources.keySet().toLongArray();sources.clear();sections.clear();removals.clear();
        for(var entry:packets.long2ObjectEntrySet()) {
            var section=ChunkSectionPos.from(entry.getLongKey());
            provider.enqueueSectionData(LightType.BLOCK,section,entry.getValue());
            world.scheduleBlockRenders(section.getSectionX(),section.getSectionY(),section.getSectionZ());
        }
        packets.clear();
        // Only emission was held. In 1.20.1 the combined provider.checkBlock
        // also recomputes client skylight, replacing authoritative server sky
        // beneath leaves with 15. Recheck BLOCK without touching SKY.
        for(long pos:positions)provider.get(LightType.BLOCK).checkBlock(BlockPos.fromLong(pos));
        if(positions.length!=0)releases++;
    }
}
