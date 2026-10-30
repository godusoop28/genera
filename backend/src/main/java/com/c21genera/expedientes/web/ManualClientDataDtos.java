package com.c21genera.expedientes.web;

import com.c21genera.expedientes.CivilStatus;
import com.c21genera.expedientes.domain.ManualClientData;
import com.c21genera.expedientes.domain.ManualClientData.ManualClientDataUpdate;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
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
      // CC#34: precio sugerido por el cliente (informativo).
      @DecimalMin(value = "0.00", inclusive = false, message = "El precio sugerido debe ser mayor a cero")
          @Digits(integer = 12, fraction = 2, message = "El precio sugerido admite hasta 12 dígitos y 2 decimales")
          BigDecimal suggestedPrice,
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
          builtAreaM2,
          suggestedPrice,
          false);
    }
  }

  /**
   * Lo único que el cliente puede modificar desde su liga pública. Antes el
   * portal aceptaba el mismo cuerpo que el staff, así que alguien con la liga
   * podía cambiar, por ejemplo, el precio autorizado.
   */
  public record PublicClientDataRequest(
      @Email String email,
      String phone,
      String notificationAddress,
      CivilStatus civilStatus,
      // CC#34: precio de venta sugerido por el cliente (MXN, opcional e informativo).
      @DecimalMin(value = "0.00", inclusive = false, message = "El precio sugerido debe ser mayor a cero")
          @Digits(integer = 12, fraction = 2, message = "El precio sugerido admite hasta 12 dígitos y 2 decimales")
          BigDecimal suggestedPrice,
      // true = el cliente dejó el precio sugerido vacío (se borra el anterior). null/false = no se toca.
      Boolean clearSuggestedPrice) {}

  public record PublicClientDataResponse(String email, String phone, String notificationAddress, CivilStatus civilStatus, BigDecimal suggestedPrice) {

    public static PublicClientDataResponse from(ManualClientData d) {
      return new PublicClientDataResponse(d.getEmail(), d.getPhone(), d.getNotificationAddress(), d.getCivilStatus(), d.getSuggestedPrice());
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
      BigDecimal builtAreaM2,
      BigDecimal suggestedPrice) {

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
          d.getBuiltAreaM2(),
          d.getSuggestedPrice());
    }
  }
}
