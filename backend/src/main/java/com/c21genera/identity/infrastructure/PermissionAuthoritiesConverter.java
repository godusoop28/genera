package com.c21genera.identity.infrastructure;

import java.util.Collection;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.stereotype.Component;

/**
 * Convierte el claim "permissions" del JWT en authorities SIN prefijo, para
 * que {@code @PreAuthorize("hasAuthority('DOCUMENT_ACCEPT')")} coincida
 * exactamente con {@link com.c21genera.identity.domain.PermissionCode}.
 */
@Component
public class PermissionAuthoritiesConverter implements Converter<Jwt, AbstractAuthenticationToken> {

  private final JwtGrantedAuthoritiesConverter delegate = new JwtGrantedAuthoritiesConverter();

  public PermissionAuthoritiesConverter() {
    delegate.setAuthorityPrefix("");
    delegate.setAuthoritiesClaimName("permissions");
  }

  @Override
  public AbstractAuthenticationToken convert(Jwt jwt) {
    Collection<GrantedAuthority> authorities = delegate.convert(jwt);
    JwtAuthenticationConverter base = new JwtAuthenticationConverter();
    base.setJwtGrantedAuthoritiesConverter(j -> authorities);
    return base.convert(jwt);
  }
}
