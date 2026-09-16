package com.c21genera.notifications.application;

import com.c21genera.notifications.domain.NotificationSender;
import com.c21genera.notifications.domain.NotificationSender.EmailAttachment;
import com.c21genera.notifications.domain.NotificationSender.EmailMessage;
import com.c21genera.shared.jobs.BackgroundJob;
import com.c21genera.shared.jobs.BackgroundJobQueue;
import com.c21genera.shared.storage.FileStorage;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Consumidor del outbox de correos (ver AGENTS §45): el envío SMTP ocurre aquí, fuera del request original. */
@Component
public class NotificationWorker {

  private static final Logger log = LoggerFactory.getLogger(NotificationWorker.class);
  private static final int BATCH_SIZE = 10;
  private static final String WORKER_ID = "notifications-worker";

  private final BackgroundJobQueue queue;
  private final NotificationSender sender;
  private final FileStorage fileStorage;

  public NotificationWorker(BackgroundJobQueue queue, NotificationSender sender, FileStorage fileStorage) {
    this.queue = queue;
    this.sender = sender;
    this.fileStorage = fileStorage;
  }

  @Scheduled(fixedDelayString = "PT5S", initialDelayString = "PT12S")
  public void pollAndProcess() {
    List<BackgroundJob> jobs = queue.claimBatch(NotificationService.JOB_TYPE, WORKER_ID, BATCH_SIZE);
    for (BackgroundJob job : jobs) {
      handle(job);
    }
  }

  private void handle(BackgroundJob job) {
    SendEmailPayload payload = queue.readPayload(job, SendEmailPayload.class);
    try {
      List<EmailAttachment> attachments = new ArrayList<>();
      for (SendEmailPayload.AttachmentRef ref : payload.attachments()) {
        byte[] content = readAll(ref.storageKey());
        attachments.add(new EmailAttachment(ref.filename(), content, "application/pdf"));
      }
      sender.send(new EmailMessage(payload.recipientEmail(), payload.subject(), payload.textBody(), attachments));
      queue.markDone(job);
    } catch (Exception e) {
      log.error("Fallo enviando correo a destinatario configurado en el job {}", job.getId(), e);
      queue.markFailedOrRetry(job, e.getMessage());
    }
  }

  private byte[] readAll(String storageKey) {
    try (InputStream in = fileStorage.get(storageKey)) {
      return in.readAllBytes();
    } catch (Exception e) {
      throw new IllegalStateException("No se pudo leer el adjunto " + storageKey, e);
    }
  }
}
