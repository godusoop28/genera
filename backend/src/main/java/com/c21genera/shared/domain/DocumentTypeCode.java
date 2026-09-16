package com.c21genera.shared.domain;

/**
 * Catálogo de tipos de documento (ver AGENTS §23). Es vocabulario
 * compartido: expedientes lo usa para calcular requisitos, documents lo usa
 * para tipar cada Document, extraction lo usa para elegir el esquema de
 * extracción. No todos son obligatorios por defecto: son tipos soportables.
 */
public enum DocumentTypeCode {
  INE,
  PASSPORT,
  CURP,
  TAX_STATUS_CERTIFICATE,
  DEED,
  PROOF_OF_ADDRESS,
  LIEN_CERTIFICATE,
  PROPERTY_TAX,
  POWER_OF_ATTORNEY,
  CONDOMINIUM_REGIME,
  WATER_RECEIPT,
  ELECTRICITY_RECEIPT,
  CADASTRAL_PLAN,
  APPRAISAL,
  LAND_USE,
  SUCCESSION,
  ADJUDICATION,
  WILL,
  MORTGAGE,
  LEASE_AGREEMENT,
  OTHER
}
