package com.c21genera.shared.storage;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;

/**
 * Guarda objetos más grandes que el máximo por objeto del proveedor (el plan
 * gratuito de Cloudinary acepta 10 MB) partiéndolos en trozos, de forma
 * transparente para el resto del sistema: quien llama usa una sola
 * storageKey. Bajo esa key queda un manifiesto pequeño y los trozos van en
 * {@code <key>.part-<n>}. Los objetos que caben en uno solo se guardan tal cual.
 *
 * <p>Como un objeto en partes no se puede descargar con una sola URL del
 * proveedor, su liga temporal la sirve el propio backend (ver {@link SignedFileLinks}).
 */
public final class ChunkedObjects {

  /** Operaciones mínimas sobre el proveedor real (un objeto = un archivo). */
  public interface RawStore {
    void put(String id, byte[] content);

    /** null si no existe. */
    byte[] get(String id);

    void delete(String id);

    /** Tamaño en bytes, o -1 si no existe. */
    long sizeOf(String id);

    /** URL firmada del proveedor para un objeto guardado entero. */
    URI temporaryUrl(String id, Duration ttl);
  }

  private static final String MAGIC = "C21-CHUNKED-V1";

  private final RawStore raw;
  private final int maxObjectBytes;
  private final BiFunction<String, Duration, URI> backendLink;
  private final Map<String, Boolean> chunkedCache = new ConcurrentHashMap<>();

  public ChunkedObjects(RawStore raw, int maxObjectBytes, BiFunction<String, Duration, URI> backendLink) {
    this.raw = raw;
    this.maxObjectBytes = maxObjectBytes;
    this.backendLink = backendLink;
  }

  public FileStorage.StoredObjectMetadata store(String key, byte[] content) {
    String sha256 = sha256Hex(content);
    if (content.length <= maxObjectBytes) {
      raw.put(key, content);
      chunkedCache.put(key, false);
      return new FileStorage.StoredObjectMetadata(key, content.length, sha256);
    }
    int parts = (content.length + maxObjectBytes - 1) / maxObjectBytes;
    for (int i = 0; i < parts; i++) {
      int from = i * maxObjectBytes;
      int to = Math.min(content.length, from + maxObjectBytes);
      byte[] part = new byte[to - from];
      System.arraycopy(content, from, part, 0, part.length);
      raw.put(partId(key, i), part);
    }
    String manifest = MAGIC + "\n" + content.length + "\n" + parts + "\n" + sha256 + "\n";
    raw.put(key, manifest.getBytes(StandardCharsets.UTF_8));
    chunkedCache.put(key, true);
    return new FileStorage.StoredObjectMetadata(key, content.length, sha256);
  }

  public InputStream get(String key) {
    byte[] head = raw.get(key);
    if (head == null) {
      throw new IllegalArgumentException("No existe el objeto: " + key);
    }
    Manifest manifest = Manifest.parse(head);
    if (manifest == null) {
      return new ByteArrayInputStream(head);
    }
    // Las partes se descargan una por una conforme se leen: nunca todas a la vez en memoria.
    List<InputStream> streams = new ArrayList<>();
    for (int i = 0; i < manifest.parts(); i++) {
      streams.add(new LazyPartStream(raw, partId(key, i)));
    }
    return new SequenceInputStream(Collections.enumeration(streams));
  }

  public void delete(String key) {
    byte[] head = raw.get(key);
    Manifest manifest = head == null ? null : Manifest.parse(head);
    if (manifest != null) {
      for (int i = 0; i < manifest.parts(); i++) {
        raw.delete(partId(key, i));
      }
    }
    raw.delete(key);
    chunkedCache.remove(key);
  }

  public boolean exists(String key) {
    return raw.sizeOf(key) >= 0;
  }

  public URI temporaryUrl(String key, Duration ttl) {
    return isChunked(key) ? backendLink.apply(key, ttl) : raw.temporaryUrl(key, ttl);
  }

  boolean isChunked(String key) {
    return chunkedCache.computeIfAbsent(
        key,
        k -> {
          long size = raw.sizeOf(k);
          // El manifiesto mide unas decenas de bytes; un archivo real de ese tamaño no existe aquí.
          if (size < 0 || size > 512) {
            return false;
          }
          byte[] head = raw.get(k);
          return head != null && Manifest.parse(head) != null;
        });
  }

  static String partId(String key, int index) {
    return key + ".part-" + index;
  }

  private record Manifest(long size, int parts) {
    static Manifest parse(byte[] head) {
      if (head.length > 512) {
        return null;
      }
      String text = new String(head, StandardCharsets.UTF_8);
      if (!text.startsWith(MAGIC + "\n")) {
        return null;
      }
      String[] lines = text.split("\n");
      try {
        return new Manifest(Long.parseLong(lines[1]), Integer.parseInt(lines[2]));
      } catch (RuntimeException e) {
        return null;
      }
    }
  }

  /** Descarga su parte la primera vez que se lee. */
  private static final class LazyPartStream extends InputStream {
    private final RawStore raw;
    private final String id;
    private ByteArrayInputStream delegate;

    LazyPartStream(RawStore raw, String id) {
      this.raw = raw;
      this.id = id;
    }

    private ByteArrayInputStream delegate() {
      if (delegate == null) {
        byte[] bytes = raw.get(id);
        if (bytes == null) {
          throw new IllegalStateException("Falta una parte del archivo: " + id);
        }
        delegate = new ByteArrayInputStream(bytes);
      }
      return delegate;
    }

    @Override
    public int read() {
      return delegate().read();
    }

    @Override
    public int read(byte[] b, int off, int len) {
      return delegate().read(b, off, len);
    }
  }

  private static String sha256Hex(byte[] bytes) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
