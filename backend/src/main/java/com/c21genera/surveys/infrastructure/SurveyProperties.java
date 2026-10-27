package com.c21genera.surveys.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** baseUrl: página pública de la encuesta en el frontend; la liga es baseUrl + "/" + token. */
@ConfigurationProperties(prefix = "app.surveys")
public record SurveyProperties(String baseUrl) {

  public SurveyProperties {
    if (baseUrl == null || baseUrl.isBlank()) {
      baseUrl = "https://genera-puce.vercel.app/encuesta";
    }
    baseUrl = baseUrl.replaceAll("/+$", "");
  }
}
