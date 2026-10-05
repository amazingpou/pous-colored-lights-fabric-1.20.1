package dev.prismlights;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.PrimitiveIterator;

/** Conservative 16-cell bricks. Unions also catch up a lagging GPU texture. */
public final class VolumeChanges {
    public static final int BRICK=16;
    private final int width,height,depth,nx,ny,nz;
    private final BitSet bricks=new BitSet();
    public VolumeChanges(int width,int height,int depth) {
        this.width=width;this.height=height;this.depth=depth;
        nx=(width+BRICK-1)/BRICK;ny=(height+BRICK-1)/BRICK;nz=(depth+BRICK-1)/BRICK;
    }
    public void all(){bricks.set(0,nx*ny*nz);}
    public boolean empty(){return bricks.isEmpty();}
    public void add(int x,int y,int z,int endX,int endY,int endZ) {
        x=Math.max(0,x);y=Math.max(0,y);z=Math.max(0,z);
        endX=Math.min(width,endX);endY=Math.min(height,endY);endZ=Math.min(depth,endZ);
        if(x>=endX || y>=endY || z>=endZ)return;
        for(int bz=z/BRICK;bz<=(endZ-1)/BRICK;bz++)for(int by=y/BRICK;by<=(endY-1)/BRICK;by++)
            bricks.set((bz*ny+by)*nx+x/BRICK,(bz*ny+by)*nx+(endX-1)/BRICK+1);
    }
    public boolean contains(int x,int y,int z){return bricks.get((z/BRICK*ny+y/BRICK)*nx+x/BRICK);}
    public VolumeChanges copy(){var result=new VolumeChanges(width,height,depth);result.bricks.or(bricks);return result;}
    public void union(VolumeChanges other) {
        if(width!=other.width || height!=other.height || depth!=other.depth)throw new IllegalArgumentException("Different grids");
        bricks.or(other.bricks);
    }
    public List<VolumeRegion> regions() {
        var result=new ArrayList<VolumeRegion>(bricks.cardinality());
        for(int i=bricks.nextSetBit(0);i>=0;i=bricks.nextSetBit(i+1)) {
            int x=i%nx*BRICK,y=i/nx%ny*BRICK,z=i/(nx*ny)*BRICK;
            result.add(new VolumeRegion(x,y,z,Math.min(BRICK,width-x),Math.min(BRICK,height-y),Math.min(BRICK,depth-z)));
        }
        return result;
    }
    public PrimitiveIterator.OfInt cells(){return cells(regions());}
    public PrimitiveIterator.OfInt cells(List<VolumeRegion> regions) {
        return new PrimitiveIterator.OfInt() {
            int region,offset;
            public boolean hasNext(){return region<regions.size();}
            public int nextInt() {
                if(!hasNext())throw new java.util.NoSuchElementException();
                var r=regions.get(region);int at=offset++;
                int index=((r.z()+at/(r.width()*r.height()))*height+r.y()+at/r.width()%r.height())*width+r.x()+at%r.width();
                if(offset==r.width()*r.height()*r.depth()){region++;offset=0;}
                return index;
            }
        };
    }
}
