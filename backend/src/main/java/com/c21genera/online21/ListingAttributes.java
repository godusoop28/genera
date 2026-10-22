package com.c21genera.online21;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Atributos de una propiedad de 21Online que Genera guarda y sincroniza
 * además de los campos fijos del formulario (características, contrato y
 * publicación). Se guardan con la misma clave y forma que usa la API de
 * 21Online ("Propiedades-propiedades.create"), así que se mandan tal cual y
 * al editar solo viajan los que cambiaron.
 *
 * <p>writeOnly: 21Online los acepta al crear o editar pero su API no los
 * devuelve; Genera conserva lo último que se capturó aquí.
 *
 * <p>Las amenidades (jardín, gimnasio, cisterna…) van en {@link #AMENITIES_KEY}
 * como mapa nombre → valor y se traducen al "caracteristicasJSON" de 21Online.
 */
public final class ListingAttributes {

  /** Clave de las amenidades dentro del mapa de atributos. */
  public static final String AMENITIES_KEY = "amenidades";

  /**
   * caracteristicasJSON tal como lo trae 21Online (en el borrador, no en el formulario): plantilla para
   * cambiar solo las casillas sin perder sus demás campos.
   */
  public static final String FEATURES_TEMPLATE = "caracteristicasJSON";

  /** Amenidad de texto: liga al recorrido virtual. */
  public static final String VIRTUAL_TOUR = "matterport";

  private ListingAttributes() {}

  public enum Group {
    FEATURES,
    CONTRACT,
    PUBLISHING
  }

  public enum Kind {
    TEXT,
    LONG_TEXT,
    URL,
    NUMBER,
    INTEGER,
    BOOLEAN,
    DATE,
    CHOICE
  }

  public record Option(String value, String label) {}

  /** apiInteger: 21Online lo declara entero (se manda como número); los demás números van como texto. */
  public record Attribute(String key, String label, Group group, Kind kind, List<Option> options, boolean writeOnly, boolean apiInteger, int maxLength) {}

  public record Amenity(String key, String label) {}

  public static final List<Attribute> ATTRIBUTES =
      List.of(
          // Características
          choice("cocina", "Cocina", Group.FEATURES, opt("integral", "Integral"), opt("cocineta", "Cocineta"), opt("soloTarja", "Solo tarja"), opt("sinCocina", "Sin cocina")),
          text("edoConservacion", "Estado de conservación", Group.FEATURES, 80),
          text("tipoPiso", "Tipo de piso", Group.FEATURES, 80),
          choice("tipoTerreno", "Forma del terreno", Group.FEATURES, opt("regular", "Regular"), opt("irregular", "Irregular")),
          number("metrosFrente", "Frente del terreno (m)", Group.FEATURES),
          number("metrosFondo", "Fondo del terreno (m)", Group.FEATURES),
          number("m2Cocina", "Cocina (m²)", Group.FEATURES),
          new Attribute("regaderas", "Regaderas", Group.FEATURES, Kind.INTEGER, List.of(), false, true, 0),
          integer("numeroElevadores", "Elevadores", Group.FEATURES),
          integer("pisoEnQueSeEncuentra", "Piso en que se encuentra", Group.FEATURES),
          new Attribute("areaDeOficinas", "Área de oficinas (m²)", Group.FEATURES, Kind.INTEGER, List.of(), false, true, 0),
          new Attribute("numeroDeCasas", "Casas en el fraccionamiento", Group.FEATURES, Kind.INTEGER, List.of(), false, true, 0),
          bool("alberca", "Alberca", Group.FEATURES),
          bool("mascotas", "Acepta mascotas", Group.FEATURES),
          // Contrato y comisión
          new Attribute("fechaFirmaCPS", "Firma del contrato de servicios", Group.CONTRACT, Kind.DATE, List.of(), true, false, 0),
          new Attribute("duracionCPS", "Duración del contrato (días)", Group.CONTRACT, Kind.INTEGER, List.of(), true, true, 0),
          new Attribute("precioEnContrato", "Precio en el contrato", Group.CONTRACT, Kind.NUMBER, List.of(), true, false, 0),
          new Attribute("monedaEnContrato", "Moneda del contrato", Group.CONTRACT, Kind.CHOICE, List.of(opt("MXN", "Pesos (MXN)"), opt("USD", "Dólares (USD)")), true, false, 0),
          number("comisionACompartirInmobiliariasExternas", "Comisión a compartir con otras inmobiliarias", Group.CONTRACT),
          new Attribute("compartidoConC21", "Compartida con la red CENTURY 21", Group.CONTRACT, Kind.BOOLEAN, List.of(), true, false, 0),
          new Attribute("conRotulo", "Con rótulo", Group.CONTRACT, Kind.BOOLEAN, List.of(), true, false, 0),
          text("statusJuridico", "Situación jurídica", Group.CONTRACT, 120),
          // Publicación
          text("encabezadoIngles", "Encabezado en inglés", Group.PUBLISHING, 200),
          new Attribute("descripcionIngles", "Descripción en inglés", Group.PUBLISHING, Kind.LONG_TEXT, List.of(), false, false, 20000),
          new Attribute("videoUrl", "Video (liga)", Group.PUBLISHING, Kind.URL, List.of(), false, false, 500),
          new Attribute("lat", "Latitud", Group.PUBLISHING, Kind.NUMBER, List.of(), false, false, 0),
          new Attribute("lon", "Longitud", Group.PUBLISHING, Kind.NUMBER, List.of(), false, false, 0),
          text("infoExtraPrecio", "Información extra del precio", Group.PUBLISHING, 200),
          bool("mantenimientoIncluidoEnPrecio", "Mantenimiento incluido en el precio", Group.PUBLISHING),
          bool("aceptaCreditos", "Acepta créditos", Group.PUBLISHING),
          text("tiposDeCreditoQueAcepta", "Créditos que acepta", Group.PUBLISHING, 200));

  /** Catálogo de amenidades de 21Online (casillas de "caracteristicasJSON"), en su orden. */
  public static final List<Amenity> AMENITIES =
      List.of(
          a("jardin", "Jardín"), a("estudio", "Estudio"), a("cuartoServicio", "Cuarto servicio"), a("desayunador", "Desayunador"), a("despensa", "Despensa"),
          a("sala", "Sala"), a("comedor", "Comedor"), a("cuartoTV", "Cuarto TV"), a("biblioteca", "Biblioteca"), a("gimnasio", "Gimnasio"),
          a("cantina", "Cantina"), a("balcon", "Balcón"), a("cuartoJuegos", "Cuarto juegos"), a("areaLavado", "Área de Lavado"),
          a("patioServicio", "Patio servicio"), a("bodega", "Bodega"), a("sotano", "Sótano"), a("atico", "Atico"),
          a("conLocalComercialAdjunto", "Con local comercial adjunto"), a("salonDeFiestas", "Salón de Fiestas"), a("canchaTenis", "Cancha tenis"),
          a("canchaBasquet", "Cancha basquet"), a("caballeriza", "Caballeriza"), a("agua", "Agua"), a("drenaje", "Drenaje"), a("luz", "Luz"),
          a("lineaTelefonica", "Línea telefónica"), a("chimenea", "Chimenea"), a("cisterna", "Cisterna"), a("aireAcondicionado", "Aire acondicionado"),
          a("calefaccion", "Calefacción"), a("jacuzzi", "Jacuzzi"), a("tvCable", "TV cable"), a("conmutador", "Conmutador"),
          a("plantaElectrica", "Planta electrica"), a("circuitoCerrado", "Circuito cerrado"), a("fibraOptica", "Fibra Óptica"), a("elevador", "Elevador"),
          a("sistemaContraIncendios", "Sistema contra incendios"), a("alumbrado", "Alumbrado"), a("hidroneumatico", "Hidroneumático"),
          a("closets", "Closets"), a("portonElectrico", "Portón eléctrico"), a("interfon", "Interfón"), a("videoPortero", "Video portero"),
          a("tanqueGasEstacionario", "Tanque gas estacionario"), a("gasTanqueCilindrico", "Gas tanque cilindrico"), a("gasRed", "Gas red"),
          a("asador", "Asador"), a("tinaco", "Tinaco"), a("internet", "Internet"), a("alarma", "Alarma"), a("hornoBarbacoa", "Horno barbacoa"),
          a("esquina", "Esquina"), a("parquesCercanos", "Parques cercanos"), a("puerto", "Puerto"), a("playa", "Playa"), a("ferrocarril", "Ferrocarril"),
          a("noNinos", "No niños"), a("soloFamilias", "Solo familias"), a("paraEjecutivos", "Para ejecutivos"), a("paraEstudiantes", "Para estudiantes"),
          a("amueblado", "Amueblado"), a("vigilanciaPrivada", "Vigilancia privada"), a("propiedadNueva", "Propiedad nueva"), a("lujo", "Lujo"),
          a("licenciaVinosYLicores", "Licencia vinos y licores"), a("trampasGrasa", "Trampas grasa"), a("purificadorAgua", "Purificador agua"),
          a("spa", "Spa"), a("restaurante", "Restaurante"), a("golf", "Golf"), a("tienda", "Tienda"), a("salasJuntas", "Salas juntas"));

  public static final Map<String, Attribute> BY_KEY = ATTRIBUTES.stream().collect(Collectors.toMap(Attribute::key, Function.identity(), (x, y) -> x, LinkedHashMap::new));

  private static final Set<String> AMENITY_KEYS = AMENITIES.stream().map(Amenity::key).collect(Collectors.toSet());

  /**
   * Forma canónica (la que se manda a 21Online) de lo que llega del formulario o de 21Online: descarta
   * claves desconocidas y vacíos. Lanza {@link IllegalArgumentException} con un mensaje para el usuario si
   * un valor no es válido.
   */
  public static Map<String, Object> normalize(Map<String, ?> in) {
    Map<String, Object> out = new TreeMap<>();
    if (in == null) {
      return out;
    }
    for (Map.Entry<String, ?> e : in.entrySet()) {
      if (AMENITIES_KEY.equals(e.getKey())) {
        Map<String, Object> amenities = amenities(e.getValue());
        if (!amenities.isEmpty()) {
          out.put(AMENITIES_KEY, amenities);
        }
        continue;
      }
      Attribute attr = BY_KEY.get(e.getKey());
      if (attr == null) {
        continue;
      }
      Object value = value(attr, e.getValue());
      if (value != null) {
        out.put(attr.key(), value);
      }
    }
    return out;
  }

  /** Solo los atributos que 21Online no devuelve (para conservarlos al traer cambios de allá). */
  public static Map<String, Object> writeOnly(Map<String, Object> extras) {
    Map<String, Object> out = new TreeMap<>();
    if (extras != null) {
      extras.forEach(
          (k, v) -> {
            Attribute attr = BY_KEY.get(k);
            if (attr != null && attr.writeOnly()) {
              out.put(k, v);
            }
          });
    }
    return out;
  }

  /** Amenidades marcadas (true) y el recorrido virtual; las desconocidas que vengan de 21Online se conservan. */
  private static Map<String, Object> amenities(Object raw) {
    Map<String, Object> out = new TreeMap<>();
    if (!(raw instanceof Map<?, ?> map)) {
      return out;
    }
    for (Map.Entry<?, ?> e : map.entrySet()) {
      String key = String.valueOf(e.getKey());
      Object v = e.getValue();
      if (VIRTUAL_TOUR.equals(key)) {
        String url = v == null ? null : v.toString().trim();
        if (url != null && !url.isEmpty()) {
          out.put(key, requireUrl(url, "La liga del recorrido virtual"));
        }
      } else if (key.matches("[A-Za-z0-9]{1,60}") && (AMENITY_KEYS.contains(key) || v instanceof Boolean) && Boolean.TRUE.equals(bool(v))) {
        out.put(key, Boolean.TRUE);
      }
    }
    return out;
  }

  private static Object value(Attribute attr, Object raw) {
    if (raw == null || (raw instanceof String s && s.isBlank())) {
      return null;
    }
    String text = raw.toString().trim();
    return switch (attr.kind()) {
      case BOOLEAN -> bool(raw);
      case TEXT, LONG_TEXT -> {
        int max = attr.maxLength() > 0 ? attr.maxLength() : 200;
        if (text.length() > max) {
          throw new IllegalArgumentException(attr.label() + ": máximo " + max + " caracteres.");
        }
        yield text;
      }
      case URL -> requireUrl(text, attr.label());
      case CHOICE -> {
        // Un valor de 21Online fuera de la lista se conserva tal cual (no se pierde al traerlo).
        if (text.length() > 80) {
          throw new IllegalArgumentException(attr.label() + ": valor no válido.");
        }
        yield text;
      }
      case DATE -> {
        String day = text.length() >= 10 ? text.substring(0, 10) : text;
        try {
          yield LocalDate.parse(day).toString();
        } catch (RuntimeException e) {
          throw new IllegalArgumentException(attr.label() + ": fecha no válida (aaaa-mm-dd).");
        }
      }
      case NUMBER, INTEGER -> {
        BigDecimal n;
        try {
          n = new BigDecimal(text.replace(",", ""));
        } catch (NumberFormatException e) {
          throw new IllegalArgumentException(attr.label() + ": debe ser un número.");
        }
        boolean coordinate = attr.key().equals("lat") || attr.key().equals("lon");
        if (!coordinate && n.signum() < 0) {
          throw new IllegalArgumentException(attr.label() + ": no puede ser negativo.");
        }
        if (coordinate && n.abs().compareTo(BigDecimal.valueOf(attr.key().equals("lat") ? 90 : 180)) > 0) {
          throw new IllegalArgumentException(attr.label() + ": coordenada fuera de rango.");
        }
        if (attr.kind() == Kind.INTEGER) {
          if (n.stripTrailingZeros().scale() > 0) {
            throw new IllegalArgumentException(attr.label() + ": debe ser un número entero.");
          }
          yield attr.apiInteger() ? (Object) n.intValueExact() : n.toBigInteger().toString();
        }
        yield n.stripTrailingZeros().toPlainString();
      }
    };
  }

  private static Boolean bool(Object raw) {
    if (raw instanceof Boolean b) {
      return b;
    }
    if (raw == null) {
      return null;
    }
    String s = raw.toString().trim().toLowerCase();
    return switch (s) {
      case "true", "1", "si", "sí", "yes" -> Boolean.TRUE;
      case "false", "0", "no" -> Boolean.FALSE;
      default -> null;
    };
  }

  private static String requireUrl(String url, String label) {
    if (url.length() > 500 || !url.matches("(?i)https?://\\S+")) {
      throw new IllegalArgumentException(label + ": debe ser una liga que empiece con http:// o https://.");
    }
    return url;
  }

  private static Attribute text(String key, String label, Group group, int max) {
    return new Attribute(key, label, group, Kind.TEXT, List.of(), false, false, max);
  }

  private static Attribute number(String key, String label, Group group) {
    return new Attribute(key, label, group, Kind.NUMBER, List.of(), false, false, 0);
  }

  private static Attribute integer(String key, String label, Group group) {
    return new Attribute(key, label, group, Kind.INTEGER, List.of(), false, false, 0);
  }

  private static Attribute bool(String key, String label, Group group) {
    return new Attribute(key, label, group, Kind.BOOLEAN, List.of(), false, false, 0);
  }

  private static Attribute choice(String key, String label, Group group, Option... options) {
    return new Attribute(key, label, group, Kind.CHOICE, List.of(options), false, false, 0);
  }

  private static Option opt(String value, String label) {
    return new Option(value, label);
  }

  private static Amenity a(String key, String label) {
    return new Amenity(key, label);
  }

  /** Amenidades en el orden del catálogo de 21Online y después las desconocidas (orden alfabético). */
  public static List<String> amenityOrder(Map<String, Object> amenities) {
    List<String> out = new ArrayList<>();
    for (Amenity a : AMENITIES) {
      out.add(a.key());
    }
    amenities.keySet().stream().filter(k -> !AMENITY_KEYS.contains(k) && !VIRTUAL_TOUR.equals(k)).sorted().forEach(out::add);
    return out;
  }
}
