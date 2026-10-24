package com.c21genera.feasibility.application;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * Texto original de los dos prompts maestros que entregó el cliente (resources/feasibility; versión 0
 * en {@link FeasibilityPromptService}, que guarda las ediciones hechas desde Configuración) más las
 * reglas de operación de este sistema, que no se editan: aquí la IA no puede hacer preguntas ni
 * producir archivos; entrega texto que el servidor convierte en Word y PowerPoint.
 */
final class FeasibilityPrompts {

  private FeasibilityPrompts() {}

  static final String ANALYST = load("/feasibility/analista-factibilidad.md");
  static final String PRESENTATION = load("/feasibility/presentacion-factibilidad.md");

  static final String ANALYST_OPERATION =
      """

      ## REGLAS DE OPERACIÓN DENTRO DEL SISTEMA CENTURY 21 GENERA

      - Este reporte se genera automáticamente desde el expediente: no puedes hacer preguntas ni esperar \
      respuestas. Si faltan datos decisivos, elabora el diagnóstico con supuestos explícitos y registra lo \
      que falta en la sección 26 (DATOS CRÍTICOS FALTANTES).
      - La información del inmueble viene de los documentos del expediente (adjuntos y datos ya leídos de \
      ellos), de lo que capturó el asesor y de las fotografías. Trátala como fuente principal del activo; si \
      hay contradicciones entre documentos, señálalas.
      - Usa la búsqueda web para investigar el mercado actual (por ejemplo Inmuebles24, Lamudi, Vivanuncios, \
      Propiedades.com, Mercado Libre Inmuebles, century21mexico.com y sitios de desarrolladores). Cada \
      comparable debe llevar la liga exacta de la publicación consultada y la fecha de consulta.
      - Entrega el reporte completo en Markdown: cada una de las 27 secciones con un encabezado de la forma \
      "## N. TÍTULO" en el orden obligatorio; subtítulos con "###"; tablas Markdown con "|"; montos en pesos \
      mexicanos con formato $1,234,567.
      - No incluyas datos personales del propietario (nombres completos, CURP, RFC, identificaciones, \
      teléfonos o correos) aunque aparezcan en los documentos: no son necesarios para el análisis.
      - No uses emojis. No agregues texto antes de la sección 1 ni después de la sección 27.
      """;

  static final String PRESENTATION_OPERATION =
      """

      ## REGLAS DE OPERACIÓN DENTRO DEL SISTEMA CENTURY 21 GENERA

      - No generas el archivo: entregas el CONTENIDO de las láminas en el JSON solicitado y el sistema arma \
      el PowerPoint editable (con logos, paleta autorizada, fotografías reales del inmueble, la gráfica de \
      posicionamiento y el mapa estático con marcadores). Las láminas 1, 11 y la estructura visual las arma \
      el sistema; tú das los textos y datos.
      - La factibilidad incluida es la fuente principal. Úsala y complétala con la búsqueda web hasta reunir \
      15 comparables verificables (5 por arriba, 5 directamente comparables y 5 por debajo de nuestra \
      propiedad). Marca como hallazgo nuevo (campo notes) lo que no venga en la factibilidad. Si no hay \
      suficientes comprobables, entrega solo los verificados: nunca inventes un comparable.
      - comparables[].url: la liga exacta de la publicación; urlVerified=true solo si la abriste y la \
      publicación corresponde al inmueble descrito. source: el portal y la fecha de consulta.
      - latitude/longitude: si la publicación muestra el punto en un mapa o una dirección exacta, esas \
      coordenadas con locationPrecision EXACTA. Si solo indica colonia o zona, las coordenadas aproximadas \
      del centro de esa colonia con locationPrecision APROXIMADA (el sistema las verifica con un servicio \
      de mapas). SIN_UBICACION solo si no se sabe ni la colonia. Lo mismo para property.
      - zone: SIEMPRE colonia y municipio, sin abreviaturas ni código postal (por ejemplo "Vista Hermosa, \
      Cuernavaca"); address: calle y número si el anuncio los muestra, si no null. El sistema usa zone y \
      address para ubicar cada comparable en el mapa.
      - Montos en pesos mexicanos como enteros (sin signos ni comas). null cuando no esté confirmado.
      - pricing.ownerPrice: precio solicitado o esperado por el propietario; recommendedPrice: precio \
      recomendado de salida; classification: ALINEADO, LIMITE_SUPERIOR o FUERA_DE_RANGO según la lectura de \
      precio de la factibilidad (SIN_DATOS si no hay evidencia).
      - Textos breves para que quepan en una lámina: executiveSummary hasta 900 caracteres; cada título de \
      fortaleza u oportunidad hasta 60 caracteres y su explicación hasta 160; marketSupply.concentration y \
      ourPosition hasta 350; ventajas y desventajas de cada grupo hasta 320; pricing.strategy hasta 300 y \
      hasta 3 justificaciones de 130; mainMessage hasta 180; attributes y promotion hasta 6 elementos de 90; \
      findings y nextSteps hasta 5 de 110; invitation hasta 220; mapConclusion hasta 220.
      - Descripciones: shortDescription máximo 300 caracteres; mediumDescription de 80 a 120 palabras; \
      fullDescription para portales. Solo información confirmada, conforme a la NOM-247-SE-2021 (precio \
      total en moneda nacional, superficies, características reales y sin afirmaciones engañosas).
      - pendingItems: datos pendientes por confirmar o no verificables (ligas, superficies, ubicación, etc.).
      - Español claro para el propietario, sin tecnicismos, sin emojis y sin garantizar plazos ni resultados.
      """;

  /** Venta: el análisis tal cual. Renta: el mismo método y la misma estructura, aplicados al arrendamiento. */
  static String operationRules(boolean rent) {
    if (!rent) {
      return "\n- OPERACIÓN: VENTA. Todos los precios son precios de venta totales en pesos mexicanos.\n";
    }
    return """

        ## OPERACIÓN: RENTA

        - Este análisis es para RENTAR el inmueble, no para venderlo. Conserva exactamente la misma \
        estructura y el mismo rigor, pero aplica todo a la renta: precio = renta mensual en pesos mexicanos; \
        comparables = inmuebles EN RENTA (no en venta); precio por m² = renta mensual por m²; absorción y DOM \
        = tiempo para rentar; comprador = arrendatario (capacidad de pago mensual, garantías, aval o póliza \
        jurídica, depósito y plazo del contrato); finanzas = renta neta, meses de depósito, mantenimiento, \
        meses sin rentar y rendimiento anual cuando haya datos para estimarlo.
        - Donde los prompts dicen "vender", "venta", "comprador" o "cierre", entiéndase "rentar", "renta", \
        "arrendatario" y "firma del contrato de arrendamiento".
        - En la presentación (JSON): pricing.*, los precios de los comparables y los de la oferta en la zona son \
        rentas mensuales; la exclusiva y los siguientes pasos se refieren a la comercialización en renta.
        """;
  }

  private static String load(String resource) {
    try (InputStream in = FeasibilityPrompts.class.getResourceAsStream(resource)) {
      if (in == null) {
        throw new IllegalStateException("Falta el recurso " + resource);
      }
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
