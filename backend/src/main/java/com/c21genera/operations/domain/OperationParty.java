package com.c21genera.operations.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/** CC#32: vendedor o comprador de la operación (en renta: arrendador o arrendatario), con su representante. */
@Entity
@Table(name = "closing_operation_party")
public class OperationParty {

  public enum PartySide {
    SELLER,
    BUYER
  }

  @Id
  private UUID id;

  @Column(nullable = false)
  private UUID operationId;

  @Column(nullable = false, length = 16)
  @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
  private PartySide side;

  @Column(nullable = false)
  private String fullName;

  private String personType;
  private String representativeName;
  private String representativeRole;
  private String email;
  private String phone;
  private String counterpartContact;
  private String notes;

  protected OperationParty() {}

  public OperationParty(UUID operationId, PartySide side) {
    this.id = UUID.randomUUID();
    this.operationId = operationId;
    this.side = side;
  }

  public void update(
      String fullName, String personType, String representativeName, String representativeRole, String email, String phone, String counterpartContact, String notes) {
    this.fullName = fullName;
    this.personType = personType;
    this.representativeName = representativeName;
    this.representativeRole = representativeRole;
    this.email = email;
    this.phone = phone;
    this.counterpartContact = counterpartContact;
    this.notes = notes;
  }

  public UUID getId() {
    return id;
  }

  public UUID getOperationId() {
    return operationId;
  }

  public PartySide getSide() {
    return side;
  }

  public String getFullName() {
    return fullName;
  }

  public String getPersonType() {
    return personType;
  }

  public String getRepresentativeName() {
    return representativeName;
  }

  public String getRepresentativeRole() {
    return representativeRole;
  }

  public String getEmail() {
    return email;
  }

  public String getPhone() {
    return phone;
  }

  public String getCounterpartContact() {
    return counterpartContact;
  }

  public String getNotes() {
    return notes;
  }
}
