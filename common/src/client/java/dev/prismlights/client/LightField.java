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
public final class LightField {
    private static final long MAX_VOLUME_VOXELS = 2_000_000;
    private final PrismConfig config;
    private final boolean reference;
    private ClientWorld world;
    private final Long2ObjectOpenHashMap<WorldChunk> chunks=new Long2ObjectOpenHashMap<>();
    private final Long2ObjectOpenHashMap<Source> emitters=new Long2ObjectOpenHashMap<>();
    private final Map<Source,Long2DoubleOpenHashMap> propagationCache=new HashMap<>();
    private List<Source> publishedSources=List.of();
    private List<Source> selectedSources=List.of();
    private Vec3d selectionPosition;
    private boolean selectionDirty=true,selectionDependsOnPosition;
    private boolean dirty=true;
    private List<Source> pendingSources;
    private Map<Source,Long2DoubleOpenHashMap> publishedPaths=Map.of();
    private final Map<Long2DoubleOpenHashMap,VolumeRegion> pathBounds=new IdentityHashMap<>();
    private final Set<Long2DoubleOpenHashMap> retainedPaths=Collections.newSetFromMap(new IdentityHashMap<>());
    private Composition composition;
    private int nextSource;
    private Propagation propagation;
    private boolean staged;
    private boolean publishedArtistic;
    private float publishedStrength;
    private LightVolume rawGeometry;
    private boolean rawArtistic;
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
        finishPending();
        LightVolume previous=GpuLightVolume.current();
        BaselineLightField reference=new BaselineLightField(config);
        for(WorldChunk chunk:chunks.values()) reference.load(world,chunk);
        reference.tick(client);
        LightVolume actual=GpuLightVolume.current();
        boolean same=previous==null?actual==null:actual!=null && previous.x==actual.x && previous.y==actual.y
            && previous.z==actual.z && previous.width==actual.width && previous.height==actual.height
            && previous.depth==actual.depth && Arrays.equals(previous.rgba,actual.rgba);
        if(!same) throw new AssertionError("Incremental irradiance differs from a fresh rebuild");
        if(previous!=null)GpuLightVolume.publish(previous);else GpuLightVolume.clear();
    }
    private record Source(BlockPos pos,int color,int emission) {}
    public LightField(PrismConfig config) {
        this(config,false);
    }
    private LightField(PrismConfig config,boolean reference) {
        this.config=config;this.reference=reference;distances.defaultReturnValue(Integer.MAX_VALUE);
        Arrays.setAll(frontier,unused->new LongArrayFIFOQueue());
    }
    private void setWorld(ClientWorld next) {
        if(world==next) return;
        world=next; chunks.clear(); emitters.clear(); propagationCache.clear(); publishedSources=List.of();
        LightTransitionGate.world(next);
        pendingSources=null;composition=null;propagation=null;staged=false;publishedPaths=Map.of();pathBounds.clear();retainedPaths.clear();
        WorldOptics.world(next);
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
        if(propagation!=null && LightInfluence.touchesChunk(pendingSources.get(nextSource).pos.getX(),pendingSources.get(nextSource).pos.getZ(),chunk.x,chunk.z,config.radius))dirty=true;
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
            if(propagation!=null && pendingSources.get(nextSource).pos.getManhattanDistance(pos)<config.radius)dirty=true;
            if(propagationCache.keySet().removeIf(source->source.pos.getManhattanDistance(pos)<config.radius)) dirty=true;
        }
    }
    public void tick(MinecraftClient client) {
        setWorld(client.world);
        GpuLightVolume.setEnabled(config.enabled);
        LightTransitionGate.configure(config.enabled && GpuLightVolume.hasMaterialSupport());
        if(world==null || client.player==null || !config.enabled) return;
        List<Source> sources=discover(client);
        if(!sources.equals(pendingSources==null?publishedSources:pendingSources))dirty=true;
        if(!dirty && pendingSources==null)LightTransitionGate.published();
        if(dirty) {
            propagationCache.keySet().retainAll(sources);
            pendingSources=sources;nextSource=0;composition=null;propagation=null;staged=false;
            GpuLightVolume.cancelStaged();dirty=false;
        }
        if(reference)advance(Long.MAX_VALUE);
    }
    /** Cooperative client-thread work: never reads a live world from a worker.
     * The previous complete field remains visible until the replacement is ready. */
    boolean workPending() {
        if(pendingSources==null || world==null || !config.enabled || dirty || selectionDirty)return false;
        // Waiting for renderer/native light is not CPU work. Leave the shared
        // frame allowance available to moving lights and sky instead of polling.
        return !(pendingSources.isEmpty() || GpuLightVolume.waitingForTerrain()) || LightTransitionGate.readyToPublish();
    }
    void advance(long budget) {
        advanceUntil(budget==Long.MAX_VALUE?Long.MAX_VALUE:System.nanoTime()+budget);
    }
    void advanceUntil(long deadline) {
        if(!workPending())return;
        while(nextSource<pendingSources.size() && System.nanoTime()<deadline) {
            Source source=pendingSources.get(nextSource);
            if(propagationCache.containsKey(source)){nextSource++;continue;}
            if(propagation==null)propagation=new Propagation(source);
            if(!propagation.advance(deadline))return;
            propagationCache.put(source,propagation.contribution);propagation=null;nextSource++;
        }
        if(nextSource<pendingSources.size() || System.nanoTime()>=deadline)return;
        if(pendingSources.isEmpty()){if(!LightTransitionGate.readyToPublish())return;GpuLightVolume.clear();finish();return;}
        if(composition==null)composition=new Composition(pendingSources);
        if(!staged && composition.advance(deadline)) {
            if(composition.volume==null){GpuLightVolume.clear();finish();return;}
            if(composition.changes.empty()){finish();return;}
            GpuLightVolume.stage(composition.volume,composition.changes);staged=true;
        }
        if(staged && GpuLightVolume.advanceStaged(world,deadline))finish();
    }
    private void finish() {
        publishedSources=pendingSources;publishedPaths=new HashMap<>();
        for(Source source:publishedSources)publishedPaths.put(source,propagationCache.get(source));
        publishedArtistic=config.artisticMixing;publishedStrength=config.strength;
        // Bounds belong to a particular immutable propagation result. Map.values
        // contains uses Map.equals, which walks/boxes whole voxel maps here.
        // Reference membership both matches this cache's semantics and keeps
        // cleanup proportional to the number of sources instead of their voxels.
        retainedPaths.clear();retainedPaths.addAll(propagationCache.values());
        pathBounds.keySet().retainAll(retainedPaths);retainedPaths.clear();
        pendingSources=null;composition=null;staged=false;
        LightTransitionGate.published();
    }
    void finishPending() {advance(Long.MAX_VALUE);}
    private List<Source> discover(MinecraftClient client) {
        Vec3d position=client.player.getPos();
        if(!selectionDirty && (!selectionDependsOnPosition || position.equals(selectionPosition))) return selectedSources;
        var order=Comparator.<Source>comparingDouble(s->s.pos.getSquaredDistance(position)).thenComparingLong(s->s.pos.asLong());
        List<Source> found=NearestSelection.first(emitters.values(),order,config.maxLights);
        // If the nearest subset crosses the volume budget, the original greedy
        // algorithm can choose later candidates. Preserve that exact fallback.
        if(!fits(found)) {found=new ArrayList<>(emitters.values());found.sort(order);}
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
        selectionDependsOnPosition=selected.size()<emitters.size();
        return selectedSources=List.copyOf(selected);
    }
    private boolean fits(List<Source> sources) {
        int minX=Integer.MAX_VALUE,minY=Integer.MAX_VALUE,minZ=Integer.MAX_VALUE,maxX=Integer.MIN_VALUE,maxY=Integer.MIN_VALUE,maxZ=Integer.MIN_VALUE;
        int margin=config.radius+2;
        for(Source s:sources) {
            minX=Math.min(minX,s.pos.getX()-margin);minY=Math.min(minY,s.pos.getY()-margin);minZ=Math.min(minZ,s.pos.getZ()-margin);
            maxX=Math.max(maxX,s.pos.getX()+margin);maxY=Math.max(maxY,s.pos.getY()+margin);maxZ=Math.max(maxZ,s.pos.getZ()+margin);
        }
        return sources.isEmpty() || (long)(maxX-minX+1)*(maxY-minY+1)*(maxZ-minZ+1)<=MAX_VOLUME_VOXELS;
    }
    private boolean loaded(BlockPos pos) {
        return pos.getY()>=world.getBottomY() && pos.getY()<world.getTopY()
            && chunks.containsKey(ChunkPos.toLong(pos.getX()>>4,pos.getZ()>>4));
    }
    /** Resumable distance buckets preserve the original traversal/map order. */
    private final class Propagation {
        final Long2DoubleOpenHashMap contribution=new Long2DoubleOpenHashMap();
        final BlockPos.Mutable next=new BlockPos.Mutable();
        final int radius;
        int distance,minX=Integer.MAX_VALUE,minY=Integer.MAX_VALUE,minZ=Integer.MAX_VALUE;
        int maxX=Integer.MIN_VALUE,maxY=Integer.MIN_VALUE,maxZ=Integer.MIN_VALUE;
        Propagation(Source source) {
            propagationBuilds++;
            radius=Math.max(1,(config.radius*source.emission+14)/15);
            distances.clear();for(var queue:frontier)queue.clear();
            BlockState state=world.getBlockState(source.pos);
            if(!(state.getBlock() instanceof ColoredLightSource light) || !light.prismActive(state)){distance=radius;return;}
            long origin=source.pos.asLong();frontier[0].enqueue(origin);distances.put(origin,0);
        }
        boolean advance(long deadline) {
            int operations=0;
            while(distance<radius) {
                if((operations++&31)==0 && System.nanoTime()>=deadline)return false;
                if(frontier[distance].isEmpty()){distance++;continue;}
                long packed=frontier[distance].dequeueLong();
                if(distance!=distances.get(packed))continue;
                double falloff=1.0-(double)distance/radius;
                // Keep the same primitive-map insertion order and arithmetic.
                contribution.put(packed,falloff*falloff*sourceEmission()/15.0);
                int x=BlockPos.unpackLongX(packed),y=BlockPos.unpackLongY(packed),z=BlockPos.unpackLongZ(packed);
                minX=Math.min(minX,x);minY=Math.min(minY,y);minZ=Math.min(minZ,z);
                maxX=Math.max(maxX,x);maxY=Math.max(maxY,y);maxZ=Math.max(maxZ,z);
                for(int direction=0;direction<6;direction++) {
                    next.set(x+(direction==0?1:direction==1?-1:0),y+(direction==2?1:direction==3?-1:0),z+(direction==4?1:direction==5?-1:0));
                    if(!loaded(next))continue;
                    int optics;
                    if(reference) {
                        var cell=world.getBlockState(next);
                        optics=(cell.isOpaqueFullCube(world,next) || cell.getOpacity(world,next)>=15?1:0)|(cell.getFluidState().isEmpty()?0:2);
                    } else optics=WorldOptics.cell(world,next);
                    if(!WorldOptics.blocked(optics)) {
                        int nextDistance=distance+WorldOptics.cost(optics);long key=next.asLong();
                        if(nextDistance<radius && nextDistance<distances.get(key)){distances.put(key,nextDistance);frontier[nextDistance].enqueue(key);}
                    }
                }
            }
            if(!contribution.isEmpty())pathBounds.put(contribution,new VolumeRegion(minX,minY,minZ,maxX-minX+1,maxY-minY+1,maxZ-minZ+1));
            return true;
        }
        private int sourceEmission(){return pendingSources.get(nextSource).emission;}
    }
    private VolumeRegion bounds(Long2DoubleOpenHashMap path) {
        if(path.isEmpty())return null;
        var cached=pathBounds.get(path);if(cached!=null)return cached;
        int minX=Integer.MAX_VALUE,minY=Integer.MAX_VALUE,minZ=Integer.MAX_VALUE,maxX=Integer.MIN_VALUE,maxY=Integer.MIN_VALUE,maxZ=Integer.MIN_VALUE;
        for(long packed:path.keySet()) {
            int x=BlockPos.unpackLongX(packed),y=BlockPos.unpackLongY(packed),z=BlockPos.unpackLongZ(packed);
            minX=Math.min(minX,x);minY=Math.min(minY,y);minZ=Math.min(minZ,z);
            maxX=Math.max(maxX,x);maxY=Math.max(maxY,y);maxZ=Math.max(maxZ,z);
        }
        var result=new VolumeRegion(minX,minY,minZ,maxX-minX+1,maxY-minY+1,maxZ-minZ+1);pathBounds.put(path,result);return result;
    }
    private static boolean sameGeometry(LightVolume a,LightVolume b) {
        return a!=null && b!=null && a.x==b.x && a.y==b.y && a.z==b.z && a.width==b.width && a.height==b.height && a.depth==b.depth;
    }
    private final class Composition {
        final List<Source> sources;
        final LightVolume volume,previous;
        final VolumeChanges changes;
        final List<VolumeRegion> regions;
        final boolean artistic=config.artisticMixing;
        final float strength=config.strength;
        int phase,copyOffset,sourceIndex;
        PrimitiveIterator.OfInt cells;
        it.unimi.dsi.fastutil.objects.ObjectIterator<it.unimi.dsi.fastutil.longs.Long2DoubleMap.Entry> entries;
        double[] channels;
        Composition(List<Source> sources) {
            this.sources=sources;previous=GpuLightVolume.current();
            int minX=Integer.MAX_VALUE,minY=Integer.MAX_VALUE,minZ=Integer.MAX_VALUE,maxX=Integer.MIN_VALUE,maxY=Integer.MIN_VALUE,maxZ=Integer.MIN_VALUE;
            for(Source source:sources) {
                var b=bounds(propagationCache.get(source));if(b==null)continue;
                minX=Math.min(minX,b.x());minY=Math.min(minY,b.y());minZ=Math.min(minZ,b.z());
                maxX=Math.max(maxX,b.endX());maxY=Math.max(maxY,b.endY());maxZ=Math.max(maxZ,b.endZ());
            }
            if(minX==Integer.MAX_VALUE){volume=null;changes=null;regions=List.of();return;}
            int width=maxX-minX+2,height=maxY-minY+2,depth=maxZ-minZ+2,length=width*height*depth*4;
            outputSlot=previous!=null && previous.rgba==outputBuffers[0]?1:0;
            if(outputBuffers[outputSlot].length!=length)outputBuffers[outputSlot]=new float[length];
            volume=new LightVolume(minX-1,minY-1,minZ-1,width,height,depth,outputBuffers[outputSlot]);
            changes=new VolumeChanges(width,height,depth);
            boolean full=reference || !sameGeometry(previous,volume) || !sameGeometry(rawGeometry,volume)
                || publishedArtistic!=artistic || rawArtistic!=artistic || publishedStrength!=strength;
            if(accumulated.length!=length) {accumulated=new float[length];full=true;}
            if(full)changes.all();
            else {
                for(var old:publishedPaths.entrySet())if(propagationCache.get(old.getKey())!=old.getValue())mark(bounds(old.getValue()));
                for(Source source:sources)if(publishedPaths.get(source)!=propagationCache.get(source))mark(bounds(propagationCache.get(source)));
            }
            rawGeometry=volume;rawArtistic=artistic;regions=changes.regions();cells=changes.cells(regions);
            if(full || changes.empty())phase=1;
        }
        private void mark(VolumeRegion b) {
            if(b!=null)changes.add(b.x()-volume.x,b.y()-volume.y,b.z()-volume.z,b.endX()-volume.x,b.endY()-volume.y,b.endZ()-volume.z);
        }
        boolean advance(long deadline) {
            if(volume==null || changes.empty())return true;
            int operations=0;
            while(true) {
                if((operations++&127)==0 && System.nanoTime()>=deadline)return false;
                if(phase==0) {
                    int length=Math.min(8192,volume.rgba.length-copyOffset);
                    System.arraycopy(previous.rgba,copyOffset,volume.rgba,copyOffset,length);copyOffset+=length;
                    if(copyOffset==volume.rgba.length)phase=1;
                    if(System.nanoTime()>=deadline)return false;
                } else if(phase==1) {
                    if(!cells.hasNext()){phase=2;continue;}
                    int offset=cells.nextInt()*4;
                    for(int i=0;i<4;i++){accumulated[offset+i]=0;volume.rgba[offset+i]=0;}
                } else if(phase==2) {
                    if(entries==null) {
                        if(sourceIndex==sources.size()){cells=changes.cells(regions);phase=3;continue;}
                        Source source=sources.get(sourceIndex++);channels=ColorMixer.components(source.color,artistic);
                        entries=it.unimi.dsi.fastutil.longs.Long2DoubleMaps.fastIterator(propagationCache.get(source));
                    }
                    if(!entries.hasNext()){entries=null;continue;}
                    var entry=entries.next();long packed=entry.getLongKey();
                    int x=BlockPos.unpackLongX(packed)-volume.x,y=BlockPos.unpackLongY(packed)-volume.y,z=BlockPos.unpackLongZ(packed)-volume.z;
                    if(!changes.contains(x,y,z))continue;
                    int offset=volume.index(x,y,z);float weight=(float)entry.getDoubleValue();
                    for(int i=0;i<3;i++)accumulated[offset+i]+=channels[i]*weight;
                    accumulated[offset+3]+=weight;
                } else {
                    if(!cells.hasNext())return true;
                    int offset=cells.nextInt()*4;float energy=accumulated[offset+3];if(!(energy>0))continue;
                    ColorMixer.writeColor(accumulated[offset],accumulated[offset+1],accumulated[offset+2],energy,artistic,volume.rgba,offset);
                    float max=Math.max(volume.rgba[offset],Math.max(volume.rgba[offset+1],volume.rgba[offset+2]));
                    float coverage=(float)(1-Math.exp(-3.5*energy));
                    if(max>0)for(int i=0;i<3;i++)volume.rgba[offset+i]=(float)Math.pow(volume.rgba[offset+i]/max,2.2)*coverage*strength*1.6f;
                    volume.rgba[offset+3]=coverage;
                }
            }
        }
    }
}
