package com.c21genera.drafts.infrastructure;

import com.c21genera.drafts.domain.FormDraft;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FormDraftRepository extends JpaRepository<FormDraft, UUID> {

  Optional<FormDraft> findByOwnerKeyAndFormKey(String ownerKey, String formKey);

  void deleteByOwnerKeyAndFormKey(String ownerKey, String formKey);
}
