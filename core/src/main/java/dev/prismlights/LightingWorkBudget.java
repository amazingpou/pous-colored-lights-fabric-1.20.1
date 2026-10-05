package dev.prismlights;

import java.util.function.BooleanSupplier;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;

/** One soft CPU deadline per rendered frame, with fair, bounded stage slices. */
public final class LightingWorkBudget {
    public record Stage(BooleanSupplier pending, LongConsumer advance) {}
    private final LongSupplier clock;
    private final long budget, slice;
    private int next;
    private long remaining;
    public LightingWorkBudget(long budget, long slice, LongSupplier clock) {
        if (budget <= 0 || slice <= 0) throw new IllegalArgumentException("Positive budgets required");
        this.budget=budget;this.slice=slice;this.clock=clock;remaining=budget;
    }
    public void beginFrame(){remaining=budget;}
    public void run(Stage... stages) {
        if (stages.length==0) return;
        long started=clock.getAsLong(),deadline=started+remaining;
        int idle=0;
        while(clock.getAsLong()<deadline && idle<stages.length) {
            Stage stage=stages[next%stages.length];next=(next+1)%stages.length;
            if(!stage.pending.getAsBoolean()){idle++;continue;}
            idle=0;
            stage.advance.accept(Math.min(deadline,clock.getAsLong()+slice));
        }
        remaining=Math.max(0,remaining-(clock.getAsLong()-started));
    }
}
