package com.c21genera.shared.storage;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Adaptador Cloudinary (alternativa a S3 cuando app.storage.provider=cloudinary): todo se
 * sube como resource_type=raw, type=private, así Cloudinary nunca lo sirve por una URL
 * pública fija. Las descargas usan Cloudinary.privateDownload(...) con expires_at, que sí
 * soporta expiración real en el plan gratuito (a diferencia de la autenticación por token,
 * que requiere el plan Advanced).
 *
 * <p>El plan gratuito no acepta objetos de más de 10 MB: los archivos más grandes (una
 * escritura escaneada de 40 MB) se guardan en partes con {@link ChunkedObjects}, sin que el
 * resto del sistema lo note; su liga de descarga la sirve el backend.
 */
@Component
@ConditionalOnProperty(prefix = "app.storage", name = "provider", havingValue = "cloudinary")
public class CloudinaryFileStorage implements FileStorage {

  private static final String RESOURCE_TYPE = "raw";
  private static final String DELIVERY_TYPE = "private";

  private final Cloudinary cloudinary;
  private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
  private final ChunkedObjects objects;

  public CloudinaryFileStorage(
      Cloudinary cloudinary,
      SignedFileLinks signedLinks,
      @Value("${app.cloudinary.max-object-bytes:9437184}") int maxObjectBytes) {
    this.cloudinary = cloudinary;
    this.objects = new ChunkedObjects(new CloudinaryRawStore(), maxObjectBytes, signedLinks::linkFor);
  }

  @Override
  public StoredObjectMetadata store(String storageKey, InputStream content, long contentLength, String contentType) {
    try {
      return objects.store(storageKey, content);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  @Override
  public InputStream get(String storageKey) {
    return objects.get(storageKey);
  }

  @Override
  public void delete(String storageKey) {
    objects.delete(storageKey);
  }

  @Override
  public boolean exists(String storageKey) {
    return objects.exists(storageKey);
  }

  @Override
  public URI generateTemporaryDownloadUrl(String storageKey, Duration ttl) {
    return objects.temporaryUrl(storageKey, ttl);
  }

  private final class CloudinaryRawStore implements ChunkedObjects.RawStore {

    @Override
    public void put(String id, byte[] content) {
      try {
        cloudinary
            .uploader()
            .upload(
                content,
                ObjectUtils.asMap(
                    "public_id", id,
                    "resource_type", RESOURCE_TYPE,
                    "type", DELIVERY_TYPE,
                    "overwrite", true,
                    "unique_filename", false));
      } catch (IOException e) {
        throw new UncheckedIOException("No se pudo subir el objeto a Cloudinary: " + id, e);
      }
    }

    @Override
    public byte[] get(String id) {
      URI downloadUrl = temporaryUrl(id, Duration.ofMinutes(5));
      try {
        HttpResponse<byte[]> response =
            httpClient.send(HttpRequest.newBuilder(downloadUrl).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() == 404) {
          return null;
        }
        if (response.statusCode() != 200) {
          throw new IllegalStateException("Cloudinary respondió " + response.statusCode() + " al descargar " + id);
        }
        return response.body();
      } catch (IOException e) {
        throw new UncheckedIOException("No se pudo descargar el objeto de Cloudinary: " + id, e);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException("Descarga interrumpida: " + id, e);
      }
    }

    @Override
    public void delete(String id) {
      try {
        cloudinary.uploader().destroy(id, ObjectUtils.asMap("resource_type", RESOURCE_TYPE, "type", DELIVERY_TYPE));
      } catch (IOException e) {
        throw new UncheckedIOException("No se pudo eliminar el objeto de Cloudinary: " + id, e);
      }
    }

    @Override
    public long sizeOf(String id) {
      try {
        Map<?, ?> resource =
            cloudinary.api().resource(id, ObjectUtils.asMap("resource_type", RESOURCE_TYPE, "type", DELIVERY_TYPE));
        Object bytes = resource.get("bytes");
        return bytes instanceof Number n ? n.longValue() : 0;
      } catch (Exception e) {
        return -1;
      }
    }

    @Override
    public URI temporaryUrl(String id, Duration ttl) {
      Map<String, Object> options =
          ObjectUtils.asMap(
              "resource_type", RESOURCE_TYPE,
              "type", DELIVERY_TYPE,
              "attachment", true,
              "expires_at", Instant.now().plus(ttl).getEpochSecond());
      try {
        return URI.create(cloudinary.privateDownload(id, null, options));
      } catch (Exception e) {
        throw new IllegalStateException("No se pudo generar la URL de descarga para: " + id, e);
      }
    }
  }
}
