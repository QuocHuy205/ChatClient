package vku.chatapp.client.model;

import vku.chatapp.common.model.Message;
import vku.chatapp.common.dto.UserDTO;
import vku.chatapp.common.model.User;

import java.util.ArrayList;
import java.util.List;

public class ChatSession {
    private UserDTO friend;
    private List<Message> messages;
    private boolean isTyping;

    public ChatSession(UserDTO friend) {
        this.friend = friend;
        this.messages = new ArrayList<>();
        this.isTyping = false;
    }

    public UserDTO getFriend() {
        return friend;
    }

    public List<Message> getMessages() {
        return messages;
    }

    public void addMessage(Message message) {
        messages.add(message);
    }

    public boolean isTyping() {
        return isTyping;
    }

    public void setTyping(boolean typing) {
        isTyping = typing;
    }
}