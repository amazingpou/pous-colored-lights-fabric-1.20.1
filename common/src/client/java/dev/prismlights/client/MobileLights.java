package dev.prismlights.client;

import dev.prismlights.MobileOcclusionMask;
import dev.prismlights.PrismFireworkEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.*;

/** Small mobile-light atlas; static lamp propagation/uploads remain independent. */
public final class MobileLights {
    public static final int LIMIT=dev.prismlights.LightSourceLimit.MOBILE_MAX;
    private static final int BUILDS_PER_TICK=2,SIZE=MobileOcclusionMask.SIZE;
    private static final Map<Integer,Entry> tracked=new LinkedHashMap<>();
    private static final Entry[] slots=new Entry[LIMIT];
    private static final FloatBuffer positions=BufferUtils.createFloatBuffer(LIMIT*4);
    private static final FloatBuffer colors=BufferUtils.createFloatBuffer(LIMIT*4);
    private static final FloatBuffer hues=BufferUtils.createFloatBuffer(LIMIT*4);
    private static final List<Entry> nearest=new ArrayList<>();
    private static final Set<Entry> selected=Collections.newSetFromMap(new IdentityHashMap<>());
    private static final FloatBuffer origins=BufferUtils.createFloatBuffer(LIMIT*4);
    private static final ByteBuffer upload=BufferUtils.createByteBuffer(MobileOcclusionMask.VOXELS);
    private static final MobileOcclusionMask.Workspace workspace=new MobileOcclusionMask.Workspace();
    private static ClientWorld world;
    private static PrismConfig config;
    private static int texture,unit=-1,count;
    private static boolean bound;
    private static int savedTexture,savedSampler;
    private static long maskBuilds,maxBuildNanos,uploads;
    private static MaskBuild pending;
    private static int remainingBuilds;
    private static final long[] buildTimes=new long[2048];
    private static final class Entry {
        final PrismFireworkEntity entity;
        int slot=-1;
        BlockPos center;
        byte[] mask,spareMask;
        final float[] hue=new float[3];
        int encodedColor;
        boolean dirty=true,uploaded;
        Entry(PrismFireworkEntity entity){this.entity=entity;encodedColor=entity.color();dev.prismlights.MobileHue.encode(encodedColor,hue);}
    }
    private MobileLights() {}
    private static void world(ClientWorld next) {
        if(world==next) return;
        tracked.clear();nearest.clear();selected.clear();Arrays.fill(slots,null);count=0;world=next;pending=null;
    }
    public static void load(ClientWorld next,PrismFireworkEntity entity) {
        world(next);tracked.put(entity.getId(),new Entry(entity));
    }
    public static void unload(ClientWorld from,int id) {
        if(world!=from) return;
        Entry entry=tracked.remove(id);
        if(entry!=null) {nearest.remove(entry);selected.remove(entry);if(pending!=null && pending.entry==entry)pending=null;}
        if(entry!=null && entry.slot>=0) slots[entry.slot]=null;
    }
    private static boolean near(BlockPos a,BlockPos b) {
        return a!=null && Math.abs(a.getX()-b.getX())<=MobileOcclusionMask.HALF && Math.abs(a.getY()-b.getY())<=MobileOcclusionMask.HALF && Math.abs(a.getZ()-b.getZ())<=MobileOcclusionMask.HALF;
    }
    public static void changed(BlockPos position) {
        if(pending!=null && near(pending.center,position)){pending.entry.dirty=true;pending=null;}
        for(Entry entry:slots) if(entry!=null && entry.center!=null
            && Math.abs(position.getX()-entry.center.getX())<=MobileOcclusionMask.HALF
            && Math.abs(position.getY()-entry.center.getY())<=MobileOcclusionMask.HALF
            && Math.abs(position.getZ()-entry.center.getZ())<=MobileOcclusionMask.HALF) entry.dirty=true;
    }
    public static void invalidateMasks() { for(Entry entry:slots) if(entry!=null) entry.dirty=true;pending=null; }
    public static void chunkChanged(ChunkPos chunk) {
        int x=chunk.getStartX(),z=chunk.getStartZ(),r=MobileOcclusionMask.HALF;
        if(pending!=null && x<=pending.center.getX()+r && x+15>=pending.center.getX()-r && z<=pending.center.getZ()+r && z+15>=pending.center.getZ()-r){pending.entry.dirty=true;pending=null;}
        for(Entry entry:slots) if(entry!=null && entry.center!=null
            && x<=entry.center.getX()+r && x+15>=entry.center.getX()-r
            && z<=entry.center.getZ()+r && z+15>=entry.center.getZ()-r) entry.dirty=true;
    }
    public static void tick(MinecraftClient client,PrismConfig options) {
        world(client.world);config=options;
        if(world==null || client.player==null || !options.enabled) {count=0;return;}
        if(tracked.isEmpty()) {count=0;return;}
        nearest.clear();nearest.addAll(tracked.values());
        nearest.removeIf(entry->entry.entity.isRemoved());
        nearest.sort(Comparator.<Entry>comparingDouble(entry->entry.entity.squaredDistanceTo(client.player))
            .thenComparingInt(entry->entry.entity.getId()));
        if(nearest.size()>LIMIT) nearest.subList(LIMIT,nearest.size()).clear();
        selected.clear();selected.addAll(nearest);
        for(int slot=0;slot<LIMIT;slot++) if(slots[slot]!=null && !selected.contains(slots[slot])) {
            slots[slot].slot=-1;slots[slot].uploaded=false;slots[slot]=null;
        }
        for(Entry entry:nearest) if(entry.slot<0) {
            for(int slot=0;slot<LIMIT;slot++) if(slots[slot]==null) {
                slots[slot]=entry;entry.slot=slot;entry.dirty=true;entry.uploaded=false;break;
            }
        }
        // New sources first; subsequent updates favor masks oldest relative
        // to the current source. At most two local masks are rebuilt per tick.
        nearest.sort(Comparator.<Entry>comparingInt(entry->entry.mask==null?0:1)
            .thenComparingDouble(entry->entry.center==null?0:-entry.center.getSquaredDistance(entry.entity.getPos())));
        if(pending!=null && !selected.contains(pending.entry))pending=null;
        remainingBuilds=BUILDS_PER_TICK;

    }
    private static final class MaskBuild {
        final Entry entry;
        final BlockPos center;
        final byte[] result;
        final MobileOcclusionMask.Builder builder;
        long nanos;
        MaskBuild(Entry entry,BlockPos center) {
            this.entry=entry;this.center=center.toImmutable();
            result=entry.spareMask==null?new byte[MobileOcclusionMask.VOXELS]:entry.spareMask;
            var minimum=center.add(-MobileOcclusionMask.HALF,-MobileOcclusionMask.HALF,-MobileOcclusionMask.HALF);
            var cursor=new BlockPos.Mutable();
            builder=new MobileOcclusionMask.Builder((x,y,z)->{
                cursor.set(minimum.getX()+x,minimum.getY()+y,minimum.getZ()+z);
                return cursor.getY()<world.getBottomY() || cursor.getY()>=world.getTopY() || !world.isChunkLoaded(cursor)
                    || WorldOptics.blocked(WorldOptics.cell(world,cursor));
            },workspace,result);
            entry.dirty=false;
        }
    }
    static boolean workPending() {
        if(world==null || config==null || !config.enabled)return false;
        for(Entry entry:nearest) {
            if(entry.entity.isRemoved() || entry.slot<0)continue;
            if(entry.mask!=null && !entry.uploaded)return true;
            if(remainingBuilds>0 && (entry.dirty || !(entry.entity.bursting()?BlockPos.ofFloored(entry.entity.burstPosition()):entry.entity.getBlockPos()).equals(entry.center)))return true;
        }
        return pending!=null && remainingBuilds>0;
    }
    static void advanceUntil(long deadline) {
        if(world==null || config==null || !config.enabled)return;
        for(Entry entry:nearest)if(entry.mask!=null && !entry.uploaded && !entry.entity.isRemoved() && entry.slot>=0) {
            if(System.nanoTime()>=deadline)return;
            ensureTexture();upload(entry);
        }

        while(remainingBuilds>0 && System.nanoTime()<deadline) {
            if(pending==null) {
                for(Entry entry:nearest) {
                    if(entry.entity.isRemoved() || entry.slot<0)continue;
                    BlockPos next=entry.entity.bursting()?BlockPos.ofFloored(entry.entity.burstPosition()):entry.entity.getBlockPos();
                    if(!next.equals(entry.center) || entry.dirty){pending=new MaskBuild(entry,next);break;}
                }
                if(pending==null)return;
            }
            long start=System.nanoTime();boolean finished=pending.builder.advance(64);pending.nanos+=System.nanoTime()-start;
            if(!finished)continue;
            if(System.nanoTime()>=deadline)return;
            Entry entry=pending.entry;entry.spareMask=entry.mask;entry.mask=pending.result;entry.center=pending.center;entry.uploaded=false;ensureTexture();upload(entry);
            buildTimes[(int)(maskBuilds%buildTimes.length)]=pending.nanos;
            maskBuilds++;maxBuildNanos=Math.max(maxBuildNanos,pending.nanos);pending=null;remainingBuilds--;
        }
    }
    public static void frame(Vec3d camera,float tickDelta) {

        count=0;positions.clear();colors.clear();hues.clear();origins.clear();
        if(world==null || config==null || !config.enabled) return;
        for(Entry entry:slots) {
            if(entry==null || entry.mask==null || entry.entity.isRemoved()) continue;
            ensureTexture();
            // Publication is scheduled with the other lighting work.

            if(!entry.uploaded) continue;
            var entity=entry.entity;float intensity=entity.lightIntensity(tickDelta);
            if(intensity<=0) continue;
            Vec3d pos=entity.bursting()?entity.burstPosition():entity.getLerpedPos(tickDelta);
            positions.put((float)(pos.x-camera.x)).put((float)(pos.y-camera.y)).put((float)(pos.z-camera.z)).put(entity.lightRadius());
            int color=entity.color();
            colors.put(((color>>16)&255)/255f).put(((color>>8)&255)/255f).put((color&255)/255f).put(intensity);
            if(color!=entry.encodedColor) {entry.encodedColor=color;dev.prismlights.MobileHue.encode(color,entry.hue);}
            hues.put(entry.hue[0]).put(entry.hue[1]).put(entry.hue[2]).put(0);
            origins.put((float)(entry.center.getX()-MobileOcclusionMask.HALF-camera.x))
                .put((float)(entry.center.getY()-MobileOcclusionMask.HALF-camera.y))
                .put((float)(entry.center.getZ()-MobileOcclusionMask.HALF-camera.z)).put(entry.slot);
            count++;
        }
        positions.flip();colors.flip();hues.flip();origins.flip();
    }
    public static int count() { return count; }
    static boolean readyBurst(int color) {
        for(Entry entry:slots) if(entry!=null && entry.uploaded && entry.entity.bursting()
            && entry.entity.color()==color && BlockPos.ofFloored(entry.entity.burstPosition()).equals(entry.center)
            && (entry.mask[MobileOcclusionMask.index(MobileOcclusionMask.HALF,MobileOcclusionMask.HALF,MobileOcclusionMask.HALF)]&255)>0)
            return true;
        return false;
    }
    static long maskBuilds() {return maskBuilds;}
    static long maxBuildNanos() {return maxBuildNanos;}
    static double buildPercentile(double fraction) {
        long[] sample=Arrays.copyOf(buildTimes,(int)Math.min(maskBuilds,buildTimes.length));
        Arrays.sort(sample);
        return sample.length==0?0:sample[Math.min(sample.length-1,(int)(sample.length*fraction))]/1e6;
    }
    static long uploads() {return uploads;}
    static void pipelineChanged() {
        restore();
        if(texture!=0 && GL11.glIsTexture(texture)) GL11.glDeleteTextures(texture);
        texture=0;
        for(Entry entry:slots) if(entry!=null) entry.uploaded=false;
    }
    private static void ensureTexture() {
        if(texture!=0) return;
        unit=Math.min(GL11.glGetInteger(GL20.GL_MAX_TEXTURE_IMAGE_UNITS),
            GL11.glGetInteger(GL20.GL_MAX_VERTEX_TEXTURE_IMAGE_UNITS))-2;
        if(unit<0 || SIZE*LIMIT>GL11.glGetInteger(GL12.GL_MAX_3D_TEXTURE_SIZE)) return;
        int oldActive=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        GL13.glActiveTexture(GL13.GL_TEXTURE0+unit);
        int old=GL11.glGetInteger(GL12.GL_TEXTURE_BINDING_3D);
        try(GpuLightVolume.PixelUnpackState ignored=new GpuLightVolume.PixelUnpackState()) {
            texture=GL11.glGenTextures();GL11.glBindTexture(GL12.GL_TEXTURE_3D,texture);
            GL11.glTexParameteri(GL12.GL_TEXTURE_3D,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_LINEAR);
            GL11.glTexParameteri(GL12.GL_TEXTURE_3D,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_LINEAR);
            GL11.glTexParameteri(GL12.GL_TEXTURE_3D,GL11.GL_TEXTURE_WRAP_S,GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL12.GL_TEXTURE_3D,GL11.GL_TEXTURE_WRAP_T,GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL12.GL_TEXTURE_3D,GL12.GL_TEXTURE_WRAP_R,GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL12.GL_TEXTURE_3D,GL12.GL_TEXTURE_MAX_LEVEL,0);
            GL12.glTexImage3D(GL12.GL_TEXTURE_3D,0,GL30.GL_R8,SIZE*LIMIT,SIZE,SIZE,0,GL11.GL_RED,GL11.GL_UNSIGNED_BYTE,(ByteBuffer)null);
            if(GL11.glGetTexLevelParameteri(GL12.GL_TEXTURE_3D,0,GL11.GL_TEXTURE_WIDTH)!=SIZE*LIMIT) {
                GL11.glDeleteTextures(texture);texture=0;
            }
        } finally {
            GL11.glBindTexture(GL12.GL_TEXTURE_3D,old);GL13.glActiveTexture(oldActive);
        }
    }
    private static void upload(Entry entry) {
        if(texture==0) return;
        int oldActive=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        GL13.glActiveTexture(GL13.GL_TEXTURE0+unit);
        int old=GL11.glGetInteger(GL12.GL_TEXTURE_BINDING_3D);
        try(GpuLightVolume.PixelUnpackState ignored=new GpuLightVolume.PixelUnpackState()) {
            upload.clear();upload.put(entry.mask).flip();
            // R8 rows are 27 bytes, unlike the aligned RGBA static volume.
            GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT,1);
            GL11.glBindTexture(GL12.GL_TEXTURE_3D,texture);
            GL12.glTexSubImage3D(GL12.GL_TEXTURE_3D,0,entry.slot*SIZE,0,0,SIZE,SIZE,SIZE,GL11.GL_RED,GL11.GL_UNSIGNED_BYTE,upload);
            entry.uploaded=true;uploads++;
        } finally {GL11.glBindTexture(GL12.GL_TEXTURE_3D,old);GL13.glActiveTexture(oldActive);}
    }
    static void bind(boolean active,int sampler,int countLocation,int positionLocation,int colorLocation,int originLocation,int hueLocation) {
        if(countLocation<0) return;
        // A valid different-unit sampler is required even while count is zero.
        ensureTexture();
        GL20.glUniform1i(countLocation,texture==0 || !active?0:count);
        GL20.glUniform1i(sampler,Math.max(0,unit));
        if(texture==0 || count==0 || !active) return;
        GL20.glUniform4fv(positionLocation,positions);GL20.glUniform4fv(colorLocation,colors);GL20.glUniform4fv(originLocation,origins);
        if(hueLocation>=0)GL20.glUniform4fv(hueLocation,hues);
        int oldActive=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        GL13.glActiveTexture(GL13.GL_TEXTURE0+unit);
        savedTexture=GL11.glGetInteger(GL12.GL_TEXTURE_BINDING_3D);savedSampler=GL30.glGetIntegeri(GL33.GL_SAMPLER_BINDING,unit);
        GL11.glBindTexture(GL12.GL_TEXTURE_3D,texture);GL33.glBindSampler(unit,0);bound=true;
        GL13.glActiveTexture(oldActive);
    }
    static void restore() {
        if(!bound) return;
        int oldActive=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        GL13.glActiveTexture(GL13.GL_TEXTURE0+unit);
        GL11.glBindTexture(GL12.GL_TEXTURE_3D,savedTexture);GL33.glBindSampler(unit,savedSampler);
        GL13.glActiveTexture(oldActive);bound=false;
    }
    static void verifyAtlas() {
        for(Entry entry:slots)if(entry!=null && entry.mask!=null) {
            byte[] expected=new byte[MobileOcclusionMask.VOXELS];var minimum=entry.center.add(-MobileOcclusionMask.HALF,-MobileOcclusionMask.HALF,-MobileOcclusionMask.HALF);
            var cursor=new BlockPos.Mutable();
            MobileOcclusionMask.build((x,y,z)->{
                cursor.set(minimum.getX()+x,minimum.getY()+y,minimum.getZ()+z);
                if(cursor.getY()<world.getBottomY() || cursor.getY()>=world.getTopY() || !world.isChunkLoaded(cursor))return true;
                var state=world.getBlockState(cursor);return state.isOpaqueFullCube(world,cursor) || state.getOpacity(world,cursor)>=15;
            },new MobileOcclusionMask.Workspace(),expected);
            if(!java.util.Arrays.equals(expected,entry.mask))throw new AssertionError("Published mobile mask differs from independent live-world geometry");
        }
        int old=GL11.glGetInteger(GL12.GL_TEXTURE_BINDING_3D);
        ByteBuffer data=BufferUtils.createByteBuffer(MobileOcclusionMask.VOXELS*LIMIT);
        try(LightingValidation.PixelPackState ignored=new LightingValidation.PixelPackState()) {
            GL11.glBindTexture(GL12.GL_TEXTURE_3D,texture);
            GL11.glGetTexImage(GL12.GL_TEXTURE_3D,0,GL11.GL_RED,GL11.GL_UNSIGNED_BYTE,data);
            for(Entry entry:slots) if(entry!=null && entry.uploaded) {
                for(int z=0;z<SIZE;z++) for(int y=0;y<SIZE;y++) for(int x=0;x<SIZE;x++) {
                    int index=(z*SIZE+y)*SIZE*LIMIT+entry.slot*SIZE+x;
                    if(data.get(index)!=entry.mask[MobileOcclusionMask.index(x,y,z)])
                        throw new AssertionError("Mobile atlas corrupted at slot "+entry.slot+" voxel "+x+","+y+","+z);
                }
            }
        } finally {GL11.glBindTexture(GL12.GL_TEXTURE_3D,old);}
    }
}
