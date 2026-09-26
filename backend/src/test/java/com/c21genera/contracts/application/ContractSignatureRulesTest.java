package com.c21genera.contracts.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.c21genera.contracts.domain.ContractGeneration;
import com.c21genera.contracts.domain.ContractGenerationStatus;
import com.c21genera.contracts.domain.ContractSignature;
import com.c21genera.shared.config.ContractsProperties;
import com.c21genera.shared.domain.ConflictException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ContractSignatureRulesTest {

  private static ContractGeneration contract(boolean draft) {
    return new ContractGeneration(
        UUID.randomUUID(), 1, "{}", "a.docx", "a.pdf", Instant.now(), UUID.randomUUID(), "x", "abc123", draft, List.of(), "Persona física");
  }

  @Test
  void typedNameMustMatchIgnoringAccentsCaseAndSpaces() {
    assertThat(ContractSignatureService.sameName("  juan  perez lopez ", "Juan Pérez López")).isTrue();
    assertThat(ContractSignatureService.sameName("Juan Perez", "Juan Pérez López")).isFalse();
    assertThat(ContractSignatureService.sameName(null, "Juan")).isFalse();
  }

  @Test
  void aDraftOrSupersededContractCanNeverBeSigned() {
    assertThatThrownBy(() -> contract(true).ensureSignable()).isInstanceOf(ConflictException.class).hasMessageContaining("INCOMPLETO");

    ContractGeneration superseded = contract(false);
    superseded.supersede(Instant.now(), "Se corrigieron datos");
    assertThatThrownBy(superseded::ensureSignable).isInstanceOf(ConflictException.class).hasMessageContaining("versión nueva");
  }

  @Test
  void aSignedContractCannotBeSupersededAndOnlyASignedOneCanBeDelivered() {
    ContractGeneration generated = contract(false);
    assertThatThrownBy(() -> generated.markDelivered(Instant.now(), "EMAIL")).isInstanceOf(ConflictException.class);

    generated.markFullySigned(Instant.now(), "firmado.pdf");
    assertThat(generated.isSupersedable()).isFalse();
    generated.markDelivered(Instant.now(), "EMAIL");
    assertThat(generated.getStatus()).isEqualTo(ContractGenerationStatus.DELIVERED);
  }

  @Test
  void aSignatureLinkWorksOnlyOnceAndNotAfterItExpiresOrIsVoided() {
    Instant now = Instant.now();
    ContractSignature signature =
        new ContractSignature(
            UUID.randomUUID(), UUID.randomUUID(), ContractSignature.Party.CLIENT, UUID.randomUUID(), "Juan", "Propietario", null, "hash",
            now.plus(Duration.ofDays(1)), now);
    assertThat(signature.isLinkUsable(now)).isTrue();
    assertThat(signature.isLinkUsable(now.plus(Duration.ofDays(2)))).isFalse();

    signature.signElectronically(now, "abc123", "Juan", "1.1.1.1", "UA", "firma.png", "h", "acepto", null);
    assertThat(signature.isLinkUsable(now.plusSeconds(1))).isFalse();
    assertThatThrownBy(() -> signature.signAutograph(now, "abc123", "e", "h", UUID.randomUUID())).isInstanceOf(ConflictException.class);

    ContractSignature voided =
        new ContractSignature(
            UUID.randomUUID(), UUID.randomUUID(), ContractSignature.Party.CLIENT, UUID.randomUUID(), "Ana", "Copropietario", null, "hash2",
            now.plus(Duration.ofDays(1)), now);
    voided.voidBecause(now, "Contrato reemplazado");
    assertThat(voided.isLinkUsable(now)).isFalse();
  }

  @Test
  void signingUrlIsDerivedFromThePublicLinkBase() {
    ContractsProperties properties = new ContractsProperties(null, null);
    assertThat(properties.signingBaseUrlOr("https://genera.vercel.app/carga")).isEqualTo("https://genera.vercel.app/firma");
    assertThat(new ContractsProperties("https://x.com/f", null).signingBaseUrlOr("https://y.com/carga")).isEqualTo("https://x.com/f");
    assertThat(properties.signingLinkTtl()).isEqualTo(Duration.ofDays(15));
  }
}
