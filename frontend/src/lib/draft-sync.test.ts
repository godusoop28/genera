import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { DraftSync, type DraftLocalStore, type DraftSaveState, type DraftTransport } from "./draft-sync";

/** Servidor falso en memoria; se puede "tirar" para simular una caída de red. */
function fakeServer() {
  let stored: string | null = null;
  let down = false;
  const transport: DraftTransport = {
    load: vi.fn(async () => {
      if (down) throw new TypeError("network");
      return stored;
    }),
    save: vi.fn(async (payload: string) => {
      if (down) throw new TypeError("network");
      stored = payload;
    }),
    remove: vi.fn(async () => {
      stored = null;
    }),
  };
  return {
    transport,
    get stored() {
      return stored;
    },
    setDown(value: boolean) {
      down = value;
    },
  };
}

/** sessionStorage falso: sobrevive entre "recargas" (instancias nuevas de DraftSync). */
function fakeLocal(): DraftLocalStore & { value: string | null } {
  const store = {
    value: null as string | null,
    read: () => store.value,
    write: (v: string) => {
      store.value = v;
    },
    clear: () => {
      store.value = null;
    },
  };
  return store;
}

describe("DraftSync (autoguardado de formularios)", () => {
  beforeEach(() => vi.useFakeTimers());
  afterEach(() => vi.useRealTimers());

  it("guarda solo tras la pausa de escritura y al recargar reconstruye el estado desde el servidor", async () => {
    const server = fakeServer();
    const states: DraftSaveState[] = [];
    const sync = new DraftSync(server.transport, { debounceMs: 500, local: fakeLocal(), onStateChange: (s) => states.push(s) });

    sync.schedule(JSON.stringify({ email: "a@b.mx" }));
    sync.schedule(JSON.stringify({ email: "ana@correo.mx" }));
    expect(server.transport.save).not.toHaveBeenCalled();

    await vi.advanceTimersByTimeAsync(600);
    expect(server.transport.save).toHaveBeenCalledTimes(1);
    expect(server.stored).toBe(JSON.stringify({ email: "ana@correo.mx" }));
    expect(states.at(-1)).toBe("saved");

    // "Refresh": instancia nueva, respaldo local vacío (otro equipo): todo sale del servidor.
    const afterRefresh = new DraftSync(server.transport, { local: fakeLocal() });
    expect(await afterRefresh.restore()).toBe(JSON.stringify({ email: "ana@correo.mx" }));
  });

  it("un fallo temporal de la API no borra lo capturado: avisa, reintenta y termina guardando", async () => {
    const server = fakeServer();
    const local = fakeLocal();
    const states: DraftSaveState[] = [];
    const sync = new DraftSync(server.transport, {
      debounceMs: 100,
      retryDelaysMs: [1000],
      local,
      onStateChange: (s) => states.push(s),
    });

    server.setDown(true);
    sync.schedule(JSON.stringify({ phone: "777 123 4567" }));
    await vi.advanceTimersByTimeAsync(150);

    expect(states.at(-1)).toBe("error");
    expect(sync.hasUnsavedChanges).toBe(true);
    // Lo escrito sigue respaldado localmente mientras el servidor no confirma.
    expect(JSON.parse(local.value!)).toEqual({ payload: JSON.stringify({ phone: "777 123 4567" }), synced: false });

    server.setDown(false);
    await vi.advanceTimersByTimeAsync(1100);
    expect(server.stored).toBe(JSON.stringify({ phone: "777 123 4567" }));
    expect(states.at(-1)).toBe("saved");
    expect(sync.hasUnsavedChanges).toBe(false);
  });

  it("si se recarga antes de que el servidor confirme, gana el cambio local y se vuelve a subir", async () => {
    const server = fakeServer();
    const local = fakeLocal();
    server.setDown(true);
    const before = new DraftSync(server.transport, { debounceMs: 100, retryDelaysMs: [60_000], local });
    before.schedule(JSON.stringify({ step: 3, email: "sin-guardar@correo.mx" }));
    await vi.advanceTimersByTimeAsync(150);
    before.dispose();

    // Recarga con el servidor de nuevo arriba: no se pierde lo que no alcanzó a guardarse.
    server.setDown(false);
    const after = new DraftSync(server.transport, { debounceMs: 100, local });
    expect(await after.restore()).toBe(JSON.stringify({ step: 3, email: "sin-guardar@correo.mx" }));
    await vi.advanceTimersByTimeAsync(150);
    expect(server.stored).toBe(JSON.stringify({ step: 3, email: "sin-guardar@correo.mx" }));
  });

  it("si el servidor no responde al abrir, se usa el último respaldo local", async () => {
    const server = fakeServer();
    const local = fakeLocal();
    local.write(JSON.stringify({ payload: JSON.stringify({ name: "Juan" }), synced: true }));
    server.setDown(true);

    const sync = new DraftSync(server.transport, { local });
    expect(await sync.restore()).toBe(JSON.stringify({ name: "Juan" }));
  });

  it("clear borra el borrador local y en el servidor (tras guardar formalmente)", async () => {
    const server = fakeServer();
    const local = fakeLocal();
    const sync = new DraftSync(server.transport, { debounceMs: 10, local });
    sync.schedule("{}");
    await vi.advanceTimersByTimeAsync(20);

    await sync.clear();
    expect(server.stored).toBeNull();
    expect(local.value).toBeNull();
  });
});
