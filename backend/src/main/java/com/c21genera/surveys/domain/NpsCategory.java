package com.c21genera.surveys.domain;

/** Clasificación NPS estándar: 0–6 detractor, 7–8 pasivo, 9–10 promotor. */
public enum NpsCategory {
  DETRACTOR("¿Qué podríamos haber hecho mejor en esta etapa?"),
  PASSIVE("¿Hay algo específico que podríamos mejorar?"),
  PROMOTER("¿Qué fue lo que más le gustó de esta experiencia?");

  private final String followUpQuestion;

  NpsCategory(String followUpQuestion) {
    this.followUpQuestion = followUpQuestion;
  }

  public String followUpQuestion() {
    return followUpQuestion;
  }

  public static NpsCategory of(int score) {
    if (score <= 6) {
      return DETRACTOR;
    }
    return score <= 8 ? PASSIVE : PROMOTER;
  }

  /** NPS = % promotores − % detractores (de −100 a 100, redondeado a un decimal). */
  public static double nps(long promoters, long detractors, long responses) {
    if (responses == 0) {
      return 0;
    }
    return Math.round((promoters - detractors) * 1000.0 / responses) / 10.0;
  }
}
