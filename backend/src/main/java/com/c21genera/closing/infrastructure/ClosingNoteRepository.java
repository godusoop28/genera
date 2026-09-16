package com.c21genera.closing.infrastructure;

import com.c21genera.closing.domain.ClosingNote;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClosingNoteRepository extends JpaRepository<ClosingNote, UUID> {

  List<ClosingNote> findByClosingCaseIdOrderByCreatedAtDesc(UUID closingCaseId);
}
