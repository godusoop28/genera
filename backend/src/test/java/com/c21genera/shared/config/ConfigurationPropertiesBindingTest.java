package com.c21genera.shared.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;

/**
 * Las propiedades de configuración se enlazan por constructor. Un record con
 * dos constructores públicos tumbó el arranque en Render ("No default
 * constructor found"), cosa que las pruebas unitarias no detectaban.
 */
class ConfigurationPropertiesBindingTest {

  @Test
  void aiPropertiesBindLikeSpringDoesAndApplyDefaults() {
    Binder binder =
        new Binder(
            new MapConfigurationPropertySource(
                Map.of(
                    "app.ai.enabled", "true",
                    "app.ai.provider", "openai",
                    "app.ai.api-key", "k",
                    "app.ai.model", "m",
                    "app.ai.base-url", "https://api.openai.com/v1",
                    "app.ai.timeout", "PT120S",
                    "app.ai.max-retries", "2")));

    AiProperties properties = binder.bind("app.ai", AiProperties.class).get();

    assertThat(properties.timeout()).isEqualTo(Duration.ofSeconds(120));
    assertThat(properties.maxPages()).isEqualTo(96);
    assertThat(properties.pagesPerBatch()).isEqualTo(8);
  }

  @Test
  void everyConfigurationPropertiesClassHasAnUnambiguousBindingConstructor() throws Exception {
    var scanner = new ClassPathScanningCandidateComponentProvider(false);
    scanner.addIncludeFilter(new AnnotationTypeFilter(ConfigurationProperties.class));
    List<Class<?>> classes = scanner.findCandidateComponents("com.c21genera").stream().map(this::load).toList();

    assertThat(classes).contains(AiProperties.class);
    for (Class<?> type : classes) {
      long publicConstructors = Arrays.stream(type.getConstructors()).count();
      boolean explicit = Arrays.stream(type.getDeclaredConstructors()).anyMatch(c -> c.isAnnotationPresent(ConstructorBinding.class));
      assertThat(publicConstructors == 1 || explicit)
          .as("%s debe tener un solo constructor público (o uno marcado con @ConstructorBinding)", type.getSimpleName())
          .isTrue();
    }
  }

  private Class<?> load(org.springframework.beans.factory.config.BeanDefinition definition) {
    try {
      return Class.forName(definition.getBeanClassName());
    } catch (ClassNotFoundException e) {
      throw new IllegalStateException(e);
    }
  }
}
