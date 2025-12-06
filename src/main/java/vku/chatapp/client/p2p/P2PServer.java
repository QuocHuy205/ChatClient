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
        // Try to find an available port starting from preferredPort
        int maxAttempts = 100;
        int attemptPort = preferredPort;

        for (int i = 0; i < maxAttempts; i++) {
            try {
                this.serverSocket = new ServerSocket(attemptPort);
                this.port = attemptPort;
                this.running = true;

                System.out.println("P2P Server started on port " + port);

                // Start accepting connections
                Thread acceptThread = new Thread(() -> {
                    while (running) {
                        try {
                            Socket clientSocket = serverSocket.accept();
                            executorService.submit(() -> handleClient(clientSocket));
                        } catch (IOException e) {
                            if (running) {
                                System.err.println("Error accepting connection: " + e.getMessage());
                            }
                        }
                    }
                });
                acceptThread.setDaemon(true);
                acceptThread.start();

                return; // Success

            } catch (IOException e) {
                // Port in use, try next port
                attemptPort++;
                if (i == maxAttempts - 1) {
                    throw new IOException("Could not find available port after " + maxAttempts + " attempts", e);
                }
            }
        }
    }

    private void handleClient(Socket socket) {
        try (ObjectInputStream ois = new ObjectInputStream(socket.getInputStream())) {

            P2PMessage message = (P2PMessage) ois.readObject();

            // ⭐ FIX QUAN TRỌNG: Lưu IP + PORT của peer gửi
            message.setSourceIp(socket.getInetAddress().getHostAddress());
            message.setSourcePort(socket.getPort());

            messageHandler.handleMessage(message, socket);

        } catch (Exception e) {
            System.err.println("Error handling client: " + e.getMessage());
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
        } catch (IOException e) {
            System.err.println("Error stopping P2P server: " + e.getMessage());
        }
    }

    public int getPort() {
        return port;
    }
}