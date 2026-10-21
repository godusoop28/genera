package com.c21genera.identity;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.c21genera.identity.domain.PasswordPolicy;
import com.c21genera.shared.domain.UnprocessableException;
import org.junit.jupiter.api.Test;

class PasswordPolicyTest {

  private static final String EMAIL = "ana.lopez@century21genera.mx";
  private static final String NAME = "Ana López";

  @Test
  void acceptsALongPassphraseWithoutSymbolRules() {
    assertThatCode(() -> PasswordPolicy.validate("tres gatos en la azotea", EMAIL, NAME)).doesNotThrowAnyException();
    assertThatCode(() -> PasswordPolicy.validate("mangoverde2026", EMAIL, NAME)).doesNotThrowAnyException();
  }

  @Test
  void rejectsShortCommonOrPersonalPasswords() {
    assertRejected("corta123", "al menos 10");
    assertRejected("1234567890", "común");
    assertRejected("Century21Genera", "común");
    assertRejected("aaaaaaaaaaaa", "común");
    assertRejected("ana.lopez2026", "correo");
    assertRejected("analopez", "al menos 10");
    assertRejected("Ana López 2026", "nombre");
    assertRejected("lopez123456", "nombre");
  }

  private static void assertRejected(String password, String messagePart) {
    assertThatThrownBy(() -> PasswordPolicy.validate(password, EMAIL, NAME))
        .isInstanceOf(UnprocessableException.class)
        .hasMessageContaining(messagePart);
  }
}
