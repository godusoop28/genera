package com.c21genera.expedientes.web;

import com.c21genera.expedientes.application.ExpedienteService;
import com.c21genera.expedientes.web.ManualClientDataDtos.ManualClientDataRequest;
import com.c21genera.expedientes.web.ManualClientDataDtos.ManualClientDataResponse;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/internal/expedientes/{id}/client-data")
@PreAuthorize("hasAnyAuthority('EXPEDIENT_VIEW_ALL', 'EXPEDIENT_VIEW_OWN')")
public class ManualClientDataController {

  private final ExpedienteService expedienteService;

  public ManualClientDataController(ExpedienteService expedienteService) {
    this.expedienteService = expedienteService;
  }

  @GetMapping
  public ManualClientDataResponse get(@PathVariable UUID id) {
    return ManualClientDataResponse.from(expedienteService.manualDataOf(id));
  }

  @PutMapping
  @PreAuthorize("hasAuthority('EXTRACTED_DATA_EDIT')")
  public ManualClientDataResponse update(@PathVariable UUID id, @RequestBody ManualClientDataRequest request) {
    return ManualClientDataResponse.from(expedienteService.updateManualData(id, request.toUpdate()));
  }
}
