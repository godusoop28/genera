package com.c21genera.documents.infrastructure;

import com.c21genera.documents.domain.DocumentReview;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentReviewRepository extends JpaRepository<DocumentReview, UUID> {}
