package dev.prismlights;

/** The mobile GLSL hue representation, prepared once per synchronized color. */
public final class MobileHue {
    private MobileHue() {}
    public static void encode(int color,float[] target) {
        float r=((color>>>16)&255)/255f,g=((color>>>8)&255)/255f,b=(color&255)/255f;
        float peak=Math.max(r,Math.max(g,b)),low=Math.min(r,Math.min(g,b)),delta=peak-low,hue=0;
        if(delta>0.00001f) {
            if(peak==r) {hue=(g-b)/delta;hue-=6f*(float)Math.floor(hue/6f);}
            else if(peak==g)hue=(b-r)/delta+2;
            else hue=(r-g)/delta+4;
        }
        float angle=hue*1.0471975512f,saturation=peak>0?delta/peak:0;
        target[0]=(float)Math.cos(angle)*saturation*saturation;
        target[1]=(float)Math.sin(angle)*saturation*saturation;
        target[2]=peak;
    }
}
