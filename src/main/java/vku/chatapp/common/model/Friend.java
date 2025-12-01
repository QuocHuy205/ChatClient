package vku.chatapp.common.model;

import vku.chatapp.common.enums.FriendRequestStatus;

import java.io.Serializable;
import java.time.LocalDateTime;

public class Friend implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long userId;
    private Long friendId;
    private FriendRequestStatus status;
    private LocalDateTime requestedAt;
    private LocalDateTime acceptedAt;

    public Friend() {}

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public Long getFriendId() { return friendId; }
    public void setFriendId(Long friendId) { this.friendId = friendId; }

    public FriendRequestStatus getStatus() { return status; }
    public void setStatus(FriendRequestStatus status) { this.status = status; }

    public LocalDateTime getRequestedAt() { return requestedAt; }
    public void setRequestedAt(LocalDateTime requestedAt) { this.requestedAt = requestedAt; }

    public LocalDateTime getAcceptedAt() { return acceptedAt; }
    public void setAcceptedAt(LocalDateTime acceptedAt) { this.acceptedAt = acceptedAt; }
}
