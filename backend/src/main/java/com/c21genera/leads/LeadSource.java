package com.c21genera.leads;

/**
 * Canal general del que llegó el lead. Control de Cambios #29: la fuente
 * precisa ("Inmuebles24", "Lona", ...) vive en el catálogo administrable
 * (lead_source_option) y cada opción pertenece a uno de estos canales.
 */
public enum LeadSource {
  WHATSAPP_BOT,
  WHATSAPP,
  PORTAL,
  SOCIAL,
  CAMPAIGN,
  WEBSITE,
  OUTDOOR,
  REFERRAL,
  WALK_IN,
  EVENT,
  PHONE,
  REACTIVATION,
  OTHER;

  /** Opción del catálogo que se usa cuando quien registra solo conoce el canal (p. ej. el bot). */
  public String defaultCode() {
    return switch (this) {
      case WHATSAPP_BOT -> "WHATSAPP_BOT";
      case WHATSAPP -> "WHATSAPP";
      case PORTAL -> "LEGACY_PORTAL";
      case SOCIAL -> "FACEBOOK_AD";
      case CAMPAIGN -> "LEGACY_CAMPAIGN";
      case WEBSITE -> "WEBSITE";
      case OUTDOOR -> "LONA";
      case REFERRAL -> "LEGACY_REFERRAL";
      case WALK_IN -> "WALK_IN";
      case EVENT -> "EXPO";
      case PHONE -> "PHONE";
      case REACTIVATION -> "INTERNAL_DATABASE";
      case OTHER -> "UNKNOWN";
    };
  }
}
