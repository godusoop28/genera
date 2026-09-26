package com.c21genera.expedientes.domain;

/**
 * Sin máximo de participantes. OWNER/CO_OWNER son los titulares del inmueble
 * (en persona moral, OWNER es la sociedad). LEGAL_REPRESENTATIVE representa
 * a una persona moral; ATTORNEY es el apoderado de una persona física.
 */
public enum ParticipantRole {
  OWNER,
  CO_OWNER,
  ATTORNEY,
  LEGAL_REPRESENTATIVE
}
