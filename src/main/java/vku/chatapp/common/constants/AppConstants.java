package vku.chatapp.common.constants;

public class AppConstants {
    public static final String APP_NAME = "VKU Chat App";
    public static final String APP_VERSION = "1.0.0";

    // RMI
    public static final String RMI_HOST = "localhost";
    public static final int RMI_PORT = 1099;
    public static final String RMI_AUTH_SERVICE = "AuthService";
    public static final String RMI_USER_SERVICE = "UserService";
    public static final String RMI_FRIEND_SERVICE = "FriendService";
    public static final String RMI_PEER_DISCOVERY_SERVICE = "PeerDiscoveryService";

    // P2P
    public static final int P2P_PORT_START = 5000;
    public static final int P2P_PORT_END = 5999;

    // Message
    public static final int MAX_MESSAGE_LENGTH = 10000;
    public static final int MAX_FILE_SIZE_MB = 100;

    // OTP
    public static final int OTP_LENGTH = 6;
    public static final int OTP_EXPIRY_MINUTES = 5;

    // Session
    public static final int SESSION_TIMEOUT_MINUTES = 30;
}