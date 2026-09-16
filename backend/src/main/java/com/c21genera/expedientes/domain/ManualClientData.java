package com.c21genera.expedientes.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Datos declarados manualmente (ver AGENTS §62). El porcentaje de indiviso
 * NO vive aquí a propósito: debe provenir de un documento (escritura /
 * régimen de condominio), no ser un dato manual por defecto.
 */
@Entity
@Table(name = "manual_client_data")
public class ManualClientData {

  @Id
  private UUID expedienteId;

  @Enumerated(EnumType.STRING)
  @Column(length = 16)
  private CivilStatus civilStatus;

  @Column(precision = 14, scale = 2)
  private BigDecimal authorizedPrice;

  private String email;
  private String phone;
  private String notificationAddress;
  private String visitInstructions;
  private Boolean marketingDataAuthorized;
  private Boolean receiveAdsAuthorized;
  private String additionalServicesRequested;
  private Integer bedrooms;
  private Integer bathrooms;
  private Integer parkingSpots;
  private String conservationStatus;
  private String availableServices;
  private String relevantFeatures;
  private LocalDate contractSignatureDate;

  protected ManualClientData() {}

  public ManualClientData(UUID expedienteId) {
    this.expedienteId = expedienteId;
  }

  public void update(ManualClientDataUpdate update) {
    this.civilStatus = update.civilStatus();
    this.authorizedPrice = update.authorizedPrice();
    this.email = update.email();
    this.phone = update.phone();
    this.notificationAddress = update.notificationAddress();
    this.visitInstructions = update.visitInstructions();
    this.marketingDataAuthorized = update.marketingDataAuthorized();
    this.receiveAdsAuthorized = update.receiveAdsAuthorized();
    this.additionalServicesRequested = update.additionalServicesRequested();
    this.bedrooms = update.bedrooms();
    this.bathrooms = update.bathrooms();
    this.parkingSpots = update.parkingSpots();
    this.conservationStatus = update.conservationStatus();
    this.availableServices = update.availableServices();
    this.relevantFeatures = update.relevantFeatures();
    this.contractSignatureDate = update.contractSignatureDate();
  }

  public UUID getExpedienteId() {
    return expedienteId;
  }

  public CivilStatus getCivilStatus() {
    return civilStatus;
  }

  public BigDecimal getAuthorizedPrice() {
    return authorizedPrice;
  }

  public String getEmail() {
    return email;
  }

  public String getPhone() {
    return phone;
  }

  public String getNotificationAddress() {
    return notificationAddress;
  }

  public String getVisitInstructions() {
    return visitInstructions;
  }

  public Boolean getMarketingDataAuthorized() {
    return marketingDataAuthorized;
  }

  public Boolean getReceiveAdsAuthorized() {
    return receiveAdsAuthorized;
  }

  public String getAdditionalServicesRequested() {
    return additionalServicesRequested;
  }

  public Integer getBedrooms() {
    return bedrooms;
  }

  public Integer getBathrooms() {
    return bathrooms;
  }

  public Integer getParkingSpots() {
    return parkingSpots;
  }

  public String getConservationStatus() {
    return conservationStatus;
  }

  public String getAvailableServices() {
    return availableServices;
  }

  public String getRelevantFeatures() {
    return relevantFeatures;
  }

  public LocalDate getContractSignatureDate() {
    return contractSignatureDate;
  }

  public record ManualClientDataUpdate(
      CivilStatus civilStatus,
      BigDecimal authorizedPrice,
      String email,
      String phone,
      String notificationAddress,
      String visitInstructions,
      Boolean marketingDataAuthorized,
      Boolean receiveAdsAuthorized,
      String additionalServicesRequested,
      Integer bedrooms,
      Integer bathrooms,
      Integer parkingSpots,
      String conservationStatus,
      String availableServices,
      String relevantFeatures,
      LocalDate contractSignatureDate) {}
}
