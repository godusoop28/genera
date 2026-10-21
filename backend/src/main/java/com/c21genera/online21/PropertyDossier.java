package com.c21genera.online21;

import com.c21genera.online21.PropertyListings.Listing;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * API pública del módulo online21: todo lo que 21Online muestra en el detalle de una propiedad
 * (como plus.21onlinemx.com/propiedades/ver/{id}) para la pantalla interna de Genera.
 *
 * <ul>
 *   <li>{@link #details}: datos generales completos, fotos, planos y asesor.
 *   <li>{@link #portals}: en qué portales está publicada (Mercado Libre, Inmuebles24…) y su liga.
 *   <li>{@link #relatedClients}: prospectos interesados en la propiedad (con su medio de origen).
 *   <li>{@link #followUps}: historial de seguimientos (visitas, llamadas…).
 *   <li>{@link #sheet}: ficha técnica en PDF (21Online no la expone por API: se arma aquí).
 * </ul>
 *
 * <p>Nunca incluye datos del dueño ni de cierre.
 */
public interface PropertyDossier {

  Optional<Details> details(long id);

  List<PortalListing> portals(long id);

  List<RelatedClient> relatedClients(long id);

  List<FollowUp> followUps(long id);

  /** PDF de la ficha técnica; vacío si la propiedad no existe. */
  Optional<byte[]> sheet(long id, SheetVariant variant);

  /** Variantes de la ficha, como en 21Online. */
  enum SheetVariant {
    /** Con los datos de contacto del asesor. */
    ADVISOR,
    /** Sin datos de contacto (para compartir con otras inmobiliarias). */
    NO_CONTACT,
    /** Sin el logotipo de la oficina ni datos de contacto. */
    NO_LOGO
  }

  record Advisor(Long online21Id, String name, String email, String mobile, String officePhone, String photoUrl) {}

  /** Un dato de la ficha con su etiqueta en español, agrupado por sección ("Generales", "Características"…). */
  record Field(String section, String label, String value) {}

  record Details(
      Listing listing,
      List<Field> fields,
      List<String> photos,
      List<String> floorPlans,
      String videoUrl,
      String headlineEnglish,
      String descriptionEnglish,
      Double latitude,
      Double longitude,
      String publicUrl,
      Advisor advisor) {}

  /** url: liga a la publicación en el portal, si 21Online la trae. */
  record PortalListing(String portal, boolean active, String status, String url, String reference, boolean withError, String error, Instant updatedAt) {}

  /** source: medio por el que llegó (portal, redes, letrero…). */
  record RelatedClient(Long id, String name, String phone, String email, String source, String status, String type, String message, Instant createdAt) {}

  record FollowUp(Long id, String type, String subtype, String advisor, String client, Instant createdAt) {}
}
