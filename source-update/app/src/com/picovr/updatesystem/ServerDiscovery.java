// Picomisu Source Update: finds update servers on the local network, so the URL never has to be
// typed (the embedded page gets no keyboard). Broadcasts "SOURCE_UPDATE_DISCOVER 1" to UDP 39031;
// tools/source-ota.py serve answers "SOURCE_UPDATE_SERVER 1 <url> <name>".
package com.picovr.updatesystem;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class ServerDiscovery {
    public static final int PORT = 39031;
    private static final byte[] DISCOVER = "SOURCE_UPDATE_DISCOVER 1".getBytes(StandardCharsets.US_ASCII);
    private static final String REPLY = "SOURCE_UPDATE_SERVER 1 ";

    public static final class Server {
        public final String url, name;

        Server(String url, String name) {
            this.url = url;
            this.name = name;
        }
    }

    private ServerDiscovery() {
    }

    /** Blocking; call off the main thread. Asks twice, collects replies for timeoutMs. */
    public static List<Server> find(int timeoutMs) {
        List<Server> found = new ArrayList<>();
        Set<String> urls = new LinkedHashSet<>();
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setBroadcast(true);
            List<InetAddress> targets = broadcastAddresses();
            long end = System.currentTimeMillis() + timeoutMs;
            long resend = System.currentTimeMillis();
            byte[] buffer = new byte[512];
            while (true) {
                long now = System.currentTimeMillis();
                if (now >= end) break;
                if (now >= resend) {
                    for (InetAddress target : targets) {
                        try {
                            socket.send(new DatagramPacket(DISCOVER, DISCOVER.length, target, PORT));
                        } catch (Exception ignored) {
                        }
                    }
                    resend = now + timeoutMs / 2;
                }
                socket.setSoTimeout((int) Math.max(1, Math.min(end, resend) - now));
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                try {
                    socket.receive(packet);
                } catch (SocketTimeoutException e) {
                    continue;
                }
                String text = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8).trim();
                if (!text.startsWith(REPLY)) continue;
                String[] parts = text.substring(REPLY.length()).split(" ", 2);
                String url = parts[0];
                if (!url.startsWith("http://") && !url.startsWith("https://")) continue;
                if (urls.add(url)) {
                    String name = parts.length > 1 ? parts[1] : packet.getAddress().getHostAddress();
                    found.add(new Server(url, name));
                }
            }
        } catch (Exception ignored) {
        }
        return found;
    }

    private static List<InetAddress> broadcastAddresses() throws Exception {
        List<InetAddress> targets = new ArrayList<>();
        for (NetworkInterface nif : Collections.list(NetworkInterface.getNetworkInterfaces())) {
            if (!nif.isUp() || nif.isLoopback()) continue;
            for (InterfaceAddress address : nif.getInterfaceAddresses()) {
                InetAddress broadcast = address.getBroadcast();
                if (broadcast != null && !targets.contains(broadcast)) targets.add(broadcast);
            }
        }
        InetAddress all = InetAddress.getByName("255.255.255.255");
        if (!targets.contains(all)) targets.add(all);
        return targets;
    }
}
