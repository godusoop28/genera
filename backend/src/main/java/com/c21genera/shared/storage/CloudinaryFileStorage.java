package com.c21genera.shared.storage;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Adaptador Cloudinary (alternativa a S3 cuando app.storage.provider=cloudinary): todo se
 * sube como resource_type=raw, type=private, así Cloudinary nunca lo sirve por una URL
 * pública fija. Las descargas usan Cloudinary.privateDownload(...) con expires_at, que sí
 * soporta expiración real en el plan gratuito (a diferencia de la autenticación por token,
 * que requiere el plan Advanced).
 */
@Component
@ConditionalOnProperty(prefix = "app.storage", name = "provider", havingValue = "cloudinary")
public class CloudinaryFileStorage implements FileStorage {

  private static final String RESOURCE_TYPE = "raw";
  private static final String DELIVERY_TYPE = "private";

  private final Cloudinary cloudinary;
  private final HttpClient httpClient = HttpClient.newHttpClient();

  public CloudinaryFileStorage(Cloudinary cloudinary) {
    this.cloudinary = cloudinary;
  }

  @Override
  public StoredObjectMetadata store(String storageKey, InputStream content, long contentLength, String contentType) {
    byte[] bytes = readAllBytes(content);
    String sha256 = sha256Hex(bytes);
    try {
      cloudinary
          .uploader()
          .upload(
              bytes,
              ObjectUtils.asMap(
                  "public_id", storageKey,
                  "resource_type", RESOURCE_TYPE,
                  "type", DELIVERY_TYPE,
                  "overwrite", true,
                  "unique_filename", false));
    } catch (IOException e) {
      throw new UncheckedIOException("No se pudo subir el objeto a Cloudinary: " + storageKey, e);
    }
    return new StoredObjectMetadata(storageKey, bytes.length, sha256);
  }

  @Override
  public InputStream get(String storageKey) {
    URI downloadUrl = generateTemporaryDownloadUrl(storageKey, Duration.ofMinutes(5));
    try {
      HttpResponse<byte[]> response =
          httpClient.send(HttpRequest.newBuilder(downloadUrl).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
      if (response.statusCode() != 200) {
        throw new IllegalArgumentException("No existe el objeto: " + storageKey);
      }
      return new ByteArrayInputStream(response.body());
    } catch (IOException | InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new UncheckedIOException(
          "No se pudo descargar el objeto de Cloudinary: " + storageKey, e instanceof IOException io ? io : new IOException(e));
    }
  }

  @Override
  public void delete(String storageKey) {
    try {
      cloudinary
          .uploader()
          .destroy(storageKey, ObjectUtils.asMap("resource_type", RESOURCE_TYPE, "type", DELIVERY_TYPE));
    } catch (IOException e) {
      throw new UncheckedIOException("No se pudo eliminar el objeto de Cloudinary: " + storageKey, e);
    }
  }

  @Override
  public boolean exists(String storageKey) {
    try {
      cloudinary.api().resource(storageKey, ObjectUtils.asMap("resource_type", RESOURCE_TYPE, "type", DELIVERY_TYPE));
      return true;
    } catch (Exception e) {
      return false;
    }
  }

  @Override
  public URI generateTemporaryDownloadUrl(String storageKey, Duration ttl) {
    Map<String, Object> options =
        ObjectUtils.asMap(
            "resource_type", RESOURCE_TYPE,
            "type", DELIVERY_TYPE,
            "attachment", true,
            "expires_at", Instant.now().plus(ttl).getEpochSecond());
    try {
      String url = cloudinary.privateDownload(storageKey, null, options);
      return URI.create(url);
    } catch (Exception e) {
      throw new IllegalStateException("No se pudo generar la URL de descarga para: " + storageKey, e);
    }
  }

  private static byte[] readAllBytes(InputStream in) {
    try {
      return in.readAllBytes();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static String sha256Hex(byte[] bytes) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(bytes));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
