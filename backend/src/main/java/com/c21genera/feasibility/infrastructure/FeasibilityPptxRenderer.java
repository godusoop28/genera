package com.c21genera.feasibility.infrastructure;

import com.c21genera.feasibility.domain.PresentationData;
import com.c21genera.feasibility.domain.PresentationData.ComparableListing;
import com.c21genera.feasibility.domain.PresentationData.GroupNote;
import com.c21genera.feasibility.domain.PresentationData.Point;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.imageio.ImageIO;
import org.apache.poi.sl.usermodel.PictureData;
import org.apache.poi.sl.usermodel.Placeholder;
import org.apache.poi.sl.usermodel.ShapeType;
import org.apache.poi.sl.usermodel.TableCell.BorderEdge;
import org.apache.poi.sl.usermodel.TextParagraph.TextAlign;
import org.apache.poi.sl.usermodel.TextShape.TextAutofit;
import org.apache.poi.sl.usermodel.VerticalAlignment;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFAutoShape;
import org.apache.poi.xslf.usermodel.XSLFNotes;
import org.apache.poi.xslf.usermodel.XSLFPictureData;
import org.apache.poi.xslf.usermodel.XSLFPictureShape;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTable;
import org.apache.poi.xslf.usermodel.XSLFTableCell;
import org.apache.poi.xslf.usermodel.XSLFTableRow;
import org.apache.poi.xslf.usermodel.XSLFTextBox;
import org.apache.poi.xslf.usermodel.XSLFTextParagraph;
import org.apache.poi.xslf.usermodel.XSLFTextRun;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.springframework.stereotype.Component;

/**
 * PowerPoint editable (16:9) de la presentación de factibilidad para el propietario: las 12
 * láminas del prompt "Presentación de factibilidad" más anexos (descripciones para portales,
 * comparables con sus ligas y datos pendientes). Todo es texto, tablas y formas editables; solo
 * las fotos, el mapa y los logos son imágenes. Paleta del manual de CENTURY 21: negro #252527,
 * dorado #BEAF86, gris claro #E4E8E9 y blanco, con láminas claras y oscuras alternadas.
 */
@Component
public class FeasibilityPptxRenderer {

  static final Color INK = new Color(0x25, 0x25, 0x27);
  static final Color GOLD = new Color(0xBE, 0xAF, 0x86);
  static final Color LIGHT = new Color(0xE4, 0xE8, 0xE9);
  static final Color GREY = new Color(0x80, 0x82, 0x85);
  static final Color MUTED = new Color(0x5E, 0x5B, 0x55);
  static final Color WHITE = Color.WHITE;
  private static final String FONT = "Arial";
  private static final double W = 960;
  private static final double H = 540;
  private static final double M = 44;
  private static final String PENDING = "Dato pendiente por confirmar";

  /** km en línea recta desde el inmueble; approximate = alguna de las dos ubicaciones es aproximada. */
  public record Distance(double km, boolean approximate) {}

  public record Deck(
      PresentationData data,
      String folio,
      String dateText,
      String advisorName,
      List<byte[]> photos,
      byte[] mapPng,
      String mapsUrl,
      Map<String, Distance> distances,
      boolean subjectLocated,
      boolean subjectApproximate,
      boolean rent) {}

  private static final List<String[]> EXCLUSIVE_BENEFITS =
      List.of(
          new String[] {"Estrategia de precio consistente", "Un solo precio y un solo mensaje en todos los canales: el comprador no ve variaciones que debiliten la negociación."},
          new String[] {"Publicidad coordinada", "Portales, redes y red CENTURY 21 con la misma información y fotografías, sin anuncios duplicados ni contradictorios."},
          new String[] {"Presentación profesional", "Fotografía, descripción y ficha técnica preparadas para el comprador objetivo."},
          new String[] {"Organización de visitas", "Agenda, filtro de prospectos y retroalimentación registrada después de cada visita."},
          new String[] {"Colaboración con otros asesores", "La exclusiva no limita a un solo asesor: se comparte con la red bajo acuerdos autorizados."},
          new String[] {"Negociación ordenada", "Cada oferta se evalúa por su resultado neto y sus condiciones, no solo por el precio nominal."},
          new String[] {"Seguimiento de resultados", "Reportes periódicos de contactos, visitas, ofertas y ajustes recomendados."},
          new String[] {"Protección del valor percibido", "Evita el desgaste de un inmueble publicado por muchos, a precios distintos y por mucho tiempo."});

  public byte[] render(Deck deck) {
    PresentationData d = deck.data();
    try (XMLSlideShow ppt = new XMLSlideShow()) {
      ppt.setPageSize(new Dimension((int) W, (int) H));
      Slides s = new Slides(ppt, deck);
      s.cover();
      s.context();
      s.strengths();
      s.supply();
      s.comparables(PresentationData.ABOVE, "Propiedades por arriba", d.groupNotes() == null ? null : d.groupNotes().above(), 5);
      s.comparables(PresentationData.SIMILAR, "Propiedades directamente comparables", d.groupNotes() == null ? null : d.groupNotes().similar(), 6);
      s.comparables(PresentationData.BELOW, "Propiedades por debajo", d.groupNotes() == null ? null : d.groupNotes().below(), 7);
      s.map();
      s.pricing();
      s.sales();
      s.exclusive();
      s.closing();
      s.annexDescriptions();
      s.annexComparables();
      s.annexPending();
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      ppt.write(out);
      return out.toByteArray();
    } catch (IOException e) {
      throw new IllegalStateException("No se pudo generar el PowerPoint de la factibilidad", e);
    }
  }

  /** Arma las láminas en orden; cada método es una lámina. */
  private static final class Slides {
    private final XMLSlideShow ppt;
    private final Deck deck;
    private final PresentationData d;
    private int number;

    Slides(XMLSlideShow ppt, Deck deck) {
      this.ppt = ppt;
      this.deck = deck;
      this.d = deck.data();
    }

    // ---------------------------------------------------------------- 1. Portada
    private String m(Long value) {
      return deck.rent() && value != null ? money(value) + "/mes" : money(value);
    }

    void cover() {
      XSLFSlide slide = newSlide(true);
      double split = 430;
      byte[] photo = deck.photos().isEmpty() ? null : deck.photos().get(0);
      if (photo != null) {
        picture(slide, coverCrop(photo, W - split, H), split, 0, W - split, H);
      } else {
        rect(slide, split, 0, W - split, H, GOLD, null);
        text(slide, split + 40, H / 2 - 30, W - split - 80, 60, "Fotografía principal pendiente: agrégala aquí", 16, INK, false, TextAlign.CENTER);
      }
      rect(slide, split, 0, 6, H, GOLD, null);
      logo(slide, "/feasibility/logo-vertical-sobre-oscuro.jpg", M, 40, 150, 87);
      text(slide, M, 175, split - 2 * M, 22, deck.rent() ? "ANÁLISIS DE FACTIBILIDAD · RENTA" : "ANÁLISIS DE FACTIBILIDAD · VENTA", 12, GOLD, true, TextAlign.LEFT);
      text(slide, M, 200, split - 2 * M, 110, nz(d.property() == null ? null : d.property().title(), "Inmueble en análisis"), 28, WHITE, true, TextAlign.LEFT);
      text(slide, M, 318, split - 2 * M, 50, nz(d.property() == null ? null : d.property().location(), PENDING), 13, LIGHT, false, TextAlign.LEFT);
      rect(slide, M, 380, 60, 3, GOLD, null);
      text(
          slide,
          M,
          392,
          split - 2 * M,
          70,
          deck.rent()
              ? "Propósito: entender el mercado de renta de tu propiedad, definir una renta mensual competitiva y la estrategia para rentarla."
              : "Propósito: entender el mercado de tu propiedad, definir un precio de salida competitivo y la estrategia para venderla.",
          11,
          LIGHT,
          false,
          TextAlign.LEFT);
      text(slide, M, 470, split - 2 * M, 40, "CENTURY 21 Genera · Cuernavaca, Morelos\n" + deck.dateText() + " · Expediente " + deck.folio(), 10, GREY, false, TextAlign.LEFT);
    }

    // ---------------------------------------------------------------- 2. Contexto
    void context() {
      XSLFSlide slide = newSlide(false);
      title(slide, "La propiedad", "Contexto de la propiedad", false);
      var p = d.property();
      double colW = 520;
      double y = 120;
      for (String[] row :
          new String[][] {
            {"Ubicación", p == null ? null : p.location()},
            {"Superficies", p == null ? null : p.surfaces()},
            {"Distribución", p == null ? null : p.distribution()},
            {"Estado", p == null ? null : p.condition()},
            {"Amenidades", p == null ? null : String.join(" · ", PresentationData.safe(p.amenities()))}
          }) {
        text(slide, M, y, 120, 54, row[0].toUpperCase(Locale.ROOT), 10, MUTED, true, TextAlign.LEFT);
        text(slide, M + 120, y, colW - 120, 54, clip(nz(row[1], PENDING), 190), 12, INK, false, TextAlign.LEFT);
        rect(slide, M, y + 56, colW, 0.8, LIGHT, null);
        y += 64;
      }
      double rx = M + colW + 30;
      double rw = W - rx - M;
      byte[] photo = deck.photos().size() > 1 ? deck.photos().get(1) : deck.photos().isEmpty() ? null : deck.photos().get(0);
      double textTop = 120;
      if (photo != null) {
        picture(slide, coverCrop(photo, rw, 170), rx, 120, rw, 170);
        textTop = 305;
      }
      rect(slide, rx, textTop, rw, H - textTop - 56, LIGHT, null);
      text(slide, rx + 14, textTop + 10, rw - 28, 20, "ATRIBUTOS DISTINTIVOS", 10, INK, true, TextAlign.LEFT);
      bullets(slide, rx + 10, textTop + 32, rw - 20, H - textTop - 96, clipAll(PresentationData.safe(p == null ? null : p.highlights()), photo != null ? 4 : 7, 120), 11, INK);
    }

    // ---------------------------------------------------------------- 3. Fortalezas y oportunidades
    void strengths() {
      XSLFSlide slide = newSlide(false);
      title(slide, "Lo que verá el comprador", "Fortalezas y áreas de oportunidad", false);
      double colW = (W - 2 * M - 24) / 2;
      pointColumn(slide, M, "FORTALEZAS", "Favorecen la venta", PresentationData.safe(d.strengths()), colW, INK, WHITE, GOLD);
      pointColumn(slide, M + colW + 24, "ÁREAS DE OPORTUNIDAD", "Pueden generar objeciones", PresentationData.safe(d.opportunities()), colW, LIGHT, INK, INK);
    }

    private void pointColumn(XSLFSlide slide, double x, String header, String sub, List<Point> points, double w, Color bg, Color fg, Color accent) {
      double top = 118;
      rect(slide, x, top, w, H - top - 52, bg, null);
      text(slide, x + 18, top + 12, w - 36, 22, header, 13, accent, true, TextAlign.LEFT);
      text(slide, x + 18, top + 32, w - 36, 18, sub, 10, bg == INK ? LIGHT : MUTED, false, TextAlign.LEFT);
      double y = top + 60;
      int count = Math.min(points.size(), 5);
      double step = count == 0 ? 0 : Math.min(62, (H - 52 - y - 10) / count);
      for (int i = 0; i < count; i++) {
        Point p = points.get(i);
        XSLFTextBox box = slide.createTextBox();
        box.setAnchor(new Rectangle2D.Double(x + 18, y, w - 36, step));
        box.clearText();
        box.setWordWrap(true);
        box.setTextAutofit(TextAutofit.NORMAL);
        XSLFTextParagraph para = box.addNewTextParagraph();
        run(para, clip(nz(p.title(), ""), 70), 12, accent == INK ? INK : GOLD, true);
        XSLFTextParagraph detail = box.addNewTextParagraph();
        run(detail, clip(nz(p.buyerPerception(), ""), 170), 10, fg, false);
        y += step;
      }
      if (count == 0) {
        text(slide, x + 18, y, w - 36, 40, PENDING, 11, fg, false, TextAlign.LEFT);
      }
    }

    // ---------------------------------------------------------------- 4. Oferta en la zona
    void supply() {
      XSLFSlide slide = newSlide(true);
      title(slide, "Mercado observado", deck.rent() ? "Oferta en renta en la zona" : "Oferta inmobiliaria en la zona", true);
      var s = d.marketSupply();
      String[][] kpis = {
        {"Propiedades únicas", s == null || s.uniqueListings() == null ? "—" : String.valueOf(s.uniqueListings())},
        {deck.rent() ? "Renta mínima /mes" : "Precio mínimo", money(s == null ? null : s.minPrice())},
        {deck.rent() ? "Renta máxima /mes" : "Precio máximo", money(s == null ? null : s.maxPrice())},
        {deck.rent() ? "Promedio /mes" : "Promedio", money(s == null ? null : s.averagePrice())},
        {deck.rent() ? "Mediana /mes" : "Mediana", money(s == null ? null : s.medianPrice())}
      };
      double tw = (W - 2 * M - 4 * 14) / 5;
      for (int i = 0; i < kpis.length; i++) {
        double x = M + i * (tw + 14);
        rect(slide, x, 118, tw, 92, new Color(0x33, 0x33, 0x36), null);
        rect(slide, x, 118, tw, 3, GOLD, null);
        text(slide, x + 10, 132, tw - 20, 36, kpis[i][1], 20, WHITE, true, TextAlign.LEFT);
        text(slide, x + 10, 172, tw - 20, 22, kpis[i][0].toUpperCase(Locale.ROOT), 9, GOLD, true, TextAlign.LEFT);
      }
      double colW = (W - 2 * M - 24) / 2;
      labeled(slide, M, 236, colW, 170, "DÓNDE SE CONCENTRA LA OFERTA", nz(s == null ? null : s.concentration(), PENDING), true);
      labeled(slide, M + colW + 24, 236, colW, 170, "CÓMO SE POSICIONA TU PROPIEDAD", nz(s == null ? null : s.ourPosition(), PENDING), true);
      text(slide, M, 420, W - 2 * M, 60, clip(nz(s == null ? null : s.scopeNote(), "Inventario observado en publicaciones verificables; no representa el total del mercado."), 300), 9.5, GREY, false, TextAlign.LEFT);
    }

    // ---------------------------------------------------------------- 5-7. Comparables
    void comparables(String group, String heading, GroupNote note, int slideNumber) {
      XSLFSlide slide = newSlide(false);
      title(slide, "Análisis de comparables · " + (slideNumber - 4) + " de 3", heading, false);
      List<ComparableListing> list = d.group(group);
      if (list.isEmpty()) {
        rect(slide, M, 130, W - 2 * M, 80, LIGHT, null);
        text(slide, M + 20, 145, W - 2 * M - 40, 50, "No se identificaron propiedades verificables en este grupo. La muestra se limita a las comprobadas.", 13, INK, false, TextAlign.LEFT);
      } else {
        comparableTable(slide, list, M, 112, W - 2 * M);
      }
      double y = list.isEmpty() ? 240 : 112 + 26 + list.size() * 34 + 18;
      double colW = (W - 2 * M - 24) / 2;
      double h = Math.max(70, H - y - 52);
      labeled(slide, M, y, colW, h, "VENTAJAS FRENTE A TU PROPIEDAD", clip(nz(note == null ? null : note.advantages(), PENDING), 330), false);
      labeled(slide, M + colW + 24, y, colW, h, "DESVENTAJAS FRENTE A TU PROPIEDAD", clip(nz(note == null ? null : note.disadvantages(), PENDING), 330), false);
    }

    private void comparableTable(XSLFSlide slide, List<ComparableListing> list, double x, double y, double w) {
      String[] headers = {"ID", "Zona", deck.rent() ? "Renta /mes" : "Precio", "Terreno", "Construcción", deck.rent() ? "$/m² al mes" : "$/m² constr.", "Estado y atributos", "Publicación"};
      double[] weights = {0.05, 0.16, 0.11, 0.08, 0.10, 0.10, 0.26, 0.12};
      XSLFTable table = slide.createTable();
      table.setAnchor(new Rectangle2D.Double(x, y, w, 26 + list.size() * 34));
      XSLFTableRow head = table.addRow();
      head.setHeight(26);
      for (String h : headers) {
        XSLFTableCell c = head.addCell();
        cellText(c, h, 9, WHITE, true);
        c.setFillColor(INK);
      }
      int i = 0;
      for (ComparableListing c : list) {
        XSLFTableRow row = table.addRow();
        row.setHeight(34);
        Color bg = i++ % 2 == 0 ? WHITE : new Color(0xF4, 0xF5, 0xF5);
        String[] values = {
          c.id(),
          clip(nz(c.zone(), "—"), 40),
          money(c.price()),
          area(c.landM2()),
          area(c.builtM2()),
          c.price() != null && c.builtM2() != null && c.builtM2() > 0 ? money(Math.round(c.price() / c.builtM2())) : "—",
          clip(join(c.condition(), c.attributes()), 95),
          ""
        };
        for (int k = 0; k < values.length; k++) {
          XSLFTableCell cell = row.addCell();
          cell.setFillColor(bg);
          if (k == values.length - 1) {
            if (c.url() != null && !c.url().isBlank()) {
              XSLFTextRun r = cellText(cell, c.urlVerified() ? "Ver publicación" : "Ver (no verificada)", 8.5, new Color(0x1F, 0x5F, 0xA8), false);
              r.setUnderlined(true);
              r.createHyperlink().setAddress(c.url());
            } else {
              cellText(cell, "Sin liga", 8.5, GREY, false);
            }
          } else {
            cellText(cell, values[k], k == 0 ? 10 : 8.5, k == 0 ? INK : INK, k == 0 || k == 2);
          }
          cell.setBorderColor(BorderEdge.bottom, LIGHT);
          cell.setBorderWidth(BorderEdge.bottom, 0.75);
        }
      }
      for (int k = 0; k < weights.length; k++) {
        table.setColumnWidth(k, w * weights[k]);
      }
    }

    // ---------------------------------------------------------------- 8. Mapa
    void map() {
      XSLFSlide slide = newSlide(false);
      title(slide, "Ubicación de la competencia", "Mapa de ubicación y distancia de los comparables", false);
      double top = 104;
      double mapW = 470;
      double mapH = mapW * StaticMapRenderer.HEIGHT / StaticMapRenderer.WIDTH;
      if (deck.mapPng() != null) {
        XSLFPictureShape pic = picture(slide, deck.mapPng(), M, top, mapW, mapH);
        if (deck.mapsUrl() != null) {
          pic.createHyperlink().setAddress(deck.mapsUrl());
        }
      }
      // Bajo el mapa: símbolos, método de medición y la conclusión obligatoria.
      XSLFTextBox legend = slide.createTextBox();
      legend.setAnchor(new Rectangle2D.Double(M, top + mapH + 4, mapW, 40));
      legend.clearText();
      legend.setWordWrap(true);
      XSLFTextParagraph lp = legend.addNewTextParagraph();
      run(lp, "● ", 11, GOLD, true);
      run(lp, "Tu propiedad (dorado)   ", 8.5, INK, false);
      run(lp, "▲ ", 9.5, INK, true);
      run(lp, "Por arriba   ", 8.5, INK, false);
      run(lp, "● ", 9.5, INK, true);
      run(lp, "Similares   ", 8.5, INK, false);
      run(lp, "▼ ", 9.5, GREY, true);
      run(lp, "Por debajo   ", 8.5, INK, false);
      run(lp, "Contorno punteado o ≈: ubicación aproximada (colonia o zona).", 8, MUTED, false);
      XSLFTextParagraph mp = legend.addNewTextParagraph();
      run(
          mp,
          "Distancias en línea recta desde tu propiedad"
              + (deck.subjectLocated() ? (deck.subjectApproximate() ? " (ubicada por su colonia: aproximadas)" : "") : " (tu propiedad no pudo ubicarse: sin distancias)")
              + "; no son recorridos por carretera.  ",
          8,
          MUTED,
          false);
      if (deck.mapsUrl() != null) {
        XSLFTextRun r = run(mp, "Abrir en Google Maps", 8, new Color(0x1F, 0x5F, 0xA8), false);
        r.setUnderlined(true);
        r.createHyperlink().setAddress(deck.mapsUrl());
      }
      double cy = top + mapH + 48;
      rect(slide, M, cy, mapW, H - 40 - cy, LIGHT, null);
      text(
          slide,
          M + 10,
          cy + 4,
          mapW - 20,
          H - 46 - cy,
          clip(
              nz(
                  d.mapConclusion(),
                  "Estas propiedades son parte de las alternativas que puede encontrar un comprador en la zona. Su ubicación, características y precio nos ayudan a entender cómo competir por su atención."),
              230),
          9,
          INK,
          false,
          TextAlign.LEFT);

      // A la derecha: número, zona, precio y distancia, a toda la altura.
      double tx = M + mapW + 16;
      double tw = W - tx - M;
      List<ComparableListing> all = PresentationData.safe(d.comparables());
      double available = H - 44 - top - 20;
      double rowH = all.isEmpty() ? 20 : Math.min(22, available / all.size());
      XSLFTable table = slide.createTable();
      table.setAnchor(new Rectangle2D.Double(tx, top, tw, 20 + rowH * all.size()));
      XSLFTableRow head = table.addRow();
      head.setHeight(20);
      for (String h : new String[] {"ID", "Zona", "Precio", "Distancia"}) {
        XSLFTableCell c = head.addCell();
        cellText(c, h, 8, WHITE, true);
        c.setFillColor(INK);
      }
      for (ComparableListing c : all) {
        XSLFTableRow row = table.addRow();
        row.setHeight(rowH);
        Distance dist = deck.distances().get(c.id());
        String distance = dist == null ? "Sin ubicación" : (dist.approximate() ? "≈ " : "") + String.format(Locale.ROOT, "%.1f km", dist.km());
        String[] values = {c.id(), clip(nz(c.zone(), "—"), 24), money(c.price()), distance};
        for (int k = 0; k < values.length; k++) {
          XSLFTableCell cell = row.addCell();
          cellText(cell, values[k], 7.5, k == 3 && dist == null ? GREY : INK, k == 0);
          cell.setInsets(new org.apache.poi.sl.usermodel.Insets2D(1, 4, 1, 4));
          cell.setBorderColor(BorderEdge.bottom, LIGHT);
          cell.setBorderWidth(BorderEdge.bottom, 0.5);
        }
      }
      double[] weights = {0.12, 0.41, 0.26, 0.19};
      for (int k = 0; k < weights.length; k++) {
        table.setColumnWidth(k, tw * weights[k]);
      }
    }

    // ---------------------------------------------------------------- 9. Precio
    void pricing() {
      XSLFSlide slide = newSlide(true);
      title(slide, "Nuestra recomendación", deck.rent() ? "Posicionamiento y recomendación de renta" : "Posicionamiento y recomendación de precio", true);
      var p = d.pricing();
      String range = p == null || p.rangeMin() == null || p.rangeMax() == null ? PENDING : money(p.rangeMin()) + " – " + m(p.rangeMax());
      String[][] kpis = {
        {"Expectativa del propietario", m(p == null ? null : p.ownerPrice())},
        {deck.rent() ? "Rango de renta sugerido" : "Rango sugerido", range},
        {deck.rent() ? "Renta mensual recomendada" : "Precio recomendado de salida", m(p == null ? null : p.recommendedPrice())}
      };
      double kw = 270;
      for (int i = 0; i < 3; i++) {
        double y = 112 + i * 84;
        boolean main = i == 2;
        rect(slide, M, y, kw, 74, main ? GOLD : new Color(0x33, 0x33, 0x36), null);
        text(slide, M + 14, y + 8, kw - 28, 18, kpis[i][0].toUpperCase(Locale.ROOT), 9, main ? INK : GOLD, true, TextAlign.LEFT);
        text(slide, M + 14, y + 28, kw - 28, 40, kpis[i][1], i == 1 ? 15 : 20, main ? INK : WHITE, true, TextAlign.LEFT);
      }
      text(slide, M, 368, kw, 120, clip(nz(p == null ? null : p.strategy(), PENDING), 300), 10, LIGHT, false, TextAlign.LEFT);
      chart(slide, M + kw + 30, 112, W - M - (M + kw + 30), 300);
      List<String> reasons = PresentationData.safe(p == null ? null : p.justification());
      bullets(slide, M + kw + 30, 420, W - M - (M + kw + 30), 80, clipAll(reasons, 3, 130), 9.5, LIGHT);
    }

    /** Barras horizontales editables: comparables con precio, la expectativa del propietario y el precio recomendado. */
    private void chart(XSLFSlide slide, double x, double y, double w, double h) {
      record Bar(String label, long value, Color color, boolean outlined) {}
      List<Bar> bars = new ArrayList<>();
      for (ComparableListing c : PresentationData.safe(d.comparables())) {
        if (c.price() != null) {
          Color color = PresentationData.ABOVE.equals(c.group()) ? new Color(0x55, 0x55, 0x5A) : PresentationData.BELOW.equals(c.group()) ? GREY : LIGHT;
          bars.add(new Bar(c.id() + " · " + clip(nz(c.zone(), ""), 18), c.price(), color, false));
        }
      }
      var p = d.pricing();
      if (p != null && p.recommendedPrice() != null) {
        bars.add(new Bar("Recomendado", p.recommendedPrice(), GOLD, false));
      }
      if (p != null && p.ownerPrice() != null) {
        bars.add(new Bar("Expectativa", p.ownerPrice(), GOLD, true));
      }
      bars.sort(Comparator.comparingLong(Bar::value).reversed());
      text(slide, x, y - 2, w, 16, deck.rent() ? "RENTAS MENSUALES PUBLICADAS FRENTE A NUESTRA PROPUESTA (MXN)" : "PRECIOS PUBLICADOS FRENTE A NUESTRA PROPUESTA (MXN)", 8.5, GOLD, true, TextAlign.LEFT);
      if (bars.isEmpty()) {
        text(slide, x, y + 30, w, 30, "Sin precios verificables para graficar.", 11, LIGHT, false, TextAlign.LEFT);
        return;
      }
      long max = bars.stream().mapToLong(Bar::value).max().orElse(1);
      double labelW = 120;
      double valueW = 84;
      double top = y + 20;
      double rowH = Math.min(18, (h - 20) / bars.size());
      for (Bar b : bars) {
        double bw = Math.max(2, (w - labelW - valueW - 8) * b.value() / (double) max);
        text(slide, x, top - 2, labelW - 6, rowH, b.label(), 7.5, b.color() == GOLD ? GOLD : LIGHT, b.color() == GOLD, TextAlign.RIGHT);
        XSLFAutoShape bar = rect(slide, x + labelW, top + 2, bw, rowH - 5, b.outlined() ? null : b.color(), b.outlined() ? GOLD : null);
        if (b.outlined()) {
          bar.setLineWidth(1.5);
        }
        text(slide, x + labelW + bw + 4, top - 2, valueW, rowH, money(b.value()), 7.5, WHITE, false, TextAlign.LEFT);
        top += rowH;
      }
    }

    // ---------------------------------------------------------------- 10. Cómo vamos a vender
    void sales() {
      XSLFSlide slide = newSlide(false);
      title(slide, "Estrategia comercial", deck.rent() ? "¿Cómo vamos a rentar tu propiedad?" : "¿Cómo vamos a vender tu propiedad?", false);
      var s = d.sales();
      rect(slide, M, 108, W - 2 * M, 54, INK, null);
      text(slide, M + 18, 114, W - 2 * M - 36, 44, clip(nz(s == null ? null : s.mainMessage(), PENDING), 200), 14, WHITE, true, TextAlign.LEFT);
      double colW = (W - 2 * M - 2 * 18) / 3;
      double y = 178;
      double h = H - y - 52;
      labeled(slide, M, y, colW, h, "POSICIONAMIENTO Y COMPRADOR OBJETIVO", clip(nz(s == null ? null : s.positioning(), PENDING), 200) + "\n\n" + clip(nz(s == null ? null : s.targetBuyer(), ""), 200), false);
      listCard(slide, M + colW + 18, y, colW, h, "ATRIBUTOS A DESTACAR", clipAll(PresentationData.safe(s == null ? null : s.attributes()), 6, 90));
      listCard(slide, M + 2 * (colW + 18), y, colW, h, "PROMOCIÓN", clipAll(PresentationData.safe(s == null ? null : s.promotion()), 6, 90));
      notes(
          slide,
          "DESCRIPCIONES PARA PORTALES (NOM-247-SE-2021)\n\nCorta (máx. 300 caracteres):\n"
              + nz(s == null ? null : s.shortDescription(), PENDING)
              + "\n\nMedia (80 a 120 palabras):\n"
              + nz(s == null ? null : s.mediumDescription(), PENDING)
              + "\n\nCompleta:\n"
              + nz(s == null ? null : s.fullDescription(), PENDING));
    }

    // ---------------------------------------------------------------- 11. Exclusiva
    void exclusive() {
      XSLFSlide slide = newSlide(true);
      title(slide, "CENTURY 21 Genera", "Ventajas de trabajar en exclusiva", true);
      double tw = (W - 2 * M - 3 * 14) / 4;
      double th = 150;
      for (int i = 0; i < EXCLUSIVE_BENEFITS.size(); i++) {
        double x = M + (i % 4) * (tw + 14);
        double y = 112 + (i / 4) * (th + 14);
        rect(slide, x, y, tw, th, new Color(0x33, 0x33, 0x36), null);
        rect(slide, x, y, 3, th, GOLD, null);
        text(slide, x + 14, y + 10, tw - 24, 40, EXCLUSIVE_BENEFITS.get(i)[0], 12, GOLD, true, TextAlign.LEFT);
        text(slide, x + 14, y + 52, tw - 24, th - 60, EXCLUSIVE_BENEFITS.get(i)[1], 9.5, LIGHT, false, TextAlign.LEFT);
      }
      text(
          slide,
          M,
          H - 76,
          W - 2 * M,
          30,
          "Una exclusiva no significa limitar tu propiedad a un solo asesor: significa coordinar todos los esfuerzos comerciales conforme a los acuerdos que tú autorices.",
          11,
          WHITE,
          true,
          TextAlign.LEFT);
    }

    // ---------------------------------------------------------------- 12. Conclusiones
    void closing() {
      XSLFSlide slide = newSlide(false);
      title(slide, "Siguientes pasos", "Conclusiones y siguientes pasos", false);
      var c = d.conclusions();
      double colW = (W - 2 * M - 24) / 2;
      listCard(slide, M, 108, colW, 250, "HALLAZGOS PRINCIPALES", clipAll(PresentationData.safe(c == null ? null : c.findings()), 5, 120));
      rect(slide, M + colW + 24, 108, colW, 72, GOLD, null);
      text(slide, M + colW + 40, 116, colW - 32, 16, deck.rent() ? "RENTA MENSUAL RECOMENDADA" : "PRECIO RECOMENDADO DE SALIDA", 9, INK, true, TextAlign.LEFT);
      text(slide, M + colW + 40, 136, colW - 32, 36, m(d.pricing() == null ? null : d.pricing().recommendedPrice()), 22, INK, true, TextAlign.LEFT);
      listCard(slide, M + colW + 24, 192, colW, 166, "CÓMO INICIAMOS (CONTRATO DE EXCLUSIVA)", clipAll(PresentationData.safe(c == null ? null : c.nextSteps()), 5, 110));
      rect(slide, M, 372, W - 2 * M, 72, INK, null);
      text(slide, M + 18, 380, W - 2 * M - 220, 58, clip(nz(c == null ? null : c.invitation(), "Te proponemos avanzar juntos con una estrategia clara y medible."), 230), 12.5, WHITE, false, TextAlign.LEFT);
      logo(slide, "/feasibility/logo-horizontal-sobre-oscuro.jpg", W - M - 190, 400, 175, 12.6);
      text(slide, M, 452, W - 2 * M, 36, nz(deck.advisorName(), "Tu asesor CENTURY 21 Genera") + " · CENTURY 21 Genera, Cuernavaca, Morelos", 10, MUTED, false, TextAlign.LEFT);
    }

    // ---------------------------------------------------------------- Anexos
    void annexDescriptions() {
      XSLFSlide slide = newSlide(false);
      title(slide, "Anexo A", "Descripciones para portales (editables)", false);
      var s = d.sales();
      labeled(slide, M, 108, W - 2 * M, 70, "CORTA (MÁX. 300 CARACTERES)", nz(s == null ? null : s.shortDescription(), PENDING), false);
      labeled(slide, M, 186, W - 2 * M, 120, "MEDIA (80 A 120 PALABRAS)", nz(s == null ? null : s.mediumDescription(), PENDING), false);
      labeled(slide, M, 314, W - 2 * M, 180, "COMPLETA (PORTALES)", nz(s == null ? null : s.fullDescription(), PENDING), false);
    }

    void annexComparables() {
      XSLFSlide slide = newSlide(false);
      title(slide, "Anexo B", "Comparables utilizados y sus enlaces", false);
      List<ComparableListing> all = PresentationData.safe(d.comparables());
      double y = 108;
      double rowH = all.isEmpty() ? 20 : Math.min(24, (H - y - 60) / all.size());
      XSLFTextBox box = slide.createTextBox();
      box.setAnchor(new Rectangle2D.Double(M, y, W - 2 * M, H - y - 52));
      box.clearText();
      box.setWordWrap(true);
      box.setTextAutofit(TextAutofit.NORMAL);
      if (all.isEmpty()) {
        run(box.addNewTextParagraph(), "No se identificaron comparables verificables.", 11, INK, false);
      }
      for (ComparableListing c : all) {
        XSLFTextParagraph p = box.addNewTextParagraph();
        p.setSpaceAfter(2.0);
        run(p, c.id() + "  ", 10, INK, true);
        run(p, groupLabel(c.group()) + " · " + nz(c.zone(), "—") + " · " + money(c.price()) + " · " + nz(c.source(), "") + "  ", 9.5, INK, false);
        if (c.url() != null && !c.url().isBlank()) {
          XSLFTextRun link = run(p, c.url(), 9, new Color(0x1F, 0x5F, 0xA8), false);
          link.setUnderlined(true);
          link.createHyperlink().setAddress(c.url());
          if (!c.urlVerified()) {
            run(p, "  (liga no verificada)", 8, GREY, false);
          }
        } else {
          run(p, "Sin liga disponible", 8, GREY, false);
        }
      }
    }

    void annexPending() {
      XSLFSlide slide = newSlide(false);
      title(slide, "Anexo C", "Resumen ejecutivo e información pendiente", false);
      labeled(slide, M, 108, W - 2 * M, 150, "RESUMEN EJECUTIVO", clip(nz(d.executiveSummary(), PENDING), 900), false);
      listCard(slide, M, 270, W - 2 * M, H - 270 - 52, "INFORMACIÓN PENDIENTE O NO VERIFICABLE", clipAll(PresentationData.safe(d.pendingItems()), 9, 160));
    }

    // ---------------------------------------------------------------- piezas comunes
    private XSLFSlide newSlide(boolean dark) {
      XSLFSlide slide = ppt.createSlide();
      number++;
      slide.getBackground().setFillColor(dark ? INK : WHITE);
      if (dark) {
        // Además del fondo, un rectángulo: algunos visores ignoran el color de fondo de la lámina.
        rect(slide, 0, 0, W, H, INK, null);
      }
      if (number > 1) {
        logo(slide, dark ? "/feasibility/logo-horizontal-sobre-oscuro.jpg" : "/feasibility/logo-horizontal-sobre-claro.jpg", M, H - 30, 150, 10.8);
        text(slide, W - M - 60, H - 36, 60, 18, String.valueOf(number), 9, dark ? GREY : MUTED, false, TextAlign.RIGHT);
      }
      return slide;
    }

    private void title(XSLFSlide slide, String kicker, String title, boolean dark) {
      text(slide, M, 30, W - 2 * M, 18, kicker.toUpperCase(Locale.ROOT), 10, GOLD, true, TextAlign.LEFT);
      text(slide, M, 48, W - 2 * M, 40, title, 24, dark ? WHITE : INK, true, TextAlign.LEFT);
      rect(slide, M, 92, 56, 3, GOLD, null);
    }

    private void labeled(XSLFSlide slide, double x, double y, double w, double h, String label, String body, boolean dark) {
      rect(slide, x, y, w, h, dark ? new Color(0x33, 0x33, 0x36) : LIGHT, null);
      XSLFTextBox box = slide.createTextBox();
      box.setAnchor(new Rectangle2D.Double(x + 12, y + 8, w - 24, h - 14));
      box.clearText();
      box.setWordWrap(true);
      box.setTextAutofit(TextAutofit.NORMAL);
      XSLFTextParagraph p = box.addNewTextParagraph();
      p.setSpaceAfter(4.0);
      run(p, label, 9, dark ? GOLD : INK, true);
      for (String line : body.split("\n")) {
        XSLFTextParagraph para = box.addNewTextParagraph();
        run(para, line, 10.5, dark ? WHITE : INK, false);
      }
    }

    private void listCard(XSLFSlide slide, double x, double y, double w, double h, String label, List<String> items) {
      rect(slide, x, y, w, h, LIGHT, null);
      text(slide, x + 12, y + 8, w - 24, 18, label, 9, INK, true, TextAlign.LEFT);
      bullets(slide, x + 6, y + 28, w - 12, h - 34, items.isEmpty() ? List.of(PENDING) : items, 10, INK);
    }

    private void notes(XSLFSlide slide, String text) {
      XSLFNotes notes = ppt.getNotesSlide(slide);
      for (XSLFTextShape shape : notes.getPlaceholders()) {
        if (shape.getTextType() == Placeholder.BODY) {
          shape.setText(text);
          return;
        }
      }
    }

    private XSLFPictureShape picture(XSLFSlide slide, byte[] image, double x, double y, double w, double h) {
      PictureData.PictureType type = isPng(image) ? PictureData.PictureType.PNG : PictureData.PictureType.JPEG;
      XSLFPictureData data = ppt.addPicture(image, type);
      XSLFPictureShape pic = slide.createPicture(data);
      pic.setAnchor(new Rectangle2D.Double(x, y, w, h));
      return pic;
    }

    private void logo(XSLFSlide slide, String resource, double x, double y, double w, double h) {
      try (InputStream in = FeasibilityPptxRenderer.class.getResourceAsStream(resource)) {
        if (in != null) {
          picture(slide, in.readAllBytes(), x, y, w, h);
        }
      } catch (IOException ignored) {
        // Sin logo la lámina sigue siendo útil.
      }
    }
  }

  // ------------------------------------------------------------------ utilidades de dibujo

  private static XSLFAutoShape rect(XSLFSlide slide, double x, double y, double w, double h, Color fill, Color line) {
    XSLFAutoShape shape = slide.createAutoShape();
    shape.setShapeType(ShapeType.RECT);
    shape.setAnchor(new Rectangle2D.Double(x, y, w, h));
    shape.setFillColor(fill);
    shape.setLineColor(line);
    return shape;
  }

  private static XSLFTextBox text(XSLFSlide slide, double x, double y, double w, double h, String text, double size, Color color, boolean bold, TextAlign align) {
    XSLFTextBox box = slide.createTextBox();
    box.setAnchor(new Rectangle2D.Double(x, y, w, h));
    box.clearText();
    box.setWordWrap(true);
    box.setInsets(new org.apache.poi.sl.usermodel.Insets2D(2, 4, 2, 4));
    box.setVerticalAlignment(VerticalAlignment.TOP);
    box.setTextAutofit(TextAutofit.NORMAL);
    String[] lines = (text == null ? "" : text).split("\n");
    for (String line : lines) {
      XSLFTextParagraph p = box.addNewTextParagraph();
      p.setTextAlign(align);
      run(p, line, size, color, bold);
    }
    return box;
  }

  private static void bullets(XSLFSlide slide, double x, double y, double w, double h, List<String> items, double size, Color color) {
    XSLFTextBox box = slide.createTextBox();
    box.setAnchor(new Rectangle2D.Double(x, y, w, h));
    box.clearText();
    box.setWordWrap(true);
    box.setTextAutofit(TextAutofit.NORMAL);
    for (String item : items) {
      XSLFTextParagraph p = box.addNewTextParagraph();
      p.setBullet(true);
      p.setBulletCharacter("■");
      p.setBulletFontColor(GOLD);
      p.setBulletFontSize(60.0);
      p.setIndent(-12.0);
      p.setLeftMargin(14.0);
      p.setSpaceAfter(5.0);
      run(p, item, size, color, false);
    }
  }

  private static XSLFTextRun run(XSLFTextParagraph p, String text, double size, Color color, boolean bold) {
    XSLFTextRun r = p.addNewTextRun();
    r.setText(text == null ? "" : text);
    r.setFontFamily(FONT);
    r.setFontSize(size);
    r.setFontColor(color);
    r.setBold(bold);
    return r;
  }

  private static XSLFTextRun cellText(XSLFTableCell cell, String text, double size, Color color, boolean bold) {
    cell.clearText();
    cell.setVerticalAlignment(VerticalAlignment.MIDDLE);
    cell.setInsets(new org.apache.poi.sl.usermodel.Insets2D(2, 5, 2, 5));
    XSLFTextParagraph p = cell.addNewTextParagraph();
    return run(p, text, size, color, bold);
  }

  // ------------------------------------------------------------------ formato de datos

  static String money(Long value) {
    if (value == null) {
      return "Pendiente";
    }
    NumberFormat f = NumberFormat.getIntegerInstance(Locale.forLanguageTag("es-MX"));
    return "$" + f.format(value);
  }

  private static String area(Double m2) {
    if (m2 == null) {
      return "—";
    }
    return NumberFormat.getIntegerInstance(Locale.forLanguageTag("es-MX")).format(Math.round(m2)) + " m²";
  }

  private static String groupLabel(String group) {
    return switch (group == null ? "" : group) {
      case PresentationData.ABOVE -> "Por arriba";
      case PresentationData.BELOW -> "Por debajo";
      default -> "Similar";
    };
  }

  private static String join(String a, String b) {
    if (a == null || a.isBlank()) {
      return nz(b, "—");
    }
    return b == null || b.isBlank() ? a : a + ". " + b;
  }

  static String nz(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value.strip();
  }

  /** Recorta textos largos para que no se desborden; el contenido completo queda en el Word. */
  static String clip(String text, int max) {
    if (text == null || text.length() <= max) {
      return text;
    }
    int cut = text.lastIndexOf(' ', max - 1);
    return text.substring(0, cut > max / 2 ? cut : max - 1).replaceAll("[\\s,;:.]+$", "") + "…";
  }

  private static List<String> clipAll(List<String> items, int maxItems, int maxChars) {
    return items.stream().filter(s -> s != null && !s.isBlank()).limit(maxItems).map(s -> clip(s.strip(), maxChars)).toList();
  }

  private static boolean isPng(byte[] image) {
    return image.length > 4 && (image[0] & 0xFF) == 0x89 && image[1] == 'P' && image[2] == 'N' && image[3] == 'G';
  }

  /** Recorta la foto al tamaño de su marco (como "rellenar") para no deformarla. */
  static byte[] coverCrop(byte[] image, double targetW, double targetH) {
    try {
      BufferedImage src = ImageIO.read(new ByteArrayInputStream(image));
      if (src == null) {
        return image;
      }
      double targetRatio = targetW / targetH;
      int sw = src.getWidth();
      int sh = src.getHeight();
      int cw = sw;
      int ch = (int) Math.round(sw / targetRatio);
      if (ch > sh) {
        ch = sh;
        cw = (int) Math.round(sh * targetRatio);
      }
      int outW = Math.min(cw, 1600);
      int outH = (int) Math.round(outW / targetRatio);
      BufferedImage out = new BufferedImage(outW, outH, BufferedImage.TYPE_INT_RGB);
      Graphics2D g = out.createGraphics();
      g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
      int sx = (sw - cw) / 2;
      int sy = (sh - ch) / 2;
      g.drawImage(src, 0, 0, outW, outH, sx, sy, sx + cw, sy + ch, null);
      g.dispose();
      ByteArrayOutputStream bytes = new ByteArrayOutputStream();
      ImageIO.write(out, "jpg", bytes);
      return bytes.toByteArray();
    } catch (IOException e) {
      return image;
    }
  }
}
