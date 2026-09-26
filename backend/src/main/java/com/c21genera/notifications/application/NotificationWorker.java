package com.c21genera.notifications.application;

import com.c21genera.notifications.domain.NotificationLog;
import com.c21genera.notifications.domain.NotificationSender;
import com.c21genera.notifications.domain.NotificationSender.EmailAttachment;
import com.c21genera.notifications.domain.NotificationSender.EmailMessage;
import com.c21genera.notifications.infrastructure.NotificationLogRepository;
import com.c21genera.shared.events.NotificationEvents.NotificationDelivered;
import com.c21genera.shared.events.NotificationEvents.NotificationDeliveryFailed;
import com.c21genera.shared.jobs.BackgroundJob;
import com.c21genera.shared.jobs.BackgroundJobQueue;
import com.c21genera.shared.jobs.JobStatus;
import com.c21genera.shared.storage.FileStorage;
import java.io.InputStream;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Consumidor del outbox de correos (ver AGENTS §45): el envío SMTP ocurre
 * aquí, fuera del request original. Cada intento actualiza notification_log;
 * si el último intento falla, queda FAILED con el error y se registra en la
 * bitácora del expediente (antes un fallo solo aparecía en los logs del
 * servidor y el staff creía que el correo había salido).
 */
@Component
public class NotificationWorker {

  private static final Logger log = LoggerFactory.getLogger(NotificationWorker.class);
  private static final int BATCH_SIZE = 10;
  private static final String WORKER_ID = "notifications-worker";

  private final BackgroundJobQueue queue;
  private final NotificationSender sender;
  private final FileStorage fileStorage;
  private final NotificationLogRepository logRepository;
  private final ApplicationEventPublisher events;
  private final TransactionTemplate transactions;
  private final Clock clock;

  public NotificationWorker(
      BackgroundJobQueue queue,
      NotificationSender sender,
      FileStorage fileStorage,
      NotificationLogRepository logRepository,
      ApplicationEventPublisher events,
      TransactionTemplate transactions,
      Clock clock) {
    this.queue = queue;
    this.sender = sender;
    this.fileStorage = fileStorage;
    this.logRepository = logRepository;
    this.events = events;
    this.transactions = transactions;
    this.clock = clock;
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
      List<SendEmailPayload.AttachmentRef> refs = payload.attachments() == null ? List.of() : payload.attachments();
      for (SendEmailPayload.AttachmentRef ref : refs) {
        byte[] content = readAll(ref.storageKey());
        attachments.add(new EmailAttachment(ref.filename(), content, "application/pdf"));
      }
      sender.send(new EmailMessage(payload.recipientEmail(), payload.subject(), payload.textBody(), attachments));
      queue.markDone(job);
      recordResult(payload, null, false);
    } catch (Exception e) {
      log.error("Fallo enviando correo del job {}", job.getId(), e);
      String error = describe(e);
      JobStatus status = queue.markFailedOrRetry(job, error);
      recordResult(payload, error, status == JobStatus.FAILED);
    }
  }

  private void recordResult(SendEmailPayload payload, String error, boolean finalFailure) {
    if (payload.notificationId() == null) {
      return; // job encolado antes de que existiera notification_log
    }
    transactions.executeWithoutResult(
        tx ->
            logRepository
                .findById(payload.notificationId())
                .ifPresent(
                    entry -> {
                      if (error == null) {
                        entry.markSent(clock.instant());
                        events.publishEvent(
                            new NotificationDelivered(entry.getExpedienteId(), entry.getId(), entry.getKind(), entry.maskedRecipient()));
                      } else {
                        entry.markAttemptFailed(error, finalFailure);
                        if (finalFailure) {
                          events.publishEvent(
                              new NotificationDeliveryFailed(
                                  entry.getExpedienteId(), entry.getId(), entry.getKind(), entry.maskedRecipient(), entry.getLastError()));
                        }
                      }
                    }));
  }

  /** Mensaje útil para el staff (p. ej. "Connection refused" = el servidor de correo no está configurado). */
  private static String describe(Exception e) {
    Throwable root = e;
    while (root.getCause() != null && root.getCause() != root) {
      root = root.getCause();
    }
    String message = root.getMessage() != null ? root.getMessage() : root.getClass().getSimpleName();
    if (message.toLowerCase(java.util.Locale.ROOT).contains("connection refused")) {
      return "El servidor de correo (SMTP) no está disponible o no está configurado: " + message;
    }
    return message;
  }

  private byte[] readAll(String storageKey) {
    try (InputStream in = fileStorage.get(storageKey)) {
      return in.readAllBytes();
    } catch (Exception e) {
      throw new IllegalStateException("No se pudo leer el adjunto " + storageKey, e);
    }
  }
}
