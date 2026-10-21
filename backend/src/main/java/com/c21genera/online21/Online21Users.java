package com.c21genera.online21;

import java.util.List;

/**
 * Usuarios (asesores, directivos) de la oficina en 21Online, para vincularlos
 * con el personal de Genera. Solo lectura: las cuentas de 21Online las
 * administra 21Online.
 */
public interface Online21Users {

  boolean available();

  /** Todos los usuarios de la oficina (todas las páginas). Lanza si 21Online no responde. */
  List<RemoteUser> list();

  record RemoteUser(long id, String name, String email, String position, String mobilePhone, String whatsapp) {}
}
