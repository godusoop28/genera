package com.c21genera.privacy.web;

import com.c21genera.privacy.application.PrivacyConsentService;
import com.c21genera.privacy.application.PrivacySignLinkService;
import com.c21genera.privacy.domain.LegalTemplate;
import com.c21genera.privacy.domain.PrivacyConsent;
import com.c21genera.privacy.domain.PrivacySignLink;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Control de Cambios #44: liga exclusiva para firmar el aviso de privacidad. Solo permite leer el
 * aviso vigente y firmarlo; no devuelve ningún dato del expediente ni da acceso a sus documentos.
 * Un token inválido, vencido, revocado o ya usado responde el mismo 404 genérico.
 */
@RestController
@RequestMapping("/api/v1/public/privacy-sign/{token}")
public class PublicPrivacySignController {

  private final PrivacySignLinkService signLinkService;
  private final PrivacyConsentService consentService;

  public PublicPrivacySignController(PrivacySignLinkService signLinkService, PrivacyConsentService consentService) {
    this.signLinkService = signLinkService;
    this.consentService = consentService;
  }

  public record SignLinkView(int noticeVersion, Instant noticeEffectiveFrom, String noticeSha256, Instant expiresAt) {}

  public record SignRequest(Boolean secondaryConsent, String signatureBase64Png) {}

  public record SignResponse(Instant signedAt, boolean secondaryPurposesAccepted) {}

  @GetMapping
  public SignLinkView view(@PathVariable String token) {
    PrivacySignLink link = signLinkService.open(token);
    LegalTemplate notice = consentService.currentNotice();
    return new SignLinkView(notice.getVersion(), notice.getEffectiveFrom(), notice.getSha256(), link.getExpiresAt());
  }

  @PostMapping
  public SignResponse sign(@PathVariable String token, @RequestBody SignRequest request, HttpServletRequest http) {
    PrivacyConsent consent =
        signLinkService.sign(
            token, Boolean.TRUE.equals(request.secondaryConsent()), request.signatureBase64Png(), clientIp(http), http.getHeader("User-Agent"));
    return new SignResponse(consent.getAcceptedAt(), consent.isSecondaryPurposesAccepted());
  }

  private static String clientIp(HttpServletRequest request) {
    String forwardedFor = request.getHeader("X-Forwarded-For");
    if (forwardedFor != null && !forwardedFor.isBlank()) {
      return forwardedFor.split(",")[0].trim();
    }
    return request.getRemoteAddr();
  }
}
