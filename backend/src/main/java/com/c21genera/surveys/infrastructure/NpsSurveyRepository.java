package com.c21genera.surveys.infrastructure;

import com.c21genera.surveys.domain.NpsSurvey;
import com.c21genera.surveys.domain.SurveyStatus;
import com.c21genera.surveys.domain.Touchpoint;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NpsSurveyRepository extends JpaRepository<NpsSurvey, UUID> {

  Optional<NpsSurvey> findByToken(String token);

  List<NpsSurvey> findByExpedienteIdOrderByCreatedAtDesc(UUID expedienteId);

  List<NpsSurvey> findByLeadIdOrderByCreatedAtDesc(UUID leadId);

  List<NpsSurvey> findByContactKeyAndSendAtGreaterThanEqual(String contactKey, Instant since);

  boolean existsByExpedienteIdAndTouchpointAndStatusIn(UUID expedienteId, Touchpoint touchpoint, Collection<SurveyStatus> statuses);

  List<NpsSurvey> findTop50ByStatusAndSendAtLessThanEqualOrderBySendAt(SurveyStatus status, Instant now);

  List<NpsSurvey> findByStatus(SurveyStatus status);

  List<NpsSurvey> findByAnsweredAtBetween(Instant from, Instant to);

  long countByStatusInAndSentAtBetween(Collection<SurveyStatus> statuses, Instant from, Instant to);

  List<NpsSurvey> findBySentAtBetween(Instant from, Instant to);
}
