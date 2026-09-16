package com.c21genera.identity.infrastructure;

import com.c21genera.identity.domain.Role;
import com.c21genera.identity.domain.RoleCode;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleRepository extends JpaRepository<Role, UUID> {

  Optional<Role> findByCode(RoleCode code);
}
