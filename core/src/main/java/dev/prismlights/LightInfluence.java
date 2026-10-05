package dev.prismlights;

/** Conservative horizontal bounds for the six-neighbor, finite-radius propagator. */
public final class LightInfluence {
    private LightInfluence() {}
    public static boolean touchesChunk(int sourceX,int sourceZ,int chunkX,int chunkZ,int radius) {
        long minX=(long)chunkX*16,minZ=(long)chunkZ*16;
        long dx=Math.max(0,Math.max(minX-sourceX,sourceX-(minX+15)));
        long dz=Math.max(0,Math.max(minZ-sourceZ,sourceZ-(minZ+15)));
        return dx+dz<radius;
    }
}
