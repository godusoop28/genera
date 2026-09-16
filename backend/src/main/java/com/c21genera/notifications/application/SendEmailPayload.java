package com.c21genera.notifications.application;

import java.util.List;

public record SendEmailPayload(
    String recipientEmail, String subject, String textBody, List<AttachmentRef> attachments) {

  public record AttachmentRef(String storageKey, String filename) {}
}
