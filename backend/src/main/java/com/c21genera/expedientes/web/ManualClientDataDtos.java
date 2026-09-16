package com.c21genera.expedientes.web;

import com.c21genera.expedientes.domain.CivilStatus;
import com.c21genera.expedientes.domain.ManualClientData;
import com.c21genera.expedientes.domain.ManualClientData.ManualClientDataUpdate;
import java.math.BigDecimal;
import java.time.LocalDate;

public final class ManualClientDataDtos {

  private ManualClientDataDtos() {}

  public record ManualClientDataRequest(
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
      LocalDate contractSignatureDate) {

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
          contractSignatureDate);
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
      LocalDate contractSignatureDate) {

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
          d.getContractSignatureDate());
    }
  }
}
