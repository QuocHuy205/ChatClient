package vku.chatapp.client.service;

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
            return false;
        }

        P2PMessage message = new P2PMessage(P2PMessageType.CALL_OFFER, null, receiverId);
        message.setMessageId(UUID.randomUUID().toString());
        message.setContent(callType.name());

        return p2pClient.sendMessage(peerInfo.getAddress(), peerInfo.getPort(), message);
    }

    public boolean answerCall(Long callerId, String callId) {
        PeerInfo peerInfo = peerRegistry.getPeerInfo(callerId);
        if (peerInfo == null) {
            return false;
        }

        P2PMessage message = new P2PMessage(P2PMessageType.CALL_ANSWER, null, callerId);
        message.setMessageId(callId);

        return p2pClient.sendMessage(peerInfo.getAddress(), peerInfo.getPort(), message);
    }

    public boolean rejectCall(Long callerId, String callId) {
        PeerInfo peerInfo = peerRegistry.getPeerInfo(callerId);
        if (peerInfo == null) {
            return false;
        }

        P2PMessage message = new P2PMessage(P2PMessageType.CALL_REJECT, null, callerId);
        message.setMessageId(callId);

        return p2pClient.sendMessage(peerInfo.getAddress(), peerInfo.getPort(), message);
    }

    public boolean endCall(Long peerId, String callId) {
        PeerInfo peerInfo = peerRegistry.getPeerInfo(peerId);
        if (peerInfo == null) {
            return false;
        }

        P2PMessage message = new P2PMessage(P2PMessageType.CALL_END, null, peerId);
        message.setMessageId(callId);

        return p2pClient.sendMessage(peerInfo.getAddress(), peerInfo.getPort(), message);
    }
}