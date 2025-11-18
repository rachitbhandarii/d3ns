package com.rachitbhandarii.d3ns.core;

import com.google.gson.Gson;

import java.security.MessageDigest;

// a unit of the blockchain that stores the dns transaction and other metadata required to ensure trust and make the blockchain tamper proof (using hash and prevHash)
public class Block {

    public int index;
    public long timestamp;
    public DnsTransaction transaction;
    public String previousHash;
    public String hash;
    public String validatorId;

    public Block(int index, long timestamp, DnsTransaction tx, String previousHash, String validatorId) {
        this.index = index;
        this.timestamp = timestamp;
        this.transaction = tx;
        this.previousHash = previousHash;
        this.validatorId = validatorId;
        this.hash = computeHash();
    }

    // hash computation helper function
    public String computeHash() {
        Gson gson = new Gson();
        String data = index +
                previousHash +
                timestamp +
                validatorId +
                gson.toJson(transaction);
        return Block.sha256(data);
    }

    // gets the sha256 hash of the data
    public static String sha256(String data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
