package com.c21genera.expedientes.web;

import com.c21genera.expedientes.ExpedienteStatus;
import com.c21genera.expedientes.application.ExpedienteService;
import com.c21genera.expedientes.domain.Expediente;
import com.c21genera.expedientes.domain.ManualClientData;
import com.c21genera.expedientes.domain.RequiredDocumentsPendingException;
import com.c21genera.expedientes.web.ManualClientDataDtos.ManualClientDataRequest;
import com.c21genera.expedientes.web.ManualClientDataDtos.ManualClientDataResponse;
import com.c21genera.publicaccess.PublicAccessTokenApi;
import com.c21genera.publicaccess.PublicLinkRevokedException;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Portal del cliente sin cuenta (ver AGENTS §90/§92/§97).
 *
 * <p>Las validaciones de "submit" se basan en estado que expedientes ya
 * posee localmente (estatus de la máquina de estados, que solo avanza a
 * WAITING_DOCUMENTS tras el consentimiento principal; y la bandera
 * allRequiredDocumentsUploaded, actualizada al escuchar el evento de
 * documents) para no depender directamente de otros módulos y evitar un
 * ciclo entre ellos (ver AGENTS §7).
 */
@RestController
@RequestMapping("/api/v1/public/expedientes/{token}")
public class PublicExpedienteController {

  private final PublicAccessTokenApi tokenApi;
  private final ExpedienteService expedienteService;

  public PublicExpedienteController(PublicAccessTokenApi tokenApi, ExpedienteService expedienteService) {
    this.tokenApi = tokenApi;
    this.expedienteService = expedienteService;
  }

  public record PublicExpedienteResponse(String folio, ExpedienteStatus status) {}

  @GetMapping
  public PublicExpedienteResponse get(@PathVariable String token) {
    Expediente expediente = expedienteService.get(resolve(token));
    return new PublicExpedienteResponse(expediente.getFolio(), expediente.getStatus());
  }

  @GetMapping("/client-data")
  public ManualClientDataResponse clientData(@PathVariable String token) {
    return ManualClientDataResponse.from(expedienteService.manualDataOf(resolve(token)));
  }

  @PutMapping("/client-data")
  public ManualClientDataResponse updateClientData(@PathVariable String token, @RequestBody ManualClientDataRequest request) {
    ManualClientData updated = expedienteService.updateManualData(resolve(token), request.toUpdate());
    return ManualClientDataResponse.from(updated);
  }

  public record SubmitResponse(boolean submitted, ExpedienteStatus status) {}

  /** Valida consentimiento, datos mínimos y documentos antes de avanzar el estado (ver AGENTS §97). */
  @PostMapping("/submit")
  public SubmitResponse submit(@PathVariable String token) {
    UUID expedienteId = resolve(token);
    Expediente expediente = expedienteService.get(expedienteId);

    if (expediente.getStatus() == ExpedienteStatus.DRAFT || expediente.getStatus() == ExpedienteStatus.WAITING_PRIVACY) {
      throw new RequiredDocumentsPendingException(
          "Debes aceptar el aviso de privacidad antes de enviar tu documentación.");
    }
    if (expediente.getStatus() != ExpedienteStatus.WAITING_DOCUMENTS) {
      throw new RequiredDocumentsPendingException("Esta documentación ya fue enviada.");
    }

    ManualClientData data = expedienteService.manualDataOf(expedienteId);
    if (isBlank(data.getEmail()) || isBlank(data.getPhone())) {
      throw new RequiredDocumentsPendingException("Completa tu correo y teléfono antes de enviar tu documentación.");
    }

    if (!expediente.isAllRequiredDocumentsUploaded()) {
      throw new RequiredDocumentsPendingException("Todavía faltan documentos obligatorios por cargar.");
    }

    expedienteService.recordDocumentsSubmitted(expedienteId);
    return new SubmitResponse(true, expedienteService.get(expedienteId).getStatus());
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }

  private UUID resolve(String token) {
    return tokenApi.resolve(token).orElseThrow(PublicLinkRevokedException::new);
  }
}
