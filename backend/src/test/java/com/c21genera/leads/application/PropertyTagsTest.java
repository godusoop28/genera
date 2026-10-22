package com.c21genera.leads.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class PropertyTagsTest {

  @Test
  void tagsAreTrimmedAndDeduplicatedIgnoringCase() {
    assertThat(PropertyService.tags(Arrays.asList("  Urge  vender ", "urge vender", "", null, "Dueño flexible")))
        .containsExactly("Urge vender", "Dueño flexible");
  }

  @Test
  void missingTagsMeanNone() {
    assertThat(PropertyService.tags(null)).isEmpty();
  }
}
