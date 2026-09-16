package com.c21genera.contracts.infrastructure;

import com.c21genera.contracts.domain.ContractCalculator;
import com.c21genera.contracts.domain.ContractReferenceData;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.springframework.stereotype.Component;

/**
 * Construye el DOCX de vista previa de PROTOTIPO (ver AGENTS §46/§79):
 * arma el documento a partir de la estructura real transcrita en
 * {@link ContractReferenceData}, sin modificar ni reproducir como oficial
 * el DOCX original registrado ante PROFECO (que permanece intacto en
 * /reference). Cuando exista una plantilla oficial con placeholders,
 * {@link DocxTemplateEngine} puede sustituir este constructor sin cambiar
 * el resto del dominio.
 */
@Component
public class ContractDocxBuilder {

  public record ContractInput(
      String clientFullName,
      String clientCharacter,
      String propertyAddress,
      ContractCalculator.Result calculations) {}

  public byte[] build(ContractInput input) throws IOException {
    try (XWPFDocument document = new XWPFDocument()) {
      title(document, "VISTA PREVIA DE PROTOTIPO — no es el documento oficial generado");
      title(document, ContractReferenceData.TITLE);
      paragraph(
          document,
          "Registrado ante PROFECO " + ContractReferenceData.PROFECO_NUMBER + " · "
              + ContractReferenceData.PROFECO_REGISTRATION_DATE);

      paragraph(
          document,
          "Contrato que celebran, por una parte, " + ContractReferenceData.INTERMEDIARY_LEGAL_NAME + " ("
              + ContractReferenceData.INTERMEDIARY_COMMERCIAL_NAME + "), representada por "
              + ContractReferenceData.LEGAL_REPRESENTATIVE + " (“la intermediaria”), y por la otra, "
              + input.clientFullName() + ", en su carácter de " + input.clientCharacter()
              + " (“el cliente”).");

      heading(document, "Cláusulas");
      for (ContractReferenceData.Clause clause : ContractReferenceData.CLAUSES) {
        boldParagraph(document, clause.title());
        paragraph(document, clause.text());
      }

      heading(document, "Anexo A — Características del inmueble");
      for (String field : ContractReferenceData.ANNEX_A_FIELDS) {
        paragraph(document, "• " + field);
      }

      heading(document, "Datos calculados");
      ContractCalculator.Result c = input.calculations();
      paragraph(document, "Domicilio del inmueble: " + input.propertyAddress());
      paragraph(document, "Precio autorizado: $" + c.price() + " MXN (" + c.priceWritten() + ")");
      paragraph(document, "Comisión (5%): $" + c.commission() + " MXN");
      paragraph(document, "IVA de comisión: $" + c.vat() + " MXN");
      paragraph(document, "Total comisión + IVA: $" + c.totalCommissionWithVat() + " MXN");
      paragraph(document, "Pena convencional: $" + c.penalty() + " MXN");
      paragraph(document, "Vigencia de exclusividad: " + c.exclusivityDays() + " días naturales");
      paragraph(document, "Fecha de terminación: " + c.exclusivityEndDate());

      ByteArrayOutputStream out = new ByteArrayOutputStream();
      document.write(out);
      return out.toByteArray();
    }
  }

  private void title(XWPFDocument document, String text) {
    XWPFParagraph paragraph = document.createParagraph();
    paragraph.setAlignment(ParagraphAlignment.CENTER);
    XWPFRun run = paragraph.createRun();
    run.setText(text);
    run.setBold(true);
  }

  private void heading(XWPFDocument document, String text) {
    XWPFParagraph paragraph = document.createParagraph();
    XWPFRun run = paragraph.createRun();
    run.setText(text);
    run.setBold(true);
    run.setFontSize(13);
  }

  private void boldParagraph(XWPFDocument document, String text) {
    XWPFParagraph paragraph = document.createParagraph();
    XWPFRun run = paragraph.createRun();
    run.setText(text);
    run.setBold(true);
  }

  private void paragraph(XWPFDocument document, String text) {
    XWPFParagraph paragraph = document.createParagraph();
    XWPFRun run = paragraph.createRun();
    run.setText(text);
  }
}
