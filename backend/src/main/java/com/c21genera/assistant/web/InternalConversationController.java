package com.c21genera.assistant.web;

import com.c21genera.assistant.application.ConversationAdminService;
import com.c21genera.assistant.application.ConversationAdminService.ConversationDetail;
import com.c21genera.assistant.application.ConversationAdminService.ConversationItem;
import com.c21genera.assistant.application.ConversationAdminService.Status;
import com.c21genera.assistant.application.SavedReplyService;
import com.c21genera.assistant.application.SavedReplyService.SavedReplyView;
import com.c21genera.assistant.domain.Conversation;
import com.c21genera.identity.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Bandeja de conversaciones del asistente de WhatsApp y simulador. */
@RestController
@PreAuthorize("hasAuthority('CONVERSATION_VIEW')")
public class InternalConversationController {

  private final ConversationAdminService service;
  private final SavedReplyService savedReplies;

  public InternalConversationController(ConversationAdminService service, SavedReplyService savedReplies) {
    this.service = service;
    this.savedReplies = savedReplies;
  }

  public record ControlRequest(@NotNull Conversation.Control control) {}

  public record TextRequest(@NotBlank @Size(max = 2000) String text) {}

  public record SavedReplyRequest(@NotBlank @Size(max = 80) String title, @NotBlank @Size(max = 2000) String text) {}

  @GetMapping("/api/v1/internal/assistant/status")
  public Status status() {
    return service.status();
  }

  @GetMapping("/api/v1/internal/conversations")
  public List<ConversationItem> list() {
    return service.list();
  }

  @GetMapping("/api/v1/internal/conversations/{id}")
  public ConversationDetail get(@PathVariable UUID id) {
    return service.get(id);
  }

  @PostMapping("/api/v1/internal/conversations/{id}/control")
  @PreAuthorize("hasAuthority('CONVERSATION_MANAGE')")
  public ConversationDetail control(@PathVariable UUID id, @Valid @RequestBody ControlRequest request) {
    return service.setControl(id, request.control());
  }

  @PostMapping("/api/v1/internal/conversations/{id}/reply")
  @PreAuthorize("hasAuthority('CONVERSATION_MANAGE')")
  public ConversationDetail reply(@PathVariable UUID id, @Valid @RequestBody TextRequest request) {
    return service.teamReply(id, request.text());
  }

  @PostMapping("/api/v1/internal/conversations/escalations/{escalationId}/resolve")
  @PreAuthorize("hasAuthority('CONVERSATION_MANAGE')")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void resolve(@PathVariable UUID escalationId) {
    service.resolveEscalation(escalationId);
  }

  /** Manda un mensaje como si fuera un cliente; la respuesta de la IA aparece en la conversación de prueba unos segundos después. */
  @PostMapping("/api/v1/internal/assistant/simulator")
  @PreAuthorize("hasAuthority('CONVERSATION_MANAGE')")
  public Map<String, UUID> simulate(@Valid @RequestBody TextRequest request, @AuthenticationPrincipal Jwt jwt) {
    CurrentUser user = CurrentUser.from(jwt);
    UUID id = service.simulate(user.id(), user.name(), request.text());
    return id == null ? Map.of() : Map.of("conversationId", id);
  }

  @DeleteMapping("/api/v1/internal/assistant/simulator")
  @PreAuthorize("hasAuthority('CONVERSATION_MANAGE')")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void resetSimulation(@AuthenticationPrincipal Jwt jwt) {
    service.resetSimulation(CurrentUser.from(jwt).id());
  }

  /** Respuestas guardadas: las ve quien ve conversaciones; las crea y edita quien puede responder. */
  @GetMapping("/api/v1/internal/conversations/saved-replies")
  public List<SavedReplyView> savedReplies() {
    return savedReplies.list();
  }

  @PostMapping("/api/v1/internal/conversations/saved-replies")
  @PreAuthorize("hasAuthority('CONVERSATION_MANAGE')")
  @ResponseStatus(HttpStatus.CREATED)
  public SavedReplyView createSavedReply(@Valid @RequestBody SavedReplyRequest request, @AuthenticationPrincipal Jwt jwt) {
    return savedReplies.create(request.title(), request.text(), CurrentUser.from(jwt).name());
  }

  @PutMapping("/api/v1/internal/conversations/saved-replies/{id}")
  @PreAuthorize("hasAuthority('CONVERSATION_MANAGE')")
  public SavedReplyView updateSavedReply(@PathVariable UUID id, @Valid @RequestBody SavedReplyRequest request) {
    return savedReplies.update(id, request.title(), request.text());
  }

  @DeleteMapping("/api/v1/internal/conversations/saved-replies/{id}")
  @PreAuthorize("hasAuthority('CONVERSATION_MANAGE')")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deleteSavedReply(@PathVariable UUID id) {
    savedReplies.delete(id);
  }
}
