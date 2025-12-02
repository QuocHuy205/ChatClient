// FILE: vku/chatapp/client/rmi/RMIClient.java
// ✅ FIX: Thêm MessageService vào RMI client

package vku.chatapp.client.rmi;

import vku.chatapp.common.constants.AppConstants;
import vku.chatapp.common.rmi.*;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

public class RMIClient {
    private static RMIClient instance;
    private Registry registry;
    private IAuthService authService;
    private IUserService userService;
    private IFriendService friendService;
    private IPeerDiscoveryService peerDiscoveryService;
    private IMessageService messageService; // ✅ NEW

    private RMIClient() {}

    public static RMIClient getInstance() {
        if (instance == null) {
            synchronized (RMIClient.class) {
                if (instance == null) {
                    instance = new RMIClient();
                }
            }
        }
        return instance;
    }

    public void connect(String host, int port) throws Exception {
        registry = LocateRegistry.getRegistry(host, port);

        authService = (IAuthService) registry.lookup(AppConstants.RMI_AUTH_SERVICE);
        userService = (IUserService) registry.lookup(AppConstants.RMI_USER_SERVICE);
        friendService = (IFriendService) registry.lookup(AppConstants.RMI_FRIEND_SERVICE);
        peerDiscoveryService = (IPeerDiscoveryService) registry.lookup(AppConstants.RMI_PEER_DISCOVERY_SERVICE);

        // ✅ NEW: Lookup MessageService
        messageService = (IMessageService) registry.lookup("MessageService");

        System.out.println("✅ Connected to RMI server at " + host + ":" + port);
    }

    public IAuthService getAuthService() {
        return authService;
    }

    public IUserService getUserService() {
        return userService;
    }

    public IFriendService getFriendService() {
        return friendService;
    }

    public IPeerDiscoveryService getPeerDiscoveryService() {
        return peerDiscoveryService;
    }

    // ✅ NEW: Getter for MessageService
    public IMessageService getMessageService() {
        return messageService;
    }

    public boolean isConnected() {
        return registry != null && authService != null;
    }
}