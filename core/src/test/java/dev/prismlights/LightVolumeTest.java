package dev.prismlights;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LightVolumeTest {
    @Test void movingEntitySamplesAContinuousFieldBetweenCells() {
        LightVolume v=new LightVolume(-3,60,5,2,1,1,new float[]{0,0,1,1, 0,1,0,1});
        assertArrayEquals(new float[]{0,0,1,1},v.sample(-2.5,60.5,5.5),1e-6f);
        assertArrayEquals(new float[]{0,0.5f,0.5f,1},v.sample(-2,60.5,5.5),1e-6f);
        assertArrayEquals(new float[]{0,1,0,1},v.sample(-1.5,60.5,5.5),1e-6f);
        float[] left=v.sample(-2.00001,60.5,5.5),right=v.sample(-1.99999,60.5,5.5);
        assertArrayEquals(left,right,0.0001f);
    }
    @Test void gridOutsideDoesNotRepeatLightAndAllThreeAxesInterpolate() {
        float[] data=new float[2*2*2*4];
        for(int i=0;i<8;i++) {data[i*4]=i;data[i*4+3]=1;}
        LightVolume v=new LightVolume(0,0,0,2,2,2,data);
        assertArrayEquals(new float[4],v.sample(20,20,20));
        assertEquals(3.5f,v.sample(1,1,1)[0],1e-6f);
        assertEquals(1,v.sample(1,1,1)[3],1e-6f);
    }
}
