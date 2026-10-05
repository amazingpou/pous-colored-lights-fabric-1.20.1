package dev.prismlights;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ColorMixerTest {
    @Test void singleHueRoundTripsItsOriginalPaletteIncludingPurpleAndMutedColors() {
        for(int color:new int[]{0x054DFF,0x8024FF,0x8991AA,0x514975,0xAD641F,0xFF75B2,0xFFFFFF}) {
            ColorMixer mix=new ColorMixer(); mix.add(color,1);
            assertArrayEquals(mix.color(false),mix.color(true),0.00001f,
                "Artistic mixing must preserve an unmixed source spectrum");
        }
    }
    @Test void duplicatingOrScalingIdenticalSourcesCannotChangeTheirHueOrSaturation() {
        for(int color:new int[]{0x054DFF,0x8024FF,0x8991AA}) {
            ColorMixer single=new ColorMixer(); single.add(color,1);
            ColorMixer dense=new ColorMixer();
            for(int i=1;i<=96;i++) dense.add(color,i*0.013);
            assertArrayEquals(single.color(true),dense.color(true),0.00001f);
        }
    }
    @Test void bluePurpleTransitionIsContinuousAndDoesNotFoldToAnotherHue() {
        float[] previous=null;
        for(int step=0;step<=100;step++) {
            ColorMixer mix=new ColorMixer();
            mix.add(0x054DFF,1-step/100.0);mix.add(0x8024FF,step/100.0);
            float[] color=mix.color(true);
            assertTrue(color[2]>color[0] && color[2]>color[1]);
            if(previous!=null) for(int c=0;c<3;c++) assertTrue(Math.abs(color[c]-previous[c])<0.02);
            previous=color;
        }
    }
    @Test void blueAndYellowMakeAControlledGreen() {
        ColorMixer mix = new ColorMixer();
        mix.add(0xFFFF05, 1); mix.add(0x054DFF, 1);
        float[] color = mix.color(true);
        assertTrue(color[1] > color[0] && color[1] > color[2]);
        assertTrue(color[0] > 0.2f && color[2] > 0.2f, "overlap should not become a saturated green spotlight");
    }

    @Test void redAndBlueMakePurpleRatherThanWhite() {
        ColorMixer mix = new ColorMixer();
        mix.add(0xFF1510, 1); mix.add(0x054DFF, 1);
        float[] color = mix.color(true);
        assertTrue(color[0] > color[1] * 1.3f);
        assertTrue(color[2] > color[1] * 1.3f);
    }

    @Test void orderWeightAndInvalidInput() {
        ColorMixer a = new ColorMixer(), b = new ColorMixer();
        a.add(0xFF1510, 0.8); a.add(0x054DFF, 0.1);
        b.add(0x054DFF, 0.1); b.add(0xFF1510, 0.8);
        assertArrayEquals(a.color(true), b.color(true), 0.00001f);
        assertTrue(a.color(true)[0] > a.color(true)[2]);
        a.add(0, Double.NaN); a.add(0, -1); a.add(0, Double.POSITIVE_INFINITY);
        assertEquals(0.9, a.energy(), 0.00001);
    }

    @Test void rgbAndArtisticModesDifferWithoutOutOfRangeChannels() {
        ColorMixer mix = new ColorMixer();
        mix.add(0xFFFF05, 1); mix.add(0x054DFF, 1);
        assertNotEquals(mix.color(false)[0], mix.color(true)[0]);
        for (boolean artistic : new boolean[]{true, false})
            for (float channel : mix.color(artistic))
                assertTrue(Float.isFinite(channel) && channel >= 0 && channel <= 1);
    }
}
