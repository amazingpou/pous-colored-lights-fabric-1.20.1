package dev.prismlights.client;

import dev.prismlights.*;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.*;
import net.minecraft.world.chunk.WorldChunk;
import it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import java.util.*;
import org.slf4j.LoggerFactory;

/** World-space propagation shared by terrain and entity material shaders.
 * No overlay geometry, no competing depth pass. */
final class BaselineLightField {
    private static final long MAX_VOLUME_VOXELS = 2_000_000;
    private final PrismConfig config;
    private ClientWorld world;
    private final Long2ObjectOpenHashMap<WorldChunk> chunks=new Long2ObjectOpenHashMap<>();
    private final Long2ObjectOpenHashMap<Source> emitters=new Long2ObjectOpenHashMap<>();
    private final Map<Source,Long2DoubleOpenHashMap> propagationCache=new HashMap<>();
    private List<Source> publishedSources=List.of();
    private List<Source> selectedSources=List.of();
    private Vec3d selectionPosition;
    private boolean selectionDirty=true,selectionDependsOnPosition;
    private boolean dirty=true;
    private float[] accumulated=new float[0];
    private final float[][] outputBuffers={new float[0],new float[0]};
    private int outputSlot;
    private final Long2IntOpenHashMap distances=new Long2IntOpenHashMap();
    private final LongArrayFIFOQueue[] frontier=new LongArrayFIFOQueue[17];
    private boolean warnedAboutVolume;
    private long propagationBuilds;
    long propagationBuilds() { return propagationBuilds; }
    int cachedSources() { return propagationCache.size(); }
    int candidateCount() { return emitters.size(); }
    int selectedCount() { return selectedSources.size(); }
    boolean hasEmitter(BlockPos pos) { return emitters.containsKey(pos.asLong()); }
    /** Differential QA: discard every cache in a separate engine, then compare the full field. */
    void verifyFreshSnapshot(MinecraftClient client) {
        LightVolume previous=GpuLightVolume.current();
        BaselineLightField reference=new BaselineLightField(config);
        for(WorldChunk chunk:chunks.values()) reference.load(world,chunk);
        reference.tick(client);
        LightVolume actual=GpuLightVolume.current();
        boolean same=previous==null?actual==null:actual!=null && previous.x==actual.x && previous.y==actual.y
            && previous.z==actual.z && previous.width==actual.width && previous.height==actual.height
            && previous.depth==actual.depth && Arrays.equals(previous.rgba,actual.rgba);
        if(!same) throw new AssertionError("Incremental irradiance differs from a fresh rebuild");
    }
    private record Source(BlockPos pos,int color,int emission) {}
    public BaselineLightField(PrismConfig config) {
        this.config=config;distances.defaultReturnValue(Integer.MAX_VALUE);
        Arrays.setAll(frontier,unused->new LongArrayFIFOQueue());
    }
    private void setWorld(ClientWorld next) {
        if(world==next) return;
        world=next; chunks.clear(); emitters.clear(); propagationCache.clear(); publishedSources=List.of();
        selectedSources=List.of();selectionPosition=null;selectionDirty=true;
        accumulated=new float[0];outputBuffers[0]=outputBuffers[1]=new float[0];
        warnedAboutVolume=false;
        GpuLightVolume.clear(); dirty=true;
    }
    public void load(ClientWorld world,WorldChunk chunk) {
        setWorld(world);
        chunks.put(chunk.getPos().toLong(),chunk);
        removeEmitters(chunk.getPos());
        for(var entity:chunk.getBlockEntities().values()) {
            if(entity instanceof PrismBlockEntity && !entity.isRemoved())
                updateEmitter(entity.getPos(),world.getBlockState(entity.getPos()));
        }
        invalidateChunk(chunk.getPos());
    }
    public void unload(ClientWorld world,WorldChunk chunk) {
        if(this.world==world) {
            chunks.remove(chunk.getPos().toLong());removeEmitters(chunk.getPos());invalidateChunk(chunk.getPos());
        }
    }
    private void removeEmitters(ChunkPos chunk) {
        if(emitters.values().removeIf(source->(source.pos.getX()>>4)==chunk.x && (source.pos.getZ()>>4)==chunk.z))
            selectionDirty=true;
    }
    private void updateEmitter(BlockPos pos,BlockState state) {
        long key=pos.asLong();
        Source next=state.getBlock() instanceof ColoredLightSource light && light.prismActive(state)
            ?new Source(pos.toImmutable(),light.prismColor(),light.prismEmission(state)):null;
        Source before=next==null?emitters.remove(key):emitters.put(key,next);
        if(!Objects.equals(before,next)) selectionDirty=true;
    }
    private void invalidateChunk(ChunkPos chunk) {
        if(propagationCache.keySet().removeIf(source->LightInfluence.touchesChunk(
                source.pos.getX(),source.pos.getZ(),chunk.x,chunk.z,config.radius))) dirty=true;
    }
    public void invalidate() { dirty=true; }
    public void blockChanged(BlockPos pos,BlockState before,BlockState after) {
        if(before==after || world==null) return;
        if(before.getBlock() instanceof ColoredLightSource || after.getBlock() instanceof ColoredLightSource) {
            if(chunks.containsKey(ChunkPos.toLong(pos.getX()>>4,pos.getZ()>>4))) updateEmitter(pos,after);
        }
        // A prism is also geometry. An opaque source can block/open paths
        // from other lights, even when its own color is off.
        boolean oldBlocked=before.isOpaqueFullCube(world,pos) || before.getOpacity(world,pos)>=15;
        boolean newBlocked=after.isOpaqueFullCube(world,pos) || after.getOpacity(world,pos)>=15;
        if(oldBlocked!=newBlocked || before.getFluidState().isEmpty()!=after.getFluidState().isEmpty()) {
            if(propagationCache.keySet().removeIf(source->source.pos.getManhattanDistance(pos)<config.radius)) dirty=true;
        }
    }
    public void tick(MinecraftClient client) {
        setWorld(client.world);
        GpuLightVolume.setEnabled(config.enabled);
        if(world==null || client.player==null || !config.enabled) return;
        List<Source> sources=discover(client);
        if(!sources.equals(publishedSources)) dirty=true;
        if(!dirty) return;

        // One complete snapshot is calculated and published in this tick. The
        // old GPU volume stays active until the replacement is fully ready, so
        // a newly placed prism can never expose a partial set of colors.
        propagationCache.keySet().retainAll(sources);
        for(Source source:sources) propagationCache.computeIfAbsent(source,this::propagate);
        publish(sources);
        publishedSources=sources;
        dirty=false;
    }
    private List<Source> discover(MinecraftClient client) {
        Vec3d position=client.player.getPos();
        if(!selectionDirty && (!selectionDependsOnPosition || position.equals(selectionPosition))) return selectedSources;
        List<Source> found=new ArrayList<>(emitters.values());
        found.sort(Comparator.<Source>comparingDouble(s->s.pos.getSquaredDistance(client.player.getPos()))
            .thenComparingLong(s->s.pos.asLong()));
        // Distance is used only to choose which lights fit the configured cap.
        // Publish the chosen set in a stable world-position order: otherwise
        // walking between unchanged lights reorders this list every tick and
        // needlessly remixes and uploads the entire irradiance volume.
        List<Source> selected=new ArrayList<>();
        int minX=Integer.MAX_VALUE,minY=Integer.MAX_VALUE,minZ=Integer.MAX_VALUE;
        int maxX=Integer.MIN_VALUE,maxY=Integer.MIN_VALUE,maxZ=Integer.MIN_VALUE;
        int margin=config.radius+2;
        for(Source candidate:found) {
            if(selected.size()>=config.maxLights) break;
            int nextMinX=Math.min(minX,candidate.pos.getX()-margin);
            int nextMinY=Math.min(minY,candidate.pos.getY()-margin);
            int nextMinZ=Math.min(minZ,candidate.pos.getZ()-margin);
            int nextMaxX=Math.max(maxX,candidate.pos.getX()+margin);
            int nextMaxY=Math.max(maxY,candidate.pos.getY()+margin);
            int nextMaxZ=Math.max(maxZ,candidate.pos.getZ()+margin);
            long voxels=(long)(nextMaxX-nextMinX+1)*(nextMaxY-nextMinY+1)*(nextMaxZ-nextMinZ+1);
            if(voxels>MAX_VOLUME_VOXELS) {
                if(!warnedAboutVolume) {
                    warnedAboutVolume=true;
                    LoggerFactory.getLogger("Prism Lights").warn("RGB field size limit reached; distant lights are omitted until the player moves closer");
                }
                continue;
            }
            selected.add(candidate);
            minX=nextMinX; minY=nextMinY; minZ=nextMinZ;
            maxX=nextMaxX; maxY=nextMaxY; maxZ=nextMaxZ;
        }
        selected.sort(Comparator.comparingLong(s->s.pos.asLong()));
        selectionPosition=position;selectionDirty=false;
        selectionDependsOnPosition=selected.size()<found.size();
        return selectedSources=List.copyOf(selected);
    }
    private boolean loaded(BlockPos pos) {
        return pos.getY()>=world.getBottomY() && pos.getY()<world.getTopY()
            && chunks.containsKey(ChunkPos.toLong(pos.getX()>>4,pos.getZ()>>4));
    }
    private Long2DoubleOpenHashMap propagate(Source source) {
        propagationBuilds++;
        Long2DoubleOpenHashMap contribution=new Long2DoubleOpenHashMap();
        BlockState state=world.getBlockState(source.pos);
        if(!(state.getBlock() instanceof ColoredLightSource light) || !light.prismActive(state)) return contribution;
        // Integer distance buckets preserve Dijkstra ordering, including water's
        // cost of two, without allocating a Node/BlockPos for every neighbor.
        distances.clear();for(var queue:frontier) queue.clear();
        long origin=source.pos.asLong();frontier[0].enqueue(origin);distances.put(origin,0);
        int radius=Math.max(1,(config.radius*source.emission+14)/15);
        BlockPos.Mutable next=new BlockPos.Mutable();
        for(int distance=0;distance<radius;distance++) while(!frontier[distance].isEmpty()) {
            long packed=frontier[distance].dequeueLong();
            if(distance!=distances.get(packed)) continue;
            double falloff=1.0-(double)distance/radius;
            contribution.put(packed,falloff*falloff*source.emission/15.0);
            int x=BlockPos.unpackLongX(packed),y=BlockPos.unpackLongY(packed),z=BlockPos.unpackLongZ(packed);
            for(int direction=0;direction<6;direction++) {
                next.set(x+(direction==0?1:direction==1?-1:0),y+(direction==2?1:direction==3?-1:0),
                    z+(direction==4?1:direction==5?-1:0));
                if(!loaded(next)) continue;
                BlockState nextState=world.getBlockState(next);
                // A single voxel cannot distinguish the near and far faces of
                // an opaque block. Writing the incident light into that voxel
                // colors its far face too when the GPU samples it linearly.
                // Keep irradiance in traversable space; the near face still
                // receives it from the adjacent air voxel.
                if(!nextState.isOpaqueFullCube(world,next) && nextState.getOpacity(world,next)<15) {
                    int nextDistance=distance+(nextState.getFluidState().isEmpty()?1:2);
                    long key=next.asLong();
                    if(nextDistance<radius && nextDistance<distances.get(key)) {
                        distances.put(key,nextDistance);frontier[nextDistance].enqueue(key);
                    }
                }
            }
        }
        return contribution;
    }
    private void publish(List<Source> sources) {
        if(sources.isEmpty()) { GpuLightVolume.clear(); return; }
        int minX=Integer.MAX_VALUE,minY=Integer.MAX_VALUE,minZ=Integer.MAX_VALUE;
        int maxX=Integer.MIN_VALUE,maxY=Integer.MIN_VALUE,maxZ=Integer.MIN_VALUE;
        boolean any=false;
        for(Source source:sources) for(long packed:propagationCache.get(source).keySet()) {
            any=true;
            int x=BlockPos.unpackLongX(packed),y=BlockPos.unpackLongY(packed),z=BlockPos.unpackLongZ(packed);
            minX=Math.min(minX,x); minY=Math.min(minY,y); minZ=Math.min(minZ,z);
            maxX=Math.max(maxX,x); maxY=Math.max(maxY,y); maxZ=Math.max(maxZ,z);
        }
        if(!any) { GpuLightVolume.clear(); return; }
        minX--;minY--;minZ--; maxX++;maxY++;maxZ++;
        int width=maxX-minX+1,height=maxY-minY+1,depth=maxZ-minZ+1;
        int length=width*height*depth*4;
        if(accumulated.length!=length) accumulated=new float[length];
        else Arrays.fill(accumulated,0);
        // Never write into the currently published CPU snapshot.
        LightVolume current=GpuLightVolume.current();
        outputSlot=current!=null && current.rgba==outputBuffers[0]?1:0;
        if(outputBuffers[outputSlot].length!=length) outputBuffers[outputSlot]=new float[length];
        else Arrays.fill(outputBuffers[outputSlot],0);
        LightVolume volume=new LightVolume(minX,minY,minZ,width,height,depth,outputBuffers[outputSlot]);
        for(Source source:sources) {
            double[] channels=ColorMixer.components(source.color,config.artisticMixing);
            for(var entry:propagationCache.get(source).long2DoubleEntrySet()) {
                long packed=entry.getLongKey(); float weight=(float)entry.getDoubleValue();
                int x=BlockPos.unpackLongX(packed)-minX;
                int y=BlockPos.unpackLongY(packed)-minY;
                int z=BlockPos.unpackLongZ(packed)-minZ;
                int offset=volume.index(x,y,z);
                for(int i=0;i<3;i++) accumulated[offset+i]+=channels[i]*weight;
                accumulated[offset+3]+=weight;
            }
        }
        for(int offset=0;offset<accumulated.length;offset+=4) {
            float energy=accumulated[offset+3];
            if(!(energy>0)) continue;
            ColorMixer.writeColor(accumulated[offset],accumulated[offset+1],accumulated[offset+2],
                energy,config.artisticMixing,volume.rgba,offset);
            float max=Math.max(volume.rgba[offset],Math.max(volume.rgba[offset+1],volume.rgba[offset+2]));
            float coverage=(float)(1-Math.exp(-3.5*energy));
            if(max>0) for(int i=0;i<3;i++) {
                float hue=volume.rgba[offset+i]/max;
                volume.rgba[offset+i]=(float)Math.pow(hue,2.2)*coverage*config.strength*1.6f;
            }
            volume.rgba[offset+3]=coverage;
        }
        GpuLightVolume.publish(volume);
    }
}
