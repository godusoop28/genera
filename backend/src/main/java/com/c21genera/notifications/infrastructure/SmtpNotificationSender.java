package com.c21genera.notifications.infrastructure;

import com.c21genera.notifications.domain.NotificationSender;
import com.c21genera.shared.config.MailProperties;
import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

/** Adaptador SMTP real (Spring Mail); en local apunta a Mailpit (ver AGENTS §45, compose.yml). */
@Component
public class SmtpNotificationSender implements NotificationSender {

  private final JavaMailSender mailSender;
  private final MailProperties properties;

  public SmtpNotificationSender(JavaMailSender mailSender, MailProperties properties) {
    this.mailSender = mailSender;
    this.properties = properties;
  }

  @Override
  public void send(EmailMessage message) {
    try {
      MimeMessage mimeMessage = mailSender.createMimeMessage();
      MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, !message.attachments().isEmpty(), "UTF-8");
      helper.setFrom(properties.from());
      helper.setTo(message.to());
      helper.setSubject(message.subject());
      helper.setText(message.textBody());
      for (EmailAttachment attachment : message.attachments()) {
        helper.addAttachment(attachment.filename(), new org.springframework.core.io.ByteArrayResource(attachment.content()), attachment.contentType());
      }
      mailSender.send(mimeMessage);
    } catch (Exception e) {
      throw new IllegalStateException("No se pudo enviar el correo a " + message.to(), e);
    }
  }
}
