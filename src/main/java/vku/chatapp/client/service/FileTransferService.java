package vku.chatapp.client.service;

import vku.chatapp.client.model.UserSession;
import vku.chatapp.client.p2p.P2PClient;
import vku.chatapp.client.p2p.PeerRegistry;
import vku.chatapp.client.rmi.RMIClient;
import vku.chatapp.common.dto.PeerInfo;
import vku.chatapp.common.enums.MessageType;
import vku.chatapp.common.protocol.P2PMessage;
import vku.chatapp.common.protocol.P2PMessageType;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class FileTransferService {
    private final P2PClient p2pClient;
    private final PeerRegistry peerRegistry;
    private final String downloadPath;
    private final String imageCachePath;

    // ✅ Supported image extensions
    private static final Set<String> IMAGE_EXTENSIONS = new HashSet<>(Arrays.asList(
            "jpg", "jpeg", "png", "gif", "bmp", "webp"
    ));

    public void cacheImage(File source) throws Exception {
        File cacheDir = new File(getImageCachePath());
        if (!cacheDir.exists()) {
            cacheDir.mkdirs();
        }

        Files.copy(
                source.toPath(),
                new File(cacheDir, source.getName()).toPath(),
                StandardCopyOption.REPLACE_EXISTING
        );
    }

    public FileTransferService() {
        this.p2pClient = new P2PClient();
        this.peerRegistry = PeerRegistry.getInstance();
        this.downloadPath = System.getProperty("user.home") + "/Downloads/VKUChat/";
        this.imageCachePath = System.getProperty("user.home") + "/Downloads/images/";

        // Create directories
        createDirectory(downloadPath);
        createDirectory(imageCachePath);
    }

    private void createDirectory(String path) {
        File dir = new File(path);
        if (!dir.exists()) {
            boolean created = dir.mkdirs();
            if (created) {
                System.out.println("✅ Created directory: " + path);
            }
        }
    }

    /**
     * ✅ Send file (auto-detect if it's an image)
     */
    public boolean sendFile(Long receiverId, File file) {
        try {
            // Fetch fresh peer info
            PeerInfo peerInfo = fetchFreshPeerInfo(receiverId);
            if (peerInfo == null) {
                System.err.println("❌ Peer not found: " + receiverId);
                return false;
            }
            peerRegistry.addPeer(peerInfo);

            // Check file size (limit to 50MB for P2P)
            long maxSize = 50 * 1024 * 1024; // 50MB
            if (file.length() > maxSize) {
                System.err.println("❌ File too large: " + file.length() + " bytes (max " + maxSize + ")");
                return false;
            }

            byte[] fileData = Files.readAllBytes(file.toPath());

            // ✅ Determine if it's an image
            MessageType messageType = isImage(file.getName()) ? MessageType.IMAGE : MessageType.FILE;

            Long senderId = UserSession.getInstance().getCurrentUser().getId();

            P2PMessage message = new P2PMessage(P2PMessageType.FILE_TRANSFER, senderId, receiverId);
            message.setMessageId(UUID.randomUUID().toString());
            message.setFileName(file.getName());
            message.setFileData(fileData);
            message.setContentType(messageType);

            System.out.println("📎 Sending " + messageType + ": " + file.getName() +
                    " (" + formatFileSize(file.length()) + ") to " + receiverId);

            boolean success = p2pClient.sendMessage(peerInfo.getAddress(), peerInfo.getPort(), message);

            if (success) {
                System.out.println("✅ " + messageType + " sent successfully");
            } else {
                System.err.println("❌ Failed to send " + messageType);
            }

            return success;

        } catch (Exception e) {
            System.err.println("❌ Error sending file: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * ✅ Receive file/image and save
     */
    public void receiveFile(P2PMessage message) {
        try {
            String fileName = message.getFileName();
            byte[] fileData = message.getFileData();
            MessageType type = message.getContentType();

            if (fileName == null || fileData == null) {
                System.err.println("❌ Invalid file data received");
                return;
            }

            // Sanitize filename
            fileName = fileName.replaceAll("[^a-zA-Z0-9\\.\\-_]", "_");

            // ✅ Save to different paths based on type
            String savePath = (type == MessageType.IMAGE) ? imageCachePath : downloadPath;
            File outputFile = new File(savePath + fileName);

            // If file exists, add number suffix
            outputFile = getUniqueFile(savePath, fileName);

            try (FileOutputStream fos = new FileOutputStream(outputFile)) {
                fos.write(fileData);
            }

            System.out.println("✅ " + type + " received and saved: " + outputFile.getAbsolutePath());
            System.out.println("   Size: " + formatFileSize(fileData.length));

        } catch (Exception e) {
            System.err.println("❌ Error receiving file: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * ✅ Get cached image file (for displaying in chat)
     */
    public File getCachedImageFile(String fileName) {
        File file = new File(imageCachePath + fileName);
        return file.exists() ? file : null;
    }

    /**
     * ✅ Check if file is an image
     */
    public static boolean isImage(String fileName) {
        if (fileName == null || fileName.isEmpty()) return false;

        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex < 0) return false;

        String ext = fileName.substring(dotIndex + 1).toLowerCase();
        return IMAGE_EXTENSIONS.contains(ext);
    }

    /**
     * ✅ Get unique filename if file already exists
     */
    private File getUniqueFile(String path, String fileName) {
        File outputFile = new File(path + fileName);

        int counter = 1;
        String baseName = fileName;
        String extension = "";
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex > 0) {
            baseName = fileName.substring(0, dotIndex);
            extension = fileName.substring(dotIndex);
        }

        while (outputFile.exists()) {
            fileName = baseName + "_(" + counter + ")" + extension;
            outputFile = new File(path + fileName);
            counter++;
        }

        return outputFile;
    }

    /**
     * ✅ Fetch fresh peer info from server
     */
    private PeerInfo fetchFreshPeerInfo(Long userId) {
        try {
            PeerInfo peerInfo = RMIClient.getInstance()
                    .getPeerDiscoveryService()
                    .getPeerInfo(userId);

            if (peerInfo != null) {
                System.out.println("✅ Fetched peer info for " + userId +
                        ": " + peerInfo.getAddress() + ":" + peerInfo.getPort());
            }

            return peerInfo;

        } catch (Exception e) {
            System.err.println("❌ Error fetching peer info: " + e.getMessage());
            return null;
        }
    }

    /**
     * ✅ Format file size to human-readable
     */
    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
    }

    public String getDownloadPath() {
        return downloadPath;
    }

    public String getImageCachePath() {
        return imageCachePath;
    }
}