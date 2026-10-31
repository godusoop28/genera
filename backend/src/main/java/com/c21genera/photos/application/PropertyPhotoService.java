package com.c21genera.photos.application;

import com.c21genera.photos.domain.PropertyPhoto;
import com.c21genera.photos.domain.PropertyPhoto.UploadedBy;
import com.c21genera.photos.infrastructure.PropertyPhotoRepository;
import com.c21genera.shared.domain.NotFoundException;
import com.c21genera.shared.domain.UnprocessableException;
import com.c21genera.shared.storage.FileStorage;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.tika.Tika;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Control de Cambios #33. Formatos y límites (pendientes de confirmar con C21 Genera): JPG, PNG y
 * WEBP de hasta 10 MB cada una y hasta 40 fotos activas por inmueble. Los iPhone convierten HEIC a
 * JPG al subir desde el navegador cuando la página solo pide JPG/PNG/WEBP.
 */
@Service
@Transactional
public class PropertyPhotoService {

  public static final long MAX_BYTES = 10L * 1024 * 1024;
  public static final int MAX_PHOTOS = 40;
  public static final int MAX_PER_UPLOAD = 20;
  public static final List<String> ALLOWED = List.of("image/jpeg", "image/png", "image/webp");
  private static final Map<String, String> EXTENSIONS = Map.of("image/jpeg", "jpg", "image/png", "png", "image/webp", "webp");

  private final PropertyPhotoRepository repository;
  private final FileStorage storage;
  private final Clock clock;
  private final Tika tika = new Tika();

  public PropertyPhotoService(PropertyPhotoRepository repository, FileStorage storage, Clock clock) {
    this.repository = repository;
    this.storage = storage;
    this.clock = clock;
  }

  public record Limits(List<String> formats, long maxBytesPerPhoto, int maxPhotos, int maxPerUpload, int remaining) {}

  public record PhotoView(
      UUID id,
      String uploadedBy,
      String uploadedByName,
      Instant uploadedAt,
      String originalFilename,
      long sizeBytes,
      boolean selectedForPortal,
      boolean mainPhoto,
      Integer portalOrder,
      URI url) {}

  public record SelectionItem(UUID photoId, boolean main) {}

  @Transactional(readOnly = true)
  public Limits limits(UUID expedienteId) {
    int remaining = (int) Math.max(0, MAX_PHOTOS - repository.countByExpedienteIdAndRemovedAtIsNull(expedienteId));
    return new Limits(List.of("JPG", "PNG", "WEBP"), MAX_BYTES, MAX_PHOTOS, MAX_PER_UPLOAD, remaining);
  }

  /** Fotos activas: primero las seleccionadas para el portal en su orden, luego el resto por fecha. */
  @Transactional(readOnly = true)
  public List<PhotoView> list(UUID expedienteId) {
    return sorted(repository.findByExpedienteIdAndRemovedAtIsNullOrderByUploadedAtAsc(expedienteId)).stream().map(this::view).toList();
  }

  /** Lo que ve el cliente en su portal: solo las fotos que él subió, sin la selección del asesor. */
  @Transactional(readOnly = true)
  public List<PhotoView> listForClient(UUID expedienteId) {
    return repository.findByExpedienteIdAndRemovedAtIsNullOrderByUploadedAtAsc(expedienteId).stream()
        .filter(p -> p.getUploadedByKind() == UploadedBy.CLIENT)
        .map(p -> new PhotoView(p.getId(), p.getUploadedByKind().name(), null, p.getUploadedAt(), p.getOriginalFilename(), p.getSizeBytes(), false, false, null, url(p)))
        .toList();
  }

  public List<PhotoView> upload(UUID expedienteId, List<MultipartFile> files, UploadedBy by, UUID userId, String byName) {
    if (files == null || files.isEmpty()) {
      throw new UnprocessableException("PHOTOS_EMPTY", "Selecciona al menos una fotografía.");
    }
    if (files.size() > MAX_PER_UPLOAD) {
      throw new UnprocessableException("PHOTOS_TOO_MANY_PER_UPLOAD", "Puedes subir hasta " + MAX_PER_UPLOAD + " fotografías a la vez.");
    }
    long current = repository.countByExpedienteIdAndRemovedAtIsNull(expedienteId);
    if (current + files.size() > MAX_PHOTOS) {
      throw new UnprocessableException(
          "PHOTOS_LIMIT", "El inmueble admite hasta " + MAX_PHOTOS + " fotografías; quedan " + Math.max(0, MAX_PHOTOS - current) + " lugares.");
    }
    // Primero se validan todas: si una no sirve no se guarda ninguna y se puede corregir la selección.
    List<String> types = new ArrayList<>();
    for (MultipartFile file : files) {
      types.add(validate(file));
    }
    Instant now = clock.instant();
    List<PhotoView> created = new ArrayList<>();
    for (int i = 0; i < files.size(); i++) {
      MultipartFile file = files.get(i);
      String type = types.get(i);
      UUID id = UUID.randomUUID();
      String key = "expedientes/%s/fotos/%s.%s".formatted(expedienteId, id, EXTENSIONS.get(type));
      FileStorage.StoredObjectMetadata stored;
      try (InputStream in = file.getInputStream()) {
        stored = storage.store(key, in, file.getSize(), type);
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
      PropertyPhoto photo =
          new PropertyPhoto(id, expedienteId, key, type, file.getSize(), stored.sha256(), clean(file.getOriginalFilename()), by, userId, byName, now.plusMillis(i));
      repository.save(photo);
      created.add(view(photo));
    }
    return created;
  }

  /**
   * El asesor elige qué fotos se usan en el portal, en qué orden y cuál es la principal (si no
   * marca ninguna, la primera). Las que no vienen en la lista quedan fuera de la selección.
   */
  public List<PhotoView> saveSelection(UUID expedienteId, List<SelectionItem> items, String byName) {
    List<PropertyPhoto> photos = repository.findByExpedienteIdAndRemovedAtIsNullOrderByUploadedAtAsc(expedienteId);
    Map<UUID, PropertyPhoto> byId = photos.stream().collect(Collectors.toMap(PropertyPhoto::getId, Function.identity()));
    Set<UUID> seen = new HashSet<>();
    for (SelectionItem item : items) {
      if (!byId.containsKey(item.photoId())) {
        throw new NotFoundException("Fotografía", item.photoId());
      }
      if (!seen.add(item.photoId())) {
        throw new UnprocessableException("PHOTO_DUPLICATED", "Una fotografía aparece dos veces en la selección.");
      }
    }
    if (items.stream().filter(SelectionItem::main).count() > 1) {
      throw new UnprocessableException("PHOTO_MAIN_DUPLICATED", "Solo puede haber una foto principal.");
    }
    boolean anyMain = items.stream().anyMatch(SelectionItem::main);
    Instant now = clock.instant();
    for (PropertyPhoto p : photos) {
      if (!seen.contains(p.getId()) && (p.isSelectedForPortal() || p.isMainPhoto())) {
        p.select(false, false, null, byName, now);
      }
    }
    for (int i = 0; i < items.size(); i++) {
      SelectionItem item = items.get(i);
      byId.get(item.photoId()).select(true, anyMain ? item.main() : i == 0, i + 1, byName, now);
    }
    return list(expedienteId);
  }

  /** Solo staff: la foto deja de usarse (y sale de la selección); el archivo se conserva. */
  public void remove(UUID expedienteId, UUID photoId, String byName) {
    PropertyPhoto photo =
        repository
            .findById(photoId)
            .filter(p -> p.getExpedienteId().equals(expedienteId) && !p.isRemoved())
            .orElseThrow(() -> new NotFoundException("Fotografía", photoId));
    boolean wasMain = photo.isMainPhoto();
    photo.remove(byName, clock.instant());
    if (wasMain) {
      // La siguiente de la selección pasa a ser la principal.
      repository.findByExpedienteIdAndRemovedAtIsNullOrderByUploadedAtAsc(expedienteId).stream()
          .filter(p -> p.isSelectedForPortal() && !p.getId().equals(photoId))
          .min(Comparator.comparing(PropertyPhoto::getPortalOrder, Comparator.nullsLast(Comparator.naturalOrder())))
          .ifPresent(p -> p.select(true, true, p.getPortalOrder(), byName, clock.instant()));
    }
  }

  private String validate(MultipartFile file) {
    String name = clean(file.getOriginalFilename());
    if (file.getSize() > MAX_BYTES) {
      throw new UnprocessableException("PHOTO_TOO_LARGE", "\"" + name + "\" pesa más de " + (MAX_BYTES / 1024 / 1024) + " MB. Elige una foto más ligera.");
    }
    String detected;
    try (InputStream in = file.getInputStream()) {
      detected = tika.detect(in.readNBytes(64 * 1024));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    if (!ALLOWED.contains(detected)) {
      String hint =
          detected.contains("heic") || detected.contains("heif")
              ? " Las fotos HEIC de iPhone se convierten solas a JPG al elegirlas desde el navegador; si no, cámbialas a JPG."
              : "";
      throw new UnprocessableException("PHOTO_UNSUPPORTED", "\"" + name + "\" no es una foto JPG, PNG o WEBP." + hint);
    }
    return detected;
  }

  private static List<PropertyPhoto> sorted(List<PropertyPhoto> photos) {
    return photos.stream()
        .sorted(
            Comparator.comparing((PropertyPhoto p) -> !p.isSelectedForPortal())
                .thenComparing(PropertyPhoto::getPortalOrder, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(PropertyPhoto::getUploadedAt))
        .toList();
  }

  private PhotoView view(PropertyPhoto p) {
    return new PhotoView(
        p.getId(),
        p.getUploadedByKind().name(),
        p.getUploadedByName(),
        p.getUploadedAt(),
        p.getOriginalFilename(),
        p.getSizeBytes(),
        p.isSelectedForPortal(),
        p.isMainPhoto(),
        p.getPortalOrder(),
        url(p));
  }

  private URI url(PropertyPhoto p) {
    return storage.generateTemporaryDownloadUrl(p.getStorageKey(), Duration.ofMinutes(30));
  }

  private static String clean(String name) {
    if (name == null || name.isBlank()) {
      return "foto";
    }
    String base = name.replace('\\', '/');
    base = base.substring(base.lastIndexOf('/') + 1);
    return base.length() > 200 ? base.substring(0, 200) : base;
  }
}
