package com.c21genera.drafts.application;

import com.c21genera.drafts.domain.FormDraft;
import com.c21genera.drafts.infrastructure.FormDraftRepository;
import com.c21genera.shared.domain.UnprocessableException;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Autoguardado de formularios en el servidor (ver {@link FormDraft}). Cada
 * dueño solo ve sus borradores: el staff por su usuario, el cliente por el
 * expediente de su liga (resuelta del token, nunca de un id que mande).
 */
@Service
@Transactional
public class FormDraftService {

  /** Un borrador es texto de formulario: con esto sobra y evita abusos. */
  static final int MAX_PAYLOAD_CHARS = 200_000;

  private static final Pattern FORM_KEY = Pattern.compile("[A-Za-z0-9:._-]{1,160}");

  private final FormDraftRepository repository;
  private final Clock clock;

  public FormDraftService(FormDraftRepository repository, Clock clock) {
    this.repository = repository;
    this.clock = clock;
  }

  public record DraftView(String formKey, String payload, Instant updatedAt) {}

  public static String userOwner(UUID userId) {
    return "user:" + userId;
  }

  public static String linkOwner(UUID expedienteId) {
    return "link:" + expedienteId;
  }

  @Transactional(readOnly = true)
  public Optional<DraftView> find(String ownerKey, String formKey) {
    validateKey(formKey);
    return repository.findByOwnerKeyAndFormKey(ownerKey, formKey).map(d -> new DraftView(d.getFormKey(), d.getPayload(), d.getUpdatedAt()));
  }

  public DraftView save(String ownerKey, String formKey, String payload) {
    validateKey(formKey);
    if (payload == null || payload.isBlank()) {
      throw new UnprocessableException("DRAFT_EMPTY", "El borrador está vacío.");
    }
    if (payload.length() > MAX_PAYLOAD_CHARS) {
      throw new UnprocessableException("DRAFT_TOO_LARGE", "El borrador es demasiado grande para guardarse.");
    }
    Instant now = clock.instant();
    FormDraft draft =
        repository
            .findByOwnerKeyAndFormKey(ownerKey, formKey)
            .map(
                existing -> {
                  existing.update(payload, now);
                  return existing;
                })
            .orElseGet(() -> repository.save(new FormDraft(ownerKey, formKey, payload, now)));
    return new DraftView(draft.getFormKey(), draft.getPayload(), draft.getUpdatedAt());
  }

  public void delete(String ownerKey, String formKey) {
    validateKey(formKey);
    repository.deleteByOwnerKeyAndFormKey(ownerKey, formKey);
  }

  private static void validateKey(String formKey) {
    if (formKey == null || !FORM_KEY.matcher(formKey).matches()) {
      throw new UnprocessableException("DRAFT_KEY_INVALID", "Identificador de borrador inválido.");
    }
  }
}
