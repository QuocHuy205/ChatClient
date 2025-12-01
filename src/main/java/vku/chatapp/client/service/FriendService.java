package vku.chatapp.client.service;

import vku.chatapp.common.dto.UserDTO;
import vku.chatapp.common.model.Friend;
import vku.chatapp.client.rmi.RMIClient;

import java.util.Collections;
import java.util.List;

public class FriendService {
    private final RMIClient rmiClient;

    public FriendService() {
        this.rmiClient = RMIClient.getInstance();
    }

    public boolean sendFriendRequest(Long userId, Long friendId) {
        try {
            return rmiClient.getFriendService().sendFriendRequest(userId, friendId);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean acceptFriendRequest(Long requestId) {
        try {
            return rmiClient.getFriendService().acceptFriendRequest(requestId);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean rejectFriendRequest(Long requestId) {
        try {
            return rmiClient.getFriendService().rejectFriendRequest(requestId);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean removeFriend(Long userId, Long friendId) {
        try {
            return rmiClient.getFriendService().removeFriend(userId, friendId);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public List<UserDTO> getFriendList(Long userId) {
        try {
            return rmiClient.getFriendService().getFriendList(userId);
        } catch (Exception e) {
            e.printStackTrace();
            return Collections.emptyList();
        }
    }

    public List<Friend> getPendingRequests(Long userId) {
        try {
            return rmiClient.getFriendService().getPendingRequests(userId);
        } catch (Exception e) {
            e.printStackTrace();
            return Collections.emptyList();
        }
    }
}