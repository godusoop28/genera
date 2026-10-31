package com.c21genera.operations.infrastructure;

import com.c21genera.operations.domain.RequirementTemplate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RequirementTemplateRepository extends JpaRepository<RequirementTemplate, UUID> {

  List<RequirementTemplate> findAllByOrderBySortOrderAsc();
}
