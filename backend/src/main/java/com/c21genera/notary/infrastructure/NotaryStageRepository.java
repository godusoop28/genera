package com.c21genera.notary.infrastructure;

import com.c21genera.notary.domain.NotaryStage;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotaryStageRepository extends JpaRepository<NotaryStage, UUID> {}
