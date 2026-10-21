package com.c21genera.online21.infrastructure;

import com.c21genera.online21.PropertyDossier;
import com.c21genera.online21.PropertyListings.Listing;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Detalle de una propiedad de 21Online para la pantalla interna (datos generales, portales,
 * clientes relacionados, seguimientos y ficha técnica). Cada consulta va en vivo a 21Online.
 * Nunca copia dueño (idClientesVendedor) ni datos de cierre; no registra cuerpos de respuesta.
 */
@Service
class Online21Dossier implements PropertyDossier {

  private static final Logger log = LoggerFactory.getLogger(Online21Dossier.class);
  private static final Pattern URL = Pattern.compile("https?://[^\\s\"'<>\\\\]+");
  /** Páginas máximas por colección (30 elementos por página en 21Online). */
  private static final int MAX_PAGES = 5;

  private static final ObjectMapper JSON = new ObjectMapper();

  private final Online21Client client;
  private final Online21Properties properties;
  private final PropertySheetPdf sheetPdf;

  Online21Dossier(Online21Client client, Online21Properties properties, PropertySheetPdf sheetPdf) {
    this.client = client;
    this.properties = properties;
    this.sheetPdf = sheetPdf;
  }

  // ---------------------------------------------------------------------------
  // Datos generales
  // ---------------------------------------------------------------------------

  @Override
  public Optional<Details> details(long id) {
    if (!client.configured()) {
      return Optional.empty();
    }
    JsonNode n = client.property(id);
    if (n == null) {
      return Optional.empty();
    }
    Listing listing = Online21ListingMapper.toListing(n);
    String template = properties.publicUrlTemplate();
    String publicUrl = template == null || template.isBlank() ? null : template.replace("{id}", Long.toString(id));
    return Optional.of(
        new Details(
            listing,
            fields(n),
            urls(n.get("fotos")),
            urls(n.get("fotosPlanos")),
            Online21PropertyMapper.text(n, "videoUrl"),
            Online21PropertyMapper.text(n, "encabezadoIngles"),
            Online21PropertyMapper.text(n, "descripcionIngles"),
            coordinate(n, "lat"),
            coordinate(n, "lon"),
            publicUrl,
            advisor(Online21PropertyMapper.advisorId(n))));
  }

  /** Todos los datos que 21Online muestra en "Datos generales", con etiquetas en español y sin vacíos. */
  static List<Field> fields(JsonNode n) {
    List<Field> out = new ArrayList<>();
    String g = "Generales";
    add(out, n, g, "Clave", "clave", Kind.TEXT);
    add(out, n, g, "Estatus", "status", Kind.STATUS);
    add(out, n, g, "Operación", "tipoOperacion", Kind.CAPITALIZE);
    add(out, n, g, "Tipo de propiedad", "tipoPropiedad", Kind.CAPITALIZE);
    add(out, n, g, "Subtipo", "subtipoPropiedad", Kind.SUBTYPE);
    add(out, n, g, "Precio de venta", "precioVenta", Kind.MONEY);
    add(out, n, g, "Precio de renta", "precioRenta", Kind.MONEY);
    add(out, n, g, "Moneda", "moneda", Kind.TEXT);
    add(out, n, g, "Información extra del precio", "infoExtraPrecio", Kind.TEXT);
    add(out, n, g, "Ocultar precio en internet", "ocultarPrecioInternet", Kind.BOOL);
    add(out, n, g, "Cuota de mantenimiento", "cuotaMantenimiento", Kind.MONEY);
    add(out, n, g, "Mantenimiento incluido en el precio", "mantenimientoIncluidoEnPrecio", Kind.BOOL);
    add(out, n, g, "Acepta créditos", "aceptaCreditos", Kind.BOOL);
    add(out, n, g, "Créditos que acepta", "tiposDeCreditoQueAcepta", Kind.TEXT);
    add(out, n, g, "Estatus jurídico", "statusJuridico", Kind.CAPITALIZE);
    add(out, n, g, "Clave catastral", "claveCatastral", Kind.TEXT);
    add(out, n, g, "Publicada en internet", "enInternet", Kind.BOOL);
    add(out, n, g, "Compartida con C21", "compartidoConC21", Kind.BOOL);
    add(out, n, g, "Con rótulo", "conRotulo", Kind.BOOL);
    add(out, n, g, "Fine Homes", "fineHomes", Kind.BOOL);

    String c = "Contrato y comisión";
    add(out, n, c, "Tipo de contrato", "tipoDeContrato", Kind.CAPITALIZE);
    add(out, n, c, "Comisión", "comision", Kind.PERCENT);
    add(out, n, c, "Comisión a compartir", "comisionACompartirInmobiliariasExternas", Kind.PERCENT);
    add(out, n, c, "Precio en contrato", "precioEnContrato", Kind.MONEY);
    add(out, n, c, "Fecha de firma del contrato", "fechaFirmaCPS", Kind.DATE);
    add(out, n, c, "Duración del contrato (días)", "duracionCPS", Kind.TEXT);
    add(out, n, c, "Fecha de expiración", "fechaExpiracion", Kind.DATE);

    String u = "Ubicación";
    add(out, n, u, "Calle", "calle", Kind.TEXT);
    add(out, n, u, "Número", "numero", Kind.TEXT);
    add(out, n, u, "Colonia", "colonia", Kind.TEXT);
    add(out, n, u, "Municipio", "municipio", Kind.TEXT);
    add(out, n, u, "Estado", "estado", Kind.TEXT);
    add(out, n, u, "Código postal", "cP", Kind.TEXT);
    add(out, n, u, "Dirección completa", "direccionCompleta", Kind.TEXT);
    add(out, n, u, "Ocultar calle en internet", "ocultarCalleInternet", Kind.BOOL);
    add(out, n, u, "Ocultar número en internet", "ocultarNumeroInternet", Kind.BOOL);

    String f = "Características";
    add(out, n, f, "Terreno (m²)", "m2T", Kind.NUMBER);
    add(out, n, f, "Construcción (m²)", "m2C", Kind.NUMBER);
    add(out, n, f, "Recámaras", "recamaras", Kind.NUMBER);
    add(out, n, f, "Baños", "banios", Kind.NUMBER);
    add(out, n, f, "Medios baños", "mediosBanios", Kind.NUMBER);
    add(out, n, f, "Estacionamientos", "estacionamientos", Kind.NUMBER);
    add(out, n, f, "Niveles construidos", "nivelesConstruidos", Kind.NUMBER);
    add(out, n, f, "Edad (años)", "edad", Kind.NUMBER);
    add(out, n, f, "Estado de conservación", "edoConservacion", Kind.CAPITALIZE);
    add(out, n, f, "Cocina", "cocina", Kind.CAPITALIZE);
    add(out, n, f, "Cocina (m²)", "m2Cocina", Kind.NUMBER);
    add(out, n, f, "Tipo de terreno", "tipoTerreno", Kind.CAPITALIZE);
    add(out, n, f, "Metros de frente", "metrosFrente", Kind.NUMBER);
    add(out, n, f, "Metros de fondo", "metrosFondo", Kind.NUMBER);
    add(out, n, f, "Elevadores", "numeroElevadores", Kind.NUMBER);
    add(out, n, f, "Piso en que se encuentra", "pisoEnQueSeEncuentra", Kind.TEXT);
    add(out, n, f, "Número de casas", "numeroDeCasas", Kind.NUMBER);
    add(out, n, f, "Área de oficinas (m²)", "areaDeOficinas", Kind.NUMBER);
    add(out, n, f, "Regaderas", "regaderas", Kind.NUMBER);
    add(out, n, f, "Tipo de piso", "tipoPiso", Kind.CAPITALIZE);
    add(out, n, f, "Ambientes", "ambientes", Kind.NUMBER);
    add(out, n, f, "Alberca", "alberca", Kind.BOOL);
    add(out, n, f, "Acepta mascotas", "mascotas", Kind.BOOL);
    String amenities = amenities(Online21PropertyMapper.text(n, "caracteristicasJSON"));
    if (amenities != null) {
      out.add(new Field(f, "Amenidades", amenities));
    }

    String o = "Notas internas";
    add(out, n, o, "Observaciones de la oficina", "observacionesOfna", Kind.TEXT);
    add(out, n, o, "Circunstancias excepcionales", "circunstanciasExcepcionales", Kind.TEXT);
    add(out, n, o, "Comentarios para la cita", "comentariosCita", Kind.TEXT);

    String d = "Fechas";
    add(out, n, d, "Alta", "fechaAlta", Kind.DATE);
    add(out, n, d, "En promoción desde", "fechaEnPromocion", Kind.DATE);
    add(out, n, d, "Última modificación", "fechaModificacion", Kind.DATE);
    add(out, n, d, "Suspensión", "fechaSuspension", Kind.DATE);
    add(out, n, d, "Cancelación", "fechaCancelacion", Kind.DATE);
    return out;
  }

  private enum Kind { TEXT, CAPITALIZE, SUBTYPE, STATUS, MONEY, NUMBER, PERCENT, BOOL, DATE }

  private static final Map<String, String> STATUS =
      Map.of(
          "enPromocion", "En promoción",
          "suspendida", "Suspendida",
          "cancelada", "Cancelada",
          "conIntencion", "Con intención",
          "conContratoFirmado", "Con contrato firmado",
          "cerrada", "Cerrada");

  private static void add(List<Field> out, JsonNode n, String section, String label, String field, Kind kind) {
    String raw = Online21PropertyMapper.text(n, field);
    if (raw == null || "null".equals(raw)) {
      return;
    }
    String value =
        switch (kind) {
          case TEXT -> raw;
          case CAPITALIZE -> Character.toUpperCase(raw.charAt(0)) + raw.substring(1).replace('-', ' ');
          case SUBTYPE -> Online21PropertyMapper.subtypeLabel(raw);
          case STATUS -> STATUS.getOrDefault(raw, raw);
          case MONEY -> money(Online21PropertyMapper.decimal(n, field));
          case NUMBER -> number(Online21PropertyMapper.decimal(n, field));
          case PERCENT -> {
            String num = number(Online21PropertyMapper.decimal(n, field));
            yield num == null ? null : num + "%";
          }
          case BOOL -> "true".equalsIgnoreCase(raw) || "1".equals(raw) ? "Sí" : "false".equalsIgnoreCase(raw) || "0".equals(raw) ? "No" : raw;
          case DATE -> raw.length() >= 10 ? raw.substring(0, 10) : raw;
        };
    if (value != null && !value.isBlank()) {
      out.add(new Field(section, label, value));
    }
  }

  private static String money(BigDecimal value) {
    if (value == null) {
      return null;
    }
    NumberFormat f = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("es-MX"));
    f.setMaximumFractionDigits(0);
    return f.format(value);
  }

  private static String number(BigDecimal value) {
    return value == null ? null : value.stripTrailingZeros().toPlainString();
  }

  /** caracteristicasJSON puede ser un arreglo de textos u objetos, o un objeto {nombre: valor}. */
  private static String amenities(String json) {
    if (json == null || json.isBlank()) {
      return null;
    }
    try {
      JsonNode node = JSON.readTree(json);
      List<String> items = new ArrayList<>();
      if (node.isArray()) {
        node.forEach(
            item -> {
              String text = item.isTextual() ? item.asText() : Online21PropertyMapper.text(item, "nombre");
              if (text == null) {
                text = Online21PropertyMapper.text(item, "label");
              }
              if (text != null && !text.isBlank()) {
                items.add(text);
              }
            });
      } else if (node.isObject()) {
        node.properties()
            .forEach(
                e -> {
                  JsonNode v = e.getValue();
                  if (v.isBoolean() ? v.asBoolean() : !(v.isNull() || v.asText().isBlank() || "0".equals(v.asText()) || "false".equals(v.asText()))) {
                    items.add(v.isBoolean() || "1".equals(v.asText()) || "true".equals(v.asText()) ? e.getKey() : e.getKey() + ": " + v.asText());
                  }
                });
      }
      return items.isEmpty() ? null : String.join(", ", items);
    } catch (java.io.IOException | RuntimeException e) {
      return null;
    }
  }

  private static List<String> urls(JsonNode photos) {
    List<String> out = new ArrayList<>();
    if (photos == null || !photos.isArray()) {
      return out;
    }
    List<JsonNode> sorted = new ArrayList<>();
    photos.forEach(sorted::add);
    sorted.sort(java.util.Comparator.comparingInt(p -> p.path("orden").asInt(Integer.MAX_VALUE)));
    for (JsonNode photo : sorted) {
      String url = photo.isTextual() ? photo.asText() : Online21PropertyMapper.text(photo, "path");
      if (url != null && url.startsWith("http")) {
        out.add(url);
      }
    }
    return out;
  }

  private static Double coordinate(JsonNode n, String field) {
    String s = Online21PropertyMapper.text(n, field);
    try {
      double v = s == null ? 0 : Double.parseDouble(s);
      return v == 0 ? null : v;
    } catch (NumberFormatException e) {
      return null;
    }
  }

  private Advisor advisor(Long online21Id) {
    if (online21Id == null) {
      return null;
    }
    try {
      JsonNode u = client.item("/api/usuarios/" + online21Id);
      if (u == null) {
        return new Advisor(online21Id, null, null, null, null, null);
      }
      String name = Online21PropertyMapper.text(u, "nombreCompleto");
      if (name == null) {
        name = personName(u);
      }
      String photo = Online21PropertyMapper.text(u, "fotoPath");
      return new Advisor(
          online21Id,
          name,
          Online21PropertyMapper.text(u, "email"),
          Online21PropertyMapper.text(u, "telMovil") != null ? Online21PropertyMapper.text(u, "telMovil") : Online21PropertyMapper.text(u, "whatsapp"),
          Online21PropertyMapper.text(u, "telOfna"),
          photo != null && photo.startsWith("http") ? photo : null);
    } catch (RuntimeException e) {
      log.warn("No se pudo leer el asesor de 21Online ({})", e.getClass().getSimpleName());
      return new Advisor(online21Id, null, null, null, null, null);
    }
  }

  // ---------------------------------------------------------------------------
  // Portales, clientes y seguimientos
  // ---------------------------------------------------------------------------

  @Override
  public List<PortalListing> portals(long id) {
    if (!client.configured()) {
      return List.of();
    }
    List<PortalListing> out = new ArrayList<>();
    for (JsonNode a : collect("/api/asignacion_portales", Map.of("idPropiedades", Long.toString(id)))) {
      out.add(portalOf(a));
    }
    out.sort(java.util.Comparator.comparing((PortalListing p) -> !p.active()).thenComparing(p -> p.portal() == null ? "" : p.portal()));
    return out;
  }

  static PortalListing portalOf(JsonNode a) {
    String url = null;
    String reference = null;
    for (String field : List.of("extra1", "extra2", "extra3", "json")) {
      String value = Online21PropertyMapper.text(a, field);
      if (value == null) {
        continue;
      }
      Matcher m = URL.matcher(value.replace("\\/", "/"));
      if (url == null && m.find()) {
        url = m.group();
      } else if (reference == null && !"json".equals(field) && !value.startsWith("http")) {
        String label = Online21PropertyMapper.text(a, "nombre" + field.substring(5));
        reference = label != null ? label + ": " + value : value;
      }
    }
    String portal = Online21PropertyMapper.text(a, "nombrePortal");
    JsonNode active = a.get("activo");
    JsonNode error = a.get("conError");
    return new PortalListing(
        portal != null ? prettyPortal(portal) : "Portal",
        active != null && (active.asBoolean(false) || "1".equals(active.asText())),
        Online21PropertyMapper.text(a, "status"),
        url,
        reference,
        error != null && (error.asBoolean(false) || "1".equals(error.asText())),
        Online21PropertyMapper.text(a, "mensajeError"),
        instant(a, "fechaModificacion"));
  }

  private static String prettyPortal(String raw) {
    String lower = raw.toLowerCase(Locale.ROOT);
    if (lower.contains("mercado")) return "Mercado Libre";
    if (lower.contains("inmuebles24")) return "Inmuebles24";
    if (lower.contains("vivanuncios")) return "Vivanuncios";
    if (lower.contains("lamudi")) return "Lamudi";
    if (lower.contains("propiedades.com") || lower.equals("propiedades")) return "Propiedades.com";
    if (lower.contains("facebook")) return "Facebook";
    if (lower.contains("easybroker")) return "EasyBroker";
    if (lower.contains("century") || lower.contains("c21")) return "CENTURY 21";
    return Character.toUpperCase(raw.charAt(0)) + raw.substring(1);
  }

  @Override
  public List<RelatedClient> relatedClients(long id) {
    if (!client.configured()) {
      return List.of();
    }
    List<RelatedClient> out = new ArrayList<>();
    for (JsonNode p : collect("/api/prospectos", Map.of("idPropiedades.id", Long.toString(id)))) {
      String phone = Online21PropertyMapper.text(p, "celular");
      out.add(
          new RelatedClient(
              p.hasNonNull("id") ? p.get("id").asLong() : null,
              Online21PropertyMapper.text(p, "nombre"),
              phone != null ? phone : Online21PropertyMapper.text(p, "telefono"),
              Online21PropertyMapper.text(p, "email"),
              Online21PropertyMapper.text(p, "medio"),
              Online21PropertyMapper.text(p, "status"),
              Online21PropertyMapper.text(p, "tipo"),
              truncate(Online21PropertyMapper.text(p, "mensaje"), 600),
              instant(p, "fechaAlta")));
    }
    out.sort(java.util.Comparator.comparing(RelatedClient::createdAt, java.util.Comparator.nullsLast(java.util.Comparator.reverseOrder())));
    return out;
  }

  /**
   * 21Online solo filtra seguimientos por la calle de la propiedad: se piden los de esa calle y se
   * dejan los que también coinciden en número y colonia.
   */
  @Override
  public List<FollowUp> followUps(long id) {
    if (!client.configured()) {
      return List.of();
    }
    JsonNode property = client.property(id);
    String street = property == null ? null : Online21PropertyMapper.text(property, "calle");
    if (street == null) {
      return List.of();
    }
    String number = Online21PropertyMapper.text(property, "numero");
    String neighborhood = Online21PropertyMapper.text(property, "colonia");
    List<FollowUp> out = new ArrayList<>();
    for (JsonNode s : collect("/api/seguimientos", Map.of("seguimientosPropiedades.idPropiedades.calle", street))) {
      if (!sameProperty(s.get("seguimientosPropiedades"), street, number, neighborhood)) {
        continue;
      }
      out.add(
          new FollowUp(
              s.hasNonNull("id") ? s.get("id").asLong() : null,
              Online21PropertyMapper.text(s, "tipo"),
              Online21PropertyMapper.text(s, "subtipo"),
              personName(s.get("idAsesor")),
              personName(s.get("idClientes")),
              instant(s, "fechaCreacion")));
    }
    out.sort(java.util.Comparator.comparing(FollowUp::createdAt, java.util.Comparator.nullsLast(java.util.Comparator.reverseOrder())));
    return out;
  }

  static boolean sameProperty(JsonNode links, String street, String number, String neighborhood) {
    if (links == null || !links.isArray()) {
      return false;
    }
    for (JsonNode link : links) {
      JsonNode p = link.get("idPropiedades");
      if (p == null || !p.isObject()) {
        continue;
      }
      if (equalsIgnoreCase(Online21PropertyMapper.text(p, "calle"), street)
          && (number == null || equalsIgnoreCase(Online21PropertyMapper.text(p, "numero"), number))
          && (neighborhood == null || equalsIgnoreCase(Online21PropertyMapper.text(p, "colonia"), neighborhood))) {
        return true;
      }
    }
    return false;
  }

  @Override
  public Optional<byte[]> sheet(long id, SheetVariant variant) {
    return details(id).map(d -> sheetPdf.render(d, variant));
  }

  // ---------------------------------------------------------------------------

  private List<JsonNode> collect(String path, Map<String, String> filters) {
    List<JsonNode> out = new ArrayList<>();
    try {
      for (int page = 1; page <= MAX_PAGES; page++) {
        JsonNode root = client.collection(path, new LinkedHashMap<>(filters), page);
        List<JsonNode> items = client.membersOf(root);
        out.addAll(items);
        JsonNode view = root == null ? null : root.has("hydra:view") ? root.get("hydra:view") : root.get("view");
        boolean hasNext = view != null && (view.hasNonNull("hydra:next") || view.hasNonNull("next"));
        if (!hasNext || items.isEmpty()) {
          break;
        }
      }
    } catch (RuntimeException e) {
      log.warn("No se pudo consultar {} en 21Online ({})", path, e.getClass().getSimpleName());
      throw new IllegalStateException("21Online no respondió. Intenta de nuevo en un momento.", e);
    }
    return out;
  }

  private static String personName(JsonNode p) {
    if (p == null || !p.isObject()) {
      return null;
    }
    String full = Online21PropertyMapper.text(p, "nombreCompleto");
    if (full != null) {
      return full;
    }
    String name = String.join(" ", java.util.stream.Stream.of("nombre", "apellidoP", "apellidoM").map(f -> Online21PropertyMapper.text(p, f)).filter(java.util.Objects::nonNull).toList());
    return name.isBlank() ? null : name;
  }

  private static boolean equalsIgnoreCase(String a, String b) {
    return a != null && b != null && a.trim().equalsIgnoreCase(b.trim());
  }

  private static Instant instant(JsonNode n, String field) {
    String s = Online21PropertyMapper.text(n, field);
    if (s == null) {
      return null;
    }
    try {
      return OffsetDateTime.parse(s).toInstant();
    } catch (RuntimeException e) {
      return null;
    }
  }

  private static String truncate(String s, int max) {
    return s == null || s.length() <= max ? s : s.substring(0, max - 1) + "…";
  }
}
