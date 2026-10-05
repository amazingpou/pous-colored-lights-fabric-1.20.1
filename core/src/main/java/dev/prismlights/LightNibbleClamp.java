package dev.prismlights;

/** Hold only increases while allowing native light to disappear immediately. */
public final class LightNibbleClamp {
    private LightNibbleClamp() {}
    public static byte[] decreasesOnly(byte[] incoming,byte[] current) {
        if(current!=null && current.length!=incoming.length)throw new IllegalArgumentException("Mismatched light section lengths");
        byte[] result=new byte[incoming.length];
        if(current==null)return result;
        for(int i=0;i<incoming.length;i++) {
            int next=incoming[i]&255,old=current[i]&255;
            result[i]=(byte)(Math.min(next&15,old&15)|(Math.min(next>>>4,old>>>4)<<4));
        }
        return result;
    }
}
