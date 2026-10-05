package dev.prismlights;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class NearestSelectionTest {
    @Test void matchesFullSortAcrossDenseWorldsAndDistanceTies() {
        var random=new Random(894);
        record Candidate(int distance,int id){}
        var order=Comparator.comparingInt(Candidate::distance).thenComparingInt(Candidate::id);
        for(int count:new int[]{0,1,71,72,73,5000}) {
            var input=new ArrayList<Candidate>();for(int i=0;i<count;i++)input.add(new Candidate(random.nextInt(30),i));
            var expected=new ArrayList<>(input);expected.sort(order);
            assertEquals(expected.subList(0,Math.min(count,72)),NearestSelection.first(input,order,72));
        }
    }
}
