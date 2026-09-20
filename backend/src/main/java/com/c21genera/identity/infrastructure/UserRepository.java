package com.c21genera.identity.infrastructure;

import com.c21genera.identity.domain.User;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, UUID> {

  Optional<User> findByEmailIgnoreCase(String email);

  boolean existsByEmailIgnoreCase(String email);

  // role es LAZY y open-in-view está desactivado: sin el EntityGraph, mapear
  // a UserResponse en el controller (fuera de la transacción) revienta con
  // LazyInitializationException.
  @EntityGraph(attributePaths = "role")
  Page<User> findAll(Pageable pageable);
}
