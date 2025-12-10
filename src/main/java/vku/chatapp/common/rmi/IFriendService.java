package vku.chatapp.common.rmi;

import vku.chatapp.common.dto.UserDTO;
import vku.chatapp.common.model.Friend;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

public interface IFriendService extends Remote {
    boolean sendFriendRequest(Long userId, Long friendId) throws RemoteException;
    boolean acceptFriendRequest(Long requestId) throws RemoteException;
    boolean rejectFriendRequest(Long requestId) throws RemoteException;
    boolean cancelFriendRequest(Long requestId, Long userId) throws RemoteException;
    boolean removeFriend(Long userId, Long friendId) throws RemoteException;
    List<UserDTO> getFriendList(Long userId) throws RemoteException;
    List<Friend> getPendingRequests(Long userId) throws RemoteException;
    List<Friend> getSentRequests(Long userId) throws RemoteException;
    UserDTO searchUserByUsername(String username) throws RemoteException;
}