package com.c21genera.surveys.infrastructure;

import com.c21genera.surveys.domain.Touchpoint;
import com.c21genera.surveys.domain.TouchpointConfig;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TouchpointConfigRepository extends JpaRepository<TouchpointConfig, Touchpoint> {}
