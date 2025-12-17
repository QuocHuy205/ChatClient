package vku.chatapp.client.p2p;

import vku.chatapp.common.protocol.P2PMessage;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class P2PServer {
    private ServerSocket serverSocket;
    private int port;
    private boolean running;
    private ExecutorService executorService;
    private P2PMessageHandler messageHandler;

    public P2PServer(P2PMessageHandler messageHandler) {
        this.messageHandler = messageHandler;
        this.executorService = Executors.newCachedThreadPool();
    }

    public void start(int preferredPort) throws IOException {
        int maxAttempts = 100;
        int attemptPort = preferredPort;

        for (int i = 0; i < maxAttempts; i++) {
            try {
                this.serverSocket = new ServerSocket(attemptPort);
                this.port = attemptPort;
                this.running = true;

                System.out.println("✅ P2P Server started on port " + port);

                Thread acceptThread = new Thread(() -> {
                    while (running) {
                        try {
                            Socket clientSocket = serverSocket.accept();
                            executorService.submit(() -> handleClient(clientSocket));
                        } catch (IOException e) {
                            if (running) {
                                System.err.println("❌ Error accepting connection: " + e.getMessage());
                            }
                        }
                    }
                });
                acceptThread.setDaemon(true);
                acceptThread.setName("P2P-Accept-Thread");
                acceptThread.start();

                return;

            } catch (IOException e) {
                attemptPort++;
                if (i == maxAttempts - 1) {
                    throw new IOException("Could not find available port after " + maxAttempts + " attempts", e);
                }
            }
        }
    }

    private void handleClient(Socket socket) {
        String clientAddress = socket.getInetAddress().getHostAddress();

        try (ObjectInputStream ois = new ObjectInputStream(socket.getInputStream())) {

            P2PMessage message = (P2PMessage) ois.readObject();

            // ✅ CRITICAL FIX: Set source IP from socket
            message.setSourceIp(clientAddress);

            // ✅ CRITICAL FIX: Use sourcePort from message (sender's P2P server port)
            // NOT socket.getPort() which is a random client port!
            // The sender should have set this before sending

            // If sourcePort not set, we can't auto-discover the peer
            // This is why we need to fetch from RMI server instead

            if (message.getSourcePort() == 0) {
                // Port not set - will need to fetch from server
                System.out.println("⚠️ Received message without sourcePort, will need RMI lookup");
            } else {
                System.out.println("✅ Received message from " + clientAddress + ":" + message.getSourcePort());
            }

            messageHandler.handleMessage(message, socket);

        } catch (Exception e) {
            System.err.println("❌ Error handling client from " + clientAddress + ": " + e.getMessage());
        } finally {
            try {
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void stop() {
        running = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
            executorService.shutdown();
            System.out.println("✅ P2P Server stopped");
        } catch (IOException e) {
            System.err.println("❌ Error stopping P2P server: " + e.getMessage());
        }
    }

    public int getPort() {
        return port;
    }

    public boolean isRunning() {
        return running;
    }
}