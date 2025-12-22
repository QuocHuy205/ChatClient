package vku.chatapp.client.service;

import vku.chatapp.client.rmi.RMIClient;
import vku.chatapp.common.dto.UserDTO;
import vku.chatapp.common.enums.UserStatus;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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

    /**
     * Upload avatar image to server (which then uploads to Cloudinary)
     * @param userId User ID
     * @param localFilePath Path to local image file
     * @return Cloudinary URL of uploaded avatar, or null if failed
     */
    public String uploadAvatar(Long userId, String localFilePath) {
        try {
            // Read file as byte array
            Path path = Path.of(localFilePath);
            byte[] imageData = Files.readAllBytes(path);
            String fileName = path.getFileName().toString();

            System.out.println("📤 Uploading avatar: " + fileName + " (" + imageData.length + " bytes)");

            // Send to server via RMI
            String cloudinaryUrl = rmiClient.getUserService().uploadAvatar(userId, imageData, fileName);

            if (cloudinaryUrl != null) {
                System.out.println("✅ Avatar uploaded successfully: " + cloudinaryUrl);
            } else {
                System.err.println("❌ Avatar upload failed");
            }

            return cloudinaryUrl;

        } catch (IOException e) {
            System.err.println("❌ Failed to read file: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
}