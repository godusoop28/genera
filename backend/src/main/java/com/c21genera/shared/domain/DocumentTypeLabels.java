package com.c21genera.shared.domain;

/**
 * Nombre en español de cada tipo de documento, para textos que genera el
 * propio backend (bitácora, correos, contrato, validaciones de
 * consistencia). La UI mantiene su propia copia en
 * frontend/src/lib/document-type-labels.ts; si cambias uno, cambia el otro.
 */
public final class DocumentTypeLabels {

  private DocumentTypeLabels() {}

  public static String of(DocumentTypeCode type) {
    if (type == null) {
      return "documento";
    }
    return switch (type) {
      case INE -> "Identificación oficial (INE)";
      case PASSPORT -> "Pasaporte";
      case CURP -> "CURP";
      case TAX_STATUS_CERTIFICATE -> "Constancia de Situación Fiscal";
      case DEED -> "Escritura";
      case PROOF_OF_ADDRESS -> "Comprobante de domicilio";
      case LIEN_CERTIFICATE -> "Certificado de libertad de gravamen";
      case PROPERTY_TAX -> "Predial";
      case POWER_OF_ATTORNEY -> "Poder notarial";
      case CONDOMINIUM_REGIME -> "Régimen de condominio";
      case WATER_RECEIPT -> "Recibo de agua";
      case ELECTRICITY_RECEIPT -> "Recibo de luz (CFE)";
      case CADASTRAL_PLAN -> "Plano catastral";
      case APPRAISAL -> "Avalúo";
      case LAND_USE -> "Uso de suelo";
      case SUCCESSION -> "Sucesión";
      case ADJUDICATION -> "Adjudicación";
      case WILL -> "Testamento";
      case MORTGAGE -> "Hipoteca";
      case LEASE_AGREEMENT -> "Contrato de arrendamiento";
      case RPP_REGISTRATION_SLIP -> "Boleta de inscripción en el Registro Público de la Propiedad";
      case MARRIAGE_CERTIFICATE -> "Acta de matrimonio";
      case PRIVATE_CONTRACT -> "Contrato privado de compraventa ratificado";
      case INCORPORATION_DEED -> "Acta constitutiva";
      case OTHER -> "Otro documento";
    };
  }

  /**
   * Descripción de lo que se espera ver en el archivo, para que la IA pueda
   * decir si el contenido corresponde al documento solicitado.
   */
  public static String expectedContent(DocumentTypeCode type) {
    return switch (type) {
      case INE -> "Credencial para votar mexicana emitida por el INE/IFE (anverso o reverso), con fotografía, nombre, CURP y clave de elector";
      case PASSPORT -> "Pasaporte con fotografía, nombre y número de pasaporte";
      case CURP -> "Constancia de la Clave Única de Registro de Población (CURP)";
      case TAX_STATUS_CERTIFICATE -> "Constancia de Situación Fiscal emitida por el SAT, con RFC, nombre o razón social y régimen fiscal";
      case DEED -> "Escritura pública (testimonio notarial) de un inmueble, con número de escritura, notario, propietario y descripción del inmueble";
      case PROOF_OF_ADDRESS -> "Comprobante de domicilio (recibo de servicio) con nombre y dirección";
      case LIEN_CERTIFICATE -> "Certificado de libertad o existencia de gravámenes del Registro Público de la Propiedad";
      case PROPERTY_TAX -> "Recibo o boleta de pago del impuesto predial, con clave catastral, propietario y ubicación del inmueble";
      case POWER_OF_ATTORNEY -> "Poder notarial otorgado ante notario público, con poderdante y apoderado";
      case CONDOMINIUM_REGIME -> "Escritura de constitución del régimen de propiedad en condominio";
      case WATER_RECEIPT -> "Recibo de pago del servicio de agua potable";
      case ELECTRICITY_RECEIPT -> "Recibo de luz de la CFE (Comisión Federal de Electricidad)";
      case CADASTRAL_PLAN -> "Plano o cédula catastral de un predio, con clave catastral, medidas y colindancias";
      case APPRAISAL -> "Avalúo de un inmueble";
      case LAND_USE -> "Constancia o licencia de uso de suelo";
      case SUCCESSION -> "Documento de juicio sucesorio";
      case ADJUDICATION -> "Escritura o resolución de adjudicación de un inmueble";
      case WILL -> "Testamento";
      case MORTGAGE -> "Contrato o documento de hipoteca";
      case LEASE_AGREEMENT -> "Contrato de arrendamiento";
      case RPP_REGISTRATION_SLIP -> "Boleta o constancia de inscripción en el Registro Público de la Propiedad, con folio real";
      case MARRIAGE_CERTIFICATE -> "Acta de matrimonio del Registro Civil";
      case PRIVATE_CONTRACT -> "Contrato privado de compraventa de un inmueble, con ratificación de firmas ante notario o autoridad";
      case INCORPORATION_DEED -> "Acta constitutiva de una sociedad mercantil, otorgada ante notario o corredor público";
      case OTHER -> "Cualquier documento";
    };
  }
}
