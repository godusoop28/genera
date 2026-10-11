package com.c21genera.assistant.infrastructure;

import com.c21genera.assistant.domain.Escalation;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EscalationRepository extends JpaRepository<Escalation, UUID> {

  List<Escalation> findByConversationIdOrderByCreatedAtDesc(UUID conversationId);

  List<Escalation> findByStatus(String status);
}
