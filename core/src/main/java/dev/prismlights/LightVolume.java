package dev.prismlights;

/** Irradiance at voxel centers: linear RGB plus colored-source coverage. */
public final class LightVolume {
    public final int x, y, z, width, height, depth;
    public final float[] rgba;
    public LightVolume(int x, int y, int z, int width, int height, int depth, float[] rgba) {
        if (width < 1 || height < 1 || depth < 1 || rgba.length != (long)width * height * depth * 4)
            throw new IllegalArgumentException("Invalid irradiance volume");
        this.x=x; this.y=y; this.z=z; this.width=width; this.height=height; this.depth=depth; this.rgba=rgba;
    }
    public int index(int px, int py, int pz) { return ((pz * height + py) * width + px) * 4; }
    public float[] sample(double px, double py, double pz) {
        double sx=px-x-0.5, sy=py-y-0.5, sz=pz-z-0.5;
        int ix=(int)Math.floor(sx), iy=(int)Math.floor(sy), iz=(int)Math.floor(sz);
        float[] result=new float[4];
        for(int dz=0;dz<2;dz++) for(int dy=0;dy<2;dy++) for(int dx=0;dx<2;dx++) {
            int xx=ix+dx, yy=iy+dy, zz=iz+dz;
            if(xx<0 || yy<0 || zz<0 || xx>=width || yy>=height || zz>=depth) continue;
            double weight=(dx==0?1-(sx-ix):sx-ix)*(dy==0?1-(sy-iy):sy-iy)*(dz==0?1-(sz-iz):sz-iz);
            int offset=index(xx,yy,zz);
            for(int channel=0;channel<4;channel++) result[channel]+=rgba[offset+channel]*weight;
        }
        return result;
    }
}
