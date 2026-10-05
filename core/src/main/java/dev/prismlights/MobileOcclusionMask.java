package dev.prismlights;

import java.util.Arrays;

/** Local connectivity mask. Solid voxels stay empty to protect their far face. */
public final class MobileOcclusionMask {
    public static final int HALF=13,SIZE=HALF*2+1,VOXELS=SIZE*SIZE*SIZE;
    private static final int MAX_PATH=24;
    @FunctionalInterface public interface BlockTest { boolean blocked(int x,int y,int z); }
    private MobileOcclusionMask() {}
    /** One client-thread scratch space, reusable across sources and updates. */
    public static final class Workspace {
        private final byte[] state=new byte[VOXELS];
        private final int[] queue=new int[VOXELS];
    }
    public static int index(int x,int y,int z) { return (z*SIZE+y)*SIZE+x; }
    public static byte[] build(BlockTest blocks) {
        return build(blocks,new Workspace(),new byte[VOXELS]);
    }
    public static byte[] build(BlockTest blocks,Workspace workspace,byte[] result) {
        var builder=new Builder(blocks,workspace,result);while(!builder.advance(Integer.MAX_VALUE)) {}
        return result;
    }
    /** Resumable connectivity. The result becomes visible only after completion. */
    public static final class Builder {
        private final BlockTest blocks;
        private final byte[] state,result;
        private final int[] queue;
        private int head,tail;
        public Builder(BlockTest blocks,Workspace workspace,byte[] result) {
            if(result.length!=VOXELS)throw new IllegalArgumentException("Invalid mask size");
            this.blocks=blocks;this.result=result;state=workspace.state;queue=workspace.queue;
            Arrays.fill(state,(byte)0);Arrays.fill(result,(byte)0);
            int center=index(HALF,HALF,HALF);
            if(!blocks.blocked(HALF,HALF,HALF)){state[center]=1;queue[tail++]=center;}
        }
        public boolean advance(int maximumNodes) {
            if(maximumNodes<1)throw new IllegalArgumentException("Nonpositive node budget");
            int nodes=0;
            while(head<tail && nodes++<maximumNodes) {
                int at=queue[head++],distance=(state[at]&255)-1;
                result[at]=(byte)255;if(distance>=MAX_PATH)continue;
                int x=at%SIZE,y=(at/SIZE)%SIZE,z=at/(SIZE*SIZE);
                for(int direction=0;direction<6;direction++) {
                    int nx=x+(direction==0?1:direction==1?-1:0);
                    int ny=y+(direction==2?1:direction==3?-1:0);
                    int nz=z+(direction==4?1:direction==5?-1:0);
                    if(nx<0 || ny<0 || nz<0 || nx>=SIZE || ny>=SIZE || nz>=SIZE)continue;
                    int next=index(nx,ny,nz);if(state[next]!=0)continue;
                    if(blocks.blocked(nx,ny,nz)){state[next]=(byte)255;continue;}
                    state[next]=(byte)(distance+2);queue[tail++]=next;
                }
            }
            return head==tail;
        }
    }
}
