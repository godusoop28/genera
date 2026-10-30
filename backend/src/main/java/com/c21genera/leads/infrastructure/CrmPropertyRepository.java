package com.c21genera.leads.infrastructure;

import com.c21genera.leads.domain.CrmProperty;
import com.c21genera.leads.domain.SyncStatus;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CrmPropertyRepository extends JpaRepository<CrmProperty, UUID>, JpaSpecificationExecutor<CrmProperty> {

  List<CrmProperty> findTop200ByOrderByCreatedAtDesc();

  List<CrmProperty> findTop25BySyncStatusOrderByCreatedAtAsc(SyncStatus status);

  List<CrmProperty> findByOnline21IdIn(Collection<Long> online21Ids);

  /** Las que ya están en 21Online (para traer lo que se cambie allá). */
  List<CrmProperty> findByOnline21IdNotNull();

  /** CC#36: fecha de alta de 21Online sin pasar por la entidad (no compite con ediciones en curso). */
  @Modifying
  @Query("update CrmProperty p set p.listedAt = :listedAt where p.id = :id")
  int updateListedAt(@Param("id") UUID id, @Param("listedAt") Instant listedAt);

  /** CC#36: etiquetas de todas las propiedades (JSON tal cual), para el filtro del listado paginado. */
  @Query("select p.tagsJson from CrmProperty p where p.tagsJson is not null")
  List<String> allTagsJson();
}
