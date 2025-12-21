package vku.chatapp.client.service;

import vku.chatapp.client.rmi.RMIClient;
import vku.chatapp.common.dto.AuthResponse;
import vku.chatapp.common.dto.LoginRequest;
import vku.chatapp.common.dto.RegisterRequest;
import vku.chatapp.common.dto.UserDTO;
import vku.chatapp.common.enums.UserStatus;

import java.rmi.RemoteException;
import java.util.List;

public class UserService {
    private final RMIClient rmiClient;
    public UserService() {
        this.rmiClient = RMIClient.getInstance();
    }

    public UserDTO getUserById(Long userId){
        try {
            return rmiClient.getUserService().getUserById(userId);
        } catch (RemoteException e) {
            e.printStackTrace();
            return null;
        }
    }

    public UserDTO getUserByUsername(String username){
        try {
            return rmiClient.getUserService().getUserByUsername(username);
        } catch (RemoteException e) {
            e.printStackTrace();
            return null;
        }
    }

    public List<UserDTO> searchUsers(String query){
        try {
            return rmiClient.getUserService().searchUsers(query);
        } catch (RemoteException e) {
            e.printStackTrace();
            return null;
        }
    }

    public boolean updateProfile(Long userId, String displayName, String bio, String avatarUrl){
        try {
            return rmiClient.getUserService().updateProfile(userId, displayName, bio, avatarUrl);
        } catch (RemoteException e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean updateStatus(Long userId, UserStatus status){
        try {
            return rmiClient.getUserService().updateStatus(userId, status);
        } catch (RemoteException e) {
            e.printStackTrace();
            return false;
        }
    }




//    UserDTO getUserById(Long userId) throws RemoteException;
//    UserDTO getUserByUsername(String username) throws RemoteException;
//    List<UserDTO> searchUsers(String query) throws RemoteException;
//    boolean updateProfile(Long userId, String displayName, String bio, String avatarUrl) throws RemoteException;
//    boolean updateStatus(Long userId, UserStatus status) throws RemoteException;
}
