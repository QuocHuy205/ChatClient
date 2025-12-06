// FILE: vku/chatapp/client/service/CallService.java
// ✅ FIX: Always get fresh peer info before sending call messages

package vku.chatapp.client.service;

import vku.chatapp.client.model.UserSession;
import vku.chatapp.client.p2p.P2PClient;
import vku.chatapp.client.p2p.PeerRegistry;
import vku.chatapp.client.rmi.RMIClient;
import vku.chatapp.common.dto.PeerInfo;
import vku.chatapp.common.enums.CallType;
import vku.chatapp.common.protocol.P2PMessage;
import vku.chatapp.common.protocol.P2PMessageType;

import java.util.UUID;

public class CallService {
    private final P2PClient p2pClient;

    public CallService() {
        this.p2pClient = new P2PClient();
    }

    /**
     * ✅ FIXED: Refresh peer info before initiating call
     */
    public boolean initiateCall(Long receiverId, CallType callType) {
        try {
            // ✅ STEP 1: Get fresh peer info from server
            PeerInfo peerInfo = fetchFreshPeerInfo(receiverId);
            if (peerInfo == null) {
                System.err.println("❌ Peer not found or offline: " + receiverId);
                return false;
            }

            // ✅ STEP 2: Update local registry
            PeerRegistry.getInstance().addPeer(peerInfo);

            // ✅ STEP 3: Create and send CALL_OFFER
            Long senderId = UserSession.getInstance().getCurrentUser().getId();
            String callId = UUID.randomUUID().toString();

            P2PMessage message = new P2PMessage(
                    P2PMessageType.CALL_OFFER,
                    senderId,
                    receiverId
            );
            message.setMessageId(callId);
            message.setContent(callType.name());

            System.out.println("📞 Initiating " + callType + " call to " + receiverId);
            System.out.println("   Address: " + peerInfo.getAddress() + ":" + peerInfo.getPort());

            return p2pClient.sendMessage(
                    peerInfo.getAddress(),
                    peerInfo.getPort(),
                    message
            );

        } catch (Exception e) {
            System.err.println("❌ Error initiating call: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * ✅ FIXED: Refresh peer info before answering
     */
    public boolean answerCall(Long callerId, String callId) {
        try {
            // ✅ STEP 1: Get fresh peer info from server
            PeerInfo peerInfo = fetchFreshPeerInfo(callerId);
            if (peerInfo == null) {
                System.err.println("❌ Caller peer not found: " + callerId);
                return false;
            }

            // ✅ STEP 2: Update local registry
            PeerRegistry.getInstance().addPeer(peerInfo);

            // ✅ STEP 3: Send CALL_ANSWER
            Long answerId = UserSession.getInstance().getCurrentUser().getId();

            P2PMessage message = new P2PMessage(
                    P2PMessageType.CALL_ANSWER,
                    answerId,
                    callerId
            );
            message.setMessageId(callId);
            message.setContent("ACCEPTED");

            System.out.println("✅ Answering call from " + callerId);
            System.out.println("   Address: " + peerInfo.getAddress() + ":" + peerInfo.getPort());

            return p2pClient.sendMessage(
                    peerInfo.getAddress(),
                    peerInfo.getPort(),
                    message
            );

        } catch (Exception e) {
            System.err.println("❌ Error answering call: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * ✅ FIXED: Refresh peer info before ending call
     */
    public boolean endCall(Long peerId, String callId) {
        try {
            // ✅ STEP 1: Try to get peer info (may fail if peer already disconnected)
            PeerInfo peerInfo = PeerRegistry.getInstance().getPeerInfo(peerId);

            // ✅ If not in registry, try to fetch from server
            if (peerInfo == null) {
                System.out.println("⚠️ Peer not in registry, fetching from server...");
                peerInfo = fetchFreshPeerInfo(peerId);
            }

            if (peerInfo == null) {
                System.err.println("⚠️ Cannot find peer to send CALL_END: " + peerId);
                return false;
            }

            // ✅ STEP 2: Send CALL_END
            Long senderId = UserSession.getInstance().getCurrentUser().getId();

            P2PMessage message = new P2PMessage(
                    P2PMessageType.CALL_END,
                    senderId,
                    peerId
            );
            message.setMessageId(callId);
            message.setContent("ENDED");

            System.out.println("📤 Sent CALL_END message");
            System.out.println("   Address: " + peerInfo.getAddress() + ":" + peerInfo.getPort());

            return p2pClient.sendMessage(
                    peerInfo.getAddress(),
                    peerInfo.getPort(),
                    message
            );

        } catch (Exception e) {
            System.err.println("❌ Error ending call: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    public boolean rejectCall(Long callerId, String callId) {
        try {
            PeerInfo peerInfo = fetchFreshPeerInfo(callerId);
            if (peerInfo == null) {
                System.err.println("❌ Caller peer not found: " + callerId);
                return false;
            }

            Long rejecterId = UserSession.getInstance().getCurrentUser().getId();

            P2PMessage message = new P2PMessage(
                    P2PMessageType.CALL_REJECT,
                    rejecterId,
                    callerId
            );
            message.setMessageId(callId);
            message.setContent("REJECTED");
            System.out.println("❌ Rejecting call from " + callerId);

            return p2pClient.sendMessage(
                    peerInfo.getAddress(),
                    peerInfo.getPort(),
                    message
            );

        } catch (Exception e) {
            System.err.println("❌ Error rejecting call: " + e.getMessage());
            return false;
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
            } else {
                System.err.println("❌ Peer info not found for: " + userId);
            }

            return peerInfo;

        } catch (Exception e) {
            System.err.println("❌ Error fetching peer info: " + e.getMessage());
            return null;
        }
    }
}