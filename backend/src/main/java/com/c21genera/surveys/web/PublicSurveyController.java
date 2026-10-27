package com.c21genera.surveys.web;

import com.c21genera.publicaccess.PublicAccessTokenApi;
import com.c21genera.publicaccess.PublicLinkRevokedException;
import com.c21genera.surveys.application.SurveyService;
import com.c21genera.surveys.application.SurveyService.PublicSurvey;
import com.c21genera.surveys.domain.Touchpoint;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Control de Cambios #28: página pública de la encuesta (sin cuenta). El
 * token de la liga identifica la encuesta; el QR fijo identifica solo el punto
 * de contacto; las encuestas "dentro del sistema" se muestran en el portal del
 * cliente con su liga de carga de documentos.
 */
@RestController
public class PublicSurveyController {

  private final SurveyService surveys;
  private final PublicAccessTokenApi portalTokens;

  public PublicSurveyController(SurveyService surveys, PublicAccessTokenApi portalTokens) {
    this.surveys = surveys;
    this.portalTokens = portalTokens;
  }

  public record AnswerRequest(@Min(0) @Max(10) int score, @Size(max = 120) String name) {}

  public record CommentRequest(@Size(max = 2000) String comment) {}

  @GetMapping("/api/v1/public/surveys/{token}")
  public PublicSurvey get(@PathVariable String token) {
    return surveys.publicView(token);
  }

  @PostMapping("/api/v1/public/surveys/{token}/answer")
  public PublicSurvey answer(@PathVariable String token, @Valid @RequestBody AnswerRequest r) {
    return surveys.answer(token, r.score());
  }

  @PostMapping("/api/v1/public/surveys/{token}/comment")
  public PublicSurvey comment(@PathVariable String token, @Valid @RequestBody CommentRequest r) {
    return surveys.comment(token, r.comment());
  }

  /** QR fijo impreso en lonas, fichas técnicas o folletos. */
  @GetMapping("/api/v1/public/surveys/touchpoints/{touchpoint}")
  public PublicSurvey staticSurvey(@PathVariable String touchpoint) {
    return surveys.staticView(touchpointOf(touchpoint));
  }

  @PostMapping("/api/v1/public/surveys/touchpoints/{touchpoint}")
  public PublicSurvey answerStatic(@PathVariable String touchpoint, @Valid @RequestBody AnswerRequest r) {
    return surveys.answerStatic(touchpointOf(touchpoint), r.score(), r.name());
  }

  /** Encuestas pendientes "dentro del sistema" para el portal del cliente. */
  @GetMapping("/api/v1/public/expedientes/{token}/surveys")
  public List<PublicSurvey> pendingInPortal(@PathVariable String token) {
    return surveys.pendingInPortal(resolve(token));
  }

  /** Punto de contacto 2: el cliente terminó de leer las instrucciones y presionó "Continuar". */
  @PostMapping("/api/v1/public/expedientes/{token}/survey-touchpoints/document-prep")
  public List<PublicSurvey> documentPrepDone(@PathVariable String token) {
    UUID expedienteId = resolve(token);
    surveys.triggerNow(Touchpoint.DOCUMENT_PREP, expedienteId);
    return surveys.pendingInPortal(expedienteId);
  }

  private UUID resolve(String token) {
    return portalTokens.resolve(token).orElseThrow(PublicLinkRevokedException::new);
  }

  private static Touchpoint touchpointOf(String value) {
    try {
      return Touchpoint.valueOf(value.toUpperCase(Locale.ROOT).replace('-', '_'));
    } catch (IllegalArgumentException e) {
      throw new com.c21genera.shared.domain.NotFoundException("Encuesta no encontrada");
    }
  }
}
