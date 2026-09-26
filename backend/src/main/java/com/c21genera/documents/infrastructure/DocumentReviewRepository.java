package com.c21genera.documents.infrastructure;

import com.c21genera.documents.domain.DocumentReview;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentReviewRepository extends JpaRepository<DocumentReview, UUID> {

  List<DocumentReview> findByDocumentVersionIdInOrderByReviewedAtDesc(Collection<UUID> documentVersionIds);
}
