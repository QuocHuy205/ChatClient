package vku.chatapp.client.service;

import vku.chatapp.client.model.UserSession;
import vku.chatapp.client.p2p.P2PClient;
import vku.chatapp.client.p2p.P2PServer;
import vku.chatapp.client.p2p.PeerRegistry;
import vku.chatapp.client.rmi.RMIClient;
import vku.chatapp.common.dto.PeerInfo;
import vku.chatapp.common.enums.MessageType;
import vku.chatapp.common.protocol.P2PMessage;
import vku.chatapp.common.protocol.P2PMessageType;

import java.util.UUID;

public class MessageService {
    private final P2PClient p2pClient;
    private P2PServer localP2PServer; // ✅ Store reference to local server

    public MessageService() {
        this.p2pClient = new P2PClient();
    }

    // ✅ NEW: Set local P2P server reference
    public void setLocalP2PServer(P2PServer server) {
        this.localP2PServer = server;
    }

    public boolean sendTextMessage(Long receiverId, String content) {
        try {
            PeerInfo peerInfo = fetchFreshPeerInfo(receiverId);
            if (peerInfo == null) {
                System.err.println("❌ Receiver not online: " + receiverId);
                return false;
            }

            PeerRegistry.getInstance().addPeer(peerInfo);

            Long senderId = UserSession.getInstance().getCurrentUser().getId();

            P2PMessage message = new P2PMessage(
                    P2PMessageType.TEXT_MESSAGE,
                    senderId,
                    receiverId
            );
            message.setMessageId(UUID.randomUUID().toString());
            message.setContent(content);
            message.setContentType(MessageType.TEXT);

            // ✅ Set source port for auto-discovery
            if (localP2PServer != null) {
                message.setSourcePort(localP2PServer.getPort());
            }

            System.out.println("📤 Sending message from " + senderId + " to " + receiverId);
            System.out.println("   Address: " + peerInfo.getAddress() + ":" + peerInfo.getPort());

            return p2pClient.sendMessage(
                    peerInfo.getAddress(),
                    peerInfo.getPort(),
                    message
            );

        } catch (Exception e) {
            System.err.println("❌ Error sending text message: " + e.getMessage());
            return false;
        }
    }

    public void sendTypingIndicator(Long receiverId, boolean isTyping) {
        try {
            PeerInfo peerInfo = PeerRegistry.getInstance().getPeerInfo(receiverId);

            if (peerInfo == null) {
                peerInfo = fetchFreshPeerInfo(receiverId);
                if (peerInfo == null) {
                    return;
                }
                PeerRegistry.getInstance().addPeer(peerInfo);
            }

            Long senderId = UserSession.getInstance().getCurrentUser().getId();

            P2PMessage message = new P2PMessage(
                    P2PMessageType.TYPING_INDICATOR,
                    senderId,
                    receiverId
            );
            message.setContent(String.valueOf(isTyping));

            if (localP2PServer != null) {
                message.setSourcePort(localP2PServer.getPort());
            }

            p2pClient.sendMessageAsync(
                    peerInfo.getAddress(),
                    peerInfo.getPort(),
                    message
            );

        } catch (Exception e) {
            // Silently ignore typing indicator errors
        }
    }

    public void sendReadReceipt(Long senderId, String messageId) {
        try {
            PeerInfo peerInfo = PeerRegistry.getInstance().getPeerInfo(senderId);

            if (peerInfo == null) {
                peerInfo = fetchFreshPeerInfo(senderId);
                if (peerInfo == null) {
                    return;
                }
                PeerRegistry.getInstance().addPeer(peerInfo);
            }

            Long readerId = UserSession.getInstance().getCurrentUser().getId();

            P2PMessage message = new P2PMessage(
                    P2PMessageType.READ_RECEIPT,
                    readerId,
                    senderId
            );
            message.setMessageId(messageId);

            if (localP2PServer != null) {
                message.setSourcePort(localP2PServer.getPort());
            }

            p2pClient.sendMessageAsync(
                    peerInfo.getAddress(),
                    peerInfo.getPort(),
                    message
            );

        } catch (Exception e) {
            // Silently ignore read receipt errors
        }
    }

    private PeerInfo fetchFreshPeerInfo(Long userId) {
        try {
            PeerInfo peerInfo = RMIClient.getInstance()
                    .getPeerDiscoveryService()
                    .getPeerInfo(userId);

            if (peerInfo != null) {
                System.out.println("✅ Fetched peer info for " + userId +
                        ": " + peerInfo.getAddress() + ":" + peerInfo.getPort());
            }

            return peerInfo;

        } catch (Exception e) {
            System.err.println("❌ Error fetching peer info: " + e.getMessage());
            return null;
        }
    }
}