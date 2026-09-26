package com.c21genera.expedientes;

/**
 * Con qué carácter comparece quien firma por el cliente. REPRESENTANTE_LEGAL
 * es exclusivo de persona moral; PROPIETARIO/COPROPIETARIO de persona física.
 */
public enum SignerCharacter {
  PROPIETARIO,
  COPROPIETARIO,
  APODERADO,
  REPRESENTANTE_LEGAL
}
