package com.c21genera.shared.pdf;

import java.util.concurrent.Semaphore;

/**
 * Un solo trabajo de imágenes grandes a la vez en todo el servidor: renderizar una página, prepararla
 * para la IA o procesar un archivo cargado. Cada uno puede ocupar decenas de MB del heap (~199 MB en
 * la instancia de 512 MB) y dos a la vez lo agotaban (29/10: el visor daba "Ocurrió un error
 * inesperado" mientras la IA leía el mismo documento). Lo que no ocupa memoria, como esperar la
 * respuesta de la IA, va fuera de esta sección para que varias lecturas avancen en paralelo.
 */
public final class HeavyImageWork {

  private HeavyImageWork() {}

  /** Justo: quien llegó primero entra primero (el visor no se queda esperando detrás de la IA). */
  private static final Semaphore PERMIT = new Semaphore(1, true);

  @FunctionalInterface
  public interface Work<T> {
    T run() throws Exception;
  }

  public static <T> T exclusively(Work<T> work) throws Exception {
    PERMIT.acquire();
    try {
      return work.run();
    } finally {
      PERMIT.release();
    }
  }
}
