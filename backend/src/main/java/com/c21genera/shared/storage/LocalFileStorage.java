package com.c21genera.shared.storage;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Almacenamiento en disco SOLO para desarrollo y pruebas locales
 * (STORAGE_PROVIDER=filesystem), para poder correr el sistema completo sin
 * MinIO ni Cloudinary. Las "URLs de descarga" son rutas file:// locales:
 * nunca usar en producción.
 */
@Component
@ConditionalOnProperty(prefix = "app.storage", name = "provider", havingValue = "filesystem")
public class LocalFileStorage implements FileStorage {

  private final Path root;

  public LocalFileStorage(@Value("${app.storage.local-root:${java.io.tmpdir}/c21genera-storage}") String root) {
    this.root = Path.of(root).toAbsolutePath().normalize();
  }

  @Override
  public StoredObjectMetadata store(String storageKey, InputStream content, long contentLength, String contentType) {
    try {
      byte[] bytes = content.readAllBytes();
      Path target = resolve(storageKey);
      Files.createDirectories(target.getParent());
      Files.write(target, bytes);
      return new StoredObjectMetadata(storageKey, bytes.length, HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    } catch (java.security.NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  @Override
  public InputStream get(String storageKey) {
    try {
      return Files.newInputStream(resolve(storageKey));
    } catch (IOException e) {
      throw new IllegalArgumentException("No existe el objeto: " + storageKey, e);
    }
  }

  @Override
  public void delete(String storageKey) {
    try {
      Files.deleteIfExists(resolve(storageKey));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  @Override
  public boolean exists(String storageKey) {
    return Files.exists(resolve(storageKey));
  }

  @Override
  public URI generateTemporaryDownloadUrl(String storageKey, Duration ttl) {
    return resolve(storageKey).toUri();
  }

  private Path resolve(String storageKey) {
    Path target = root.resolve(storageKey).normalize();
    if (!target.startsWith(root)) {
      throw new IllegalArgumentException("Clave de almacenamiento inválida");
    }
    return target;
  }
}
