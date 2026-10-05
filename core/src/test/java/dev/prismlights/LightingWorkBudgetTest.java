package dev.prismlights;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicLong;

class LightingWorkBudgetTest {
    @Test void sharesOneDeadlineAndRotatesFirstStageAcrossFrames() {
        var clock=new AtomicLong();var seen=new ArrayList<Integer>();
        var budget=new LightingWorkBudget(10,4,clock::get);
        var stages=new LightingWorkBudget.Stage[3];
        for(int i=0;i<3;i++){int index=i;stages[i]=new LightingWorkBudget.Stage(()->true,end->{seen.add(index);clock.set(end);});}
        budget.run(stages);assertEquals(10,clock.get());assertEquals(java.util.List.of(0,1,2),seen);
        // A stage exceeding its soft slice must consume the shared remainder;
        // later stages do not each receive a new whole-frame allowance.
        seen.clear();
        stages[0]=new LightingWorkBudget.Stage(()->true,end->{seen.add(0);clock.addAndGet(12);});
        budget.beginFrame();budget.run(stages);assertEquals(java.util.List.of(0),seen);
        seen.clear();budget.run(stages);assertTrue(seen.isEmpty());
        budget.beginFrame();budget.run(stages);assertEquals(1,seen.get(0));
    }
    @Test void idleStagesDoNotSpinAndFinishedStagesStopImmediately() {
        var clock=new AtomicLong();var budget=new LightingWorkBudget(10,2,clock::get);
        budget.run(new LightingWorkBudget.Stage(()->false,end->fail()));assertEquals(0,clock.get());
        boolean[] pending={true};
        budget.run(new LightingWorkBudget.Stage(()->pending[0],end->{pending[0]=false;clock.incrementAndGet();}));
        assertEquals(1,clock.get());
    }
}
