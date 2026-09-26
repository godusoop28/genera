package com.c21genera.notifications.application;

import java.util.List;
import java.util.UUID;

/** notificationId: renglón de notification_log que se actualiza con el resultado del envío. */
public record SendEmailPayload(
    UUID notificationId, UUID expedienteId, String recipientEmail, String subject, String textBody, List<AttachmentRef> attachments) {

  public record AttachmentRef(String storageKey, String filename) {}
}
