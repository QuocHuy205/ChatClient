package vku.chatapp.client.service;

import vku.chatapp.client.model.UserSession;
import vku.chatapp.client.p2p.P2PClient;
import vku.chatapp.client.p2p.P2PServer;
import vku.chatapp.client.p2p.PeerRegistry;
import vku.chatapp.client.rmi.RMIClient;
import vku.chatapp.common.dto.PeerInfo;
import vku.chatapp.common.enums.CallType;
import vku.chatapp.common.protocol.P2PMessage;
import vku.chatapp.common.protocol.P2PMessageType;

import java.util.UUID;

public class CallService {
    private final P2PClient p2pClient;
    private P2PServer localP2PServer; // ✅ Store reference to local server

    public CallService() {
        this.p2pClient = new P2PClient();
    }

    // ✅ NEW: Set local P2P server reference
    public void setLocalP2PServer(P2PServer server) {
        this.localP2PServer = server;
    }

    public boolean initiateCall(Long receiverId, CallType callType) {
        try {
            PeerInfo peerInfo = fetchFreshPeerInfo(receiverId);
            if (peerInfo == null) {
                System.err.println("❌ Peer not found or offline: " + receiverId);
                return false;
            }

            PeerRegistry.getInstance().addPeer(peerInfo);

            Long senderId = UserSession.getInstance().getCurrentUser().getId();
            String callId = UUID.randomUUID().toString();

            P2PMessage message = new P2PMessage(
                    P2PMessageType.CALL_OFFER,
                    senderId,
                    receiverId
            );
            message.setMessageId(callId);
            message.setContent(callType.name());

            // ✅ Set source port for auto-discovery
            if (localP2PServer != null) {
                message.setSourcePort(localP2PServer.getPort());
            }

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

    public boolean answerCall(Long callerId, String callId) {
        try {
            PeerInfo peerInfo = fetchFreshPeerInfo(callerId);
            if (peerInfo == null) {
                System.err.println("❌ Caller peer not found: " + callerId);
                return false;
            }

            PeerRegistry.getInstance().addPeer(peerInfo);

            Long answerId = UserSession.getInstance().getCurrentUser().getId();

            P2PMessage message = new P2PMessage(
                    P2PMessageType.CALL_ANSWER,
                    answerId,
                    callerId
            );
            message.setMessageId(callId);
            message.setContent("ACCEPTED");

            if (localP2PServer != null) {
                message.setSourcePort(localP2PServer.getPort());
            }

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

    public boolean endCall(Long peerId, String callId) {
        try {
            PeerInfo peerInfo = PeerRegistry.getInstance().getPeerInfo(peerId);

            if (peerInfo == null) {
                System.out.println("⚠️ Peer not in registry, fetching from server...");
                peerInfo = fetchFreshPeerInfo(peerId);
            }

            if (peerInfo == null) {
                System.err.println("⚠️ Cannot find peer to send CALL_END: " + peerId);
                return false;
            }

            Long senderId = UserSession.getInstance().getCurrentUser().getId();

            P2PMessage message = new P2PMessage(
                    P2PMessageType.CALL_END,
                    senderId,
                    peerId
            );
            message.setMessageId(callId);
            message.setContent("ENDED");

            if (localP2PServer != null) {
                message.setSourcePort(localP2PServer.getPort());
            }

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

            if (localP2PServer != null) {
                message.setSourcePort(localP2PServer.getPort());
            }

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