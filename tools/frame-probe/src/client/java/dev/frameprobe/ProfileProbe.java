package dev.frameprobe;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayDeque;
import jdk.jfr.*;

/** Diagnostic-only correlation. Kept out of the production mod and timing runs. */
public final class ProfileProbe {
    public static final boolean ACTIVE=Boolean.getBoolean("frameprobe.profile");
    private static Recording recording;
    private static Frame interval;
    private static long frame;
    private static final ArrayDeque<Span> spans=new ArrayDeque<>();
    @Name("frameprobe.Interval") @Label("Rendered frame interval") @StackTrace(false)
    public static final class Frame extends Event {public String phase;public long frame;}
    @Name("frameprobe.Span") @Label("Lighting operation") @StackTrace(false)
    public static final class Span extends Event {public String phase,stage;public long frame;}
    private ProfileProbe() {}
    static void start(Path directory) {
        if(!ACTIVE)return;
        try {
            recording=new Recording(Configuration.getConfiguration("profile"));
            recording.setDestination(directory.resolve("profile.jfr"));
            recording.enable("frameprobe.Interval").withThreshold(Duration.ofMillis(12));
            recording.enable("frameprobe.Span").withThreshold(Duration.ofNanos(50_000));
            recording.enable("jdk.ExecutionSample").withPeriod(Duration.ofMillis(1));
            recording.enable("jdk.NativeMethodSample").withPeriod(Duration.ofMillis(1));
            recording.enable("jdk.ObjectAllocationSample").withStackTrace();
            recording.start();
        } catch(Exception e){throw new IllegalStateException(e);}
    }
    public static void frame() {
        if(!ACTIVE || recording==null)return;
        if(interval!=null){interval.end();interval.commit();}
        interval=new Frame();interval.phase=FrameProbe.phaseLabel();interval.frame=++frame;interval.begin();
    }
    public static void begin(String stage) {
        if(!ACTIVE || recording==null)return;
        Span event=new Span();event.phase=FrameProbe.phaseLabel();event.stage=stage;event.frame=frame;event.begin();spans.push(event);
    }
    public static void end() {
        if(!ACTIVE || recording==null)return;
        Span event=spans.pop();event.end();event.commit();
    }
    static void finish() {
        if(recording!=null){if(interval!=null){interval.end();interval.commit();}recording.stop();recording.close();recording=null;}
    }
}
