package com.c21genera.contracts.web;

import com.c21genera.contracts.application.ContractService;
import com.c21genera.contracts.application.ContractSignatureService;
import com.c21genera.contracts.application.ContractSignatureService.SignCommand;
import com.c21genera.contracts.domain.ContractGeneration;
import com.c21genera.contracts.domain.ContractSignature;
import com.c21genera.contracts.web.ContractDtos.CalculationsResponse;
import com.c21genera.contracts.web.ContractDtos.ContractGenerationResponse;
import com.c21genera.contracts.web.ContractDtos.DeliverRequest;
import com.c21genera.contracts.web.ContractDtos.GenerateResponse;
import com.c21genera.contracts.web.ContractDtos.ReadinessResponse;
import com.c21genera.contracts.web.ContractDtos.SignRequest;
import com.c21genera.contracts.web.ContractDtos.SigningLinkResponse;
import com.c21genera.identity.CurrentUser;
import com.c21genera.shared.config.StorageProperties;
import com.c21genera.shared.domain.NotFoundException;
import com.c21genera.shared.domain.UnprocessableException;
import com.c21genera.shared.security.ExpedienteAccessPolicy;
import com.c21genera.shared.storage.FileStorage;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.apache.tika.Tika;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * "Marcar firmado" ya no existe: un contrato solo queda firmado cuando cada
 * parte firmó con evidencia (ver ContractSignatureService).
 */
@RestController
@PreAuthorize("hasAnyAuthority('EXPEDIENT_VIEW_ALL', 'EXPEDIENT_VIEW_OWN')")
public class ContractController {

  private static final Set<String> EVIDENCE_TYPES = Set.of("application/pdf", "image/jpeg", "image/png");

  private final ContractService contractService;
  private final ContractSignatureService signatureService;
  private final ExpedienteAccessPolicy accessPolicy;
  private final FileStorage fileStorage;
  private final StorageProperties storageProperties;
  private final Tika tika = new Tika();

  public ContractController(
      ContractService contractService,
      ContractSignatureService signatureService,
      ExpedienteAccessPolicy accessPolicy,
      FileStorage fileStorage,
      StorageProperties storageProperties) {
    this.contractService = contractService;
    this.signatureService = signatureService;
    this.accessPolicy = accessPolicy;
    this.fileStorage = fileStorage;
    this.storageProperties = storageProperties;
  }

  @GetMapping("/api/v1/internal/expedientes/{expedienteId}/contract/data")
  public CalculationsResponse data(@PathVariable UUID expedienteId, @AuthenticationPrincipal Jwt jwt) {
    requireExpediente(expedienteId, jwt);
    return CalculationsResponse.from(contractService.calculationsOf(expedienteId));
  }

  /** Qué falta para poder generar el contrato para firma (requisitos del proceso y datos del contrato). */
  @GetMapping("/api/v1/internal/expedientes/{expedienteId}/contract/readiness")
  public ReadinessResponse readiness(@PathVariable UUID expedienteId, @AuthenticationPrincipal Jwt jwt) {
    requireExpediente(expedienteId, jwt);
    return ReadinessResponse.from(contractService.readinessOf(expedienteId));
  }

  /** mode=final (por defecto) exige que no falte nada; mode=draft genera un borrador INCOMPLETO bloqueado para firma. */
  @PostMapping("/api/v1/internal/expedientes/{expedienteId}/contracts/generate")
  @PreAuthorize("hasAuthority('CONTRACT_GENERATE')")
  @ResponseStatus(HttpStatus.CREATED)
  public GenerateResponse generate(
      @PathVariable UUID expedienteId, @RequestParam(defaultValue = "final") String mode, @AuthenticationPrincipal Jwt jwt) {
    CurrentUser user = requireExpediente(expedienteId, jwt);
    ContractService.Generated generated = contractService.generate(expedienteId, "draft".equalsIgnoreCase(mode), user.toActor());
    return new GenerateResponse(
        toResponse(generated.contract()), generated.signingLinks().stream().map(SigningLinkResponse::from).toList());
  }

  @GetMapping("/api/v1/internal/expedientes/{expedienteId}/contracts")
  public List<ContractGenerationResponse> list(@PathVariable UUID expedienteId, @AuthenticationPrincipal Jwt jwt) {
    requireExpediente(expedienteId, jwt);
    return contractService.listOf(expedienteId).stream().map(this::toResponse).toList();
  }

  @GetMapping("/api/v1/internal/contracts/{contractId}")
  public ContractGenerationResponse get(@PathVariable UUID contractId, @AuthenticationPrincipal Jwt jwt) {
    return toResponse(requireContract(contractId, jwt));
  }

  public record DownloadResponse(URI url) {}

  /** file=pdf (por defecto), docx o signed (contrato firmado con su constancia de firmas). */
  @GetMapping("/api/v1/internal/contracts/{contractId}/download")
  public DownloadResponse download(
      @PathVariable UUID contractId, @RequestParam(defaultValue = "pdf") String file, @AuthenticationPrincipal Jwt jwt) {
    ContractGeneration contract = requireContract(contractId, jwt);
    String key =
        switch (file) {
          case "docx" -> contract.getDocxStorageKey();
          case "signed" -> contract.getSignedPackageKey();
          default -> contract.getPdfStorageKey();
        };
    if (key == null) {
      throw new NotFoundException("Ese archivo no está disponible para esta versión del contrato.");
    }
    return new DownloadResponse(fileStorage.generateTemporaryDownloadUrl(key, storageProperties.presignedUrlTtl()));
  }

  /** La intermediaria firma desde el sistema (usuario con CONTRACT_SIGN). */
  @PostMapping("/api/v1/internal/contracts/{contractId}/signatures/intermediary")
  @PreAuthorize("hasAuthority('CONTRACT_SIGN')")
  public ContractGenerationResponse signAsIntermediary(
      @PathVariable UUID contractId, @RequestBody SignRequest request, @AuthenticationPrincipal Jwt jwt, HttpServletRequest http) {
    requireContract(contractId, jwt);
    CurrentUser user = CurrentUser.from(jwt);
    signatureService.signAsIntermediary(
        contractId,
        new SignCommand(request.typedName(), request.signatureImageBase64(), request.documentSha256(), request.accepted(), clientIp(http), http.getHeader("User-Agent")),
        user.toActor());
    return toResponse(contractService.get(contractId));
  }

  /** Firma autógrafa: se carga el contrato impreso, firmado a mano por todas las partes y escaneado. */
  @PostMapping("/api/v1/internal/contracts/{contractId}/signatures/autograph")
  @PreAuthorize("hasAuthority('CONTRACT_SIGN')")
  public ContractGenerationResponse registerAutograph(
      @PathVariable UUID contractId, @RequestParam("file") MultipartFile file, @AuthenticationPrincipal Jwt jwt) {
    requireContract(contractId, jwt);
    byte[] content;
    try {
      content = file.getBytes();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    String detected = tika.detect(content);
    if (!EVIDENCE_TYPES.contains(detected)) {
      throw new UnprocessableException("UNSUPPORTED_FILE", "Carga el contrato firmado como PDF, JPG o PNG.");
    }
    signatureService.registerAutographSignatures(contractId, content, detected, CurrentUser.from(jwt).toActor());
    return toResponse(contractService.get(contractId));
  }

  /** Nueva liga de firma para un firmante del cliente (la anterior deja de funcionar). */
  @PostMapping("/api/v1/internal/contract-signatures/{signatureId}/reissue-link")
  @PreAuthorize("hasAuthority('CONTRACT_GENERATE')")
  public SigningLinkResponse reissueLink(@PathVariable UUID signatureId, @AuthenticationPrincipal Jwt jwt) {
    ContractSignature signature = signatureService.signature(signatureId);
    requireExpediente(signature.getExpedienteId(), jwt);
    return SigningLinkResponse.from(signatureService.reissueLink(signatureId));
  }

  @PostMapping("/api/v1/internal/contracts/{contractId}/mark-delivered")
  @PreAuthorize("hasAuthority('CONTRACT_GENERATE')")
  public ContractGenerationResponse markDelivered(
      @PathVariable UUID contractId, @Valid @RequestBody DeliverRequest request, @AuthenticationPrincipal Jwt jwt) {
    requireContract(contractId, jwt);
    return toResponse(contractService.markDelivered(contractId, request.method(), CurrentUser.from(jwt).toActor()));
  }

  private ContractGenerationResponse toResponse(ContractGeneration contract) {
    return ContractGenerationResponse.from(contract, signatureService.signaturesOf(contract.getId()));
  }

  private CurrentUser requireExpediente(UUID expedienteId, Jwt jwt) {
    CurrentUser user = CurrentUser.from(jwt);
    accessPolicy.requireAccess(expedienteId, user.id(), user.permissions());
    return user;
  }

  private ContractGeneration requireContract(UUID contractId, Jwt jwt) {
    ContractGeneration contract = contractService.get(contractId);
    requireExpediente(contract.getExpedienteId(), jwt);
    return contract;
  }

  static String clientIp(HttpServletRequest request) {
    String forwardedFor = request.getHeader("X-Forwarded-For");
    if (forwardedFor != null && !forwardedFor.isBlank()) {
      return forwardedFor.split(",")[0].trim();
    }
    return request.getRemoteAddr();
  }
}
