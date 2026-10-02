"use client";

import { apiClient } from "@/lib/api/client";
import type { DraftResponse } from "@/lib/api/types";
import { DraftSync, type DraftLocalStore, type DraftSaveState, type DraftTransport } from "@/lib/draft-sync";
import { useCallback, useEffect, useRef, useState } from "react";

// Transportes: el staff guarda bajo su usuario; el cliente, bajo su liga.

export function internalDraftTransport(formKey: string): DraftTransport {
  const path = `/internal/drafts/${encodeURIComponent(formKey)}`;
  return {
    load: async () => (await apiClient.get<DraftResponse | undefined>(path))?.payload ?? null,
    save: async (payload) => {
      await apiClient.put<DraftResponse>(path, { payload });
    },
    remove: async () => {
      await apiClient.delete<void>(path);
    },
  };
}

export function publicDraftTransport(token: string, formKey: string): DraftTransport {
  const path = `/public/expedientes/${token}/drafts/${encodeURIComponent(formKey)}`;
  return {
    load: async () => (await apiClient.get<DraftResponse | undefined>(path))?.payload ?? null,
    save: async (payload) => {
      await apiClient.put<DraftResponse>(path, { payload });
    },
    remove: async () => {
      await apiClient.delete<void>(path);
    },
  };
}

export function sessionDraftStore(key: string): DraftLocalStore {
  const fullKey = `genera-autosave:${key}`;
  return {
    read: () => window.sessionStorage.getItem(fullKey),
    write: (value) => window.sessionStorage.setItem(fullKey, value),
    clear: () => window.sessionStorage.removeItem(fullKey),
  };
}

export interface ServerDraft<T> {
  /** undefined mientras se consulta; null si no había borrador. */
  restored: T | null | undefined;
  status: DraftSaveState;
  lastSavedAt: Date | null;
  /** Registrar el valor actual del formulario (se autoguarda). */
  update: (value: T) => void;
  /** Borrar el borrador (tras guardar formalmente). */
  clear: () => Promise<void>;
}

/**
 * Autoguardado en el servidor para un formulario. `key` identifica el
 * borrador (y su respaldo local); null desactiva el autoguardado.
 */
export function useServerDraft<T>(key: string | null, makeTransport: () => DraftTransport): ServerDraft<T> {
  // El valor recuperado se asocia a su key: si la key cambia, se vuelve a "cargando".
  const [result, setResult] = useState<{ key: string | null; value: T | null }>({ key: null, value: null });
  const restored = key === null ? null : result.key === key ? result.value : undefined;
  const [status, setStatus] = useState<DraftSaveState>("idle");
  const [lastSavedAt, setLastSavedAt] = useState<Date | null>(null);
  const syncRef = useRef<DraftSync | null>(null);
  const transportRef = useRef(makeTransport);

  useEffect(() => {
    if (!key) return;
    let active = true;
    const sync = new DraftSync(transportRef.current(), {
      local: sessionDraftStore(key),
      onStateChange: (state, savedAt) => {
        if (!active) return;
        setStatus(state);
        setLastSavedAt(savedAt);
      },
    });
    syncRef.current = sync;
    sync.restore().then((payload) => {
      if (!active) return;
      let value: T | null = null;
      try {
        value = payload ? (JSON.parse(payload) as T) : null;
      } catch {
        value = null;
      }
      setResult({ key, value });
    });
    // Al salir de la página se intenta subir lo pendiente.
    const onHide = () => void sync.flush();
    window.addEventListener("pagehide", onHide);
    return () => {
      active = false;
      window.removeEventListener("pagehide", onHide);
      void sync.flush().finally(() => sync.dispose());
    };
  }, [key]);

  const update = useCallback((value: T) => {
    syncRef.current?.schedule(JSON.stringify(value));
  }, []);

  const clear = useCallback(async () => {
    await syncRef.current?.clear();
  }, []);

  return { restored, status, lastSavedAt, update, clear };
}
