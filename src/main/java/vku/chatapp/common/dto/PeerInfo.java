package vku.chatapp.common.dto;

import java.io.Serializable;

public class PeerInfo implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long userId;
    private String address;
    private int port;
    private long lastHeartbeat;

    public PeerInfo() {}

    public PeerInfo(Long userId, String address, int port) {
        this.userId = userId;
        this.address = address;
        this.port = port;
        this.lastHeartbeat = System.currentTimeMillis();
    }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }

    public long getLastHeartbeat() { return lastHeartbeat; }
    public void setLastHeartbeat(long lastHeartbeat) { this.lastHeartbeat = lastHeartbeat; }
}