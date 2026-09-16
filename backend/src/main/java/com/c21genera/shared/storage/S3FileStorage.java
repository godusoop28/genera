package com.c21genera.shared.storage;

import com.c21genera.shared.config.StorageProperties;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

/**
 * Adaptador S3-compatible (MinIO en local/dev, AWS S3 o Cloudflare R2 en
 * producción, sin cambiar el dominio). El bucket se asume privado.
 */
@Component
public class S3FileStorage implements FileStorage {

  private final S3Client s3Client;
  private final S3Presigner presigner;
  private final StorageProperties properties;

  public S3FileStorage(S3Client s3Client, S3Presigner presigner, StorageProperties properties) {
    this.s3Client = s3Client;
    this.presigner = presigner;
    this.properties = properties;
  }

  @Override
  public StoredObjectMetadata store(String storageKey, InputStream content, long contentLength, String contentType) {
    byte[] bytes = readAllBytes(content);
    String sha256 = sha256Hex(bytes);

    s3Client.putObject(
        PutObjectRequest.builder()
            .bucket(properties.bucket())
            .key(storageKey)
            .contentType(contentType)
            .contentLength((long) bytes.length)
            .build(),
        RequestBody.fromBytes(bytes));

    return new StoredObjectMetadata(storageKey, bytes.length, sha256);
  }

  @Override
  public InputStream get(String storageKey) {
    try {
      return s3Client.getObject(GetObjectRequest.builder().bucket(properties.bucket()).key(storageKey).build());
    } catch (NoSuchKeyException e) {
      throw new IllegalArgumentException("No existe el objeto: " + storageKey, e);
    }
  }

  @Override
  public void delete(String storageKey) {
    s3Client.deleteObject(DeleteObjectRequest.builder().bucket(properties.bucket()).key(storageKey).build());
  }

  @Override
  public boolean exists(String storageKey) {
    try {
      s3Client.headObject(HeadObjectRequest.builder().bucket(properties.bucket()).key(storageKey).build());
      return true;
    } catch (NoSuchKeyException e) {
      return false;
    }
  }

  @Override
  public URI generateTemporaryDownloadUrl(String storageKey, Duration ttl) {
    GetObjectPresignRequest presignRequest =
        GetObjectPresignRequest.builder()
            .signatureDuration(ttl)
            .getObjectRequest(b -> b.bucket(properties.bucket()).key(storageKey))
            .build();
    return URI.create(presigner.presignGetObject(presignRequest).url().toString());
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
