package com.c21genera.receipts.domain;

import java.time.Instant;

/**
 * Un archivo efectivamente recibido en el expediente (cada versión de cada
 * documento, incluidos los adicionales). current: es la versión vigente.
 */
public record ReceivedItem(String name, String participant, int versionNumber, Instant receivedAt, boolean current, String status) {}
