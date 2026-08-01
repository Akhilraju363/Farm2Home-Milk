package com.farm2home.common.core.sms;

public record SmsSendResult(boolean success, String providerMessageId, String failureReason) {

    public static SmsSendResult success(String providerMessageId) {
        return new SmsSendResult(true, providerMessageId, null);
    }

    public static SmsSendResult failure(String failureReason) {
        return new SmsSendResult(false, null, failureReason);
    }
}
