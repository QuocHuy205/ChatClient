package vku.chatapp.common.exception;

public class ChatAppException extends Exception {
    public ChatAppException(String message) {
        super(message);
    }

    public ChatAppException(String message, Throwable cause) {
        super(message, cause);
    }
}
