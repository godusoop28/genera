package com.c21genera.closing.infrastructure;

import com.c21genera.closing.domain.ClosingTask;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClosingTaskRepository extends JpaRepository<ClosingTask, UUID> {

  List<ClosingTask> findByClosingCaseIdOrderByCreatedAtAsc(UUID closingCaseId);

  Optional<ClosingTask> findByClosingCaseIdAndCode(UUID closingCaseId, String code);
}
