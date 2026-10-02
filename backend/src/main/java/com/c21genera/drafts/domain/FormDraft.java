package com.c21genera.drafts.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Lo capturado en un formulario que todavía no se ha "guardado" formalmente
 * (p. ej. el alta de un expediente o una corrección de datos del contrato que
 * requiere motivo). Se autoguarda en el servidor mientras se escribe, así
 * recargar la página, cambiar de equipo o una caída de red no hace perder lo
 * capturado. owner: "user:&lt;id&gt;" (staff) o "link:&lt;expediente&gt;" (cliente).
 */
@Entity
@Table(name = "form_draft")
public class FormDraft {

  @Id
  private UUID id;

  @Column(nullable = false, length = 80)
  private String ownerKey;

  @Column(nullable = false, length = 160)
  private String formKey;

  @Column(nullable = false)
  private String payload;

  @Column(nullable = false)
  private Instant updatedAt;

  protected FormDraft() {}

  public FormDraft(String ownerKey, String formKey, String payload, Instant now) {
    this.id = UUID.randomUUID();
    this.ownerKey = ownerKey;
    this.formKey = formKey;
    this.payload = payload;
    this.updatedAt = now;
  }

  public void update(String payload, Instant now) {
    this.payload = payload;
    this.updatedAt = now;
  }

  public String getFormKey() {
    return formKey;
  }

  public String getPayload() {
    return payload;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
