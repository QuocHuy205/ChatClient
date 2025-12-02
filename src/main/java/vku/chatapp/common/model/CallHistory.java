// FILE: vku/chatapp/common/model/CallHistory.java
// ✅ NEW: Model để lưu call history vào database

package vku.chatapp.common.model;

import vku.chatapp.common.enums.CallType;
import vku.chatapp.common.enums.CallStatus;

import java.io.Serializable;
import java.time.LocalDateTime;

public class CallHistory implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long callerId;
    private Long calleeId;
    private CallType callType;
    private CallStatus status;
    private Integer durationSeconds;
    private LocalDateTime startedAt;
    private LocalDateTime endedAt;
    private LocalDateTime createdAt;

    public CallHistory() {}

    public CallHistory(Long callerId, Long calleeId, CallType callType) {
        this.callerId = callerId;
        this.calleeId = calleeId;
        this.callType = callType;
        this.status = CallStatus.RINGING;
        this.createdAt = LocalDateTime.now();
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getCallerId() { return callerId; }
    public void setCallerId(Long callerId) { this.callerId = callerId; }

    public Long getCalleeId() { return calleeId; }
    public void setCalleeId(Long calleeId) { this.calleeId = calleeId; }

    public CallType getCallType() { return callType; }
    public void setCallType(CallType callType) { this.callType = callType; }

    public CallStatus getStatus() { return status; }
    public void setStatus(CallStatus status) { this.status = status; }

    public Integer getDurationSeconds() { return durationSeconds; }
    public void setDurationSeconds(Integer durationSeconds) { this.durationSeconds = durationSeconds; }

    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }

    public LocalDateTime getEndedAt() { return endedAt; }
    public void setEndedAt(LocalDateTime endedAt) { this.endedAt = endedAt; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}