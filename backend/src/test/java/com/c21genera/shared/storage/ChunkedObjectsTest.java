package com.c21genera.shared.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * Un documento de más de 10 MB (el máximo por objeto del plan gratuito de
 * Cloudinary) se guarda en partes y se recupera idéntico, sin que el resto del
 * sistema use más de una storageKey.
 */
class ChunkedObjectsTest {

  private static final int TEN_MB = 10 * 1024 * 1024;

  /** Proveedor falso que, como Cloudinary gratis, rechaza objetos de más de 10 MB. */
  static final class CappedStore implements ChunkedObjects.RawStore {
    final Map<String, byte[]> objects = new HashMap<>();

    @Override
    public void put(String id, byte[] content) {
      if (content.length > TEN_MB) {
        throw new IllegalStateException("File size too large");
      }
      objects.put(id, content.clone());
    }

    @Override
    public byte[] get(String id) {
      return objects.get(id);
    }

    @Override
    public void delete(String id) {
      objects.remove(id);
    }

    @Override
    public long sizeOf(String id) {
      return objects.containsKey(id) ? objects.get(id).length : -1;
    }

    @Override
    public URI temporaryUrl(String id, Duration ttl) {
      return URI.create("https://proveedor.example/" + id);
    }
  }

  private final CappedStore raw = new CappedStore();
  private final ChunkedObjects objects =
      new ChunkedObjects(raw, 9 * 1024 * 1024, (key, ttl) -> URI.create("https://api.example/api/v1/public/files/" + key));

  @Test
  void aDocumentLargerThan10MbIsStoredInPartsAndReadBackIdentical() throws Exception {
    byte[] deed = new byte[25 * 1024 * 1024];
    new Random(7).nextBytes(deed);

    FileStorage.StoredObjectMetadata meta = objects.store("expedientes/1/escritura.pdf", deed);

    assertThat(meta.size()).isEqualTo(deed.length);
    assertThat(raw.objects).containsKeys("expedientes/1/escritura.pdf.part-0", "expedientes/1/escritura.pdf.part-2");
    assertThat(raw.objects.values()).allMatch(part -> part.length <= TEN_MB);
    try (var in = objects.get("expedientes/1/escritura.pdf")) {
      assertThat(in.readAllBytes()).isEqualTo(deed);
    }
  }

  @Test
  void aSplitFileIsDownloadedThroughTheBackendAndASmallOneDirectlyFromTheProvider() {
    objects.store("grande.pdf", new byte[12 * 1024 * 1024]);
    objects.store("foto.jpg", new byte[2000]);

    assertThat(objects.temporaryUrl("grande.pdf", Duration.ofMinutes(5)).toString()).startsWith("https://api.example/");
    assertThat(objects.temporaryUrl("foto.jpg", Duration.ofMinutes(5)).toString()).startsWith("https://proveedor.example/");
  }

  @Test
  void deletingASplitFileRemovesAllItsParts() {
    objects.store("grande.pdf", new byte[20 * 1024 * 1024]);

    objects.delete("grande.pdf");

    assertThat(raw.objects).isEmpty();
  }
}
