package com.c21genera.propertycatalog.infrastructure;

import com.c21genera.propertycatalog.domain.PropertyCategory;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PropertyCategoryRepository extends JpaRepository<PropertyCategory, UUID> {

  boolean existsByNameIgnoreCase(String name);
}
