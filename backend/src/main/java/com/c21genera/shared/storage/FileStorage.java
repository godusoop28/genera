package com.c21genera.shared.storage;

import java.io.InputStream;
import java.net.URI;
import java.time.Duration;

/**
 * Puerto de almacenamiento de archivos (S3-compatible). El dominio nunca
 * conoce si detrás hay MinIO, AWS S3 o Cloudflare R2 (ver AGENTS §28-30).
 * El bucket es siempre privado: nunca se exponen URLs permanentes, solo
 * URLs firmadas y temporales generadas bajo demanda tras validar permisos.
 */
public interface FileStorage {

  /** Sube el contenido bajo la storageKey dada, generada por el backend (nunca por el nombre del cliente). */
  StoredObjectMetadata store(String storageKey, InputStream content, long contentLength, String contentType);

  InputStream get(String storageKey);

  void delete(String storageKey);

  boolean exists(String storageKey);

  /** URL firmada de descarga temporal, válida por {@code ttl}. */
  URI generateTemporaryDownloadUrl(String storageKey, Duration ttl);

  record StoredObjectMetadata(String storageKey, long size, String sha256) {}
}
