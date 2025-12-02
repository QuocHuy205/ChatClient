// FILE: vku/chatapp/client/service/FileTransferService.java
// ✅ FIX: Thêm senderId vào file transfer messages

package vku.chatapp.client.service;

import vku.chatapp.client.model.UserSession;
import vku.chatapp.client.p2p.P2PClient;
import vku.chatapp.client.p2p.PeerRegistry;
import vku.chatapp.common.dto.PeerInfo;
import vku.chatapp.common.enums.MessageType;
import vku.chatapp.common.protocol.P2PMessage;
import vku.chatapp.common.protocol.P2PMessageType;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.util.UUID;

public class FileTransferService {
    private final P2PClient p2pClient;
    private final PeerRegistry peerRegistry;
    private final String downloadPath;

    public FileTransferService() {
        this.p2pClient = new P2PClient();
        this.peerRegistry = PeerRegistry.getInstance();
        this.downloadPath = System.getProperty("user.home") + "/Downloads/VKUChat/";

        // Create download directory if not exists
        File dir = new File(downloadPath);
        if (!dir.exists()) {
            boolean created = dir.mkdirs();
            if (created) {
                System.out.println("✅ Created download directory: " + downloadPath);
            }
        }
    }

    public boolean sendFile(Long receiverId, File file) {
        try {
            PeerInfo peerInfo = peerRegistry.getPeerInfo(receiverId);
            if (peerInfo == null) {
                System.err.println("❌ Peer not found: " + receiverId);
                return false;
            }

            // Check file size (limit to 50MB for P2P)
            long maxSize = 50 * 1024 * 1024; // 50MB
            if (file.length() > maxSize) {
                System.err.println("❌ File too large: " + file.length() + " bytes (max " + maxSize + ")");
                return false;
            }

            byte[] fileData = Files.readAllBytes(file.toPath());

            // ✅ FIX: Add senderId
            Long senderId = UserSession.getInstance().getCurrentUser().getId();

            P2PMessage message = new P2PMessage(P2PMessageType.FILE_TRANSFER, senderId, receiverId);
            message.setMessageId(UUID.randomUUID().toString());
            message.setFileName(file.getName());
            message.setFileData(fileData);
            message.setContentType(MessageType.FILE);

            System.out.println("📎 Sending file: " + file.getName() +
                    " (" + formatFileSize(file.length()) + ") to " + receiverId);

            boolean success = p2pClient.sendMessage(peerInfo.getAddress(), peerInfo.getPort(), message);

            if (success) {
                System.out.println("✅ File sent successfully");
            } else {
                System.err.println("❌ Failed to send file");
            }

            return success;

        } catch (Exception e) {
            System.err.println("❌ Error sending file: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    public void receiveFile(P2PMessage message) {
        try {
            String fileName = message.getFileName();
            byte[] fileData = message.getFileData();

            if (fileName == null || fileData == null) {
                System.err.println("❌ Invalid file data received");
                return;
            }

            // Sanitize filename
            fileName = fileName.replaceAll("[^a-zA-Z0-9\\.\\-_]", "_");

            File outputFile = new File(downloadPath + fileName);

            // If file exists, add number suffix
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
                outputFile = new File(downloadPath + fileName);
                counter++;
            }

            try (FileOutputStream fos = new FileOutputStream(outputFile)) {
                fos.write(fileData);
            }

            System.out.println("✅ File received and saved: " + outputFile.getAbsolutePath());
            System.out.println("   Size: " + formatFileSize(fileData.length));

        } catch (Exception e) {
            System.err.println("❌ Error receiving file: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
    }

    // ✅ Getter for download path
    public String getDownloadPath() {
        return downloadPath;
    }
}