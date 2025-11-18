package com.rachitbhandarii.d3ns.core;

import java.util.*;

// the data structure to store all the dns transactions for each server
public class Blockchain {

    private final List<Block> chain;

    public Blockchain() {
        chain = new ArrayList<>();
        chain.add(createGenesisBlock());
    }

    // the first block that would be mined
    private Block createGenesisBlock() {
        return new Block(0, System.currentTimeMillis(), null, "0", "genesis");
    }

    // top most block fetcher
    public synchronized Block getLatestBlock() {
        return chain.get(chain.size() - 1);
    }

    // helper to add block
    public synchronized boolean addBlock(Block block) {
        Block last = getLatestBlock();
        if (!last.hash.equals(block.previousHash)) return false;
        if (!block.hash.equals(block.computeHash())) return false;
        chain.add(block);
        return true;
    }

    // produces a new block
    public synchronized Block produceBlock(DnsTransaction tx, String validatorId) {
        Block prevBlock = getLatestBlock();
        return new Block(prevBlock.index + 1, System.currentTimeMillis(), tx, prevBlock.hash, validatorId);
    }

    // helps resolve the ip corresponding to the domain name if available else returns nothing
    public synchronized Optional<String> resolveDomain(String domain) {
        for (int i = chain.size() - 1; i >= 0; i--) {
            Block block = chain.get(i);
            if (block.transaction != null && block.transaction.domain().equalsIgnoreCase(domain)) {
                return Optional.of(block.transaction.ip());
            }
        }
        return Optional.empty();
    }

    public synchronized int size() {
        return chain.size();
    }

    public synchronized List<Block> getChainCopy() {
        return new ArrayList<>(chain);
    }

    // helper to syncronise the chain across all the peers
    public synchronized void replaceChain(List<Block> longer) {
        chain.clear();
        chain.addAll(longer);
    }

}
