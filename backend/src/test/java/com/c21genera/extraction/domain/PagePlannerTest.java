package com.c21genera.extraction.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.c21genera.extraction.domain.PagePlanner.Batch;
import com.c21genera.extraction.domain.PagePlanner.Plan;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class PagePlannerTest {

  @Test
  void aScannedDeedOf80PagesIsReviewedStartEndAndThenTheRestInBatches() {
    Plan plan = PagePlanner.plan(80, List.of(), List.of("deedNumber", "publicRegistryFolio"), true, 8, 96);

    assertThat(plan.textMode()).isFalse();
    assertThat(plan.batches().get(0).pages()).containsExactly(1, 2, 3, 4, 5, 6, 7, 8);
    assertThat(plan.batches().get(1).pages()).containsExactly(77, 78, 79, 80);
    assertThat(plan.batches().get(2).pages()).startsWith(9);
    // Todas las páginas quedan cubiertas (no solo las primeras 12), sin repetir y en lotes de máximo 8.
    List<Integer> all = plan.batches().stream().flatMap(b -> b.pages().stream()).toList();
    assertThat(all).doesNotHaveDuplicates().hasSize(80);
    assertThat(plan.batches()).allMatch(b -> b.pages().size() <= 8);
  }

  @Test
  void theMaxPagesBudgetIsRespected() {
    Plan plan = PagePlanner.plan(300, List.of(), List.of("deedNumber"), true, 8, 96);

    assertThat(plan.pagesPlanned()).isEqualTo(96);
    assertThat(plan.batches().get(1).pages()).contains(300);
  }

  @Test
  void aPdfWithNativeTextIsRankedByRelevanceWithThePageThatMentionsTheFolioFirstAfterTheCover() {
    List<String> texts = new ArrayList<>(Collections.nCopies(30, "Texto general del testimonio notarial ".repeat(10)));
    texts.set(21, "Inscrito en el REGISTRO PÚBLICO de la Propiedad bajo el FOLIO REAL 12345 " + "relleno ".repeat(30));

    Plan plan = PagePlanner.plan(30, texts, List.of("publicRegistryFolio"), true, 8, 96);

    assertThat(plan.textMode()).isTrue();
    assertThat(plan.batches().getFirst().pages()).startsWith(1, 22);
  }

  @Test
  void shortDocumentsLikeAnIneAreReviewedInASingleBatch() {
    Plan plan = PagePlanner.plan(2, List.of(), List.of("fullName"), false, 8, 96);

    assertThat(plan.batches()).extracting(Batch::pages).containsExactly(List.of(1, 2));
  }
}
