package dev.prismlights;

/** Rectangular cell range; origins may be world coordinates or volume-local. */
public record VolumeRegion(int x,int y,int z,int width,int height,int depth) {
    public VolumeRegion {
        if(width<1 || height<1 || depth<1)throw new IllegalArgumentException("Empty region");
    }
    public int endX(){return x+width;}
    public int endY(){return y+height;}
    public int endZ(){return z+depth;}
}
