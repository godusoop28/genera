package com.c21genera.shared.jobs;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BackgroundJobRepository extends JpaRepository<BackgroundJob, UUID> {

  /**
   * SKIP LOCKED evita que dos instancias del worker tomen el mismo job (ver
   * AGENTS §35: diseñado para poder escalar horizontalmente sin cola externa).
   */
  @Query(
      value =
          "select * from background_job "
              + "where type = :type and status = 'QUEUED' and next_attempt_at <= :now "
              + "order by next_attempt_at asc limit :limit for update skip locked",
      nativeQuery = true)
  List<BackgroundJob> claimBatch(@Param("type") String type, @Param("now") Instant now, @Param("limit") int limit);
}
