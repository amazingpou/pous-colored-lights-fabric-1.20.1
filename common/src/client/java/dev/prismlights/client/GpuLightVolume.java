package dev.prismlights.client;

import dev.prismlights.LightVolume;
import dev.prismlights.VolumeChanges;
import net.minecraft.util.math.Vec3d;
import net.minecraft.client.MinecraftClient;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;
import org.lwjgl.system.MemoryUtil;
import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.Arrays;
import org.slf4j.LoggerFactory;

/** RGB irradiance consumed during material lighting, before tone mapping/TAA. */
public final class GpuLightVolume {
    private static LightVolume volume;
    private static int texture,unit=-1;
    private static int spareTexture,width,height,depth,spareWidth,spareHeight,spareDepth;
    private static FloatBuffer uploadBuffer;
    private static LightVolume spareVolume;
    private static VolumeChanges previousChanges;
    private static boolean enabled=true,renderingWorld;
    private static boolean bindingOwned, warnedAboutUpload;
    private static int savedTexture, savedSampler;
    private static long activeBindings;
    static long activeBindings() { return activeBindings; }
    private static boolean materialSupport;
    static boolean hasMaterialSupport() {return materialSupport;}
    private static float daylight,strength=0.85f;
    private static boolean artistic=true;
    private static Vec3d camera=Vec3d.ZERO;
    private static final Matrix4f inverseView=new Matrix4f(),inverseProjection=new Matrix4f();
    private static final FloatBuffer matrixBuffer=BufferUtils.createFloatBuffer(16);
    private static final Map<Integer,Locations> locations=new HashMap<>();
    private record Locations(int sampler,int origin,int size,int active,int day,int invView,int invProjection,
        int staticActive,int strength,int artistic,int mobileSampler,int mobileCount,int mobilePositions,int mobileColors,int mobileOrigins,int skySampler,int skyActive,int mobileHues) {}
    public static void setEnabled(boolean next) { enabled=next; }
    public static void setLightOptions(float power,boolean mixing) { strength=power;artistic=mixing; }
    public static void clear() { cancelStaged();volume=null;SkyExposure.clear(); }
    public static LightVolume current() { return volume; }
    static int currentTexture() { return texture; }
    public static void beginWorld(Vec3d pos,Matrix4f view,Matrix4f projection) {
        camera=pos; inverseView.set(view).invert(); inverseProjection.set(projection).invert(); renderingWorld=true;
        var world=MinecraftClient.getInstance().world;
        daylight=world==null?0:(float)Math.max(0,Math.cos(world.getSkyAngle(1.0f)*Math.PI*2));
    }
    public static void endWorld() { restoreBinding(); renderingWorld=false;vanillaBindDepth=0; }
    public static void forgetProgram(int program) { locations.remove(program); }
    public static void forgetCurrentProgram() { forgetProgram(GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM)); }
    /** Iris destroys and recreates whole program/texture sets when shaders are
     * toggled. GL object numbers may then be reused, so neither cached uniform
     * locations nor the previous volume texture are valid across the switch. */
    public static void pipelineChanged() {
        cancelStaged();restoreBinding();
        locations.clear();
        materialSupport=false;
        MobileLights.pipelineChanged();
        SkyExposure.pipelineChanged();
        if(texture!=0 && GL11.glIsTexture(texture)) GL11.glDeleteTextures(texture);
        if(spareTexture!=0 && GL11.glIsTexture(spareTexture)) GL11.glDeleteTextures(spareTexture);
        texture=0;
        spareTexture=0;width=height=depth=spareWidth=spareHeight=spareDepth=0;
        spareVolume=null;previousChanges=null;
        volume=null;
        // The owner invalidates its composition after a pipeline reload; the
        // replacement then uses the same sliced publication as normal updates.
    }
    private static Publication pending;
    private static final int TRANSFER_FLOATS=16*16*16*4;
    private static FloatBuffer stagedBuffer;
    static void stage(LightVolume next,VolumeChanges changes) {
        cancelStaged();pending=new Publication(next,changes);
    }
    static boolean staging(){return pending!=null;}
    static boolean waitingForTerrain(){return pending!=null && pending.waitingForTerrain;}
    static boolean stagedHasTexture(){return pending!=null && pending.texture!=0;}
    static void cancelStaged() {
        if(pending!=null && pending.texture!=0)GL11.glDeleteTextures(pending.texture);
        pending=null;SkyExposure.cancelPrepared();
    }
    /** Copies/compares bounded chunks and fills only a hidden texture. */
    private static final class Publication {
        final LightVolume next;
        final VolumeChanges changes;
        int compared,texture,regionIndex;
        boolean different,initialized,waitingForTerrain;
        java.util.List<dev.prismlights.VolumeRegion> regions;
        Publication(LightVolume next,VolumeChanges changes){this.next=next;this.changes=changes;different=!sameGeometry(volume,next);}
    }
    static boolean advanceStaged(net.minecraft.client.world.ClientWorld world,long deadline) {
        Publication job=pending;if(job==null)return true;
        while(!job.different && job.compared<job.next.rgba.length) {
            if(System.nanoTime()>=deadline)return false;
            int end=Math.min(job.compared+8192,job.next.rgba.length);
            job.different=!Arrays.equals(volume.rgba,job.compared,end,job.next.rgba,job.compared,end);job.compared=end;
        }
        if(!job.different){pending=null;return true;}
        if(System.nanoTime()>=deadline)return false;
        int maximum=GL11.glGetInteger(GL12.GL_MAX_3D_TEXTURE_SIZE);
        if(job.next.width>maximum || job.next.height>maximum || job.next.depth>maximum) {
            cancelStaged();uploadUnavailable("RGB volume exceeds GL_MAX_3D_TEXTURE_SIZE="+maximum);return true;
        }
        if(stagedBuffer==null)stagedBuffer=MemoryUtil.memAllocFloat(TRANSFER_FLOATS);
        restoreBinding();
        int oldActive=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);if(unit<0)unit=reservedUnit();
        GL13.glActiveTexture(GL13.GL_TEXTURE0+unit);int oldBinding=GL11.glGetInteger(GL12.GL_TEXTURE_BINDING_3D);
        try(PixelUnpackState ignored=new PixelUnpackState()) {
            if(!job.initialized) {
                boolean reuse=spareTexture!=0 && spareWidth==job.next.width && spareHeight==job.next.height && spareDepth==job.next.depth;
                VolumeChanges transfer=new VolumeChanges(job.next.width,job.next.height,job.next.depth);
                if(reuse && job.changes!=null && sameGeometry(volume,job.next) && sameGeometry(spareVolume,job.next) && previousChanges!=null) {
                    transfer.union(job.changes);transfer.union(previousChanges);
                } else transfer.all();
                job.regions=transfer.regions();
                job.texture=spareTexture==0?GL11.glGenTextures():spareTexture;spareTexture=0;spareVolume=null;
                GL11.glBindTexture(GL12.GL_TEXTURE_3D,job.texture);
                GL11.glTexParameteri(GL12.GL_TEXTURE_3D,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_LINEAR);
                GL11.glTexParameteri(GL12.GL_TEXTURE_3D,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_LINEAR);
                GL11.glTexParameteri(GL12.GL_TEXTURE_3D,GL11.GL_TEXTURE_WRAP_S,GL12.GL_CLAMP_TO_EDGE);
                GL11.glTexParameteri(GL12.GL_TEXTURE_3D,GL11.GL_TEXTURE_WRAP_T,GL12.GL_CLAMP_TO_EDGE);
                GL11.glTexParameteri(GL12.GL_TEXTURE_3D,GL12.GL_TEXTURE_WRAP_R,GL12.GL_CLAMP_TO_EDGE);
                GL11.glTexParameteri(GL12.GL_TEXTURE_3D,GL12.GL_TEXTURE_MAX_LEVEL,0);
                if(!reuse)GL12.glTexImage3D(GL12.GL_TEXTURE_3D,0,GL30.GL_RGBA16F,job.next.width,job.next.height,job.next.depth,0,GL11.GL_RGBA,GL11.GL_FLOAT,(FloatBuffer)null);
                if(GL11.glGetTexLevelParameteri(GL12.GL_TEXTURE_3D,0,GL11.GL_TEXTURE_WIDTH)!=job.next.width) {
                    cancelStaged();uploadUnavailable("Driver could not allocate the RGB texture");return true;
                }
                job.initialized=true;
            }
            GL11.glBindTexture(GL12.GL_TEXTURE_3D,job.texture);
            while(job.regionIndex<job.regions.size()) {
                if(System.nanoTime()>=deadline)return false;
                var r=job.regions.get(job.regionIndex++);stagedBuffer.clear();
                for(int z=r.z();z<r.endZ();z++)for(int y=r.y();y<r.endY();y++)stagedBuffer.put(job.next.rgba,job.next.index(r.x(),y,z),r.width()*4);
                stagedBuffer.flip();
                GL12.glTexSubImage3D(GL12.GL_TEXTURE_3D,0,r.x(),r.y(),r.z(),r.width(),r.height(),r.depth(),GL11.GL_RGBA,GL11.GL_FLOAT,stagedBuffer);
            }
        } finally {GL11.glBindTexture(GL12.GL_TEXTURE_3D,oldBinding);GL13.glActiveTexture(oldActive);}
        if(!SkyExposure.prepare(world,job.next,deadline))return false;
        job.waitingForTerrain=!LightTransitionGate.readyToPublish();
        if(job.waitingForTerrain)return false;
        // Both textures are complete before either public input changes.
        spareTexture=texture;spareWidth=width;spareHeight=height;spareDepth=depth;spareVolume=volume;
        previousChanges=job.changes!=null && sameGeometry(volume,job.next)?job.changes.copy():null;
        texture=job.texture;volume=job.next;width=volume.width;height=volume.height;depth=volume.depth;
        job.texture=0;pending=null;SkyExposure.commitPrepared(volume);return true;
    }
    public static void publish(LightVolume next) {
        publish(next,null);
    }
    public static void publish(LightVolume next,VolumeChanges changes) {
        cancelStaged();
        if(volume!=null && volume.x==next.x && volume.y==next.y && volume.z==next.z
            && volume.width==next.width && volume.height==next.height && volume.depth==next.depth
            && Arrays.equals(volume.rgba,next.rgba)) return;
        int maxDimension=GL11.glGetInteger(GL12.GL_MAX_3D_TEXTURE_SIZE);
        if(next.width>maxDimension || next.height>maxDimension || next.depth>maxDimension) {
            uploadUnavailable("RGB volume exceeds GL_MAX_3D_TEXTURE_SIZE="+maxDimension);
            return;
        }
        restoreBinding();
        int oldActive=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        if(unit<0) unit=reservedUnit();
        GL13.glActiveTexture(GL13.GL_TEXTURE0+unit);
        int oldBinding=GL11.glGetInteger(GL12.GL_TEXTURE_BINDING_3D);
        int nextTexture=spareTexture==0?GL11.glGenTextures():spareTexture;
        boolean reuse=spareTexture!=0 && spareWidth==next.width && spareHeight==next.height && spareDepth==next.depth;
        VolumeChanges transfer=null;
        if(reuse && changes!=null && sameGeometry(volume,next) && sameGeometry(spareVolume,next) && previousChanges!=null) {
            transfer=changes.copy();transfer.union(previousChanges);
        }
        spareTexture=0;
        boolean uploaded=false;
        try (PixelUnpackState ignored=new PixelUnpackState()) {
            if(uploadBuffer==null || uploadBuffer.capacity()<next.rgba.length) {
                if(uploadBuffer!=null) MemoryUtil.memFree(uploadBuffer);
                uploadBuffer=MemoryUtil.memAllocFloat(next.rgba.length);
            }
            GL11.glBindTexture(GL12.GL_TEXTURE_3D,nextTexture);
            GL11.glTexParameteri(GL12.GL_TEXTURE_3D,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_LINEAR);
            GL11.glTexParameteri(GL12.GL_TEXTURE_3D,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_LINEAR);
            GL11.glTexParameteri(GL12.GL_TEXTURE_3D,GL11.GL_TEXTURE_WRAP_S,GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL12.GL_TEXTURE_3D,GL11.GL_TEXTURE_WRAP_T,GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL12.GL_TEXTURE_3D,GL12.GL_TEXTURE_WRAP_R,GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL12.GL_TEXTURE_3D,GL12.GL_TEXTURE_MAX_LEVEL,0);
            var regions=transfer==null?java.util.List.<dev.prismlights.VolumeRegion>of():transfer.regions();
            long cells=0;for(var region:regions)cells+=(long)region.width()*region.height()*region.depth();
            if(transfer!=null && cells*2<next.rgba.length/4) {
                for(var region:regions) {
                    uploadBuffer.clear();
                    for(int z=region.z();z<region.endZ();z++)for(int y=region.y();y<region.endY();y++)
                        uploadBuffer.put(next.rgba,next.index(region.x(),y,z),region.width()*4);
                    uploadBuffer.flip();
                    GL12.glTexSubImage3D(GL12.GL_TEXTURE_3D,0,region.x(),region.y(),region.z(),region.width(),region.height(),region.depth(),GL11.GL_RGBA,GL11.GL_FLOAT,uploadBuffer);
                }
            } else {
                uploadBuffer.clear();uploadBuffer.put(next.rgba).flip();
                if(reuse) GL12.glTexSubImage3D(GL12.GL_TEXTURE_3D,0,0,0,0,next.width,next.height,next.depth,GL11.GL_RGBA,GL11.GL_FLOAT,uploadBuffer);
                else GL12.glTexImage3D(GL12.GL_TEXTURE_3D,0,GL30.GL_RGBA16F,next.width,next.height,next.depth,0,GL11.GL_RGBA,GL11.GL_FLOAT,uploadBuffer);
            }
            if(GL11.glGetTexLevelParameteri(GL12.GL_TEXTURE_3D,0,GL11.GL_TEXTURE_WIDTH)!=next.width) {
                uploadUnavailable("Driver could not allocate the RGB texture");
                return;
            }
            int previous=texture;
            spareTexture=previous;spareWidth=width;spareHeight=height;spareDepth=depth;
            spareVolume=volume;
            previousChanges=changes!=null && sameGeometry(volume,next)?changes.copy():null;
            texture=nextTexture;volume=next;width=next.width;height=next.height;depth=next.depth;
            uploaded=true;
        } finally {
            if(!uploaded) GL11.glDeleteTextures(nextTexture);
            GL11.glBindTexture(GL12.GL_TEXTURE_3D,oldBinding); GL13.glActiveTexture(oldActive);
        }
    }
    private static boolean sameGeometry(LightVolume a,LightVolume b) {
        return a!=null && b!=null && a.x==b.x && a.y==b.y && a.z==b.z && a.width==b.width && a.height==b.height && a.depth==b.depth;
    }
    private static void uploadUnavailable(String reason) {
        volume=null;
        if(warnedAboutUpload) return;
        warnedAboutUpload=true;
        LoggerFactory.getLogger("Prism Lights").warn("{}; using normal Minecraft luminance",reason);
    }
    public static void bindCurrentProgram() { bindProgram(GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM)); }
    private static int vanillaBindDepth;
    public static void beginVanillaBind() {vanillaBindDepth++;}
    public static void endVanillaBind() {if(vanillaBindDepth>0)vanillaBindDepth--;bindCurrentProgram();}
    public static void bindProgram(int program) {
        restoreBinding();
        // Vanilla installs its material samplers after _glUseProgram. Its tail
        // binds our inputs once; direct Iris calls still use this hook normally.
        if(vanillaBindDepth>0)return;
        if(program==0) return;
        Locations loc=locations.computeIfAbsent(program,p->new Locations(
            GL20.glGetUniformLocation(p,"prismIrradiance"),GL20.glGetUniformLocation(p,"prismGridMinRel"),
            GL20.glGetUniformLocation(p,"prismGridSize"),GL20.glGetUniformLocation(p,"prismActive"),
            GL20.glGetUniformLocation(p,"prismDaylight"),
            GL20.glGetUniformLocation(p,"prismInverseView"),GL20.glGetUniformLocation(p,"prismInverseProjection"),
            GL20.glGetUniformLocation(p,"prismStaticActive"),GL20.glGetUniformLocation(p,"prismStrength"),
            GL20.glGetUniformLocation(p,"prismArtistic"),GL20.glGetUniformLocation(p,"prismMobileOcclusion"),
            GL20.glGetUniformLocation(p,"prismMobileCount"),GL20.glGetUniformLocation(p,"prismMobilePositions[0]"),
            GL20.glGetUniformLocation(p,"prismMobileColors[0]"),GL20.glGetUniformLocation(p,"prismMobileOrigins[0]"),
            GL20.glGetUniformLocation(p,"prismSkyExposure"),GL20.glGetUniformLocation(p,"prismSkyActive"),GL20.glGetUniformLocation(p,"prismMobileHues[0]")));
        if(loc.active<0) return;
        if(renderingWorld)materialSupport=true;
        // Different sampler types must never share unit 0, even when the light
        // is disabled. Keep a valid neutral 3D texture for menus and loading.
        if(texture==0) createNeutralTexture();
        GL20.glUniform1i(loc.sampler,unit);
        boolean active=renderingWorld && enabled && (volume!=null || MobileLights.count()>0);
        GL20.glUniform1f(loc.active,active?1:0);
        GL20.glUniform1f(loc.staticActive,volume!=null?1:0);
        GL20.glUniform1f(loc.strength,strength);
        GL20.glUniform1f(loc.artistic,artistic?1:0);
        MobileLights.bind(active,loc.mobileSampler,loc.mobileCount,loc.mobilePositions,loc.mobileColors,loc.mobileOrigins,loc.mobileHues);
        SkyExposure.bind(active,loc.skySampler,loc.skyActive);
        if(!active) return;
        activeBindings++;
        if(loc.day>=0) GL20.glUniform1f(loc.day,daylight);
        int oldActive=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        GL13.glActiveTexture(GL13.GL_TEXTURE0+unit);
        savedTexture=GL11.glGetInteger(GL12.GL_TEXTURE_BINDING_3D);
        savedSampler=GL30.glGetIntegeri(GL33.GL_SAMPLER_BINDING,unit);
        bindingOwned=true;
        GL11.glBindTexture(GL12.GL_TEXTURE_3D,texture); GL33.glBindSampler(unit,0); GL13.glActiveTexture(oldActive);
        if(volume!=null) {
            GL20.glUniform3f(loc.origin,(float)(volume.x-camera.x),(float)(volume.y-camera.y),(float)(volume.z-camera.z));
            GL20.glUniform3f(loc.size,volume.width,volume.height,volume.depth);
        } else GL20.glUniform3f(loc.size,1,1,1);
        if(loc.invView>=0) { matrixBuffer.clear(); inverseView.get(matrixBuffer); GL20.glUniformMatrix4fv(loc.invView,false,matrixBuffer); }
        if(loc.invProjection>=0) { matrixBuffer.clear(); inverseProjection.get(matrixBuffer); GL20.glUniformMatrix4fv(loc.invProjection,false,matrixBuffer); }
    }
    private static void createNeutralTexture() {
        int oldActive=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        if(unit<0) unit=reservedUnit();
        GL13.glActiveTexture(GL13.GL_TEXTURE0+unit);
        int oldBinding=GL11.glGetInteger(GL12.GL_TEXTURE_BINDING_3D);
        texture=GL11.glGenTextures(); GL11.glBindTexture(GL12.GL_TEXTURE_3D,texture);
        GL11.glTexParameteri(GL12.GL_TEXTURE_3D,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_LINEAR);
        GL11.glTexParameteri(GL12.GL_TEXTURE_3D,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_LINEAR);
        GL11.glTexParameteri(GL12.GL_TEXTURE_3D,GL12.GL_TEXTURE_MAX_LEVEL,0);
        try (PixelUnpackState ignored=new PixelUnpackState()) {
            GL12.glTexImage3D(GL12.GL_TEXTURE_3D,0,GL30.GL_RGBA16F,1,1,1,0,GL11.GL_RGBA,GL11.GL_FLOAT,new float[4]);
            width=height=depth=1;
        } finally {
            GL11.glBindTexture(GL12.GL_TEXTURE_3D,oldBinding);
            GL13.glActiveTexture(oldActive);
        }
    }
    private static void restoreBinding() {
        MobileLights.restore();
        SkyExposure.restore();
        if(!bindingOwned) return;
        int oldActive=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        GL13.glActiveTexture(GL13.GL_TEXTURE0+unit);
        GL11.glBindTexture(GL12.GL_TEXTURE_3D,savedTexture);
        GL33.glBindSampler(unit,savedSampler);
        GL13.glActiveTexture(oldActive);
        bindingOwned=false;
    }
    private static int reservedUnit() {
        // Keep the index within both material stages' limits, including
        // drivers with less reliable high combined-unit indices.
        return Math.min(GL11.glGetInteger(GL20.GL_MAX_TEXTURE_IMAGE_UNITS),
            GL11.glGetInteger(GL20.GL_MAX_VERTEX_TEXTURE_IMAGE_UNITS))-1;
    }
    /** NativeImage leaves row length and skip offsets behind after atlas
     * uploads. Inheriting those strides corrupts RGB volumes and can read past
     * the native buffer. A bound unpack PBO also changes pointer semantics. */
    static final class PixelUnpackState implements AutoCloseable {
        private static final int[] NAMES={GL11.GL_UNPACK_ALIGNMENT,GL11.GL_UNPACK_ROW_LENGTH,
            GL12.GL_UNPACK_IMAGE_HEIGHT,GL11.GL_UNPACK_SKIP_PIXELS,GL11.GL_UNPACK_SKIP_ROWS,
            GL12.GL_UNPACK_SKIP_IMAGES,GL11.GL_UNPACK_SWAP_BYTES,GL11.GL_UNPACK_LSB_FIRST};
        private final int[] previous=new int[NAMES.length];
        private final int buffer=GL11.glGetInteger(GL21.GL_PIXEL_UNPACK_BUFFER_BINDING);
        PixelUnpackState() {
            for(int i=0;i<NAMES.length;i++) {
                previous[i]=GL11.glGetInteger(NAMES[i]);
                GL11.glPixelStorei(NAMES[i],i==0?4:0);
            }
            GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER,0);
        }
        @Override public void close() {
            for(int i=0;i<NAMES.length;i++) GL11.glPixelStorei(NAMES[i],previous[i]);
            GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER,buffer);
        }
    }
}
