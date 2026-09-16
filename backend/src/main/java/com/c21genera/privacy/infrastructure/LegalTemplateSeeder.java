package com.c21genera.privacy.infrastructure;

import com.c21genera.privacy.domain.LegalTemplate;
import com.c21genera.privacy.domain.LegalTemplateType;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Registra la metadata (versión + SHA-256) de las plantillas legales
 * empaquetadas con el backend (ver AGENTS §64/§147). No altera los
 * documentos originales: solo calcula su hash y lo guarda para poder saber
 * exactamente qué versión aceptó cada cliente.
 */
@Component
@Order(10)
public class LegalTemplateSeeder implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(LegalTemplateSeeder.class);

  private final LegalTemplateRepository repository;
  private final Clock clock;

  public LegalTemplateSeeder(LegalTemplateRepository repository, Clock clock) {
    this.repository = repository;
    this.clock = clock;
  }

  @Override
  public void run(ApplicationArguments args) throws Exception {
    registerIfChanged(
        LegalTemplateType.PRIVACY_NOTICE_RECEPTION, "legal-templates/aviso-recepcion-documentos.docx");
    registerIfChanged(LegalTemplateType.INTERMEDIATION_CONTRACT, "legal-templates/contrato-profeco-original.docx");
  }

  private void registerIfChanged(LegalTemplateType type, String classpathLocation) throws IOException {
    String sha256 = sha256Of(classpathLocation);
    Optional<LegalTemplate> current = repository.findTopByTypeOrderByVersionDesc(type);

    if (current.isPresent() && current.get().getSha256().equals(sha256)) {
      return; // ya registrado, sin cambios
    }

    int nextVersion = current.map(t -> t.getVersion() + 1).orElse(1);
    repository.findByTypeAndActiveTrue(type).ifPresent(t -> t.deactivate(clock.instant()));
    repository.save(new LegalTemplate(type, nextVersion, clock.instant(), sha256, classpathLocation));
    log.info("Plantilla legal registrada: {} v{} ({})", type, nextVersion, sha256);
  }

  private static String sha256Of(String classpathLocation) throws IOException {
    try (InputStream in = new ClassPathResource(classpathLocation).getInputStream()) {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] buffer = new byte[8192];
      int read;
      while ((read = in.read(buffer)) != -1) {
        digest.update(buffer, 0, read);
      }
      return HexFormat.of().formatHex(digest.digest());
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
