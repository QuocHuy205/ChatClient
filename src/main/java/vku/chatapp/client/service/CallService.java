// FILE: vku/chatapp/client/service/CallService.java
// ✅ FIX: Thêm senderId vào call messages

package vku.chatapp.client.service;

import vku.chatapp.client.model.UserSession;
import vku.chatapp.client.p2p.P2PClient;
import vku.chatapp.client.p2p.PeerRegistry;
import vku.chatapp.common.dto.PeerInfo;
import vku.chatapp.common.enums.CallType;
import vku.chatapp.common.protocol.P2PMessage;
import vku.chatapp.common.protocol.P2PMessageType;

import java.util.UUID;

public class CallService {
    private final P2PClient p2pClient;
    private final PeerRegistry peerRegistry;

    public CallService() {
        this.p2pClient = new P2PClient();
        this.peerRegistry = PeerRegistry.getInstance();
    }

    public boolean initiateCall(Long receiverId, CallType callType) {
        PeerInfo peerInfo = peerRegistry.getPeerInfo(receiverId);
        if (peerInfo == null) {
            System.err.println("❌ Peer not found for call: " + receiverId);
            return false;
        }

        // ✅ FIX: Add senderId
        Long senderId = UserSession.getInstance().getCurrentUser().getId();

        P2PMessage message = new P2PMessage(P2PMessageType.CALL_OFFER, senderId, receiverId);
        message.setMessageId(UUID.randomUUID().toString());
        message.setContent(callType.name());

        System.out.println("📞 Initiating " + callType + " call to " + receiverId);

        return p2pClient.sendMessage(peerInfo.getAddress(), peerInfo.getPort(), message);
    }

    public boolean answerCall(Long callerId, String callId) {
        PeerInfo peerInfo = peerRegistry.getPeerInfo(callerId);
        if (peerInfo == null) {
            System.err.println("❌ Peer not found for answer: " + callerId);
            return false;
        }

        // ✅ FIX: Add senderId
        Long senderId = UserSession.getInstance().getCurrentUser().getId();

        P2PMessage message = new P2PMessage(P2PMessageType.CALL_ANSWER, senderId, callerId);
        message.setMessageId(callId);

        System.out.println("✅ Answering call from " + callerId);

        return p2pClient.sendMessage(peerInfo.getAddress(), peerInfo.getPort(), message);
    }

    public boolean rejectCall(Long callerId, String callId) {
        PeerInfo peerInfo = peerRegistry.getPeerInfo(callerId);
        if (peerInfo == null) {
            System.err.println("❌ Peer not found for reject: " + callerId);
            return false;
        }

        // ✅ FIX: Add senderId
        Long senderId = UserSession.getInstance().getCurrentUser().getId();

        P2PMessage message = new P2PMessage(P2PMessageType.CALL_REJECT, senderId, callerId);
        message.setMessageId(callId);

        System.out.println("❌ Rejecting call from " + callerId);

        return p2pClient.sendMessage(peerInfo.getAddress(), peerInfo.getPort(), message);
    }

    public boolean endCall(Long peerId, String callId) {
        PeerInfo peerInfo = peerRegistry.getPeerInfo(peerId);
        if (peerInfo == null) {
            System.err.println("❌ Peer not found for end call: " + peerId);
            return false;
        }

        // ✅ FIX: Add senderId
        Long senderId = UserSession.getInstance().getCurrentUser().getId();

        P2PMessage message = new P2PMessage(P2PMessageType.CALL_END, senderId, peerId);
        message.setMessageId(callId);

        System.out.println("📞 Ending call with " + peerId);

        return p2pClient.sendMessage(peerInfo.getAddress(), peerInfo.getPort(), message);
    }
}