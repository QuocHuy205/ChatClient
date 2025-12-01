package vku.chatapp.common.model;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

public class Group implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private String name;
    private String avatarUrl;
    private Long creatorId;
    private LocalDateTime createdAt;
    private List<Long> memberIds;

    public Group() {}

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }

    public Long getCreatorId() { return creatorId; }
    public void setCreatorId(Long creatorId) { this.creatorId = creatorId; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public List<Long> getMemberIds() { return memberIds; }
    public void setMemberIds(List<Long> memberIds) { this.memberIds = memberIds; }
}