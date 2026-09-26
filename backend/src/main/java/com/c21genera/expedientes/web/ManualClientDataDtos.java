package com.c21genera.expedientes.web;

import com.c21genera.expedientes.CivilStatus;
import com.c21genera.expedientes.domain.ManualClientData;
import com.c21genera.expedientes.domain.ManualClientData.ManualClientDataUpdate;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import java.math.BigDecimal;
import java.time.LocalDate;

public final class ManualClientDataDtos {

  private ManualClientDataDtos() {}

  /** Uso interno (staff). reason queda en el historial de correcciones. */
  public record ManualClientDataRequest(
      CivilStatus civilStatus,
      @DecimalMin(value = "0.00", inclusive = false, message = "El precio debe ser mayor a cero") BigDecimal authorizedPrice,
      @Email String email,
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
      LocalDate contractSignatureDate,
      BigDecimal landAreaM2,
      BigDecimal builtAreaM2,
      String reason) {

    public ManualClientDataUpdate toUpdate() {
      return new ManualClientDataUpdate(
          civilStatus,
          authorizedPrice,
          email,
          phone,
          notificationAddress,
          visitInstructions,
          marketingDataAuthorized,
          receiveAdsAuthorized,
          additionalServicesRequested,
          bedrooms,
          bathrooms,
          parkingSpots,
          conservationStatus,
          availableServices,
          relevantFeatures,
          contractSignatureDate,
          landAreaM2,
          builtAreaM2);
    }
  }

  /**
   * Lo único que el cliente puede modificar desde su liga pública. Antes el
   * portal aceptaba el mismo cuerpo que el staff, así que alguien con la liga
   * podía cambiar, por ejemplo, el precio autorizado.
   */
  public record PublicClientDataRequest(@Email String email, String phone, String notificationAddress, CivilStatus civilStatus) {}

  public record PublicClientDataResponse(String email, String phone, String notificationAddress, CivilStatus civilStatus) {

    public static PublicClientDataResponse from(ManualClientData d) {
      return new PublicClientDataResponse(d.getEmail(), d.getPhone(), d.getNotificationAddress(), d.getCivilStatus());
    }
  }

  public record ManualClientDataResponse(
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
      LocalDate contractSignatureDate,
      BigDecimal landAreaM2,
      BigDecimal builtAreaM2) {

    public static ManualClientDataResponse from(ManualClientData d) {
      return new ManualClientDataResponse(
          d.getCivilStatus(),
          d.getAuthorizedPrice(),
          d.getEmail(),
          d.getPhone(),
          d.getNotificationAddress(),
          d.getVisitInstructions(),
          d.getMarketingDataAuthorized(),
          d.getReceiveAdsAuthorized(),
          d.getAdditionalServicesRequested(),
          d.getBedrooms(),
          d.getBathrooms(),
          d.getParkingSpots(),
          d.getConservationStatus(),
          d.getAvailableServices(),
          d.getRelevantFeatures(),
          d.getContractSignatureDate(),
          d.getLandAreaM2(),
          d.getBuiltAreaM2());
    }
  }
}
