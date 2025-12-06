package vku.chatapp.client.p2p;

import vku.chatapp.client.media.MediaManager;
import vku.chatapp.common.dto.PeerInfo;
import vku.chatapp.common.protocol.P2PMessage;
import vku.chatapp.common.protocol.P2PMessageType;

import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

public class P2PMessageHandler {
    private List<MessageListener> listeners;
    private MediaManager mediaManager;

    public P2PMessageHandler() {
        this.listeners = new ArrayList<>();
        this.mediaManager = MediaManager.getInstance();
    }

    public void handleMessage(P2PMessage message, Socket socket) {
        System.out.println("Received P2P message: " + message.getType());

        PeerRegistry.getInstance().updatePeer(
                message.getSenderId(),
                message.getSourceIp(),
                message.getSourcePort()
        );

        long senderId = message.getSenderId();
        PeerRegistry registry = PeerRegistry.getInstance();

        // 🔥 Nếu chưa có peer → auto-add
        if (registry.getPeer(senderId) == null) {
            registry.addPeer(new PeerInfo(
                    senderId,
                    socket.getInetAddress().getHostAddress(),
                    socket.getPort()
            ));

            System.out.println("🙌 Auto-added peer from incoming message: " + senderId);
        }

        // Handle media streams directly
        if (message.getType() == P2PMessageType.AUDIO_STREAM) {
            mediaManager.handleIncomingAudio(message);
            return;
        }

        if (message.getType() == P2PMessageType.VIDEO_STREAM) {
            mediaManager.handleIncomingVideo(message);
            return;
        }

        // Notify all listeners for other message types
        notifyListeners(message);
    }

    public void addListener(MessageListener listener) {
        listeners.add(listener);
    }

    public void removeListener(MessageListener listener) {
        listeners.remove(listener);
    }

    private void notifyListeners(P2PMessage message) {
        for (MessageListener listener : listeners) {
            try {
                listener.onMessageReceived(message);
            } catch (Exception e) {
                System.err.println("Error notifying listener: " + e.getMessage());
            }
        }
    }

    public interface MessageListener {
        void onMessageReceived(P2PMessage message);
    }
}