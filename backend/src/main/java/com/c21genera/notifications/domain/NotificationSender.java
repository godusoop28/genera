package com.c21genera.notifications.domain;

import java.util.List;

/**
 * Puerto de envío de correo (ver AGENTS §45-47). El dominio nunca conoce el
 * proveedor SMTP real detrás; en tests/local se puede sustituir por una
 * implementación en memoria.
 */
public interface NotificationSender {

  void send(EmailMessage message);

  record EmailAttachment(String filename, byte[] content, String contentType) {}

  record EmailMessage(String to, String subject, String textBody, List<EmailAttachment> attachments) {}
}
