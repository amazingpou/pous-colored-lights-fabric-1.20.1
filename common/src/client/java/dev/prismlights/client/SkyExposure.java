package dev.prismlights.client;

import dev.prismlights.LightVolume;
import dev.prismlights.VolumeChanges;
import dev.prismlights.VolumeRegion;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.world.LightType;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLong;
import org.lwjgl.opengl.*;
import org.lwjgl.system.MemoryUtil;

/** Cached local skylight, independent of vertex AO and the lamp's opaque voxel. */
public final class SkyExposure {
    static boolean qaFallback;
    private static final AtomicLong revision=new AtomicLong();
    private static final java.util.concurrent.ConcurrentLinkedQueue<ChunkSectionPos> changedSections=new java.util.concurrent.ConcurrentLinkedQueue<>();
    private static volatile LightVolume bounds,preparingBounds;
    private static SkyBuild pending;
    private static boolean forceFull;
    private static boolean unavailable;
    private static int spareTexture,spareWidth,spareHeight,spareDepth;
    private static VolumeChanges previousChanges;
    private static ClientWorld world;
    private static long sampledRevision=-1,builds,uploads,lastBuildNanos;
    private static byte[] raw=new byte[0],mask=new byte[0],scratch=new byte[0];
    private static ByteBuffer buffer;
    private static int texture,unit=-1,width,height,depth,savedTexture,savedSampler;
    private static boolean bindingOwned,valid;
    static long builds() {return builds;}
    static long uploads() {return uploads;}
    static long lastBuildNanos() {return lastBuildNanos;}
    public static void changed(LightType type,ChunkSectionPos section) {
        if(type!=LightType.SKY)return;
        if(intersects(bounds,section) || intersects(preparingBounds,section)){changedSections.add(section);revision.incrementAndGet();}
    }
    private static boolean intersects(LightVolume b,ChunkSectionPos section) {
        if(b==null)return false;
        int x=section.getSectionX()*16,y=section.getSectionY()*16,z=section.getSectionZ()*16;
        return x<=b.x+b.width && x+16>=b.x && y<=b.y+b.height && y+16>=b.y && z<=b.z+b.depth && z+16>=b.z;
    }
    static void cancelPrepared() {
        if(pending!=null){if(pending.texture!=0)GL11.glDeleteTextures(pending.texture);forceFull=true;}
        pending=null;preparingBounds=null;
    }
    static void clear() {
        cancelPrepared();world=null;bounds=null;valid=false;sampledRevision=-1;
        forceFull=true;unavailable=false;previousChanges=null;changedSections.clear();
    }
    static void pipelineChanged() {
        restore();cancelPrepared();
        if(texture!=0 && GL11.glIsTexture(texture))GL11.glDeleteTextures(texture);
        if(spareTexture!=0 && GL11.glIsTexture(spareTexture))GL11.glDeleteTextures(spareTexture);
        texture=spareTexture=0;width=height=depth=spareWidth=spareHeight=spareDepth=0;
        valid=false;sampledRevision=-1;forceFull=true;
        previousChanges=null;
        unavailable=false;
    }
    static boolean workPending(ClientWorld next,LightVolume volume) {
        if(GpuLightVolume.staging())return false;
        return pending!=null || (next!=null && volume!=null && (next!=world || !sameGeometry(bounds,volume) || sampledRevision!=revision.get() || (!valid && !unavailable) || forceFull));
    }
    static void advance(ClientWorld next,LightVolume volume,long deadline) {
        if(next==null || volume==null){cancelPrepared();world=next;bounds=null;valid=false;changedSections.clear();return;}
        if(prepare(next,volume,deadline))commitPrepared(volume);
    }
    /** Synchronous completion exists only for the opt-in independent QA oracle. */
    static void tick(ClientWorld next,LightVolume volume) {advance(next,volume,Long.MAX_VALUE);}
    static boolean prepare(ClientWorld next,LightVolume volume,long deadline) {
        if(pending!=null && (pending.world!=next || pending.volume!=volume))cancelPrepared();
        if(pending==null) {
            if(next==world && (valid || unavailable) && !forceFull && sameGeometry(bounds,volume) && sampledRevision==revision.get())return true;
            if(System.nanoTime()>=deadline)return false;
            pending=new SkyBuild(next,volume);preparingBounds=volume;
        }
        return pending.advance(deadline);
    }
    static void commitPrepared(LightVolume volume) {
        if(pending!=null) {
            SkyBuild job=pending;
            if(!job.done || job.volume!=volume)throw new IllegalStateException("Incomplete skylight publication");
            byte[] old=mask;mask=scratch;scratch=old;
            if(job.texture!=0) {
                previousChanges=job.same?job.changes.copy():null;
                spareTexture=texture;spareWidth=width;spareHeight=height;spareDepth=depth;
                texture=job.texture;job.texture=0;width=volume.width;height=volume.height;depth=volume.depth;
            }
            world=job.world;sampledRevision=job.stamp;builds++;lastBuildNanos=job.nanos;
            if(job.uploaded)uploads++;
            pending=null;preparingBounds=null;forceFull=false;unavailable=job.unavailable;valid=!unavailable;
        }
        bounds=volume;
    }
    private static final class SkyBuild {
        final ClientWorld world;
        final LightVolume volume;
        final long stamp=revision.get();
        final boolean same;
        final java.util.List<VolumeRegion> regions;
        final VolumeChanges changes;
        java.util.PrimitiveIterator.OfInt cells;
        int phase,copyOffset,texture,regionIndex;
        long nanos;
        boolean different,done,uploaded,unavailable;
        long sectionKey=Long.MIN_VALUE;
        net.minecraft.world.chunk.ChunkNibbleArray data;
        final BlockPos.Mutable position=new BlockPos.Mutable();
        SkyBuild(ClientWorld world,LightVolume volume) {
            this.world=world;this.volume=volume;
            same=SkyExposure.world==world && sameGeometry(bounds,volume) && raw.length==volume.width*volume.height*volume.depth;
            int length=volume.width*volume.height*volume.depth;
            if(raw.length!=length){raw=new byte[length];mask=new byte[length];scratch=new byte[length];}
            changes=new VolumeChanges(volume.width,volume.height,volume.depth);
            ChunkSectionPos section;int drained=0;
            while(drained++<1024 && (section=changedSections.poll())!=null) {
                int x=section.getSectionX()*16-volume.x,y=section.getSectionY()*16-volume.y,z=section.getSectionZ()*16-volume.z;
                changes.add(x-1,y-1,z-1,x+17,y+17,z+17);
            }
            if(!same || sampledRevision<0 || !valid || forceFull || changes.empty() || !changedSections.isEmpty())changes.all();
            regions=changes.regions();cells=changes.cells(regions);
            if(!same)phase=1;
            different=!same || !valid || width!=volume.width || height!=volume.height || depth!=volume.depth;
        }
        boolean advance(long deadline) {
            long start=System.nanoTime();
            try {
                var provider=world.getLightingProvider().get(LightType.SKY);
                int row=volume.width,plane=row*volume.height,operations=0;
                while(!done) {
                    if((operations++&127)==0 && System.nanoTime()>=deadline)return false;
                    if(phase==0) {
                        int length=Math.min(8192,mask.length-copyOffset);
                        System.arraycopy(mask,copyOffset,scratch,copyOffset,length);copyOffset+=length;
                        if(copyOffset==mask.length)phase=1;
                        if(System.nanoTime()>=deadline)return false;
                    } else if(phase==1) {
                        if(!cells.hasNext()){cells=changes.cells(regions);phase=2;continue;}
                        int i=cells.nextInt(),x=i%row+volume.x,y=i/row%volume.height+volume.y,z=i/plane+volume.z;
                        long key=ChunkSectionPos.asLong(x>>4,y>>4,z>>4);
                        if(key!=sectionKey){data=provider.getLightSection(ChunkSectionPos.from(x>>4,y>>4,z>>4));sectionKey=key;}
                        int value=data==null?provider.getLightLevel(position.set(x,y,z)):data.get(x&15,y&15,z&15);
                        raw[i]=(byte)(value*17);
                    } else if(phase==2) {
                        if(!cells.hasNext()){phase=3;continue;}
                        int i=cells.nextInt(),x=i%row,y=i/row%volume.height,z=i/plane,value=raw[i]&255;
                        if(value==0) {
                            if(x>0)value=Math.max(value,raw[i-1]&255);if(x+1<row)value=Math.max(value,raw[i+1]&255);
                            if(z>0)value=Math.max(value,raw[i-plane]&255);if(z+1<volume.depth)value=Math.max(value,raw[i+plane]&255);
                            if(y>0)value=Math.max(value,raw[i-row]&255);if(y+1<volume.height)value=Math.max(value,raw[i+row]&255);
                        }
                        scratch[i]=(byte)value;if(scratch[i]!=mask[i])different=true;
                    } else {
                        if(!different){done=true;continue;}
                        if(!uploadNext(deadline))return false;
                        done=true;
                    }
                }
                return true;
            } finally {nanos+=System.nanoTime()-start;}
        }
        private boolean uploadNext(long deadline) {
            restore();ensureUnit();if(unit<0){unavailable=true;return true;}
            int maximum=GL11.glGetInteger(GL12.GL_MAX_3D_TEXTURE_SIZE);
            if(volume.width>maximum || volume.height>maximum || volume.depth>maximum){unavailable=true;return true;}
            if(buffer==null || buffer.capacity()<4096){if(buffer!=null)MemoryUtil.memFree(buffer);buffer=MemoryUtil.memAlloc(4096);}
            int oldActive=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);GL13.glActiveTexture(GL13.GL_TEXTURE0+unit);
            int oldBinding=GL11.glGetInteger(GL12.GL_TEXTURE_BINDING_3D);
            try(GpuLightVolume.PixelUnpackState ignored=new GpuLightVolume.PixelUnpackState()) {
                GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT,1);
                if(texture==0) {
                    boolean reuse=spareTexture!=0 && spareWidth==volume.width && spareHeight==volume.height && spareDepth==volume.depth;
                    texture=spareTexture==0?GL11.glGenTextures():spareTexture;spareTexture=0;
                    GL11.glBindTexture(GL12.GL_TEXTURE_3D,texture);
                    GL11.glTexParameteri(GL12.GL_TEXTURE_3D,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_LINEAR);
                    GL11.glTexParameteri(GL12.GL_TEXTURE_3D,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_LINEAR);
                    GL11.glTexParameteri(GL12.GL_TEXTURE_3D,GL11.GL_TEXTURE_WRAP_S,GL12.GL_CLAMP_TO_EDGE);
                    GL11.glTexParameteri(GL12.GL_TEXTURE_3D,GL11.GL_TEXTURE_WRAP_T,GL12.GL_CLAMP_TO_EDGE);
                    GL11.glTexParameteri(GL12.GL_TEXTURE_3D,GL12.GL_TEXTURE_WRAP_R,GL12.GL_CLAMP_TO_EDGE);
                    GL11.glTexParameteri(GL12.GL_TEXTURE_3D,GL12.GL_TEXTURE_MAX_LEVEL,0);
                    if(!reuse)GL12.glTexImage3D(GL12.GL_TEXTURE_3D,0,GL30.GL_R8,volume.width,volume.height,volume.depth,0,GL11.GL_RED,GL11.GL_UNSIGNED_BYTE,(ByteBuffer)null);
                    // Sky has its own publication history; RGB may have many
                    // color-only generations between two sky changes.
                    var transfer=new VolumeChanges(volume.width,volume.height,volume.depth);
                    if(reuse && same && previousChanges!=null){transfer.union(changes);transfer.union(previousChanges);}
                    else transfer.all();
                    uploadRegions=transfer.regions();
                }
                GL11.glBindTexture(GL12.GL_TEXTURE_3D,texture);
                while(regionIndex<uploadRegions.size()) {
                    if(System.nanoTime()>=deadline)return false;
                    var r=uploadRegions.get(regionIndex++);buffer.clear();
                    for(int z=r.z();z<r.endZ();z++)for(int y=r.y();y<r.endY();y++)buffer.put(scratch,(z*volume.height+y)*volume.width+r.x(),r.width());
                    buffer.flip();GL12.glTexSubImage3D(GL12.GL_TEXTURE_3D,0,r.x(),r.y(),r.z(),r.width(),r.height(),r.depth(),GL11.GL_RED,GL11.GL_UNSIGNED_BYTE,buffer);
                }
                if(GL11.glGetTexLevelParameteri(GL12.GL_TEXTURE_3D,0,GL11.GL_TEXTURE_WIDTH)!=volume.width) {
                    GL11.glDeleteTextures(texture);texture=0;unavailable=true;return true;
                }
                uploaded=true;return true;
            } finally {GL11.glBindTexture(GL12.GL_TEXTURE_3D,oldBinding);GL13.glActiveTexture(oldActive);}
        }
        java.util.List<VolumeRegion> uploadRegions;
    }
    private static boolean sameGeometry(LightVolume a,LightVolume b) {
        return a!=null && a.x==b.x && a.y==b.y && a.z==b.z && a.width==b.width && a.height==b.height && a.depth==b.depth;
    }
    static void verifySlicedSnapshot(ClientWorld next,LightVolume volume) {
        tick(next,volume);int previousTexture=texture;LightVolume previousBounds=bounds;
        forceFull=true;
        pending=new SkyBuild(next,volume);preparingBounds=volume;pending.different=true;
        int attempts=0;
        while(pending==null || pending.texture==0) {
            // A ready hidden texture is also cancellable before commit. Do not
            // mistake a fast upload for a runtime failure.
            prepare(next,volume,System.nanoTime()+100_000L);
            if(texture!=previousTexture || bounds!=previousBounds || !valid)throw new AssertionError("Partial sky replaced the published input");
            if(++attempts>10000)throw new AssertionError("Sky cancellation fixture did not reach upload");
        }
        cancelPrepared();
        if(texture!=previousTexture || bounds!=previousBounds || !valid)throw new AssertionError("Sky cancellation lost the published input");
        pending=new SkyBuild(next,volume);preparingBounds=volume;pending.different=true;
        attempts=0;
        while(!prepare(next,volume,System.nanoTime()+100_000L)) {
            if(texture!=previousTexture || bounds!=previousBounds || !valid)throw new AssertionError("Sky recovery exposed partial texels");
            if(++attempts>10000)throw new AssertionError("Sky recovery did not finish");
        }
        commitPrepared(volume);verifyFreshSnapshot(next,volume);
        org.slf4j.LoggerFactory.getLogger("Prism Lights").info("Cooperative sky cancellation PASS: old texture retained, raw resampled, exact GPU readback; slices={}",attempts);
    }
    static void verifyReloadSnapshot(ClientWorld next,LightVolume volume) {
        if(volume==null)return;
        clear();
        if(world!=null || bounds!=null || pending!=null)throw new AssertionError("Skylight retained an unloaded world");
        pipelineChanged();
        // Simulate a menu/material binding creating its typed neutral sampler
        // before the next world sample. Matching CPU masks must still resize it.
        int old=GL11.glGetInteger(GL12.GL_TEXTURE_BINDING_3D);texture=GL11.glGenTextures();
        try(GpuLightVolume.PixelUnpackState ignored=new GpuLightVolume.PixelUnpackState()) {
            GL11.glBindTexture(GL12.GL_TEXTURE_3D,texture);
            GL12.glTexImage3D(GL12.GL_TEXTURE_3D,0,GL30.GL_R8,1,1,1,0,GL11.GL_RED,GL11.GL_UNSIGNED_BYTE,(ByteBuffer)null);
        } finally {GL11.glBindTexture(GL12.GL_TEXTURE_3D,old);}
        width=height=depth=1;
        verifyFreshSnapshot(next,volume);
        if(width!=volume.width || height!=volume.height || depth!=volume.depth)throw new AssertionError("Skylight neutral sampler was not resized after reload");
    }
    /** Independent full-world oracle and GPU readback, restricted to opt-in QA. */
    static void verifyFreshSnapshot(ClientWorld next,LightVolume volume) {
        if(volume==null)return;
        tick(next,volume);
        byte[] expectedRaw=new byte[mask.length],expected=new byte[mask.length];
        var provider=next.getLightingProvider().get(LightType.SKY);var pos=new BlockPos.Mutable();
        int row=volume.width,plane=row*volume.height;
        for(int z=0;z<volume.depth;z++)for(int y=0;y<volume.height;y++)for(int x=0;x<row;x++)
            expectedRaw[(z*volume.height+y)*row+x]=(byte)(17*provider.getLightLevel(pos.set(volume.x+x,volume.y+y,volume.z+z)));
        for(int z=0;z<volume.depth;z++)for(int y=0;y<volume.height;y++)for(int x=0;x<row;x++) {
            int i=(z*volume.height+y)*row+x,value=expectedRaw[i]&255;
            if(value==0) {
                if(x>0)value=Math.max(value,expectedRaw[i-1]&255);if(x+1<row)value=Math.max(value,expectedRaw[i+1]&255);
                if(y>0)value=Math.max(value,expectedRaw[i-row]&255);if(y+1<volume.height)value=Math.max(value,expectedRaw[i+row]&255);
                if(z>0)value=Math.max(value,expectedRaw[i-plane]&255);if(z+1<volume.depth)value=Math.max(value,expectedRaw[i+plane]&255);
            }
            expected[i]=(byte)value;
        }
        if(!Arrays.equals(expected,mask))throw new AssertionError("Partial skylight differs from direct full sampling");
        int old=GL11.glGetInteger(GL12.GL_TEXTURE_BINDING_3D);
        var readback=org.lwjgl.BufferUtils.createByteBuffer(mask.length);
        try(LightingValidation.PixelPackState ignored=new LightingValidation.PixelPackState()) {
            GL11.glBindTexture(GL12.GL_TEXTURE_3D,texture);GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT,1);
            GL11.glGetTexImage(GL12.GL_TEXTURE_3D,0,GL11.GL_RED,GL11.GL_UNSIGNED_BYTE,readback);
            for(int i=0;i<mask.length;i++)if(readback.get(i)!=mask[i])throw new AssertionError("Skylight GPU texel differs at "+i);
        } finally {GL11.glBindTexture(GL12.GL_TEXTURE_3D,old);}
    }
    private static void ensureUnit() {
        if(unit<0)unit=Math.min(GL11.glGetInteger(GL20.GL_MAX_TEXTURE_IMAGE_UNITS),GL11.glGetInteger(GL20.GL_MAX_VERTEX_TEXTURE_IMAGE_UNITS))-3;
    }
    static void bind(boolean active,int sampler,int activeLocation) {
        if(sampler<0)return;
        ensureUnit();if(unit<0) {GL20.glUniform1f(activeLocation,0);return;}
        if(texture==0) {
            // A valid typed texture also keeps disabled/menu samplers separate.
            int old=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);GL13.glActiveTexture(GL13.GL_TEXTURE0+unit);
            int oldBinding=GL11.glGetInteger(GL12.GL_TEXTURE_BINDING_3D);texture=GL11.glGenTextures();
            GL11.glBindTexture(GL12.GL_TEXTURE_3D,texture);
            try(GpuLightVolume.PixelUnpackState ignored=new GpuLightVolume.PixelUnpackState()) {
                GL12.glTexImage3D(GL12.GL_TEXTURE_3D,0,GL30.GL_R8,1,1,1,0,GL11.GL_RED,GL11.GL_UNSIGNED_BYTE,(ByteBuffer)null);
            }
            GL11.glBindTexture(GL12.GL_TEXTURE_3D,oldBinding);GL13.glActiveTexture(old);width=height=depth=1;
        }
        GL20.glUniform1i(sampler,unit);GL20.glUniform1f(activeLocation,active && valid && !qaFallback && bounds==GpuLightVolume.current()?1:0);
        int old=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);GL13.glActiveTexture(GL13.GL_TEXTURE0+unit);
        savedTexture=GL11.glGetInteger(GL12.GL_TEXTURE_BINDING_3D);savedSampler=GL30.glGetIntegeri(GL33.GL_SAMPLER_BINDING,unit);
        GL11.glBindTexture(GL12.GL_TEXTURE_3D,texture);GL33.glBindSampler(unit,0);bindingOwned=true;GL13.glActiveTexture(old);
    }
    static void restore() {
        if(!bindingOwned)return;
        int old=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);GL13.glActiveTexture(GL13.GL_TEXTURE0+unit);
        GL11.glBindTexture(GL12.GL_TEXTURE_3D,savedTexture);GL33.glBindSampler(unit,savedSampler);GL13.glActiveTexture(old);bindingOwned=false;
    }
}
