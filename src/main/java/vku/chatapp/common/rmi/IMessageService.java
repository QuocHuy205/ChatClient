// FILE: vku/chatapp/common/rmi/IMessageService.java
// ✅ NEW: RMI Service để lưu messages vào database

package vku.chatapp.common.rmi;

import vku.chatapp.common.model.Message;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

public interface IMessageService extends Remote {
    /**
     * Save a message to database
     */
    Message saveMessage(Message message) throws RemoteException;

    /**
     * Get conversation history between two users
     */
    List<Message> getConversationHistory(Long user1Id, Long user2Id, int limit) throws RemoteException;

    /**
     * Update message status (DELIVERED, READ)
     */
    boolean updateMessageStatus(Long messageId, String status) throws RemoteException;

    /**
     * Get unread message count for a user
     */
    int getUnreadCount(Long userId) throws RemoteException;
}