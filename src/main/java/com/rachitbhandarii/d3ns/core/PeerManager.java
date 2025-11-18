package com.rachitbhandarii.d3ns.core;

import java.net.InetSocketAddress;
import java.util.*;
import java.util.stream.Collectors;

// peer manager manages the syncronisation between multiple peers
public class PeerManager {

    private final Set<InetSocketAddress> peers = new HashSet<>();

    public synchronized void addPeer(InetSocketAddress peer) {
        peers.add(peer);
    }

    public synchronized Set<InetSocketAddress> getPeers() {
        return new HashSet<>(peers);
    }

    public synchronized List<String> getValidatorsIncludingSelf(String selfId) {
        Set<String> validatorSet = peers.stream().map(p -> p.getAddress().getHostAddress()+":"+p.getPort()).collect(Collectors.toSet());
        validatorSet.add(selfId);
        List<String> validators = new ArrayList<>(validatorSet);
        Collections.sort(validators);
        return validators;
    }
}
