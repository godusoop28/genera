package com.c21genera.documents.infrastructure;

import com.c21genera.documents.domain.DocumentPage;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentPageRepository extends JpaRepository<DocumentPage, UUID> {

  List<DocumentPage> findByDocumentVersionIdOrderByPageNumberAsc(UUID documentVersionId);
}
