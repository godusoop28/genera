package com.c21genera.contracts.domain;

import static com.c21genera.contracts.domain.document.ContractDocument.Span.b;
import static com.c21genera.contracts.domain.document.ContractDocument.Span.t;

import com.c21genera.contracts.domain.document.ContractDocument;
import com.c21genera.contracts.domain.document.ContractDocument.Block;
import com.c21genera.contracts.domain.document.ContractDocument.Cell;
import com.c21genera.contracts.domain.document.ContractDocument.Image;
import com.c21genera.contracts.domain.document.ContractDocument.Paragraph;
import com.c21genera.contracts.domain.document.ContractDocument.Row;
import com.c21genera.contracts.domain.document.ContractDocument.SectionHeading;
import com.c21genera.contracts.domain.document.ContractDocument.Table;
import com.c21genera.expedientes.ExpedienteSummary;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Constancia que se agrega al final del contrato firmado: por cada firma,
 * quién firmó, en qué carácter, cómo, cuándo, desde dónde y sobre qué
 * documento exacto (versión y huella SHA-256). Permite comprobar después que
 * todas las partes firmaron la misma versión.
 */
public final class SignatureCertificate {

  private static final DateTimeFormatter TIMESTAMP =
      DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss 'hora del centro de México'").withZone(ZoneId.of("America/Mexico_City"));

  private SignatureCertificate() {}

  public static ContractDocument build(
      ContractGeneration contract, ExpedienteSummary expediente, List<ContractSignature> signatures, Map<UUID, byte[]> signatureImages) {
    List<Block> blocks = new ArrayList<>();
    blocks.add(new SectionHeading("Constancia de firmas"));
    blocks.add(
        Paragraph.of(
            t("Contrato de prestación de servicios de intermediación, expediente "),
            b(expediente.folio()),
            t(", versión "),
            b(String.valueOf(contract.getVersionNumber())),
            t(", generado el "),
            b(TIMESTAMP.format(contract.getGeneratedAt())),
            t(".")));
    blocks.add(
        new Paragraph(
            List.of(
            t("Huella digital SHA-256 del documento firmado: "),
            b(contract.getDocumentSha256()),
            t(". Cualquier cambio al documento produce una huella distinta; todas las firmas registradas abajo corresponden a esta misma huella.")),
            ContractDocument.Align.LEFT,
            0));

    for (ContractSignature s : signatures) {
      List<Row> rows = new ArrayList<>();
      rows.add(Row.of(Cell.header(s.getSignerName(), 2)));
      rows.add(row("Carácter", s.getSignerCapacity()));
      rows.add(row("Parte", s.getParty() == ContractSignature.Party.CLIENT ? "Cliente" : "Intermediaria"));
      rows.add(
          row(
              "Método",
              s.getMethod() == ContractSignature.Method.AUTOGRAPH_SCAN
                  ? "Firma autógrafa (contrato firmado a mano y digitalizado)"
                  : "Firma electrónica simple (nombre escrito, trazo y aceptación expresa)"));
      rows.add(row("Fecha y hora", s.getSignedAt() == null ? "" : TIMESTAMP.format(s.getSignedAt())));
      rows.add(row("Huella del documento firmado", s.getDocumentSha256()));
      if (s.getMethod() == ContractSignature.Method.ELECTRONIC_SIMPLE) {
        rows.add(row("Nombre escrito por el firmante", s.getTypedName()));
        if (s.getRegisteredByUserId() == null) {
          rows.add(row("Dirección IP", s.getIpAddress()));
          rows.add(row("Navegador / dispositivo", s.getUserAgent()));
        } else {
          rows.add(row("Firmado desde", "Sistema interno (usuario autenticado " + s.getRegisteredByUserId() + ")"));
        }
        rows.add(row("Huella del trazo de firma", s.getSignatureImageSha256()));
        rows.add(row("Declaración aceptada", s.getConsentText()));
      } else {
        rows.add(row("Huella del archivo escaneado", s.getEvidenceSha256()));
        rows.add(row("Registrado por el usuario", String.valueOf(s.getRegisteredByUserId())));
      }
      blocks.add(new Table(List.of(30, 70), rows));
      byte[] image = signatureImages.get(s.getId());
      if (image != null) {
        blocks.add(new Image(image, 180, "Trazo de firma de " + s.getSignerName()));
      }
    }
    return new ContractDocument("Constancia de firmas", false, List.of(), blocks);
  }

  private static Row row(String label, String value) {
    return Row.of(Cell.bold(label), Cell.text(value == null ? "—" : value));
  }
}
