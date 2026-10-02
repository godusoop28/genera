package com.c21genera.extraction.domain;

import com.c21genera.shared.domain.DocumentTypeCode;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Campos que se intentan extraer por tipo de documento (ver AGENTS §38):
 * los que se cruzan entre documentos para la prueba de consistencia, los que
 * el contrato necesita para prellenarse y los que ayudan al revisor. TODOS
 * los tipos tienen esquema: ninguno se queda sin extracción. Una extracción
 * parcial es válida (lo que no se ve se queda sin dato), y los datos útiles
 * fuera del esquema se conservan aparte (ver {@link #EXTRA_PREFIX}).
 */
public final class DocumentFieldSchemas {

  private DocumentFieldSchemas() {}

  /** Prefijo de los datos útiles que la IA encontró fuera del esquema del tipo ("extra.Sección electoral"). */
  public static final String EXTRA_PREFIX = "extra.";

  private static final List<String> NOTARY = List.of("notaryName", "notaryNumber", "notaryPlace");

  private static final Map<DocumentTypeCode, List<String>> SCHEMAS = new EnumMap<>(DocumentTypeCode.class);

  static {
    SCHEMAS.put(DocumentTypeCode.INE, List.of("fullName", "curp", "electorKey", "birthDate", "address", "expirationYear"));
    SCHEMAS.put(DocumentTypeCode.PASSPORT, List.of("fullName", "passportNumber", "nationality", "birthDate", "curp", "expirationDate"));
    SCHEMAS.put(DocumentTypeCode.CURP, List.of("fullName", "curp", "birthDate"));
    SCHEMAS.put(DocumentTypeCode.TAX_STATUS_CERTIFICATE, List.of("fullName", "rfc", "curp", "taxRegime", "address"));
    SCHEMAS.put(
        DocumentTypeCode.DEED,
        concat(
            List.of("ownerFullName", "propertyAddress", "deedNumber", "deedDate"),
            NOTARY,
            List.of("publicRegistryFolio", "landArea", "builtArea")));
    SCHEMAS.put(DocumentTypeCode.PROOF_OF_ADDRESS, List.of("fullName", "address", "issueDate"));
    SCHEMAS.put(DocumentTypeCode.LIEN_CERTIFICATE, List.of("ownerFullName", "propertyAddress", "publicRegistryFolio", "hasLiens", "issueDate"));
    SCHEMAS.put(DocumentTypeCode.PROPERTY_TAX, List.of("ownerFullName", "propertyAddress", "cadastralKey", "landArea", "builtArea", "paidPeriod"));
    SCHEMAS.put(
        DocumentTypeCode.POWER_OF_ATTORNEY,
        concat(List.of("grantorFullName", "attorneyFullName", "instrumentNumber", "instrumentDate"), NOTARY, List.of("publicRegistryFolio")));
    SCHEMAS.put(
        DocumentTypeCode.CONDOMINIUM_REGIME,
        concat(
            List.of("propertyAddress", "unitNumber", "undividedPercentage", "deedNumber", "deedDate"),
            NOTARY,
            List.of("registryDate", "regimeRegistrationFolio")));
    SCHEMAS.put(DocumentTypeCode.WATER_RECEIPT, List.of("fullName", "address", "issueDate", "serviceNumber"));
    SCHEMAS.put(DocumentTypeCode.ELECTRICITY_RECEIPT, List.of("fullName", "address", "issueDate", "serviceNumber"));
    SCHEMAS.put(DocumentTypeCode.CADASTRAL_PLAN, List.of("ownerFullName", "propertyAddress", "cadastralKey", "landArea", "builtArea"));
    SCHEMAS.put(
        DocumentTypeCode.APPRAISAL,
        List.of("ownerFullName", "propertyAddress", "appraisalDate", "appraisedValue", "landArea", "builtArea", "appraiserName"));
    SCHEMAS.put(
        DocumentTypeCode.LAND_USE,
        List.of("ownerFullName", "propertyAddress", "cadastralKey", "permittedUse", "issueDate", "issuingAuthority"));
    SCHEMAS.put(
        DocumentTypeCode.SUCCESSION,
        List.of("deceasedFullName", "heirFullNames", "executorFullName", "courtOrNotary", "caseNumber", "resolutionDate", "propertyAddress"));
    SCHEMAS.put(
        DocumentTypeCode.ADJUDICATION,
        concat(
            List.of("ownerFullName", "propertyAddress", "instrumentNumber", "instrumentDate"),
            NOTARY,
            List.of("publicRegistryFolio", "landArea", "builtArea")));
    SCHEMAS.put(
        DocumentTypeCode.WILL,
        concat(List.of("testatorFullName", "heirFullNames", "executorFullName", "instrumentNumber", "instrumentDate"), NOTARY));
    SCHEMAS.put(
        DocumentTypeCode.MORTGAGE,
        List.of(
            "debtorFullName", "creditorName", "propertyAddress", "amount", "instrumentNumber", "instrumentDate", "publicRegistryFolio", "isCancelled"));
    SCHEMAS.put(
        DocumentTypeCode.LEASE_AGREEMENT,
        List.of("landlordFullName", "tenantFullName", "propertyAddress", "rentAmount", "startDate", "endDate"));
    SCHEMAS.put(DocumentTypeCode.RPP_REGISTRATION_SLIP, List.of("ownerFullName", "propertyAddress", "publicRegistryFolio", "registryDate"));
    SCHEMAS.put(
        DocumentTypeCode.MARRIAGE_CERTIFICATE,
        List.of("fullName", "spouseFullName", "marriageDate", "maritalRegime", "registryPlace", "actNumber"));
    SCHEMAS.put(
        DocumentTypeCode.PRIVATE_CONTRACT,
        concat(
            List.of("sellerFullName", "buyerFullName", "propertyAddress", "contractDate", "ratificationDate"),
            NOTARY,
            List.of("registryDate", "publicRegistryFolio", "landArea")));
    SCHEMAS.put(
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
            "mercantileFolio",
            "legalRepresentativeFullName"));
    // Extracción genérica: cualquier documento legible deja datos útiles al revisor.
    SCHEMAS.put(DocumentTypeCode.OTHER, List.of("personNames", "addresses", "dates", "referenceNumbers", "propertyAddress", "summary"));
  }

  /**
   * Documentos notariales o registrales que pueden tener decenas de páginas y
   * los datos clave en páginas interiores o al final (inscripción registral).
   */
  private static final Set<DocumentTypeCode> LONG_DOCUMENTS =
      Set.of(
          DocumentTypeCode.DEED,
          DocumentTypeCode.PRIVATE_CONTRACT,
          DocumentTypeCode.INCORPORATION_DEED,
          DocumentTypeCode.POWER_OF_ATTORNEY,
          DocumentTypeCode.CONDOMINIUM_REGIME,
          DocumentTypeCode.LIEN_CERTIFICATE,
          DocumentTypeCode.RPP_REGISTRATION_SLIP,
          DocumentTypeCode.SUCCESSION,
          DocumentTypeCode.ADJUDICATION,
          DocumentTypeCode.WILL,
          DocumentTypeCode.MORTGAGE,
          DocumentTypeCode.APPRAISAL,
          DocumentTypeCode.LEASE_AGREEMENT,
          DocumentTypeCode.OTHER);

  /** Qué significa cada campo, para indicárselo a la IA junto con su nombre. */
  private static final Map<String, String> DESCRIPTIONS =
      Map.ofEntries(
          Map.entry("fullName", "nombre completo de la persona"),
          Map.entry("curp", "CURP (18 caracteres alfanuméricos)"),
          Map.entry("electorKey", "clave de elector de la credencial (18 caracteres)"),
          Map.entry("birthDate", "fecha de nacimiento"),
          Map.entry("address", "domicilio completo"),
          Map.entry("expirationYear", "año de vigencia (el último año que es válida)"),
          Map.entry("expirationDate", "fecha de vencimiento"),
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
          Map.entry("paidPeriod", "periodo o año pagado"),
          Map.entry("serviceNumber", "número de servicio o de cuenta"),
          Map.entry("grantorFullName", "nombre de quien otorga el poder"),
          Map.entry("attorneyFullName", "nombre del apoderado"),
          Map.entry("instrumentNumber", "número del instrumento notarial"),
          Map.entry("instrumentDate", "fecha del instrumento"),
          Map.entry("regimeRegistrationFolio", "folio real de la constitución del régimen de condominio"),
          Map.entry("unitNumber", "unidad privativa (departamento, casa o local; p. ej. DEPTO 4-B)"),
          Map.entry("undividedPercentage", "porcentaje de indiviso de la unidad"),
          Map.entry("spouseFullName", "nombre del cónyuge"),
          Map.entry("marriageDate", "fecha del matrimonio"),
          Map.entry("maritalRegime", "régimen matrimonial (sociedad conyugal o separación de bienes)"),
          Map.entry("registryPlace", "lugar (municipio/estado) del Registro Civil"),
          Map.entry("actNumber", "número de acta"),
          Map.entry("sellerFullName", "nombre del vendedor"),
          Map.entry("buyerFullName", "nombre del comprador"),
          Map.entry("contractDate", "fecha del contrato"),
          Map.entry("ratificationDate", "fecha de ratificación de firmas"),
          Map.entry("companyName", "razón social"),
          Map.entry("companyType", "tipo de sociedad (p. ej. Sociedad Anónima de Capital Variable)"),
          Map.entry("commerceRegistryPlace", "ciudad del Registro Público de Comercio"),
          Map.entry("mercantileFolio", "folio mercantil"),
          Map.entry("legalRepresentativeFullName", "nombre del administrador o representante legal designado"),
          Map.entry("appraisalDate", "fecha del avalúo"),
          Map.entry("appraisedValue", "valor concluido del avalúo"),
          Map.entry("appraiserName", "nombre del perito valuador o unidad de valuación"),
          Map.entry("permittedUse", "uso de suelo autorizado"),
          Map.entry("issuingAuthority", "autoridad que emite"),
          Map.entry("deceasedFullName", "nombre de la persona fallecida (autor de la sucesión)"),
          Map.entry("heirFullNames", "nombres de los herederos o legatarios, separados por coma"),
          Map.entry("executorFullName", "nombre del albacea"),
          Map.entry("courtOrNotary", "juzgado o notaría donde se tramitó"),
          Map.entry("caseNumber", "número de expediente"),
          Map.entry("resolutionDate", "fecha de la resolución o escritura"),
          Map.entry("testatorFullName", "nombre del testador"),
          Map.entry("debtorFullName", "nombre del deudor hipotecario"),
          Map.entry("creditorName", "acreedor (banco, INFONAVIT, FOVISSSTE, persona)"),
          Map.entry("amount", "monto del crédito"),
          Map.entry("isCancelled", "si la hipoteca está cancelada (sí/no)"),
          Map.entry("landlordFullName", "nombre del arrendador"),
          Map.entry("tenantFullName", "nombre del arrendatario"),
          Map.entry("rentAmount", "renta mensual"),
          Map.entry("startDate", "fecha de inicio"),
          Map.entry("endDate", "fecha de terminación"),
          Map.entry("personNames", "nombres de las personas que aparecen, separados por coma"),
          Map.entry("addresses", "domicilios que aparecen, separados por punto y coma"),
          Map.entry("dates", "fechas relevantes y a qué corresponden"),
          Map.entry("referenceNumbers", "números de folio, escritura, cuenta o expediente y a qué corresponden"),
          Map.entry("summary", "en una o dos líneas, de qué trata el documento"));

  /**
   * Cómo leer cada tipo cuando hay variantes de diseño: la IA no debe suponer
   * que los datos están siempre en la misma posición.
   */
  private static final Map<DocumentTypeCode, String> HINTS =
      Map.of(
          DocumentTypeCode.INE,
          """
          Puede ser credencial INE (modelos C, D, E, F, G, H) o IFE antigua, foto vertical u horizontal, \
          solo el frente, solo el reverso o ambos (en imágenes distintas o en la misma). En el frente: NOMBRE \
          (apellido paterno, materno y nombre(s) en renglones separados: júntalos como aparecen), DOMICILIO, \
          CLAVE DE ELECTOR, CURP (las IFE más antiguas no la traen), FECHA DE NACIMIENTO, SEXO, SECCIÓN, \
          AÑO DE REGISTRO y VIGENCIA (p. ej. "2019 - 2029": expirationYear es 2029; en IFE antigua puede \
          ser un solo año). En el reverso, la zona MRZ (renglones que empiezan con "IDMEX") permite leer \
          nombre y fecha de nacimiento si el frente no se ve. Lee cada dato por su etiqueta, no por su \
          posición. Si solo se ve un lado, extrae lo de ese lado y avísalo en warnings.""",
          DocumentTypeCode.DEED,
          """
          Testimonio notarial; puede ser largo y viejo. El número de escritura, volumen, fecha y notario \
          suelen estar en las primeras páginas; el inmueble (ubicación, superficie, medidas) en las \
          declaraciones y cláusulas; la inscripción en el Registro Público (folio real) suele estar en un \
          sello o boleta al final.""",
          DocumentTypeCode.MARRIAGE_CERTIFICATE,
          """
          Acta del Registro Civil; puede ser formato antiguo, copia certificada o impresión moderna. \
          fullName es uno de los contrayentes y spouseFullName el otro. El régimen puede decir \
          "sociedad conyugal" o "separación de bienes".""",
          DocumentTypeCode.PROPERTY_TAX,
          """
          Recibo o boleta predial de cualquier municipio; puede estar a nombre de solo uno de los \
          copropietarios o de un dueño anterior: transcribe el nombre tal como aparece.""",
          DocumentTypeCode.OTHER,
          """
          Tipo no especificado: identifica qué documento es y transcribe sus datos principales \
          (personas, domicilios, fechas, números de folio o escritura).""");

  /** "campo (qué es)" para cada campo, en el orden recibido. */
  public static List<String> describe(List<String> fieldNames) {
    return fieldNames.stream().map(f -> DESCRIPTIONS.containsKey(f) ? f + " (" + DESCRIPTIONS.get(f) + ")" : f).toList();
  }

  public static List<String> fieldsFor(DocumentTypeCode type) {
    return SCHEMAS.getOrDefault(type, SCHEMAS.get(DocumentTypeCode.OTHER));
  }

  public static boolean isLongDocument(DocumentTypeCode type) {
    return LONG_DOCUMENTS.contains(type);
  }

  /** Indicaciones de lectura del tipo, o cadena vacía. */
  public static String readingHints(DocumentTypeCode type) {
    return HINTS.getOrDefault(type, "");
  }

  public static boolean isExtra(String fieldName) {
    return fieldName != null && fieldName.startsWith(EXTRA_PREFIX);
  }

  @SafeVarargs
  private static List<String> concat(List<String>... lists) {
    return java.util.Arrays.stream(lists).flatMap(List::stream).toList();
  }
}
