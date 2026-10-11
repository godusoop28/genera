package com.c21genera.assistant.infrastructure;

import com.c21genera.assistant.domain.Conversation;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ConversationRepository extends JpaRepository<Conversation, UUID> {

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select c from Conversation c where c.waId = :waId")
  Optional<Conversation> lockByWaId(@Param("waId") String waId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select c from Conversation c where c.id = :id")
  Optional<Conversation> lockById(@Param("id") UUID id);

  /** Conversaciones con mensajes pendientes cuya ráfaga ya terminó. */
  @Query("select c.id from Conversation c where c.processAfter is not null and c.processAfter <= :now and (c.leaseUntil is null or c.leaseUntil < :now) order by c.processAfter")
  List<UUID> findDue(@Param("now") Instant now, Pageable page);

  /** Candidatas a canalizar por abandono: con interés, sin canalizar, la IA activa y sin mensajes recientes. */
  @Query(
      "select c.id from Conversation c where c.test = false and c.handoffState = :none and c.control = :ai"
          + " and c.lastInboundAt < :idleSince and c.lastInboundAt > :notBefore and c.processAfter is null and c.primaryIntent in :intents")
  List<UUID> findAbandonedRaw(
      @Param("none") Conversation.HandoffState none,
      @Param("ai") Conversation.Control ai,
      @Param("idleSince") Instant idleSince,
      @Param("notBefore") Instant notBefore,
      @Param("intents") List<String> intents,
      Pageable page);

  default List<UUID> findAbandoned(Instant idleSince, Instant notBefore, List<String> intents, Pageable page) {
    return findAbandonedRaw(Conversation.HandoffState.NONE, Conversation.Control.AI, idleSince, notBefore, intents, page);
  }

  List<Conversation> findAllByOrderByLastMessageAtDesc(Pageable page);
}
