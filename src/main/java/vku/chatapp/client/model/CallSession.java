package vku.chatapp.client.model;

import vku.chatapp.common.enums.CallStatus;
import vku.chatapp.common.enums.CallType;
import vku.chatapp.common.dto.UserDTO;

public class CallSession {
    private String callId;
    private UserDTO peer;
    private CallType callType;
    private CallStatus status;
    private long startTime;
    private long endTime;
    private boolean isCaller;

    public CallSession(String callId, UserDTO peer, CallType callType, boolean isCaller) {
        this.callId = callId;
        this.peer = peer;
        this.callType = callType;
        this.isCaller = isCaller;
        this.status = CallStatus.INITIATING;
    }

    public void start() {
        this.status = CallStatus.CONNECTED;
        this.startTime = System.currentTimeMillis();
    }

    public void end() {
        this.status = CallStatus.ENDED;
        this.endTime = System.currentTimeMillis();
    }

    public long getDuration() {
        if (startTime == 0) return 0;
        long end = endTime > 0 ? endTime : System.currentTimeMillis();
        return (end - startTime) / 1000; // seconds
    }

    // Getters and Setters
    public String getCallId() { return callId; }
    public void setCallId(String callId) { this.callId = callId; }

    public UserDTO getPeer() { return peer; }
    public void setPeer(UserDTO peer) { this.peer = peer; }

    public CallType getCallType() { return callType; }
    public void setCallType(CallType callType) { this.callType = callType; }

    public CallStatus getStatus() { return status; }
    public void setStatus(CallStatus status) { this.status = status; }

    public boolean isCaller() { return isCaller; }
    public void setCaller(boolean caller) { isCaller = caller; }
}