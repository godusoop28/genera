package com.c21genera.extraction.domain;

import com.c21genera.shared.domain.DocumentTypeCode;
import java.util.List;
import java.util.Map;

/**
 * Campos que se intentan extraer por tipo de documento (ver AGENTS §38):
 * los que se cruzan entre documentos para la prueba de consistencia y los
 * que el contrato necesita (escritura, notaría, acta constitutiva, poder,
 * régimen de condominio, identificación), para prellenarlo.
 */
public final class DocumentFieldSchemas {

  private DocumentFieldSchemas() {}

  private static final Map<DocumentTypeCode, List<String>> SCHEMAS =
      Map.ofEntries(
          Map.entry(DocumentTypeCode.INE, List.of("fullName", "curp", "electorKey", "birthDate", "address", "expirationYear")),
          Map.entry(DocumentTypeCode.PASSPORT, List.of("fullName", "passportNumber", "nationality", "birthDate", "curp")),
          Map.entry(DocumentTypeCode.CURP, List.of("fullName", "curp", "birthDate")),
          Map.entry(DocumentTypeCode.TAX_STATUS_CERTIFICATE, List.of("fullName", "rfc", "curp", "taxRegime", "address")),
          Map.entry(
              DocumentTypeCode.DEED,
              List.of(
                  "ownerFullName",
                  "propertyAddress",
                  "deedNumber",
                  "deedDate",
                  "notaryName",
                  "notaryNumber",
                  "notaryPlace",
                  "publicRegistryFolio",
                  "landArea",
                  "builtArea")),
          Map.entry(DocumentTypeCode.PROOF_OF_ADDRESS, List.of("fullName", "address", "issueDate")),
          Map.entry(DocumentTypeCode.LIEN_CERTIFICATE, List.of("ownerFullName", "propertyAddress", "publicRegistryFolio", "hasLiens")),
          Map.entry(
              DocumentTypeCode.PROPERTY_TAX, List.of("ownerFullName", "propertyAddress", "cadastralKey", "landArea", "builtArea")),
          Map.entry(
              DocumentTypeCode.POWER_OF_ATTORNEY,
              List.of(
                  "grantorFullName",
                  "attorneyFullName",
                  "instrumentNumber",
                  "instrumentDate",
                  "notaryName",
                  "notaryNumber",
                  "notaryPlace",
                  "publicRegistryFolio")),
          Map.entry(
              DocumentTypeCode.CONDOMINIUM_REGIME,
              List.of(
                  "propertyAddress",
                  "deedNumber",
                  "deedDate",
                  "notaryName",
                  "notaryNumber",
                  "notaryPlace",
                  "registryDate",
                  "regimeRegistrationFolio")),
          Map.entry(DocumentTypeCode.ELECTRICITY_RECEIPT, List.of("fullName", "address", "issueDate")),
          Map.entry(DocumentTypeCode.WATER_RECEIPT, List.of("fullName", "address", "issueDate")),
          Map.entry(DocumentTypeCode.CADASTRAL_PLAN, List.of("propertyAddress", "cadastralKey", "landArea", "builtArea")),
          Map.entry(DocumentTypeCode.RPP_REGISTRATION_SLIP, List.of("ownerFullName", "propertyAddress", "publicRegistryFolio")),
          Map.entry(DocumentTypeCode.MARRIAGE_CERTIFICATE, List.of("fullName", "spouseFullName", "marriageDate", "maritalRegime")),
          Map.entry(
              DocumentTypeCode.PRIVATE_CONTRACT,
              List.of(
                  "sellerFullName",
                  "buyerFullName",
                  "propertyAddress",
                  "contractDate",
                  "ratificationDate",
                  "notaryName",
                  "notaryNumber",
                  "notaryPlace",
                  "registryDate",
                  "publicRegistryFolio",
                  "landArea")),
          Map.entry(
              DocumentTypeCode.INCORPORATION_DEED,
              List.of(
                  "companyName",
                  "companyType",
                  "instrumentNumber",
                  "instrumentDate",
                  "notaryTitle",
                  "notaryName",
                  "notaryNumber",
                  "notaryPlace",
                  "commerceRegistryPlace",
                  "mercantileFolio")));

  /** Qué significa cada campo, para indicárselo a la IA junto con su nombre. */
  private static final Map<String, String> DESCRIPTIONS =
      Map.ofEntries(
          Map.entry("fullName", "nombre completo de la persona"),
          Map.entry("curp", "CURP (18 caracteres)"),
          Map.entry("electorKey", "clave de elector de la credencial"),
          Map.entry("birthDate", "fecha de nacimiento"),
          Map.entry("address", "domicilio completo"),
          Map.entry("expirationYear", "año de vigencia"),
          Map.entry("passportNumber", "número de pasaporte"),
          Map.entry("nationality", "nacionalidad"),
          Map.entry("rfc", "RFC con homoclave"),
          Map.entry("taxRegime", "régimen fiscal"),
          Map.entry("ownerFullName", "nombre del propietario o adquirente del inmueble (si son varios, todos separados por coma)"),
          Map.entry("propertyAddress", "ubicación completa del inmueble"),
          Map.entry("deedNumber", "número de la escritura o instrumento"),
          Map.entry("deedDate", "fecha en que se otorgó la escritura"),
          Map.entry("notaryName", "nombre del notario o corredor público que da fe"),
          Map.entry("notaryNumber", "número de la notaría o correduría"),
          Map.entry("notaryPlace", "ciudad y estado de la notaría"),
          Map.entry("notaryTitle", "si quien da fe es Notario o Corredor"),
          Map.entry("publicRegistryFolio", "folio real o datos de inscripción en el Registro Público"),
          Map.entry("registryDate", "fecha de inscripción en el Registro Público"),
          Map.entry("landArea", "superficie del terreno en m²"),
          Map.entry("builtArea", "superficie construida en m²"),
          Map.entry("issueDate", "fecha de emisión"),
          Map.entry("hasLiens", "si reporta gravámenes (sí/no y cuáles)"),
          Map.entry("cadastralKey", "clave catastral"),
          Map.entry("grantorFullName", "nombre de quien otorga el poder"),
          Map.entry("attorneyFullName", "nombre del apoderado"),
          Map.entry("instrumentNumber", "número del instrumento notarial"),
          Map.entry("instrumentDate", "fecha del instrumento"),
          Map.entry("regimeRegistrationFolio", "folio real de la constitución del régimen de condominio"),
          Map.entry("spouseFullName", "nombre del cónyuge"),
          Map.entry("marriageDate", "fecha del matrimonio"),
          Map.entry("maritalRegime", "régimen matrimonial (sociedad conyugal o separación de bienes)"),
          Map.entry("sellerFullName", "nombre del vendedor"),
          Map.entry("buyerFullName", "nombre del comprador"),
          Map.entry("contractDate", "fecha del contrato"),
          Map.entry("ratificationDate", "fecha de ratificación de firmas"),
          Map.entry("companyName", "razón social"),
          Map.entry("companyType", "tipo de sociedad (p. ej. Sociedad Anónima de Capital Variable)"),
          Map.entry("commerceRegistryPlace", "ciudad del Registro Público de Comercio"),
          Map.entry("mercantileFolio", "folio mercantil"));

  /** "campo (qué es)" para cada campo, en el orden recibido. */
  public static List<String> describe(List<String> fieldNames) {
    return fieldNames.stream().map(f -> DESCRIPTIONS.containsKey(f) ? f + " (" + DESCRIPTIONS.get(f) + ")" : f).toList();
  }

  /** Documentos fuera de esta lista no tienen esquema de extracción todavía: se devuelve vacío. */
  public static List<String> fieldsFor(DocumentTypeCode type) {
    return SCHEMAS.getOrDefault(type, List.of());
  }
}
