package com.farm2home.common.core.push;

public record PushSendResult(boolean success, String providerMessageId, String failureReason) {

    public static PushSendResult success(String providerMessageId) {
        return new PushSendResult(true, providerMessageId, null);
    }

    public static PushSendResult failure(String failureReason) {
        return new PushSendResult(false, null, failureReason);
    }
}
