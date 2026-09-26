package com.c21genera.contracts.domain.document;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Contenido del contrato independiente del formato: la misma estructura se
 * convierte a DOCX (editable, con el encabezado/logo del modelo registrado)
 * y a PDF (el archivo que se firma y cuya huella SHA-256 queda en la
 * evidencia de cada firma).
 *
 * <p>Un {@link Span} "pendiente" marca un dato que falta: en un borrador se
 * resalta en amarillo; un contrato con datos pendientes nunca se puede
 * enviar a firma.
 */
public record ContractDocument(String title, boolean draft, List<String> missingItems, List<Block> blocks) {

  public sealed interface Block permits SectionHeading, Paragraph, Table, PageBreak, SignatureLines, Image {}

  public enum Align {
    LEFT,
    CENTER,
    JUSTIFY
  }

  public record Span(String text, boolean bold, boolean pending) {

    public static Span t(String text) {
      return new Span(text, false, false);
    }

    public static Span b(String text) {
      return new Span(text, true, false);
    }

    /** Dato faltante: se muestra como "[PENDIENTE: descripción]". */
    public static Span pending(String description) {
      return new Span("[PENDIENTE: " + description + "]", true, true);
    }
  }

  /** Barra sombreada centrada, como "Declaraciones" / "Cláusulas" / "Anexo A" en el modelo registrado. */
  public record SectionHeading(String text) implements Block {}

  public record Paragraph(List<Span> spans, Align align, int indent) implements Block {

    public static Paragraph of(Span... spans) {
      return new Paragraph(List.of(spans), Align.JUSTIFY, 0);
    }

    public static Paragraph of(List<Span> spans) {
      return new Paragraph(List.copyOf(spans), Align.JUSTIFY, 0);
    }

    public static Paragraph centered(Span... spans) {
      return new Paragraph(List.of(spans), Align.CENTER, 0);
    }

    public static Paragraph indented(Span... spans) {
      return new Paragraph(List.of(spans), Align.JUSTIFY, 1);
    }

    public static Paragraph indented(List<Span> spans) {
      return new Paragraph(List.copyOf(spans), Align.JUSTIFY, 1);
    }
  }

  public record Cell(List<List<Span>> paragraphs, int colSpan, boolean shaded, Align align) {

    public static Cell text(String text) {
      return new Cell(List.of(List.of(Span.t(text))), 1, false, Align.LEFT);
    }

    public static Cell bold(String text) {
      return new Cell(List.of(List.of(Span.b(text))), 1, false, Align.LEFT);
    }

    public static Cell header(String text, int colSpan) {
      return new Cell(List.of(List.of(Span.b(text))), colSpan, true, Align.CENTER);
    }

    public static Cell centered(String text) {
      return new Cell(List.of(List.of(Span.b(text))), 1, false, Align.CENTER);
    }

    public static Cell spans(List<Span> spans) {
      return new Cell(List.of(List.copyOf(spans)), 1, false, Align.LEFT);
    }

    public static Cell bullets(List<String> items) {
      List<List<Span>> paragraphs = new ArrayList<>();
      for (String item : items) {
        paragraphs.add(List.of(Span.t("• " + item)));
      }
      return new Cell(paragraphs, 1, false, Align.JUSTIFY);
    }

    public static Cell lines(List<List<Span>> paragraphs) {
      return new Cell(List.copyOf(paragraphs), 1, false, Align.LEFT);
    }
  }

  public record Row(List<Cell> cells) {

    public static Row of(Cell... cells) {
      return new Row(Arrays.asList(cells));
    }
  }

  /** columnWeights: ancho relativo de cada columna. */
  public record Table(List<Integer> columnWeights, List<Row> rows) implements Block {}

  public record PageBreak() implements Block {}

  /** Líneas de firma con el nombre/carácter debajo, acomodadas en dos columnas. */
  public record SignatureLines(List<String> labels) implements Block {}

  /** Imagen PNG (p. ej. el trazo de una firma) con su pie; widthPoints: ancho en el PDF. */
  public record Image(byte[] png, float widthPoints, String caption) implements Block {}
}
