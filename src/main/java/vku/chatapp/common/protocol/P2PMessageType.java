package vku.chatapp.common.protocol;

public enum P2PMessageType {
    TEXT_MESSAGE,
    FILE_TRANSFER,
    CALL_OFFER,
    CALL_ANSWER,
    CALL_REJECT,
    CALL_END,
    AUDIO_STREAM,
    VIDEO_STREAM,
    TYPING_INDICATOR,
    READ_RECEIPT,
    HEARTBEAT
}