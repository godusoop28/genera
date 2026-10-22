package com.c21genera.assistant.application;

import com.c21genera.assistant.domain.SavedReply;
import com.c21genera.assistant.infrastructure.SavedReplyRepository;
import com.c21genera.shared.domain.NotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Respuestas guardadas del equipo (compartidas por toda la oficina). */
@Service
@Transactional
public class SavedReplyService {

  public record SavedReplyView(UUID id, String title, String text, String createdBy, Instant updatedAt) {}

  private final SavedReplyRepository replies;
  private final Clock clock;

  public SavedReplyService(SavedReplyRepository replies, Clock clock) {
    this.replies = replies;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public List<SavedReplyView> list() {
    return replies.findAllByOrderByTitleAsc().stream().map(SavedReplyService::view).toList();
  }

  public SavedReplyView create(String title, String text, String author) {
    return view(replies.save(new SavedReply(title, text, author, clock.instant())));
  }

  public SavedReplyView update(UUID id, String title, String text) {
    SavedReply reply = replies.findById(id).orElseThrow(() -> new NotFoundException("Respuesta guardada", id));
    reply.edit(title, text, clock.instant());
    return view(reply);
  }

  public void delete(UUID id) {
    replies.deleteById(id);
  }

  private static SavedReplyView view(SavedReply r) {
    return new SavedReplyView(r.getId(), r.getTitle(), r.getText(), r.getCreatedBy(), r.getUpdatedAt());
  }
}
