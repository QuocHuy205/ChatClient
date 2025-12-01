package vku.chatapp.client.service;

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
        new File(downloadPath).mkdirs();
    }

    public boolean sendFile(Long receiverId, File file) {
        try {
            PeerInfo peerInfo = peerRegistry.getPeerInfo(receiverId);
            if (peerInfo == null) {
                System.err.println("Peer not found: " + receiverId);
                return false;
            }

            byte[] fileData = Files.readAllBytes(file.toPath());

            P2PMessage message = new P2PMessage(P2PMessageType.FILE_TRANSFER, null, receiverId);
            message.setMessageId(UUID.randomUUID().toString());
            message.setFileName(file.getName());
            message.setFileData(fileData);
            message.setContentType(MessageType.FILE);

            return p2pClient.sendMessage(peerInfo.getAddress(), peerInfo.getPort(), message);

        } catch (Exception e) {
            System.err.println("Error sending file: " + e.getMessage());
            return false;
        }
    }

    public void receiveFile(P2PMessage message) {
        try {
            String fileName = message.getFileName();
            byte[] fileData = message.getFileData();

            if (fileName == null || fileData == null) {
                return;
            }

            File outputFile = new File(downloadPath + fileName);

            try (FileOutputStream fos = new FileOutputStream(outputFile)) {
                fos.write(fileData);
            }

            System.out.println("File saved: " + outputFile.getAbsolutePath());

        } catch (Exception e) {
            System.err.println("Error receiving file: " + e.getMessage());
        }
    }
}
