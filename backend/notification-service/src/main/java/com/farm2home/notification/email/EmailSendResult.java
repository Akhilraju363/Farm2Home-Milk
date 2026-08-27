package com.farm2home.notification.email;

/** Mirrors {@code com.farm2home.common.core.sms.SmsSendResult}. */
public record EmailSendResult(boolean success, String providerMessageId, String failureReason) {

    public static EmailSendResult success(String providerMessageId) {
        return new EmailSendResult(true, providerMessageId, null);
    }

    public static EmailSendResult failure(String failureReason) {
        return new EmailSendResult(false, null, failureReason);
    }
}
