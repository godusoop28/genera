package com.c21genera.expedientes.domain;

import com.c21genera.expedientes.CivilStatus;
import com.c21genera.expedientes.IdDocumentType;
import com.c21genera.expedientes.MaritalRegime;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Propietario, copropietario, representante legal o apoderado. No hay un
 * máximo: cada uno lleva sus propios datos, documentos y firma. En persona
 * moral, el participante OWNER es la sociedad (sin estado civil ni
 * identificación personal); quien la representa es un LEGAL_REPRESENTATIVE.
 */
@Entity
@Table(name = "expediente_participant")
public class ExpedienteParticipant {

  @Id
  private UUID id;

  @Column(nullable = false)
  private UUID expedienteId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 24)
  private ParticipantRole role;

  @Column(nullable = false)
  private String fullName;

  @Column(nullable = false)
  private int ordinal;

  private String nationality;

  @Enumerated(EnumType.STRING)
  @Column(length = 24)
  private IdDocumentType idDocumentType;

  private String idDocumentNumber;
  private String idDocumentIssuer;
  private LocalDate birthDate;

  @Enumerated(EnumType.STRING)
  @Column(length = 16)
  private CivilStatus civilStatus;

  @Enumerated(EnumType.STRING)
  @Column(length = 24)
  private MaritalRegime maritalRegime;

  @Column(length = 13)
  private String rfc;

  @Column(length = 18)
  private String curp;

  private String email;
  private String phone;
  private String address;

  protected ExpedienteParticipant() {}

  public ExpedienteParticipant(UUID expedienteId, ParticipantRole role, String fullName, int ordinal) {
    this.id = UUID.randomUUID();
    this.expedienteId = expedienteId;
    this.role = role;
    this.fullName = fullName;
    this.ordinal = ordinal;
  }

  public record Details(
      String nationality,
      IdDocumentType idDocumentType,
      String idDocumentNumber,
      String idDocumentIssuer,
      LocalDate birthDate,
      CivilStatus civilStatus,
      MaritalRegime maritalRegime,
      String rfc,
      String curp,
      String email,
      String phone,
      String address) {

    public static Details empty() {
      return new Details(null, null, null, null, null, null, null, null, null, null, null, null);
    }
  }

  /** Reemplaza todos los datos editables (lo usa el staff al corregir). */
  public void update(ParticipantRole role, String fullName, Details details) {
    this.role = role;
    this.fullName = fullName;
    this.nationality = blankToNull(details.nationality());
    this.idDocumentType = details.idDocumentType();
    this.idDocumentNumber = blankToNull(details.idDocumentNumber());
    this.idDocumentIssuer = blankToNull(details.idDocumentIssuer());
    this.birthDate = details.birthDate();
    this.civilStatus = details.civilStatus();
    this.maritalRegime = details.civilStatus() == CivilStatus.CASADO ? details.maritalRegime() : null;
    this.rfc = upperOrNull(details.rfc());
    this.curp = upperOrNull(details.curp());
    this.email = blankToNull(details.email());
    this.phone = blankToNull(details.phone());
    this.address = blankToNull(details.address());
  }

  /** El cliente, desde su liga, solo puede declarar su estado civil. */
  public void declareCivilStatus(CivilStatus civilStatus, MaritalRegime maritalRegime) {
    this.civilStatus = civilStatus;
    this.maritalRegime = civilStatus == CivilStatus.CASADO ? maritalRegime : null;
  }

  public void moveTo(int ordinal) {
    this.ordinal = ordinal;
  }

  public Details details() {
    return new Details(
        nationality, idDocumentType, idDocumentNumber, idDocumentIssuer, birthDate, civilStatus, maritalRegime, rfc, curp, email,
        phone, address);
  }

  public boolean isOwner() {
    return role == ParticipantRole.OWNER || role == ParticipantRole.CO_OWNER;
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.strip();
  }

  private static String upperOrNull(String value) {
    String cleaned = blankToNull(value);
    return cleaned == null ? null : cleaned.toUpperCase(java.util.Locale.ROOT);
  }

  public UUID getId() {
    return id;
  }

  public UUID getExpedienteId() {
    return expedienteId;
  }

  public ParticipantRole getRole() {
    return role;
  }

  public String getFullName() {
    return fullName;
  }

  public int getOrdinal() {
    return ordinal;
  }

  public CivilStatus getCivilStatus() {
    return civilStatus;
  }
}
