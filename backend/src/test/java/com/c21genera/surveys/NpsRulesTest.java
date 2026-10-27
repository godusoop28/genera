package com.c21genera.surveys;

import static org.assertj.core.api.Assertions.assertThat;

import com.c21genera.surveys.domain.NpsCategory;
import com.c21genera.surveys.domain.NpsSurvey;
import com.c21genera.surveys.domain.NpsSurvey.Contact;
import com.c21genera.surveys.domain.SurveyChannel;
import com.c21genera.surveys.domain.SurveyStatus;
import com.c21genera.surveys.domain.Touchpoint;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Control de Cambios #28: clasificación NPS y datos de la encuesta (sin base de datos). */
class NpsRulesTest {

  @Test
  void classifiesScores() {
    assertThat(NpsCategory.of(0)).isEqualTo(NpsCategory.DETRACTOR);
    assertThat(NpsCategory.of(6)).isEqualTo(NpsCategory.DETRACTOR);
    assertThat(NpsCategory.of(7)).isEqualTo(NpsCategory.PASSIVE);
    assertThat(NpsCategory.of(8)).isEqualTo(NpsCategory.PASSIVE);
    assertThat(NpsCategory.of(9)).isEqualTo(NpsCategory.PROMOTER);
    assertThat(NpsCategory.of(10)).isEqualTo(NpsCategory.PROMOTER);
  }

  @Test
  void npsIsPromotersMinusDetractors() {
    // 6 promotores, 2 pasivos, 2 detractores de 10 → 60 % − 20 % = 40.
    assertThat(NpsCategory.nps(6, 2, 10)).isEqualTo(40.0);
    assertThat(NpsCategory.nps(1, 2, 3)).isEqualTo(-33.3);
    assertThat(NpsCategory.nps(0, 0, 0)).isZero();
  }

  @Test
  void contactKeyPrefersPhoneThenEmail() {
    UUID exp = UUID.randomUUID();
    assertThat(new Contact(exp, "F", null, "Ana", "+52 1 777 123 4567", "a@b.mx", null, null).key()).isEqualTo("tel:7771234567");
    assertThat(new Contact(exp, "F", null, "Ana", null, "Ana@B.mx", null, null).key()).isEqualTo("mail:ana@b.mx");
    assertThat(new Contact(exp, "F", null, "Ana", null, null, null, null).key()).isEqualTo("exp:" + exp);
  }

  @Test
  void answeringMovesToAnsweredWithCategory() {
    Instant now = Instant.parse("2026-10-26T18:00:00Z");
    NpsSurvey s = new NpsSurvey(Touchpoint.CLOSING, SurveyChannel.WHATSAPP, new Contact(null, null, UUID.randomUUID(), "Ana", null, null, null, null), null, true, now, null, now);
    assertThat(s.getStatus()).isEqualTo(SurveyStatus.SCHEDULED);
    assertThat(s.getToken()).hasSizeGreaterThanOrEqualTo(30);
    s.answer(9, now);
    s.comment("  Muy buena atención  ");
    assertThat(s.getStatus()).isEqualTo(SurveyStatus.ANSWERED);
    assertThat(s.category()).isEqualTo(NpsCategory.PROMOTER);
    assertThat(s.getComment()).isEqualTo("Muy buena atención");
  }
}
