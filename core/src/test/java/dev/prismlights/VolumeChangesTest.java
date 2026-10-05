package dev.prismlights;

import org.junit.jupiter.api.Test;
import java.util.BitSet;
import static org.junit.jupiter.api.Assertions.*;

class VolumeChangesTest {
    @Test void boundaryAndClippingCoverChangedCellsWithoutDuplicateVisits() {
        var changes=new VolumeChanges(35,19,21);
        changes.add(-2,-1,14,18,20,25);changes.add(15,0,15,17,3,18);
        var visited=new BitSet();var cells=changes.cells();
        while(cells.hasNext()) {int i=cells.nextInt();assertFalse(visited.get(i));visited.set(i);}
        for(int z=0;z<21;z++)for(int y=0;y<19;y++)for(int x=0;x<35;x++)
            assertEquals(changes.contains(x,y,z),visited.get((z*19+y)*35+x));
        assertTrue(changes.contains(17,18,20));assertFalse(changes.contains(34,18,20));
    }
    @Test void unionCatchesPreviousTextureChangesAndCopyIsIndependent() {
        var previous=new VolumeChanges(48,16,16);previous.add(0,0,0,1,1,1);
        var current=new VolumeChanges(48,16,16);current.add(47,15,15,48,16,16);
        var upload=current.copy();upload.union(previous);
        assertTrue(upload.contains(0,0,0));assertTrue(upload.contains(47,15,15));
        assertFalse(current.contains(0,0,0));assertFalse(upload.contains(20,0,0));
        assertThrows(IllegalArgumentException.class,()->upload.union(new VolumeChanges(47,16,16)));
    }
    @Test void fullGridIncludesEveryCellOnPartialBricks() {
        var changes=new VolumeChanges(17,18,3);changes.all();int count=0;
        var iterator=changes.cells();while(iterator.hasNext()){iterator.nextInt();count++;}
        assertEquals(17*18*3,count);
    }
}
