package dev.prismlights;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class LightNibbleClampTest {
    @Test void everyPairPreservesEachIndependentNibbleAndNeverMutatesInput() {
        for(int a=0;a<256;a++)for(int b=0;b<256;b++) {
            byte[] incoming={(byte)a},old={(byte)b};
            int held=LightNibbleClamp.decreasesOnly(incoming,old)[0]&255;
            assertEquals(Math.min(a&15,b&15),held&15);
            assertEquals(Math.min(a>>>4,b>>>4),held>>>4);
            assertEquals((byte)a,incoming[0]);assertEquals((byte)b,old[0]);
        }
    }
    @Test void absentSectionIsDarkAndMalformedSectionIsRejected() {
        assertArrayEquals(new byte[2048],LightNibbleClamp.decreasesOnly(new byte[2048],null));
        assertThrows(IllegalArgumentException.class,()->LightNibbleClamp.decreasesOnly(new byte[2],new byte[1]));
    }
}
