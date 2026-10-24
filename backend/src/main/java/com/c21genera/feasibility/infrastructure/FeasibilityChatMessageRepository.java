package com.c21genera.feasibility.infrastructure;

import com.c21genera.feasibility.domain.FeasibilityChatMessage;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FeasibilityChatMessageRepository extends JpaRepository<FeasibilityChatMessage, UUID> {

  List<FeasibilityChatMessage> findByReportIdOrderByCreatedAtAsc(UUID reportId);

  List<FeasibilityChatMessage> findByStatus(FeasibilityChatMessage.Status status);
}
