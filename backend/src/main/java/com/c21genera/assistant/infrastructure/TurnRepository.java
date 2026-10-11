package com.c21genera.assistant.infrastructure;

import com.c21genera.assistant.domain.ConversationTurn;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TurnRepository extends JpaRepository<ConversationTurn, UUID> {}
