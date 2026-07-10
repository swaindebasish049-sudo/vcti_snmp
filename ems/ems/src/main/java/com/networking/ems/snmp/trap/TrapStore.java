package com.networking.ems.snmp.trap;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;

import org.springframework.stereotype.Component;

import com.networking.ems.snmp.model.TrapEvent;

/**
 * Holds the most recently received traps in memory, newest first.
 *
 * This is the demo-grade "sink" for traps: a bounded ring buffer the UI polls.
 * Swapping this for a Kafka producer (publish each trap to a topic) is the only
 * change needed to turn the receiver into a streaming pipeline -- the listener
 * that feeds it does not change.
 */
@Component
public class TrapStore {

    /** Keep the buffer bounded so a trap storm can't exhaust memory. */
    private static final int MAX_RETAINED = 500;

    private final ConcurrentLinkedDeque<TrapEvent> traps = new ConcurrentLinkedDeque<>();

    public void add(TrapEvent trap) {
        traps.addFirst(trap);
        while (traps.size() > MAX_RETAINED) {
            traps.pollLast();
        }
    }

    /** Snapshot, newest first. */
    public List<TrapEvent> list() {
        return new ArrayList<>(traps);
    }

    public int clear() {
        int n = traps.size();
        traps.clear();
        return n;
    }
}
