package com.c21genera.identity.infrastructure;

import com.c21genera.identity.domain.User;
import com.c21genera.shared.config.JwtProperties;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

/** Emisión de access tokens JWT firmados por el propio backend. */
@Component
public class JwtTokenService {

  private final JwtEncoder jwtEncoder;
  private final JwtProperties properties;
  private final Clock clock;

  public JwtTokenService(JwtEncoder jwtEncoder, JwtProperties properties, Clock clock) {
    this.jwtEncoder = jwtEncoder;
    this.properties = properties;
    this.clock = clock;
  }

  public IssuedAccessToken issueAccessToken(User user) {
    Instant now = clock.instant();
    Instant expiresAt = now.plus(properties.accessTtl());

    List<String> permissions = user.getRole().getPermissions().stream().map(p -> p.getCode()).toList();

    JwtClaimsSet claims =
        JwtClaimsSet.builder()
            .issuer(properties.issuer())
            .issuedAt(now)
            .expiresAt(expiresAt)
            .subject(user.getId().toString())
            .claim("email", user.getEmail())
            .claim("name", user.getName())
            .claim("role", user.getRole().getCode().name())
            .claim("permissions", permissions)
            .build();

    String token =
        jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(org.springframework.security.oauth2.jose.jws.MacAlgorithm.HS256).build(), claims)).getTokenValue();

    return new IssuedAccessToken(token, expiresAt);
  }

  public record IssuedAccessToken(String token, Instant expiresAt) {}
}
