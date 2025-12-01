package vku.chatapp.client.p2p;

import vku.chatapp.common.protocol.P2PMessage;

import java.io.ObjectOutputStream;
import java.net.Socket;

public class P2PClient {

    public boolean sendMessage(String address, int port, P2PMessage message) {
        try (Socket socket = new Socket(address, port);
             ObjectOutputStream oos = new ObjectOutputStream(socket.getOutputStream())) {

            oos.writeObject(message);
            oos.flush();
            return true;

        } catch (Exception e) {
            System.err.println("Failed to send P2P message: " + e.getMessage());
            return false;
        }
    }

    public void sendMessageAsync(String address, int port, P2PMessage message) {
        new Thread(() -> sendMessage(address, port, message)).start();
    }
}