package com.c21genera.expedientes;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Datos legales que el contrato de intermediación necesita y que dependen
 * del tipo de persona y de cómo se acredita la propiedad (ver el contrato
 * registrado ante PROFECO, declaraciones II.a-II.g). Todos los campos son
 * opcionales al capturarse: {@code contracts} valida cuáles son
 * obligatorios para la variante concreta antes de permitir generar un
 * contrato para firma.
 */
public record LegalDetails(
    CompanyData company,
    RepresentationData representation,
    DeedData deed,
    PrivateContractData privateContract,
    CondominiumData condominium,
    Map<String, Boolean> propertyChecklist,
    String advertisingMedia,
    /* Control de Cambios #48: a qué terreno o parte corresponde la escritura principal. */
    String deedLabel,
    Boolean deedIncludesConstruction,
    /* Control de Cambios #48: otras escrituras de la misma operación, cada una con su documento. */
    List<AdditionalDeed> additionalDeeds) {

  public LegalDetails(
      CompanyData company,
      RepresentationData representation,
      DeedData deed,
      PrivateContractData privateContract,
      CondominiumData condominium,
      Map<String, Boolean> propertyChecklist,
      String advertisingMedia) {
    this(company, representation, deed, privateContract, condominium, propertyChecklist, advertisingMedia, null, null, null);
  }

  public static LegalDetails empty() {
    return new LegalDetails(null, null, null, null, null, Map.of(), null);
  }

  /**
   * Control de Cambios #48: una escritura más de la misma operación (p. ej. otro terreno o la
   * construcción). documentId: el documento «escritura adicional» del expediente del que salen sus
   * datos (nunca se mezclan con los de otra escritura). includeInContract: si el contrato la lista.
   */
  public record AdditionalDeed(
      java.util.UUID documentId,
      String label,
      Boolean includesConstruction,
      Boolean includeInContract,
      String number,
      LocalDate date,
      String notaryName,
      String notaryNumber,
      String notaryPlace,
      String registryData,
      LocalDate registryDate) {

    public DeedData asDeedData() {
      return new DeedData(number, date, notaryName, notaryNumber, notaryPlace, registryData, registryDate);
    }
  }

  /** Persona moral (declaración II.a.2). */
  public record CompanyData(
      String companyType,
      String rfc,
      String instrumentNumber,
      LocalDate instrumentDate,
      String notaryTitle,
      String notaryNumber,
      String notaryPlace,
      String notaryName,
      String commerceRegistryPlace,
      String mercantileFolio) {}

  /** Representante legal de persona moral o apoderado de persona física (declaración II.b). */
  public record RepresentationData(
      String capacity,
      String instrumentNumber,
      LocalDate instrumentDate,
      String notaryTitle,
      String notaryNumber,
      String notaryPlace,
      String notaryName,
      String registryPlace,
      String registryFolio) {}

  /** Propiedad acreditada con escritura pública (declaración II.d, primera opción). */
  public record DeedData(
      String number,
      LocalDate date,
      String notaryName,
      String notaryNumber,
      String notaryPlace,
      String registryData,
      /* Control de Cambios #19: fecha de inscripción en el Registro Público de la Propiedad. */
      LocalDate registryDate) {

    public DeedData(String number, LocalDate date, String notaryName, String notaryNumber, String notaryPlace, String registryData) {
      this(number, date, notaryName, notaryNumber, notaryPlace, registryData, null);
    }
  }

  /** Propiedad acreditada con contrato privado ratificado (declaración II.d, segunda opción). */
  public record PrivateContractData(
      String sellerName,
      String buyerName,
      LocalDate date,
      LocalDate ratificationDate,
      String ratifiedBefore,
      String notaryNumber,
      String notaryPlace,
      String notaryName,
      LocalDate registryDate,
      String registryPlace,
      String realFolio) {}

  /** Régimen de propiedad en condominio (declaración II.f). */
  public record CondominiumData(
      String deedNumber,
      LocalDate date,
      String notaryNumber,
      String notaryPlace,
      String notaryName,
      LocalDate registryDate,
      String realFolio) {}
}
