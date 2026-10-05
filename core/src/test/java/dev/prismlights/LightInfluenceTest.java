package dev.prismlights;

import org.junit.jupiter.api.Test;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

class LightInfluenceTest {
    @Test void includesNeighborAcrossNegativeChunkBorderAndExcludesUnreachableDiagonal() {
        assertTrue(LightInfluence.touchesChunk(-1,0,0,0,3));
        assertFalse(LightInfluence.touchesChunk(-3,0,0,0,3));
        assertFalse(LightInfluence.touchesChunk(-2,-2,0,0,4));
        assertTrue(LightInfluence.touchesChunk(-2,-2,0,0,5));
        assertTrue(LightInfluence.touchesChunk(-16,-16,-1,-1,3));
    }
    @Test void matchesEveryVoxelInTheChunkForRandomCoordinatesAndRadii() {
        var random=new Random(164096);
        for(int sample=0;sample<1000;sample++) {
            int sx=random.nextInt(200)-100,sz=random.nextInt(200)-100;
            int cx=random.nextInt(14)-7,cz=random.nextInt(14)-7,radius=3+random.nextInt(14);
            boolean reachable=false;
            for(int x=cx*16;x<cx*16+16;x++) for(int z=cz*16;z<cz*16+16;z++)
                reachable|=Math.abs(x-sx)+Math.abs(z-sz)<radius;
            assertEquals(reachable,LightInfluence.touchesChunk(sx,sz,cx,cz,radius));
        }
    }
}
