package com.c21genera.identity;

import java.util.List;
import java.util.UUID;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * API pública del módulo identity: representa al usuario interno autenticado
 * a partir de los claims del JWT, sin necesidad de volver a consultar la
 * base de datos. Cualquier módulo puede usar esto para saber "quién" está
 * ejecutando una acción (por ejemplo, quién firma la recepción documental).
 */
public record CurrentUser(UUID id, String name, String email, String role, List<String> permissions) {

  public static CurrentUser from(Jwt jwt) {
    return new CurrentUser(
        UUID.fromString(jwt.getSubject()),
        jwt.getClaimAsString("name"),
        jwt.getClaimAsString("email"),
        jwt.getClaimAsString("role"),
        jwt.getClaimAsStringList("permissions"));
  }

  public boolean hasPermission(String permissionCode) {
    return permissions != null && permissions.contains(permissionCode);
  }
}
