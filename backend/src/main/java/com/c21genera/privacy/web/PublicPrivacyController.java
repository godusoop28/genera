package com.c21genera.privacy.web;

import com.c21genera.privacy.application.PrivacyConsentService;
import com.c21genera.privacy.application.PrivacyConsentService.RecordConsentCommand;
import com.c21genera.privacy.domain.PrivacyConsent;
import com.c21genera.privacy.web.PrivacyDtos.ConsentResponse;
import com.c21genera.privacy.web.PrivacyDtos.NoticeResponse;
import com.c21genera.privacy.web.PrivacyDtos.RecordConsentRequest;
import com.c21genera.publicaccess.PublicAccessTokenApi;
import com.c21genera.publicaccess.PublicLinkRevokedException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public/expedientes/{token}")
public class PublicPrivacyController {

  private final PrivacyConsentService privacyConsentService;
  private final PublicAccessTokenApi tokenApi;

  public PublicPrivacyController(PrivacyConsentService privacyConsentService, PublicAccessTokenApi tokenApi) {
    this.privacyConsentService = privacyConsentService;
    this.tokenApi = tokenApi;
  }

  @GetMapping("/privacy-notice")
  public NoticeResponse notice(@PathVariable String token) {
    resolve(token);
    return NoticeResponse.from(privacyConsentService.currentNotice());
  }

  @PostMapping("/privacy-consent")
  public ConsentResponse recordConsent(
      @PathVariable String token, @Valid @RequestBody RecordConsentRequest request, HttpServletRequest httpRequest) {
    UUID expedienteId = resolve(token);
    PrivacyConsent consent =
        privacyConsentService.recordConsent(
            new RecordConsentCommand(
                expedienteId,
                request.mainConsent(),
                request.secondaryConsent(),
                request.signatureBase64Png(),
                clientIp(httpRequest),
                httpRequest.getHeader("User-Agent")));
    return ConsentResponse.from(consent);
  }

  private UUID resolve(String token) {
    return tokenApi.resolve(token).orElseThrow(PublicLinkRevokedException::new);
  }

  private static String clientIp(HttpServletRequest request) {
    String forwardedFor = request.getHeader("X-Forwarded-For");
    if (forwardedFor != null && !forwardedFor.isBlank()) {
      return forwardedFor.split(",")[0].trim();
    }
    return request.getRemoteAddr();
  }
}
