package com.c21genera.privacy.web;

import com.c21genera.privacy.domain.LegalTemplate;
import com.c21genera.privacy.domain.PrivacyConsent;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

public final class PrivacyDtos {

  private PrivacyDtos() {}

  public record NoticeResponse(UUID templateId, String type, int version, String sha256, Instant effectiveFrom) {

    public static NoticeResponse from(LegalTemplate t) {
      return new NoticeResponse(t.getId(), t.getType().name(), t.getVersion(), t.getSha256(), t.getEffectiveFrom());
    }
  }

  public record RecordConsentRequest(
      @NotNull Boolean mainConsent, @NotNull Boolean secondaryConsent, String signatureBase64Png) {}

  public record ConsentResponse(
      boolean mainPurposesAccepted, boolean secondaryPurposesAccepted, Instant acceptedAt, boolean hasSignature) {

    public static ConsentResponse from(PrivacyConsent c) {
      return new ConsentResponse(
          c.isMainPurposesAccepted(), c.isSecondaryPurposesAccepted(), c.getAcceptedAt(), c.getSignatureStorageKey() != null);
    }
  }
}
