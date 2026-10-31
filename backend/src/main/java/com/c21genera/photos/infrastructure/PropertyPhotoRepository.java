package com.c21genera.photos.infrastructure;

import com.c21genera.photos.domain.PropertyPhoto;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PropertyPhotoRepository extends JpaRepository<PropertyPhoto, UUID> {

  List<PropertyPhoto> findByExpedienteIdAndRemovedAtIsNullOrderByUploadedAtAsc(UUID expedienteId);

  long countByExpedienteIdAndRemovedAtIsNull(UUID expedienteId);
}
