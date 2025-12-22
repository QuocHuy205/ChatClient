package vku.chatapp.common.rmi;

import vku.chatapp.common.dto.UserDTO;
import vku.chatapp.common.enums.UserStatus;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

public interface IUserService extends Remote {
    UserDTO getUserById(Long userId) throws RemoteException;
    UserDTO getUserByUsername(String username) throws RemoteException;
    List<UserDTO> searchUsers(String query) throws RemoteException;
    boolean updateProfile(Long userId, String displayName, String bio, String avatarUrl) throws RemoteException;
    boolean updateStatus(Long userId, UserStatus status) throws RemoteException;
    String uploadAvatar(Long userId, byte[] imageData, String fileName) throws RemoteException;
}