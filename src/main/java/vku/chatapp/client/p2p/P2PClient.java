package vku.chatapp.client.p2p;

import vku.chatapp.common.protocol.P2PMessage;

import java.io.ObjectOutputStream;
import java.net.Socket;

public class P2PClient {
    private static int localP2PPort = 0; // ✅ Static port reference

    // ✅ NEW: Set local P2P server port (called from MainController)
    public static void setLocalP2PPort(int port) {
        localP2PPort = port;
        System.out.println("📡 P2PClient: Local P2P port set to " + port);
    }

    public boolean sendMessage(String address, int port, P2PMessage message) {
        try (Socket socket = new Socket(address, port);
             ObjectOutputStream oos = new ObjectOutputStream(socket.getOutputStream())) {

            // ✅ CRITICAL: Always set sourcePort if not already set
            if (message.getSourcePort() == 0 && localP2PPort > 0) {
                message.setSourcePort(localP2PPort);
                System.out.println("📤 Auto-set sourcePort: " + localP2PPort);
            }

            oos.writeObject(message);
            oos.flush();
            return true;

        } catch (Exception e) {
            System.err.println("Failed to send P2P message to " + address + ":" + port + " - " + e.getMessage());
            return false;
        }
    }

    public void sendMessageAsync(String address, int port, P2PMessage message) {
        new Thread(() -> sendMessage(address, port, message)).start();
    }
}