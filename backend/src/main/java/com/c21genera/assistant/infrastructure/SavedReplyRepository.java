package com.c21genera.assistant.infrastructure;

import com.c21genera.assistant.domain.SavedReply;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SavedReplyRepository extends JpaRepository<SavedReply, UUID> {

  List<SavedReply> findAllByOrderByTitleAsc();
}
