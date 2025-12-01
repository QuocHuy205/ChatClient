// FILE: vku/chatapp/client/service/MessageService.java
// ✅ FIX: Thêm senderId vào P2PMessage

package vku.chatapp.client.service;

import vku.chatapp.common.dto.PeerInfo;
import vku.chatapp.common.enums.MessageType;
import vku.chatapp.common.protocol.P2PMessage;
import vku.chatapp.common.protocol.P2PMessageType;
import vku.chatapp.client.p2p.P2PClient;
import vku.chatapp.client.p2p.PeerRegistry;
import vku.chatapp.client.model.UserSession;

import java.util.UUID;

public class MessageService {
    private final P2PClient p2pClient;
    private final PeerRegistry peerRegistry;

    public MessageService() {
        this.p2pClient = new P2PClient();
        this.peerRegistry = PeerRegistry.getInstance();
    }

    public boolean sendTextMessage(Long receiverId, String content) {
        PeerInfo peerInfo = peerRegistry.getPeerInfo(receiverId);
        if (peerInfo == null) {
            System.err.println("❌ Peer not found: " + receiverId);
            System.err.println("Available peers: " + peerRegistry);
            return false;
        }

        // ✅ FIX 7: THÊM SENDER_ID VÀO MESSAGE
        Long senderId = UserSession.getInstance().getCurrentUser().getId();

        P2PMessage message = new P2PMessage(P2PMessageType.TEXT_MESSAGE, senderId, receiverId);
        message.setMessageId(UUID.randomUUID().toString());
        message.setContent(content);
        message.setContentType(MessageType.TEXT);

        System.out.println("📤 Sending message from " + senderId + " to " + receiverId);
        System.out.println("   Address: " + peerInfo.getAddress() + ":" + peerInfo.getPort());

        return p2pClient.sendMessage(peerInfo.getAddress(), peerInfo.getPort(), message);
    }

    public void sendTextMessageAsync(Long receiverId, String content) {
        new Thread(() -> sendTextMessage(receiverId, content)).start();
    }

    public boolean sendFile(Long receiverId, String fileName, byte[] fileData) {
        PeerInfo peerInfo = peerRegistry.getPeerInfo(receiverId);
        if (peerInfo == null) {
            return false;
        }

        // ✅ FIX: THÊM SENDER_ID
        Long senderId = UserSession.getInstance().getCurrentUser().getId();

        P2PMessage message = new P2PMessage(P2PMessageType.FILE_TRANSFER, senderId, receiverId);
        message.setMessageId(UUID.randomUUID().toString());
        message.setFileName(fileName);
        message.setFileData(fileData);
        message.setContentType(MessageType.FILE);

        return p2pClient.sendMessage(peerInfo.getAddress(), peerInfo.getPort(), message);
    }

    public void sendTypingIndicator(Long receiverId, boolean isTyping) {
        PeerInfo peerInfo = peerRegistry.getPeerInfo(receiverId);
        if (peerInfo == null) {
            return;
        }

        // ✅ FIX: THÊM SENDER_ID
        Long senderId = UserSession.getInstance().getCurrentUser().getId();

        P2PMessage message = new P2PMessage(P2PMessageType.TYPING_INDICATOR, senderId, receiverId);
        message.setContent(String.valueOf(isTyping));

        p2pClient.sendMessageAsync(peerInfo.getAddress(), peerInfo.getPort(), message);
    }

    public void sendReadReceipt(Long receiverId, String messageId) {
        PeerInfo peerInfo = peerRegistry.getPeerInfo(receiverId);
        if (peerInfo == null) {
            return;
        }

        // ✅ FIX: THÊM SENDER_ID
        Long senderId = UserSession.getInstance().getCurrentUser().getId();

        P2PMessage message = new P2PMessage(P2PMessageType.READ_RECEIPT, senderId, receiverId);
        message.setMessageId(messageId);

        p2pClient.sendMessageAsync(peerInfo.getAddress(), peerInfo.getPort(), message);
    }
}