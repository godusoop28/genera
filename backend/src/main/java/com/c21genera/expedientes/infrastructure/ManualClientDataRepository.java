package com.c21genera.expedientes.infrastructure;

import com.c21genera.expedientes.domain.ManualClientData;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ManualClientDataRepository extends JpaRepository<ManualClientData, UUID> {}
