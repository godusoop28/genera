package com.c21genera.drafts.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.c21genera.drafts.domain.FormDraft;
import com.c21genera.drafts.infrastructure.FormDraftRepository;
import com.c21genera.shared.domain.UnprocessableException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Al refrescar, el formulario se reconstruye con lo que se autoguardó en el servidor. */
class FormDraftServiceTest {

  private final Map<String, FormDraft> table = new HashMap<>();
  private FormDraftService service;

  @BeforeEach
  void setUp() {
    FormDraftRepository repository = mock(FormDraftRepository.class);
    when(repository.findByOwnerKeyAndFormKey(anyString(), anyString()))
        .thenAnswer(inv -> Optional.ofNullable(table.get(inv.getArgument(0) + "|" + inv.getArgument(1))));
    when(repository.save(any()))
        .thenAnswer(
            inv -> {
              FormDraft draft = inv.getArgument(0);
              // La clave real (dueño|formulario) la decide el servicio; aquí basta con la del primer guardado.
              table.put(lastOwner + "|" + draft.getFormKey(), draft);
              return draft;
            });
    service = new FormDraftService(repository, Clock.fixed(Instant.parse("2026-10-01T12:00:00Z"), ZoneOffset.UTC));
  }

  private String lastOwner;

  private void save(String owner, String key, String payload) {
    lastOwner = owner;
    service.save(owner, key, payload);
  }

  @Test
  void whatWasAutosavedIsReturnedAfterARefreshAndUpdatesOverwriteIt() {
    String owner = FormDraftService.userOwner(UUID.randomUUID());
    save(owner, "nuevo-expediente", "{\"owners\":[{\"fullName\":\"Juan\"}]}");
    save(owner, "nuevo-expediente", "{\"owners\":[{\"fullName\":\"Juan Pérez\"}]}");

    assertThat(service.find(owner, "nuevo-expediente")).hasValueSatisfying(d -> assertThat(d.payload()).contains("Juan Pérez"));
  }

  @Test
  void eachOwnerOnlySeesItsOwnDrafts() {
    String client = FormDraftService.linkOwner(UUID.randomUUID());
    save(client, "client-data", "{\"email\":\"a@b.mx\"}");

    assertThat(service.find(FormDraftService.linkOwner(UUID.randomUUID()), "client-data")).isEmpty();
  }

  @Test
  void invalidKeysAndHugePayloadsAreRejected() {
    String owner = FormDraftService.userOwner(UUID.randomUUID());
    assertThatThrownBy(() -> service.save(owner, "../otro", "{}")).isInstanceOf(UnprocessableException.class);
    assertThatThrownBy(() -> service.save(owner, "ok", "x".repeat(FormDraftService.MAX_PAYLOAD_CHARS + 1)))
        .isInstanceOf(UnprocessableException.class);
  }
}
