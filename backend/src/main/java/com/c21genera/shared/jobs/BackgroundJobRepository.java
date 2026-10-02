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
   *
   * <p>También recupera los jobs que quedaron "en proceso" con un candado más
   * viejo que {@code staleBefore}: su worker murió a medias (reinicio, falta
   * de memoria, despliegue). Sin esto se quedaban así para siempre y el
   * documento nunca terminaba de procesarse (E2E 02/10).
   */
  @Query(
      value =
          "select * from background_job "
              + "where type = :type and ((status = 'QUEUED' and next_attempt_at <= :now) "
              + "or (status = 'PROCESSING' and locked_at < :staleBefore)) "
              + "order by next_attempt_at asc limit :limit for update skip locked",
      nativeQuery = true)
  List<BackgroundJob> claimBatch(
      @Param("type") String type, @Param("now") Instant now, @Param("staleBefore") Instant staleBefore, @Param("limit") int limit);
}
