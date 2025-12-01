package vku.chatapp.common.protocol;

import vku.chatapp.common.enums.MessageType;

import java.io.Serializable;

public class P2PMessage implements Serializable {
    private static final long serialVersionUID = 1L;

    private String messageId;
    private P2PMessageType type;
    private Long senderId;
    private Long receiverId;
    private String content;
    private MessageType contentType;
    private byte[] fileData;
    private String fileName;
    private long timestamp;

    public P2PMessage() {
        this.timestamp = System.currentTimeMillis();
    }

    public P2PMessage(P2PMessageType type, Long senderId, Long receiverId) {
        this();
        this.type = type;
        this.senderId = senderId;
        this.receiverId = receiverId;
    }

    // Getters and Setters
    public String getMessageId() { return messageId; }
    public void setMessageId(String messageId) { this.messageId = messageId; }

    public P2PMessageType getType() { return type; }
    public void setType(P2PMessageType type) { this.type = type; }

    public Long getSenderId() { return senderId; }
    public void setSenderId(Long senderId) { this.senderId = senderId; }

    public Long getReceiverId() { return receiverId; }
    public void setReceiverId(Long receiverId) { this.receiverId = receiverId; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public MessageType getContentType() { return contentType; }
    public void setContentType(MessageType contentType) { this.contentType = contentType; }

    public byte[] getFileData() { return fileData; }
    public void setFileData(byte[] fileData) { this.fileData = fileData; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
}