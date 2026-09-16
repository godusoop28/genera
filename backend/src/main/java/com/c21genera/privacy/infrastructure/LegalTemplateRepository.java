package com.c21genera.privacy.infrastructure;

import com.c21genera.privacy.domain.LegalTemplate;
import com.c21genera.privacy.domain.LegalTemplateType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LegalTemplateRepository extends JpaRepository<LegalTemplate, UUID> {

  Optional<LegalTemplate> findByTypeAndActiveTrue(LegalTemplateType type);

  Optional<LegalTemplate> findTopByTypeOrderByVersionDesc(LegalTemplateType type);
}
