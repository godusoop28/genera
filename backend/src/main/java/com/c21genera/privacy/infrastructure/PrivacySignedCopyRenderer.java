package com.c21genera.privacy.infrastructure;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.multipdf.PDFMergerUtility;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.stereotype.Component;

/**
 * Control de Cambios #45: copia del aviso de privacidad firmado digitalmente que se guarda en los
 * documentos del expediente. Es el PDF del aviso que vio el cliente (la versión vigente al
 * firmar) con una hoja final de constancia: fecha y hora, versión y huella del aviso, medio, IP,
 * navegador, finalidades aceptadas y la imagen de la firma.
 */
@Component
public class PrivacySignedCopyRenderer {

  private static final PDRectangle PAGE = PDRectangle.LETTER;
  private static final float MARGIN = 60;
  private static final ZoneId MX = ZoneId.of("America/Mexico_City");
  private static final DateTimeFormatter DATE_TIME =
      DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss 'hora del centro de México'", Locale.forLanguageTag("es-MX"));

  public record Evidence(
      String channelLabel,
      Instant signedAt,
      int noticeVersion,
      String noticeSha256,
      boolean mainPurposesAccepted,
      boolean secondaryPurposesAccepted,
      String ipAddress,
      String userAgent,
      byte[] signaturePng,
      String signatureSha256) {}

  /** noticePdf puede ser null (p. ej. si el archivo del aviso no está en el almacenamiento): sale solo la constancia. */
  public byte[] render(byte[] noticePdf, Evidence evidence) {
    byte[] certificate = certificate(evidence);
    if (noticePdf == null || noticePdf.length == 0) {
      return certificate;
    }
    try (PDDocument notice = Loader.loadPDF(noticePdf);
        PDDocument cert = Loader.loadPDF(new RandomAccessReadBuffer(certificate))) {
      new PDFMergerUtility().appendDocument(notice, cert);
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      notice.save(out);
      return out.toByteArray();
    } catch (IOException e) {
      // Un PDF del aviso dañado no impide guardar la constancia.
      return certificate;
    }
  }

  private byte[] certificate(Evidence e) {
    try (PDDocument pdf = new PDDocument()) {
      PDPage page = new PDPage(PAGE);
      pdf.addPage(page);
      PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
      PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
      float width = PAGE.getWidth() - 2 * MARGIN;
      try (PDPageContentStream cs = new PDPageContentStream(pdf, page)) {
        float y = PAGE.getHeight() - MARGIN;
        y = text(cs, bold, 14, "Constancia de aceptación y firma del Aviso de Privacidad", MARGIN, y, width, regular);
        y -= 6;
        y =
            text(
                cs,
                regular,
                9.5f,
                "CENTURY 21 Genera. Esta hoja forma parte del aviso de privacidad que antecede y registra cómo y cuándo lo aceptó y firmó el titular.",
                MARGIN,
                y,
                width,
                regular);
        y -= 10;
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[] {"Fecha y hora de la firma", DATE_TIME.format(e.signedAt().atZone(MX))});
        rows.add(new String[] {"Medio", e.channelLabel()});
        rows.add(new String[] {"Versión del aviso", "v" + e.noticeVersion()});
        rows.add(new String[] {"Huella SHA-256 del aviso", nullToDash(e.noticeSha256())});
        rows.add(new String[] {"Finalidad principal", e.mainPurposesAccepted() ? "Aceptada" : "No aceptada"});
        rows.add(new String[] {"Finalidades secundarias y promocionales", e.secondaryPurposesAccepted() ? "Autorizadas" : "No autorizadas"});
        rows.add(new String[] {"Dirección IP", nullToDash(e.ipAddress())});
        rows.add(new String[] {"Navegador", nullToDash(e.userAgent())});
        rows.add(new String[] {"Huella SHA-256 de la firma", nullToDash(e.signatureSha256())});
        for (String[] row : rows) {
          y = text(cs, bold, 9.5f, row[0], MARGIN, y, width, regular);
          y = text(cs, regular, 9.5f, row[1], MARGIN + 12, y, width - 12, regular);
          y -= 4;
        }
        y -= 10;
        y = text(cs, bold, 10, "Firma del titular", MARGIN, y, width, regular);
        if (e.signaturePng() != null && e.signaturePng().length > 0) {
          PDImageXObject image = PDImageXObject.createFromByteArray(pdf, e.signaturePng(), "firma");
          float maxW = 260;
          float maxH = 110;
          float scale = Math.min(maxW / image.getWidth(), maxH / image.getHeight());
          float w = image.getWidth() * scale;
          float h = image.getHeight() * scale;
          cs.drawImage(image, MARGIN, y - h - 6, w, h);
          y -= h + 12;
        } else {
          y = text(cs, regular, 9.5f, "Sin imagen de firma registrada (aceptación por casilla).", MARGIN, y, width, regular);
        }
        cs.moveTo(MARGIN, y);
        cs.lineTo(MARGIN + 260, y);
        cs.stroke();
      }
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      pdf.save(out);
      return out.toByteArray();
    } catch (IOException ex) {
      throw new UncheckedIOException("No se pudo generar la constancia del aviso de privacidad", ex);
    }
  }

  private static String nullToDash(String value) {
    return value == null || value.isBlank() ? "—" : value;
  }

  private static float text(PDPageContentStream cs, PDType1Font font, float size, String value, float x, float y, float width, PDType1Font encoder)
      throws IOException {
    for (String line : wrap(clean(value, encoder), font, size, width)) {
      cs.beginText();
      cs.setFont(font, size);
      cs.newLineAtOffset(x, y - size);
      cs.showText(line);
      cs.endText();
      y -= size * 1.4f;
    }
    return y;
  }

  private static List<String> wrap(String value, PDType1Font font, float size, float width) throws IOException {
    List<String> lines = new ArrayList<>();
    StringBuilder line = new StringBuilder();
    for (String word : value.split(" ")) {
      // Palabras muy largas (huellas, navegador) se cortan para no salirse de la hoja.
      while (font.getStringWidth(word) / 1000 * size > width && word.length() > 1) {
        int cut = word.length() - 1;
        while (cut > 1 && font.getStringWidth(word.substring(0, cut)) / 1000 * size > width) {
          cut--;
        }
        if (!line.isEmpty()) {
          lines.add(line.toString());
          line = new StringBuilder();
        }
        lines.add(word.substring(0, cut));
        word = word.substring(cut);
      }
      String candidate = line.isEmpty() ? word : line + " " + word;
      if (font.getStringWidth(candidate) / 1000 * size > width && !line.isEmpty()) {
        lines.add(line.toString());
        line = new StringBuilder(word);
      } else {
        line = new StringBuilder(candidate);
      }
    }
    lines.add(line.toString());
    return lines;
  }

  private static String clean(String value, PDType1Font encoder) {
    String normalized = value.replace('—', '-').replace('–', '-').replace('\n', ' ').replace('\r', ' ').replace('\t', ' ');
    StringBuilder out = new StringBuilder();
    for (char ch : normalized.toCharArray()) {
      try {
        encoder.encode(String.valueOf(ch));
        out.append(ch);
      } catch (IllegalArgumentException | IOException e) {
        out.append('?');
      }
    }
    return out.toString();
  }
}
