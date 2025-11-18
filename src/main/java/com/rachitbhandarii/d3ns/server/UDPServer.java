package com.rachitbhandarii.d3ns.server;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.rachitbhandarii.d3ns.core.*;

import java.net.*;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.*;

// server is a node in a blockchain network and there can be many servers
public class UDPServer {

    // everything has been made static as per server session there can be only one blockchain and mempool and peer manager configured

    private static final Gson gson = new Gson();
    private static final Blockchain blockchain = new Blockchain();
    private static final Mempool mempool = new Mempool();
    private static final PeerManager peerManager = new PeerManager();
    private static String HOST;
    private static int PORT;
    // the public key or the public identity of the server is its host address and port
    private static String SELF_ID;

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage:\t--host <host_address> --port <port_number> --peers <peer1_address>:<peer1_port_number>,<peer2_address>:<peer2_port_number>...");
            return;
        }
        String peersArg = "";
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--host" -> HOST = args[++i];
                case "--port" -> PORT = Integer.parseInt(args[++i]);
                case "--peers" -> peersArg = args[++i];
            }
        }

        SELF_ID = HOST + ":" + PORT;

        if (!peersArg.isBlank()) {
            String[] list = peersArg.split(",");
            for (String p : list) {
                String[] hp = p.trim().split(":");
                if (hp.length != 2) continue;
                try {
                    InetSocketAddress addr = new InetSocketAddress(hp[0], Integer.parseInt(hp[1]));
                    peerManager.addPeer(addr);
                } catch (Exception ignored) {}
            }
        }

        // start a dns socket
        DatagramSocket socket;
        try {
            socket = new DatagramSocket(new InetSocketAddress(HOST, PORT));
            System.out.println("D3NS Server listening on " + HOST + ":" + PORT);
        } catch (Exception e) {
            System.out.println("Cannot bind to " + HOST + ":" + PORT + " -> " + e.getMessage());
            return;
        }

        // sync chain from peers
        requestChain(socket);

        // start PoA algorithm
        startBlockProducer(socket);

        byte[] buf = new byte[8192];
        while (true) {
            // accept packets to the socket
            DatagramPacket recvPkt = new DatagramPacket(buf, buf.length);
            try {
                socket.receive(recvPkt);
            } catch (Exception e) {
                System.out.println("Error receiving packet: " + e.getMessage());
                return;
            }

            String incoming = new String(recvPkt.getData(), recvPkt.getOffset(), recvPkt.getLength(), StandardCharsets.UTF_8);
            JsonMessage msg;
            try {
                msg = gson.fromJson(incoming, JsonMessage.class);
            } catch (Exception ex) {
                System.out.println("Corrupted packet: " + ex.getMessage());
                continue;
            }

            // handle the message received from the client by routing it to the correct helper method
            String reply = routeMessage(msg, recvPkt.getSocketAddress(), socket);

            if (reply != null) {
                byte[] out = reply.getBytes(StandardCharsets.UTF_8);
                DatagramPacket sendPkt = new DatagramPacket(out, out.length, recvPkt.getAddress(), recvPkt.getPort());
                socket.send(sendPkt);
            }
        }
    }

    private static String routeMessage(JsonMessage msg, SocketAddress sender, DatagramSocket socket) {

        return switch (msg.type) {
            case "ADD" ->
                    handleAdd(msg.payload, socket);
            case "QUERY" ->
                    handleQuery(msg.payload);
            case "TX_BROADCAST" -> {
                handleTxBroadcast(msg.payload);
                yield null;
            }
            case "BLOCK_BROADCAST" -> {
                handleBlockBroadcast(msg.payload, socket);
                yield null;
            }
            case "CHAIN_REQUEST" -> {
                sendDirectChainResponse(sender, socket);
                yield null;
            }
            case "CHAIN_RESPONSE" -> {
                handleChainResponse(msg.payload);
                yield null;
            }
            default -> gson.toJson(Map.of("status", "UNKNOWN_TYPE"));
        };
    }

    // client handlers

    // adds the block containing the domain and ip to the mempool
    private static String handleAdd(String payload, DatagramSocket socket) {
        Type type = new TypeToken<Map<String, String>>() {}.getType();
        Map<String, String> map = gson.fromJson(payload, type);

        String domain = map.get("domain");
        String ip = map.get("ip");

        if (domain == null || ip == null) return json("ERROR", "missing fields");

        DnsTransaction tx = new DnsTransaction(domain, ip);
        mempool.add(tx);

        broadcast("TX_BROADCAST", gson.toJson(tx), socket);

        return json("OK", "tx added to mempool");
    }

    // resolves the ip for a queried domain name
    private static String handleQuery(String payload) {
        Type type = new TypeToken<Map<String, String>>() {}.getType();
        Map<String, String> map = gson.fromJson(payload, type);
        String domain = map.get("domain");
        if (domain == null) return json("ERROR", "domain missing");

        Optional<String> response = blockchain.resolveDomain(domain);
        return response.map(s -> gson.toJson(Map.of("status", "FOUND", "ip", s)))
                .orElse(gson.toJson(Map.of("status", "NOT_FOUND")));
    }

    // PoA implementation

    // we start a background process that constantly handles producing blocks from mempool to the blockchain persistance using PoA
    private static void startBlockProducer(DatagramSocket socket) {
        Thread t = new Thread(() -> {
            while (true) {
                try { Thread.sleep(2000); } catch (InterruptedException ignored) {}

                if (mempool.isEmpty()) continue;
                if (!isMyTurn()) continue;

                DnsTransaction tx = mempool.poll();
                if (tx == null) continue;

                Block b = blockchain.produceBlock(tx, SELF_ID);
                boolean added = blockchain.addBlock(b);
                if (added) {
                    broadcast("BLOCK_BROADCAST", gson.toJson(b), socket);
                    System.out.println("Produced block idx=" + b.index + " validator=" + SELF_ID + " tx=" + tx);
                } else {
                    requestChain(socket);
                }
            }
        }, "block-producer-" + SELF_ID);
        t.setDaemon(true);
        t.start();
    }

    // it only happens if its this server's turn to sign the block
    private static boolean isMyTurn() {
        List<String> validators = peerManager.getValidatorsIncludingSelf(SELF_ID);
        Collections.sort(validators);
        if (validators.isEmpty()) return true;
        Block latest = blockchain.getLatestBlock();
        int nextIndex = (latest.index + 1) % validators.size();
        String nextValidator = validators.get(nextIndex);
        return SELF_ID.equals(nextValidator);
    }

    //  broadcast and handler

    // after adding the block to the local blockchain we broadcast it to other peers as well
    private static void handleTxBroadcast(String payload) {
        try {
            DnsTransaction tx = gson.fromJson(payload, DnsTransaction.class);
            mempool.add(tx);
        } catch (Exception e) {
            System.out.println("Corrupted tx: " + e.getMessage());
        }
    }

    private static void handleBlockBroadcast(String payload, DatagramSocket socket) {
        try {
            Block block = gson.fromJson(payload, Block.class);

            List<String> validators = peerManager.getValidatorsIncludingSelf(SELF_ID);
            if (!validators.contains(block.validatorId)) {
                requestChain(socket);
                return;
            }
            Block latest = blockchain.getLatestBlock();
            if (block.index > latest.index + 1) {
                requestChain(socket);
                return;
            }
            boolean ok = blockchain.addBlock(block);

            if (!ok) {
                requestChain(socket);
            } else {
                System.out.println("Appended block!");
            }
        } catch (Exception e) {
            System.out.println("Corrupted tx: " + e.getMessage());
        }
    }

    // requesting chain from the other peers
    private static void requestChain(DatagramSocket socket) {
        JsonMessage m = new JsonMessage();
        m.type = "CHAIN_REQUEST";
        m.payload = "";

        peerManager.getPeers().forEach(p -> {
            try {
                byte[] out = gson.toJson(m).getBytes(StandardCharsets.UTF_8);
                DatagramPacket pkt = new DatagramPacket(out, out.length, p);
                socket.send(pkt);
            } catch (Exception ignored) {}
        });
    }

    // handling the response received after replacing chain
    private static void handleChainResponse(String payload) {
        try {
            Type t = new TypeToken<List<Block>>() {}.getType();
            List<Block> remote = gson.fromJson(payload, t);
            if (remote.size() > blockchain.size()) {
                blockchain.replaceChain(remote);
                System.out.println("Replaced local chain with remote chain (size=" + remote.size() + ")");
            }
        } catch (Exception ignored) {}
    }

    // sending a response after replacing the chain
    private static void sendDirectChainResponse(SocketAddress addr, DatagramSocket socket) {
        try {
            JsonMessage m = new JsonMessage();
            m.type = "CHAIN_RESPONSE";
            m.payload = gson.toJson(blockchain.getChainCopy());

            byte[] out = gson.toJson(m).getBytes(StandardCharsets.UTF_8);
            DatagramPacket pkt = new DatagramPacket(out, out.length, addr);
            socket.send(pkt);

        } catch (Exception e) {
            System.out.println("Error sending direct chain response: " + e);
        }
    }

    // utilities

    // helper to broadcast any message using a udp socket
    private static void broadcast(String type, String payload, DatagramSocket socket) {
        JsonMessage msg = new JsonMessage();
        msg.type = type;
        msg.payload = payload;
        peerManager.getPeers().forEach(peer -> {
            try {
                byte[] out = gson.toJson(msg).getBytes(StandardCharsets.UTF_8);
                DatagramPacket pkt = new DatagramPacket(out, out.length, peer);
                socket.send(pkt);
            } catch (Exception ignored) {}
        });
    }

    // converting data of status and msg into json format
    private static String json(String status, String msg) {
        return gson.toJson(Map.of("status", status, "msg", msg));
    }
}
