package com.rachitbhandarii.d3ns.core;

import java.util.*;

// mempool stores the unverified transactions temporarily before being stored to the blockchain
public class Mempool {

    private final Queue<DnsTransaction> queue = new LinkedList<>();

    public synchronized void add(DnsTransaction tx) {
        queue.offer(tx);
    }

    public synchronized DnsTransaction poll() {
        return queue.poll();
    }

    public synchronized boolean isEmpty() {
        return queue.isEmpty();
    }
}
