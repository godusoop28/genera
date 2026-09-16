package com.c21genera.identity.infrastructure;

import com.c21genera.identity.domain.Permission;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PermissionRepository extends JpaRepository<Permission, String> {}
