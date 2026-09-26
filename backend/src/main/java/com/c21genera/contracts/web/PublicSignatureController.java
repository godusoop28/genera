package com.c21genera.contracts.web;

import com.c21genera.contracts.application.ContractSignatureService;
import com.c21genera.contracts.application.ContractSignatureService.SignCommand;
import com.c21genera.contracts.application.ContractSignatureService.SigningView;
import com.c21genera.contracts.domain.ContractSignature;
import com.c21genera.contracts.web.ContractDtos.SignRequest;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Liga personal de firma de cada firmante del cliente (sin cuenta). El token
 * es la credencial: vence, sirve para una sola firma y deja de funcionar si
 * el contrato se reemplaza por una versión nueva.
 */
@RestController
@RequestMapping("/api/v1/public/signatures/{token}")
public class PublicSignatureController {

  private final ContractSignatureService signatureService;

  public PublicSignatureController(ContractSignatureService signatureService) {
    this.signatureService = signatureService;
  }

  @GetMapping
  public SigningView view(@PathVariable String token) {
    return signatureService.publicView(token);
  }

  public record SignedResponse(boolean signed, Instant signedAt, String documentSha256) {}

  @PostMapping
  public SignedResponse sign(@PathVariable String token, @RequestBody SignRequest request, HttpServletRequest http) {
    ContractSignature signature =
        signatureService.signByClient(
            token,
            new SignCommand(
                request.typedName(),
                request.signatureImageBase64(),
                request.documentSha256(),
                request.accepted(),
                ContractController.clientIp(http),
                http.getHeader("User-Agent")));
    return new SignedResponse(true, signature.getSignedAt(), signature.getDocumentSha256());
  }
}
