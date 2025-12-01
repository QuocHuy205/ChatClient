package vku.chatapp.client.p2p;

import vku.chatapp.common.dto.PeerInfo;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class PeerRegistry {
    private static PeerRegistry instance;
    private final Map<Long, PeerConnection> peers;

    private PeerRegistry() {
        this.peers = new ConcurrentHashMap<>();
    }

    public static PeerRegistry getInstance() {
        if (instance == null) {
            synchronized (PeerRegistry.class) {
                if (instance == null) {
                    instance = new PeerRegistry();
                }
            }
        }
        return instance;
    }

    public void addPeer(PeerInfo peerInfo) {
        peers.put(peerInfo.getUserId(), new PeerConnection(peerInfo));
    }

    public void removePeer(Long userId) {
        peers.remove(userId);
    }

    public PeerConnection getPeer(Long userId) {
        return peers.get(userId);
    }

    public PeerInfo getPeerInfo(Long userId) {
        PeerConnection connection = peers.get(userId);
        return connection != null ? connection.getPeerInfo() : null;
    }

    public void updatePeerContact(Long userId) {
        PeerConnection connection = peers.get(userId);
        if (connection != null) {
            connection.updateLastContact();
        }
    }

    public void clear() {
        peers.clear();
    }
}