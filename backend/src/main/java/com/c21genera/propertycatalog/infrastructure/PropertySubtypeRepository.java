package com.c21genera.propertycatalog.infrastructure;

import com.c21genera.propertycatalog.domain.PropertySubtype;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PropertySubtypeRepository extends JpaRepository<PropertySubtype, UUID> {

  boolean existsByCategoryIdAndNameIgnoreCase(UUID categoryId, String name);
}
