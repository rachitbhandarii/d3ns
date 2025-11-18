package com.rachitbhandarii.d3ns.client;

import com.google.gson.Gson;
import com.rachitbhandarii.d3ns.core.JsonMessage;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.util.*;

// client that can fetch and add information to a server
public class UDPClient {

    private final Gson gson = new Gson();
    private final DatagramSocket socket;
    private final List<InetSocketAddress> peers;
    private final Random rand = new Random();

    public UDPClient(List<InetSocketAddress> peers) throws SocketException {
        this.socket = new DatagramSocket();
        this.peers = peers;
    }

    private InetSocketAddress pickRandomPeer() {
        return peers.get(rand.nextInt(peers.size()));
    }

    public String sendQuery(String domain, String ip, String type) throws Exception {
        // we pick a random peer to send packet to
        InetSocketAddress targetPeer = pickRandomPeer();

        JsonMessage msg = new JsonMessage();
        msg.type = type;

        Map<String, String> map;
        if (Objects.equals(type, "QUERY")){
            map = Map.of("domain", domain);
        } else {
            map = Map.of("domain", domain, "ip", ip);
        }
        msg.payload = gson.toJson(map);
        // send packet to the peer
        byte[] out = gson.toJson(msg).getBytes();
        DatagramPacket sendPkt = new DatagramPacket(
                out, out.length, targetPeer.getAddress(), targetPeer.getPort()
        );

        socket.send(sendPkt);
        // recv packet from the peer
        byte[] in = new byte[4096];
        DatagramPacket recvPkt = new DatagramPacket(in, in.length);
        socket.setSoTimeout(3000);
        socket.receive(recvPkt);

        return new String(recvPkt.getData(), recvPkt.getOffset(), recvPkt.getLength());
    }

    public static void main(String[] args) throws Exception {

        if (args.length < 1) {
            System.err.println("Usage:\n\t[QUERY]\tquery --domain <domain_name> --peers <peer1_address>:<peer1_port_number>,<peer2_address>:<peer2_port_number>...\n\t[ADD]\tadd --domain <domain_name> --ip <ip_address> --peers <peer1_address>:<peer1_port_number>,<peer2_address>:<peer2_port_number>...");
            return;
        }

        String command = args[0];
        if (!command.equals("query") && !command.equals("add")){
            System.err.println("Usage:\n\t[QUERY]\tquery --domain <domain_name> --peers <peer1_address>:<peer1_port_number>,<peer2_address>:<peer2_port_number>...\n\t[ADD]\tadd --domain <domain_name> --ip <ip_address> --peers <peer1_address>:<peer1_port_number>,<peer2_address>:<peer2_port_number>...");
            return;
        }

        String peersArg = "";
        String domain = "";
        String ip = "";

        for (int i = 1; i < args.length; i++) {
            switch (args[i]) {
                case "--peers" -> peersArg = args[++i];
                case "--domain" -> domain = args[++i];
                case "--ip" -> ip = args[++i];
            }
        }

        if (peersArg.isEmpty()) {
            System.err.println("Error: --peers must be provided.");
            return;
        }

        List<InetSocketAddress> peerList = new ArrayList<>();
        for (String peer : peersArg.split(",")) {
            String[] hp = peer.split(":");
            peerList.add(new InetSocketAddress(hp[0], Integer.parseInt(hp[1])));
        }

        UDPClient client = new UDPClient(peerList);

        String response = client.sendQuery(domain, ip, command.toUpperCase());
        System.out.println(response);
    }
}
