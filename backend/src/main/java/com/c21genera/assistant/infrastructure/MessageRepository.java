package com.c21genera.assistant.infrastructure;

import com.c21genera.assistant.domain.ConversationMessage;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MessageRepository extends JpaRepository<ConversationMessage, UUID> {

  boolean existsByExternalId(String externalId);

  List<ConversationMessage> findByConversationIdOrderBySeqAsc(UUID conversationId);

  /** Los últimos N mensajes (en orden descendente; quien llama los invierte). */
  @Query("select m from ConversationMessage m where m.conversationId = :id order by m.seq desc")
  List<ConversationMessage> findRecent(@Param("id") UUID conversationId, Pageable page);

  List<ConversationMessage> findByStatusOrderByCreatedAtAsc(ConversationMessage.Status status, Pageable page);

  default List<ConversationMessage> findQueued(Pageable page) {
    return findByStatusOrderByCreatedAtAsc(ConversationMessage.Status.QUEUED, page);
  }
}
