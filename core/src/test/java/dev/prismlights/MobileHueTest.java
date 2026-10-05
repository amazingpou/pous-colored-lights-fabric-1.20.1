package dev.prismlights;

import org.junit.jupiter.api.Test;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

class MobileHueTest {
    @Test void preparedHuePreservesPaletteAndRandomColorMixRepresentation() {
        var random=new Random(731);float[] result=new float[3];
        for(int i=0;i<20000;i++) {
            int color=i<256?i*0x010101:random.nextInt(0x1000000);
            MobileHue.encode(color,result);double[] reference=ColorMixer.components(color,true);
            for(int c=0;c<3;c++)assertEquals(reference[c],result[c],0.000003,"channel="+c+" color="+color);
        }
    }
}
