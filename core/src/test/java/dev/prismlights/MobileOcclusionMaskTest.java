package dev.prismlights;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MobileOcclusionMaskTest {
    @org.junit.jupiter.api.Test void resumableBuildMatchesCompletedMaskThroughNarrowDoorway() {
        MobileOcclusionMask.BlockTest room=(x,y,z)->x==MobileOcclusionMask.HALF+2 && !(y==MobileOcclusionMask.HALF && z==MobileOcclusionMask.HALF+1);
        byte[] expected=MobileOcclusionMask.build(room),actual=new byte[MobileOcclusionMask.VOXELS];
        var builder=new MobileOcclusionMask.Builder(room,new MobileOcclusionMask.Workspace(),actual);
        int slices=0;while(!builder.advance(37))slices++;
        org.junit.jupiter.api.Assertions.assertTrue(slices>1);
        org.junit.jupiter.api.Assertions.assertArrayEquals(expected,actual);
    }
    @Test void reusedWorkspaceAndOutputDoNotKeepOldLightOrObstacles() {
        var workspace=new MobileOcclusionMask.Workspace();
        byte[] reused=new byte[MobileOcclusionMask.VOXELS];
        int c=MobileOcclusionMask.HALF;
        MobileOcclusionMask.build((x,y,z)->false,workspace,reused);
        assertArrayEquals(MobileOcclusionMask.build((x,y,z)->z==c+2),
            MobileOcclusionMask.build((x,y,z)->z==c+2,workspace,reused));
        MobileOcclusionMask.build((x,y,z)->true,workspace,reused);
        for(byte value:reused) assertEquals(0,value);
        assertArrayEquals(MobileOcclusionMask.build((x,y,z)->false),
            MobileOcclusionMask.build((x,y,z)->false,workspace,reused));
    }
    @Test void openRoomLightsBothSidesAndKeepsSolidCellsDark() {
        int c=MobileOcclusionMask.HALF;
        byte[] mask=MobileOcclusionMask.build((x,y,z)->y<c);
        assertEquals(255,mask[MobileOcclusionMask.index(c+7,c,c)]&255);
        assertEquals(255,mask[MobileOcclusionMask.index(c-7,c,c)]&255);
        assertEquals(0,mask[MobileOcclusionMask.index(c,c-1,c)]&255);
    }
    @Test void sealedWallPreventsLightOnFarSide() {
        int c=MobileOcclusionMask.HALF;
        byte[] mask=MobileOcclusionMask.build((x,y,z)->z==c+2);
        assertEquals(255,mask[MobileOcclusionMask.index(c,c,c+1)]&255);
        assertEquals(0,mask[MobileOcclusionMask.index(c,c,c+2)]&255);
        assertEquals(0,mask[MobileOcclusionMask.index(c,c,c+3)]&255);
    }
    @Test void openingDoorRestoresReachability() {
        int c=MobileOcclusionMask.HALF;
        byte[] mask=MobileOcclusionMask.build((x,y,z)->z==c+2 && !(x==c && y==c));
        assertTrue((mask[MobileOcclusionMask.index(c,c,c+3)]&255)>0);
    }
    @Test void SourceInsideSolidCubeDoesNotLeak() {
        byte[] mask=MobileOcclusionMask.build((x,y,z)->true);
        for(byte value:mask) assertEquals(0,value);
    }
}
