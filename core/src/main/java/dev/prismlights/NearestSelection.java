package dev.prismlights;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

/** Bounded nearest selection with the exact caller's tie-breaking order. */
public final class NearestSelection {
    private NearestSelection() {}
    public static <T> List<T> first(Iterable<T> candidates,Comparator<? super T> order,int limit) {
        if(limit<1)throw new IllegalArgumentException("Nonpositive limit");
        if(candidates instanceof java.util.Collection<T> collection && collection.size()<=limit) {
            var result=new ArrayList<T>(collection);result.sort(order);return result;
        }
        var nearest=new PriorityQueue<T>(limit,order.reversed());
        for(T candidate:candidates) {
            if(nearest.size()<limit)nearest.add(candidate);
            else if(order.compare(candidate,nearest.peek())<0){nearest.poll();nearest.add(candidate);}
        }
        var result=new ArrayList<T>(nearest);result.sort(order);return result;
    }
}
