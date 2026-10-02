// Borradores de formularios que sobreviven a recargar la página (no a cerrar
// la pestaña: sessionStorage, para no dejar datos personales guardados en el
// equipo). Todo acceso va en try/catch: en modo privado puede no existir.

const PREFIX = "genera-draft:";

export function readDraft<T>(key: string): T | null {
  try {
    const raw = window.sessionStorage.getItem(PREFIX + key);
    return raw ? (JSON.parse(raw) as T) : null;
  } catch {
    return null;
  }
}

export function writeDraft(key: string, value: unknown): void {
  try {
    window.sessionStorage.setItem(PREFIX + key, JSON.stringify(value));
  } catch {
    // Sin almacenamiento disponible: el formulario sigue funcionando, solo no se recupera al recargar.
  }
}

export function clearDraft(key: string): void {
  try {
    window.sessionStorage.removeItem(PREFIX + key);
  } catch {
    // Ignorado: ver writeDraft.
  }
}
