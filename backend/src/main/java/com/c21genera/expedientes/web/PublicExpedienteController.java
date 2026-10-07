package com.c21genera.expedientes.web;

import com.c21genera.documents.DocumentsApi;
import com.c21genera.expedientes.CivilStatus;
import com.c21genera.expedientes.ExpedienteStatus;
import com.c21genera.expedientes.MaritalRegime;
import com.c21genera.expedientes.PersonType;
import com.c21genera.expedientes.application.ExpedienteService;
import com.c21genera.expedientes.domain.Expediente;
import com.c21genera.expedientes.domain.ExpedienteParticipant;
import com.c21genera.expedientes.domain.ManualClientData;
import com.c21genera.expedientes.domain.ManualClientData.ManualClientDataUpdate;
import com.c21genera.expedientes.domain.RequiredDocumentsPendingException;
import com.c21genera.expedientes.web.ManualClientDataDtos.PublicClientDataRequest;
import com.c21genera.expedientes.web.ManualClientDataDtos.PublicClientDataResponse;
import com.c21genera.publicaccess.PublicAccessTokenApi;
import com.c21genera.publicaccess.PublicLinkRevokedException;
import com.c21genera.shared.events.Actor;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.web.bind.annotation.DeleteMapping;
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
 *
 * <p>Nunca se devuelven datos completos de identificación por esta vía:
 * nombre y domicilio van enmascarados, lo suficiente para que el cliente
 * reconozca que la liga es suya (y detecte si recibió una liga equivocada)
 * sin exponer los datos de otra persona.
 */
@RestController
@RequestMapping("/api/v1/public/expedientes/{token}")
public class PublicExpedienteController {

  private final PublicAccessTokenApi tokenApi;
  private final ExpedienteService expedienteService;
  private final DocumentsApi documentsApi;

  public PublicExpedienteController(PublicAccessTokenApi tokenApi, ExpedienteService expedienteService, DocumentsApi documentsApi) {
    this.documentsApi = documentsApi;
    this.tokenApi = tokenApi;
    this.expedienteService = expedienteService;
  }

  public record PublicExpedienteResponse(
      String folio, ExpedienteStatus status, PersonType personType, String maskedOwnerName, String maskedPropertyAddress) {}

  @GetMapping
  public PublicExpedienteResponse get(@PathVariable String token) {
    Expediente expediente = expedienteService.get(resolve(token));
    return new PublicExpedienteResponse(
        expediente.getFolio(),
        expediente.getStatus(),
        expediente.getPersonType(),
        maskName(expediente.getOwnerDisplayName()),
        propertyHint(expediente.getPropertyAddress(), expediente.getPropertyReference()));
  }

  /**
   * El cliente indica que no reconoce el expediente (la liga pudo llegar a la
   * persona equivocada): la liga deja de funcionar de inmediato para que no se
   * carguen datos ahí, y queda en la bitácora para que el asesor lo revise.
   */
  @PostMapping("/not-recognized")
  public void notRecognized(@PathVariable String token) {
    tokenApi.revoke(resolve(token), Actor.client(), "el cliente indicó que no reconoce el expediente");
  }

  @GetMapping("/client-data")
  public PublicClientDataResponse clientData(@PathVariable String token) {
    return PublicClientDataResponse.from(expedienteService.manualDataOf(resolve(token)));
  }

  @PutMapping("/client-data")
  public PublicClientDataResponse updateClientData(@PathVariable String token, @Valid @RequestBody PublicClientDataRequest request) {
    ManualClientData updated =
        expedienteService.updateManualData(
            resolve(token),
            ManualClientDataUpdate.fromClient(request.email(), request.phone(), request.notificationAddress(), request.civilStatus()),
            Actor.client(),
            null);
    return PublicClientDataResponse.from(updated);
  }

  /**
   * Todos los que comparecen, con nombre enmascarado: el paso Documentos dice
   * de quién es cada documento (p. ej. dos representantes legales). Los
   * titulares persona física además declaran aquí su estado civil.
   */
  public record PublicParticipantResponse(UUID id, String role, String displayName, CivilStatus civilStatus, MaritalRegime maritalRegime) {}

  @GetMapping("/participants")
  public List<PublicParticipantResponse> participants(@PathVariable String token) {
    UUID expedienteId = resolve(token);
    return expedienteService.participantsOf(expedienteId).stream()
        .map(
            p ->
                new PublicParticipantResponse(
                    p.getId(), p.getRole().name(), maskName(p.getFullName()), p.getCivilStatus(), p.details().maritalRegime()))
        .toList();
  }

  public record CivilStatusRequest(@NotNull CivilStatus civilStatus, MaritalRegime maritalRegime) {}

  @PutMapping("/participants/{participantId}/civil-status")
  public PublicParticipantResponse declareCivilStatus(
      @PathVariable String token, @PathVariable UUID participantId, @Valid @RequestBody CivilStatusRequest request) {
    UUID expedienteId = resolve(token);
    ExpedienteParticipant p = expedienteService.declareCivilStatus(expedienteId, participantId, request.civilStatus(), request.maritalRegime());
    return new PublicParticipantResponse(p.getId(), p.getRole().name(), maskName(p.getFullName()), p.getCivilStatus(), p.details().maritalRegime());
  }

  public record OwnerNameRequest(@NotBlank @Size(max = 200) String fullName) {}

  /** El cliente agrega un copropietario que faltó; se le piden sus documentos en el paso Documentos. */
  @PostMapping("/participants")
  public PublicParticipantResponse addCoOwner(@PathVariable String token, @Valid @RequestBody OwnerNameRequest request) {
    ExpedienteParticipant p = expedienteService.addCoOwnerByClient(resolve(token), request.fullName());
    return new PublicParticipantResponse(p.getId(), p.getRole().name(), maskName(p.getFullName()), p.getCivilStatus(), p.details().maritalRegime());
  }

  @PutMapping("/participants/{participantId}")
  public PublicParticipantResponse renameOwner(
      @PathVariable String token, @PathVariable UUID participantId, @Valid @RequestBody OwnerNameRequest request) {
    ExpedienteParticipant p = expedienteService.renameOwnerByClient(resolve(token), participantId, request.fullName());
    return new PublicParticipantResponse(p.getId(), p.getRole().name(), maskName(p.getFullName()), p.getCivilStatus(), p.details().maritalRegime());
  }

  @DeleteMapping("/participants/{participantId}")
  public void removeCoOwner(@PathVariable String token, @PathVariable UUID participantId) {
    expedienteService.removeCoOwnerByClient(resolve(token), participantId);
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

    if (expediente.getPersonType() == PersonType.FISICA) {
      boolean civilStatusMissing =
          expedienteService.participantsOf(expedienteId).stream()
              .filter(ExpedienteParticipant::isOwner)
              .anyMatch(p -> p.getCivilStatus() == null);
      if (civilStatusMissing) {
        throw new RequiredDocumentsPendingException("Indica el estado civil de cada propietario antes de enviar tu documentación.");
      }
    }

    // La fuente de verdad son los documentos mismos, no la bandera que mantiene un evento
    // (podía quedar desfasada si el cliente subía varios archivos a la vez; E2E 02/10).
    if (!documentsApi.allRequiredUploaded(expedienteId)) {
      throw new RequiredDocumentsPendingException("Todavía faltan documentos obligatorios por cargar.");
    }
    if (!expediente.isAllRequiredDocumentsUploaded()) {
      expedienteService.markAllRequiredDocumentsUploaded(expedienteId);
    }

    expedienteService.recordDocumentsSubmitted(expedienteId);
    return new SubmitResponse(true, expedienteService.get(expedienteId).getStatus());
  }

  /** "Juan Pérez López" -> "Juan P. L."; suficiente para reconocerse, no para identificar a un tercero. */
  static String maskName(String fullName) {
    if (fullName == null || fullName.isBlank()) {
      return "";
    }
    return Arrays.stream(fullName.split(",| y "))
        .map(String::strip)
        .filter(s -> !s.isEmpty())
        .map(
            name -> {
              String[] parts = name.split("\\s+");
              StringBuilder masked = new StringBuilder(parts[0]);
              for (int i = 1; i < parts.length; i++) {
                masked.append(' ').append(Character.toUpperCase(parts[i].charAt(0))).append('.');
              }
              return masked.toString();
            })
        .collect(Collectors.joining(", "));
  }

  /**
   * Lo que el cliente ve del inmueble: el domicilio enmascarado si ya se leyó de
   * los documentos; si no, la referencia corta que capturó el asesor al crear el
   * expediente (ya pensada para mostrarse, sin número ni datos completos).
   */
  static String propertyHint(String address, String reference) {
    if (!isBlank(address)) {
      return maskAddress(address);
    }
    return isBlank(reference) ? "" : reference.strip();
  }

  /** Solo el inicio de la calle y el municipio/estado; nunca el domicilio completo. */
  static String maskAddress(String address) {
    if (address == null || address.isBlank()) {
      return "";
    }
    String[] segments = address.split(",");
    String street = segments[0].strip();
    String start = street.length() > 12 ? street.substring(0, 12).strip() + "…" : street;
    if (segments.length >= 4) {
      return start + ", " + segments[segments.length - 3].strip() + ", " + segments[segments.length - 2].strip();
    }
    return start;
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }

  private UUID resolve(String token) {
    return tokenApi.resolve(token).orElseThrow(PublicLinkRevokedException::new);
  }
}
