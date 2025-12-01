package vku.chatapp.common.util;

import org.mindrot.jbcrypt.BCrypt;

public class EncryptionUtil {
    public static String hashPassword(String password) {
        return BCrypt.hashpw(password, BCrypt.gensalt());
    }

    public static boolean verifyPassword(String password, String hash) {
        return BCrypt.checkpw(password, hash);
    }

    public static String generateToken() {
        return java.util.UUID.randomUUID().toString();
    }
}