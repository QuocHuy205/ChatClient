// FILE: vku/chatapp/client/service/StatusUpdateService.java
// ✅ NEW: Service để poll status updates từ server

package vku.chatapp.client.service;

import javafx.application.Platform;
import vku.chatapp.client.model.UserSession;
import vku.chatapp.client.p2p.PeerRegistry;
import vku.chatapp.client.rmi.RMIClient;
import vku.chatapp.common.dto.PeerInfo;
import vku.chatapp.common.enums.UserStatus;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

public class StatusUpdateService {
    private static StatusUpdateService instance;
    private Timer pollTimer;
    private List<StatusUpdateListener> listeners;
    private Set<Long> currentlyOnline;

    private StatusUpdateService() {
        this.listeners = new CopyOnWriteArrayList<>();
        this.currentlyOnline = new HashSet<>();
    }

    public static StatusUpdateService getInstance() {
        if (instance == null) {
            synchronized (StatusUpdateService.class) {
                if (instance == null) {
                    instance = new StatusUpdateService();
                }
            }
        }
        return instance;
    }

    public void startPolling() {
        if (pollTimer != null) {
            pollTimer.cancel();
        }

        pollTimer = new Timer(true);
        pollTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                checkStatusUpdates();
            }
        }, 2000, 8000); // Poll every 10 seconds

        System.out.println("✅ Status polling started");
    }

    public void stopPolling() {
        if (pollTimer != null) {
            pollTimer.cancel();
            pollTimer = null;
        }
        currentlyOnline.clear();
        System.out.println("✅ Status polling stopped");
    }

    private void checkStatusUpdates() {
        try {
            Long userId = UserSession.getInstance().getCurrentUser().getId();
            List<PeerInfo> onlineFriends = RMIClient.getInstance()
                    .getPeerDiscoveryService()
                    .getOnlineFriends(userId);

            Set<Long> newOnlineSet = new HashSet<>();

            // Update peer registry and collect online user IDs
            for (PeerInfo peerInfo : onlineFriends) {
                PeerRegistry.getInstance().addPeer(peerInfo);
                newOnlineSet.add(peerInfo.getUserId());
            }

            // Detect newly online users
            Set<Long> justCameOnline = new HashSet<>(newOnlineSet);
            justCameOnline.removeAll(currentlyOnline);

            // Detect newly offline users
            Set<Long> justWentOffline = new HashSet<>(currentlyOnline);
            justWentOffline.removeAll(newOnlineSet);

            // Update current state
            currentlyOnline = newOnlineSet;

            // Notify listeners on JavaFX thread
            if (!justCameOnline.isEmpty() || !justWentOffline.isEmpty()) {
                Platform.runLater(() -> {
                    for (Long friendId : justCameOnline) {
                        notifyStatusChanged(friendId, UserStatus.ONLINE);
                    }
                    for (Long friendId : justWentOffline) {
                        notifyStatusChanged(friendId, UserStatus.OFFLINE);
                        PeerRegistry.getInstance().removePeer(friendId);
                    }
                });
            }

        } catch (Exception e) {
            System.err.println("❌ Error checking status updates: " + e.getMessage());
        }
    }

    public void addListener(StatusUpdateListener listener) {
        listeners.add(listener);
    }

    public void removeListener(StatusUpdateListener listener) {
        listeners.remove(listener);
    }

    private void notifyStatusChanged(Long userId, UserStatus newStatus) {
        for (StatusUpdateListener listener : listeners) {
            try {
                listener.onStatusChanged(userId, newStatus);
            } catch (Exception e) {
                System.err.println("❌ Error notifying listener: " + e.getMessage());
            }
        }
    }

    public interface StatusUpdateListener {
        void onStatusChanged(Long userId, UserStatus newStatus);
    }
}