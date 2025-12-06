// FILE: vku/chatapp/client/service/MessageService.java
// ✅ FIX: Always fetch fresh peer info before sending messages

package vku.chatapp.client.service;

import vku.chatapp.client.model.UserSession;
import vku.chatapp.client.p2p.P2PClient;
import vku.chatapp.client.p2p.PeerRegistry;
import vku.chatapp.client.rmi.RMIClient;
import vku.chatapp.common.dto.PeerInfo;
import vku.chatapp.common.enums.MessageType;
import vku.chatapp.common.protocol.P2PMessage;
import vku.chatapp.common.protocol.P2PMessageType;

import java.util.UUID;

public class MessageService {
    private final P2PClient p2pClient;

    public MessageService() {
        this.p2pClient = new P2PClient();
    }

    /**
     * ✅ FIXED: Fetch fresh peer info before sending text message
     */
    public boolean sendTextMessage(Long receiverId, String content) {
        try {
            // ✅ Get fresh peer info from server
            PeerInfo peerInfo = fetchFreshPeerInfo(receiverId);
            if (peerInfo == null) {
                System.err.println("❌ Receiver not online: " + receiverId);
                return false;
            }

            // ✅ Update local registry
            PeerRegistry.getInstance().addPeer(peerInfo);

            // ✅ Send message
            Long senderId = UserSession.getInstance().getCurrentUser().getId();

            P2PMessage message = new P2PMessage(
                    P2PMessageType.TEXT_MESSAGE,
                    senderId,
                    receiverId
            );
            message.setMessageId(UUID.randomUUID().toString());
            message.setContent(content);
            message.setContentType(MessageType.TEXT);

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

    /**
     * ✅ FIXED: Fetch fresh peer info before sending typing indicator
     */
    public void sendTypingIndicator(Long receiverId, boolean isTyping) {
        try {
            // ✅ Get peer info (use cached if available, else fetch)
            PeerInfo peerInfo = PeerRegistry.getInstance().getPeerInfo(receiverId);

            if (peerInfo == null) {
                peerInfo = fetchFreshPeerInfo(receiverId);
                if (peerInfo == null) {
                    return; // Silently fail for typing indicator
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

            p2pClient.sendMessageAsync(
                    peerInfo.getAddress(),
                    peerInfo.getPort(),
                    message
            );

        } catch (Exception e) {
            // Silently ignore typing indicator errors
        }
    }

    /**
     * ✅ FIXED: Fetch fresh peer info before sending read receipt
     */
    public void sendReadReceipt(Long senderId, String messageId) {
        try {
            PeerInfo peerInfo = PeerRegistry.getInstance().getPeerInfo(senderId);

            if (peerInfo == null) {
                peerInfo = fetchFreshPeerInfo(senderId);
                if (peerInfo == null) {
                    return; // Silently fail
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

            p2pClient.sendMessageAsync(
                    peerInfo.getAddress(),
                    peerInfo.getPort(),
                    message
            );

        } catch (Exception e) {
            // Silently ignore read receipt errors
        }
    }

    /**
     * ✅ NEW: Fetch fresh peer info from RMI server
     */
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