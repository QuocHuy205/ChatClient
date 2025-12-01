package vku.chatapp.client.p2p;

import vku.chatapp.common.dto.PeerInfo;

public class PeerConnection {
    private PeerInfo peerInfo;
    private long lastContact;
    private boolean connected;

    public PeerConnection(PeerInfo peerInfo) {
        this.peerInfo = peerInfo;
        this.lastContact = System.currentTimeMillis();
        this.connected = true;
    }

    public PeerInfo getPeerInfo() {
        return peerInfo;
    }

    public void setPeerInfo(PeerInfo peerInfo) {
        this.peerInfo = peerInfo;
    }

    public long getLastContact() {
        return lastContact;
    }

    public void updateLastContact() {
        this.lastContact = System.currentTimeMillis();
    }

    public boolean isConnected() {
        return connected;
    }

    public void setConnected(boolean connected) {
        this.connected = connected;
    }

    public boolean isTimedOut(long timeoutMs) {
        return System.currentTimeMillis() - lastContact > timeoutMs;
    }
}