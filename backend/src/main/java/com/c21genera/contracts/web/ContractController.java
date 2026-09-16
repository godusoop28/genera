package com.c21genera.contracts.web;

import com.c21genera.contracts.application.ContractService;
import com.c21genera.contracts.web.ContractDtos.CalculationsResponse;
import com.c21genera.contracts.web.ContractDtos.ContractGenerationResponse;
import com.c21genera.contracts.web.ContractDtos.DeliverRequest;
import com.c21genera.identity.CurrentUser;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@PreAuthorize("hasAnyAuthority('EXPEDIENT_VIEW_ALL', 'EXPEDIENT_VIEW_OWN')")
public class ContractController {

  private final ContractService contractService;

  public ContractController(ContractService contractService) {
    this.contractService = contractService;
  }

  @GetMapping("/api/v1/internal/expedientes/{expedienteId}/contract/data")
  public CalculationsResponse data(@PathVariable UUID expedienteId) {
    return CalculationsResponse.from(contractService.calculationsOf(expedienteId));
  }

  @PostMapping("/api/v1/internal/expedientes/{expedienteId}/contracts/generate")
  @PreAuthorize("hasAuthority('CONTRACT_GENERATE')")
  @ResponseStatus(HttpStatus.CREATED)
  public ContractGenerationResponse generate(@PathVariable UUID expedienteId, @AuthenticationPrincipal Jwt jwt) {
    return ContractGenerationResponse.from(contractService.generate(expedienteId, CurrentUser.from(jwt).id()));
  }

  @GetMapping("/api/v1/internal/expedientes/{expedienteId}/contracts")
  public List<ContractGenerationResponse> list(@PathVariable UUID expedienteId) {
    return contractService.listOf(expedienteId).stream().map(ContractGenerationResponse::from).toList();
  }

  @GetMapping("/api/v1/internal/contracts/{contractId}")
  public ContractGenerationResponse get(@PathVariable UUID contractId) {
    return ContractGenerationResponse.from(contractService.get(contractId));
  }

  @PostMapping("/api/v1/internal/contracts/{contractId}/mark-signed")
  @PreAuthorize("hasAuthority('CONTRACT_GENERATE')")
  public ContractGenerationResponse markSigned(@PathVariable UUID contractId) {
    return ContractGenerationResponse.from(contractService.markSigned(contractId));
  }

  @PostMapping("/api/v1/internal/contracts/{contractId}/mark-delivered")
  @PreAuthorize("hasAuthority('CONTRACT_GENERATE')")
  public ContractGenerationResponse markDelivered(@PathVariable UUID contractId, @Valid @RequestBody DeliverRequest request) {
    return ContractGenerationResponse.from(contractService.markDelivered(contractId, request.method()));
  }
}
