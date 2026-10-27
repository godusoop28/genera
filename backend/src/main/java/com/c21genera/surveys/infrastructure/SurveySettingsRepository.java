package com.c21genera.surveys.infrastructure;

import com.c21genera.surveys.domain.SurveySettings;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SurveySettingsRepository extends JpaRepository<SurveySettings, Short> {}
