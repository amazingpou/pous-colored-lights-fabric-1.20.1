package dev.prismlights.client;

import dev.prismlights.LightVolume;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;
import org.slf4j.LoggerFactory;

/** Opt-in GPU regression: dirty NativeImage strides and a bound PBO must
 * neither corrupt irradiance nor leak changed upload state back to Minecraft. */
final class GpuUploadValidation {
    static void run() {
        int[] unpack={GL11.GL_UNPACK_ALIGNMENT,GL11.GL_UNPACK_ROW_LENGTH,GL12.GL_UNPACK_IMAGE_HEIGHT,
            GL11.GL_UNPACK_SKIP_PIXELS,GL11.GL_UNPACK_SKIP_ROWS,GL12.GL_UNPACK_SKIP_IMAGES,
            GL11.GL_UNPACK_SWAP_BYTES,GL11.GL_UNPACK_LSB_FIRST};
        int[] dirty={8,37,23,3,2,1,1,1};
        int[] old=new int[unpack.length];
        int buffer=GL11.glGetInteger(GL21.GL_PIXEL_UNPACK_BUFFER_BINDING);
        int temporary=GL15.glGenBuffers();
        LightVolume previous=GpuLightVolume.current();
        int activeTexture=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        int textureBinding=GL11.glGetInteger(GL12.GL_TEXTURE_BINDING_3D);
        float[] values=new float[32];
        for(int i=0;i<values.length;i++) values[i]=i/32f;
        try {
            for(int i=0;i<unpack.length;i++) {old[i]=GL11.glGetInteger(unpack[i]);GL11.glPixelStorei(unpack[i],dirty[i]);}
            GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER,temporary);
            GL15.glBufferData(GL21.GL_PIXEL_UNPACK_BUFFER,512,GL15.GL_STATIC_DRAW);
            GpuLightVolume.publish(new LightVolume(100000,100000,100000,2,2,2,values));
            if(GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE)!=activeTexture
                || GL11.glGetInteger(GL12.GL_TEXTURE_BINDING_3D)!=textureBinding)
                throw new AssertionError("Upload texture binding leaked");
            for(int i=0;i<unpack.length;i++) if(GL11.glGetInteger(unpack[i])!=dirty[i]) throw new AssertionError("Upload state leaked: "+unpack[i]);
            if(GL11.glGetInteger(GL21.GL_PIXEL_UNPACK_BUFFER_BINDING)!=temporary) throw new AssertionError("Upload PBO binding leaked");
            verifyPixels(values);
            // Alternate payloads repeatedly: exercise both persistent texture slots,
            // including glTexSubImage3D with deliberately dirty unpack/PBO state.
            var slots=new java.util.HashSet<Integer>();
            slots.add(GpuLightVolume.currentTexture());
            float[] alternate=new float[values.length];
            for(int i=0;i<alternate.length;i++) alternate[i]=1-values[i];
            for(int i=0;i<8;i++) {
                float[] expected=(i&1)==0?alternate:values;
                GpuLightVolume.publish(new LightVolume(100000,100000,100000,2,2,2,expected));
                slots.add(GpuLightVolume.currentTexture());verifyPixels(expected);
                for(int j=0;j<unpack.length;j++) if(GL11.glGetInteger(unpack[j])!=dirty[j])
                    throw new AssertionError("Reused texture upload leaked unpack state");
            }
            if(slots.size()!=2) throw new AssertionError("Repeated uploads did not reuse two texture slots: "+slots);
            verifyPartialHistory();
            verifyCooperativePublication();
            LoggerFactory.getLogger("Prism Lights").info("GPU double-buffer reuse PASS: 8 swaps, exact texels and dirty unpack/PBO state");
            verifyMaterialBindings();
            int limit=GL11.glGetInteger(GL12.GL_MAX_3D_TEXTURE_SIZE);
            GpuLightVolume.publish(new LightVolume(0,0,0,limit+1,1,1,new float[(limit+1)*4]));
            if(GpuLightVolume.current()!=null) throw new AssertionError("Oversized GPU volume did not fall back");
            LoggerFactory.getLogger("Prism Lights").info("GPU compatibility regression PASS: material sampler/texture state restored; oversized dimension uses normal luminance");
            LoggerFactory.getLogger("Prism Lights").info("GPU upload regression PASS: dirty row/image strides, offsets, byte order and PBO; exact RGB readback and state restoration");
        } finally {
            for(int i=0;i<unpack.length;i++) GL11.glPixelStorei(unpack[i],old[i]);
            GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER,buffer);GL15.glDeleteBuffers(temporary);
            if(previous!=null) GpuLightVolume.publish(previous); else GpuLightVolume.clear();
        }
    }
    private static void verifyPartialHistory() {
        int w=128,h=16,d=16;
        float[] initial=new float[w*h*d*4];java.util.Arrays.fill(initial,0.25f);
        GpuLightVolume.publish(new LightVolume(-70,-63,11,w,h,d,initial));
        float[] second=initial.clone();java.util.Arrays.fill(second,0.5f);
        GpuLightVolume.publish(new LightVolume(-70,-63,11,w,h,d,second));
        float[] current=second;
        for(int step=0;step<8;step++) {
            float[] next=current.clone();int x=step%2==0?0:112;
            var changes=new dev.prismlights.VolumeChanges(w,h,d);changes.add(x,0,0,x+16,h,d);
            for(int z=0;z<d;z++)for(int y=0;y<h;y++)for(int xx=x;xx<x+16;xx++)
                java.util.Arrays.fill(next,((z*h+y)*w+xx)*4,((z*h+y)*w+xx)*4+4,(step+1)/16f);
            GpuLightVolume.publish(new LightVolume(-70,-63,11,w,h,d,next),changes);verifyPixels(next);current=next;
        }
        LoggerFactory.getLogger("Prism Lights").info("GPU partial history PASS: distant alternating regions, 8 swaps, every texel exact");
    }
    private static void verifyCooperativePublication() {
        var world=net.minecraft.client.MinecraftClient.getInstance().world;
        int w=128,h=16,d=16;
        float[] initial=new float[w*h*d*4];java.util.Arrays.fill(initial,0.25f);
        var old=new LightVolume(-70,-63,11,w,h,d,initial);GpuLightVolume.publish(old);SkyExposure.tick(world,old);
        float[] current=initial;
        int yields=0;
        for(int step=0;step<8;step++) {
            float[] next=current.clone();int x=step%2==0?0:112;
            var changes=new dev.prismlights.VolumeChanges(w,h,d);changes.add(x,0,0,x+16,h,d);
            for(int z=0;z<d;z++)for(int y=0;y<h;y++)for(int xx=x;xx<x+16;xx++)
                java.util.Arrays.fill(next,((z*h+y)*w+xx)*4,((z*h+y)*w+xx)*4+4,(step+1)/16f);
            var before=GpuLightVolume.current();int texture=GpuLightVolume.currentTexture();
            var target=new LightVolume(-70,-63,11,w,h,d,next);GpuLightVolume.stage(target,changes);
            if(GpuLightVolume.advanceStaged(world,System.nanoTime()-1))throw new AssertionError("Expired deadline performed publication");
            int slices=0;
            while(!GpuLightVolume.advanceStaged(world,System.nanoTime()+100_000L)) {
                if(GpuLightVolume.current()!=before || GpuLightVolume.currentTexture()!=texture)
                    throw new AssertionError("Incomplete generation became visible");
                if(++slices>10000)throw new AssertionError("Cooperative publication did not finish");
                yields++;
            }
            verifyPixels(next);SkyExposure.verifyFreshSnapshot(world,target);current=next;
        }
        var before=GpuLightVolume.current();float[] canceled=current.clone();java.util.Arrays.fill(canceled,0.75f);
        // A capacity-sized hidden texture guarantees enough transfer work to
        // interrupt without relying on this driver's speed on a 32 KiB mask.
        float[] largeCanceled=new float[128*128*128*4];java.util.Arrays.fill(largeCanceled,0.75f);
        GpuLightVolume.stage(new LightVolume(-70,-63,11,128,128,128,largeCanceled),null);
        int attempts=0;
        while(!GpuLightVolume.stagedHasTexture()) {
            if(GpuLightVolume.advanceStaged(world,System.nanoTime()+100_000L))throw new AssertionError("Cancellation fixture unexpectedly finished");
            if(++attempts>10000)throw new AssertionError("Cancellation fixture never prepared a texture");
        }
        GpuLightVolume.cancelStaged();
        if(GpuLightVolume.current()!=before)throw new AssertionError("Cancellation replaced the visible field");
        verifyPixels(current);
        // Cancellation consumed an old spare; the next update must use a full
        // transfer rather than trusting its former two-generation history.
        var target=new LightVolume(-70,-63,11,w,h,d,canceled);GpuLightVolume.stage(target,null);
        if(!GpuLightVolume.advanceStaged(world,Long.MAX_VALUE))throw new AssertionError("Recovery did not finish");
        verifyPixels(canceled);SkyExposure.verifyFreshSnapshot(world,target);
        SkyExposure.verifySlicedSnapshot(world,target);
        if(yields==0)throw new AssertionError("Fixture never exercised a yielding publication");
        LoggerFactory.getLogger("Prism Lights").info("Cooperative RGB/sky publication PASS: 8 sliced swaps, hidden partial textures, cancellation/recovery, exact texels; yields={}",yields);
    }
    private static void verifyMaterialBindings() {
        int slot=Math.min(GL11.glGetInteger(GL20.GL_MAX_TEXTURE_IMAGE_UNITS),
            GL11.glGetInteger(GL20.GL_MAX_VERTEX_TEXTURE_IMAGE_UNITS))-1;
        int active=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE), program=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        GL13.glActiveTexture(GL13.GL_TEXTURE0+slot);
        int binding=GL11.glGetInteger(GL12.GL_TEXTURE_BINDING_3D);
        int sampler=GL30.glGetIntegeri(GL33.GL_SAMPLER_BINDING,slot);
        int borrowedTexture=GL11.glGenTextures(), borrowedSampler=GL33.glGenSamplers();
        GL13.glActiveTexture(GL13.GL_TEXTURE0+slot-1);
        int mobileBinding=GL11.glGetInteger(GL12.GL_TEXTURE_BINDING_3D);
        int mobileSampler=GL30.glGetIntegeri(GL33.GL_SAMPLER_BINDING,slot-1);
        int borrowedMobileTexture=GL11.glGenTextures(),borrowedMobileSampler=GL33.glGenSamplers();
        GL11.glBindTexture(GL12.GL_TEXTURE_3D,borrowedMobileTexture);GL33.glBindSampler(slot-1,borrowedMobileSampler);
        GL13.glActiveTexture(GL13.GL_TEXTURE0+slot);
        int vertex=GL20.glCreateShader(GL20.GL_VERTEX_SHADER), fragment=GL20.glCreateShader(GL20.GL_FRAGMENT_SHADER);
        int probe=GL20.glCreateProgram();
        try {
            GL20.glShaderSource(vertex,"#version 150\nuniform sampler3D prismIrradiance; uniform float prismActive; uniform sampler3D prismMobileOcclusion; uniform int prismMobileCount; void main(){gl_Position=texture(prismIrradiance,vec3(0.5))*prismActive+texture(prismMobileOcclusion,vec3(0.5))*float(prismMobileCount);}");
            GL20.glShaderSource(fragment,"#version 150\nout vec4 color; void main(){color=vec4(1.0);}");
            GL20.glCompileShader(vertex); GL20.glCompileShader(fragment);
            GL20.glAttachShader(probe,vertex); GL20.glAttachShader(probe,fragment); GL20.glLinkProgram(probe);
            if(GL20.glGetProgrami(probe,GL20.GL_LINK_STATUS)==0) throw new AssertionError(GL20.glGetProgramInfoLog(probe));
            GL11.glBindTexture(GL12.GL_TEXTURE_3D,borrowedTexture); GL33.glBindSampler(slot,borrowedSampler);
            GL20.glUseProgram(probe);
            GpuLightVolume.beginWorld(net.minecraft.util.math.Vec3d.ZERO,new org.joml.Matrix4f(),new org.joml.Matrix4f());
            GpuLightVolume.bindCurrentProgram();
            GpuLightVolume.endWorld();
            if(GL11.glGetInteger(GL12.GL_TEXTURE_BINDING_3D)!=borrowedTexture
                || GL30.glGetIntegeri(GL33.GL_SAMPLER_BINDING,slot)!=borrowedSampler
                || GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE)!=GL13.GL_TEXTURE0+slot)
                throw new AssertionError("Material draw leaked another renderer's GL bindings");
            GL13.glActiveTexture(GL13.GL_TEXTURE0+slot-1);
            if(GL11.glGetInteger(GL12.GL_TEXTURE_BINDING_3D)!=borrowedMobileTexture
                || GL30.glGetIntegeri(GL33.GL_SAMPLER_BINDING,slot-1)!=borrowedMobileSampler)
                throw new AssertionError("Mobile sampler/texture binding leaked");
            GL13.glActiveTexture(GL13.GL_TEXTURE0+slot);
        } finally {
            GpuLightVolume.endWorld(); GpuLightVolume.forgetProgram(probe);
            GL20.glUseProgram(program); GL20.glDeleteProgram(probe);
            GL20.glDeleteShader(vertex); GL20.glDeleteShader(fragment);
            GL11.glBindTexture(GL12.GL_TEXTURE_3D,binding); GL33.glBindSampler(slot,sampler);
            GL11.glDeleteTextures(borrowedTexture); GL33.glDeleteSamplers(borrowedSampler);
            GL13.glActiveTexture(GL13.GL_TEXTURE0+slot-1);
            GL11.glBindTexture(GL12.GL_TEXTURE_3D,mobileBinding);GL33.glBindSampler(slot-1,mobileSampler);
            GL11.glDeleteTextures(borrowedMobileTexture);GL33.glDeleteSamplers(borrowedMobileSampler);
            GL13.glActiveTexture(active);
        }
    }
    private static void verifyPixels(float[] expected) {
        int[] pack={GL11.GL_PACK_ALIGNMENT,GL11.GL_PACK_ROW_LENGTH,GL12.GL_PACK_IMAGE_HEIGHT,
            GL11.GL_PACK_SKIP_PIXELS,GL11.GL_PACK_SKIP_ROWS,GL12.GL_PACK_SKIP_IMAGES,
            GL11.GL_PACK_SWAP_BYTES,GL11.GL_PACK_LSB_FIRST};
        int[] previous=new int[pack.length];
        int oldTexture=GL11.glGetInteger(GL12.GL_TEXTURE_BINDING_3D);
        int oldBuffer=GL11.glGetInteger(GL21.GL_PIXEL_PACK_BUFFER_BINDING);
        try {
            for(int i=0;i<pack.length;i++){previous[i]=GL11.glGetInteger(pack[i]);GL11.glPixelStorei(pack[i],i==0?4:0);}
            GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER,0);
            GL11.glBindTexture(GL12.GL_TEXTURE_3D,GpuLightVolume.currentTexture());
            var actual=BufferUtils.createFloatBuffer(expected.length);
            GL11.glGetTexImage(GL12.GL_TEXTURE_3D,0,GL11.GL_RGBA,GL11.GL_FLOAT,actual);
            for(int i=0;i<expected.length;i++) if(actual.get(i)!=expected[i]) throw new AssertionError("Corrupt RGB texel "+i+": "+actual.get(i)+" != "+expected[i]);
        } finally {
            GL11.glBindTexture(GL12.GL_TEXTURE_3D,oldTexture);
            GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER,oldBuffer);
            for(int i=0;i<pack.length;i++) GL11.glPixelStorei(pack[i],previous[i]);
        }
    }
}
