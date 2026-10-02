// Autoguardado de formularios en el servidor, sin depender de React.
//
// Reglas (revisión del cliente 01/10: "se pierde el progreso al refrescar"):
// - Cada cambio se guarda solo, unos instantes después de dejar de escribir.
// - Mientras el servidor no confirme, el valor se respalda localmente: si se
//   recarga la página antes, se recupera lo último escrito.
// - Si el guardado falla (red, servidor dormido), NUNCA se borra nada: se
//   avisa "No se pudo guardar" y se reintenta solo, con espera creciente.
// - Al reabrir, se reconstruye desde el servidor; si había un cambio local que
//   no alcanzó a guardarse, gana ese cambio y se vuelve a intentar subirlo.

export type DraftSaveState = "idle" | "saving" | "saved" | "error";

export interface DraftTransport {
  /** Payload guardado en el servidor, o null si no hay borrador. */
  load(): Promise<string | null>;
  save(payload: string): Promise<void>;
  remove(): Promise<void>;
}

/** Respaldo local (sessionStorage en el navegador). */
export interface DraftLocalStore {
  read(): string | null;
  write(value: string): void;
  clear(): void;
}

export interface DraftSyncOptions {
  debounceMs?: number;
  /** Esperas entre reintentos; la última se repite indefinidamente. */
  retryDelaysMs?: number[];
  onStateChange?: (state: DraftSaveState, lastSavedAt: Date | null) => void;
  local?: DraftLocalStore;
  /** Reemplazable en pruebas. */
  timers?: { setTimeout: typeof setTimeout; clearTimeout: typeof clearTimeout };
}

interface LocalEnvelope {
  payload: string;
  /** El servidor ya confirmó este valor. */
  synced: boolean;
}

export class DraftSync {
  private readonly debounceMs: number;
  private readonly retryDelaysMs: number[];
  private readonly timers: { setTimeout: typeof setTimeout; clearTimeout: typeof clearTimeout };
  private pending: string | null = null;
  private lastSynced: string | null = null;
  private timer: ReturnType<typeof setTimeout> | null = null;
  private attempt = 0;
  private inFlight: Promise<void> | null = null;
  private disposed = false;
  private state: DraftSaveState = "idle";
  private lastSavedAt: Date | null = null;

  constructor(
    private readonly transport: DraftTransport,
    private readonly options: DraftSyncOptions = {},
  ) {
    this.debounceMs = options.debounceMs ?? 800;
    this.retryDelaysMs = options.retryDelaysMs ?? [2000, 5000, 10000, 20000];
    this.timers = options.timers ?? { setTimeout: globalThis.setTimeout.bind(globalThis), clearTimeout: globalThis.clearTimeout.bind(globalThis) };
  }

  get saveState(): DraftSaveState {
    return this.state;
  }

  /** Hay un cambio que el servidor todavía no confirmó. */
  get hasUnsavedChanges(): boolean {
    return this.pending !== null;
  }

  /**
   * Reconstruye el estado al abrir: el cambio local sin guardar (si existe)
   * gana sobre el servidor y se reprograma su subida; si no, lo del servidor;
   * si el servidor no responde, el último respaldo local.
   */
  async restore(): Promise<string | null> {
    const local = this.readLocal();
    if (local && !local.synced) {
      this.schedule(local.payload);
      return local.payload;
    }
    try {
      const remote = await this.transport.load();
      this.lastSynced = remote;
      if (remote !== null) this.writeLocal({ payload: remote, synced: true });
      return remote;
    } catch {
      return local?.payload ?? null;
    }
  }

  /** Registra el valor actual del formulario; se sube tras la pausa de escritura. */
  schedule(payload: string): void {
    if (this.disposed) return;
    if (payload === this.lastSynced && this.pending === null) return;
    this.pending = payload;
    this.writeLocal({ payload, synced: false });
    this.attempt = 0;
    this.restartTimer(this.debounceMs);
  }

  /** Sube ya lo pendiente (p. ej. antes de navegar). */
  async flush(): Promise<void> {
    this.clearTimer();
    await this.push();
  }

  /** El formulario se guardó formalmente (o se canceló): se borra el borrador. */
  async clear(): Promise<void> {
    this.clearTimer();
    this.pending = null;
    this.lastSynced = null;
    this.options.local?.clear();
    try {
      await this.transport.remove();
    } catch {
      // Un borrador huérfano en el servidor no hace daño: se reemplaza en el siguiente uso.
    }
    this.setState("idle");
  }

  dispose(): void {
    this.disposed = true;
    this.clearTimer();
  }

  private async push(): Promise<void> {
    if (this.inFlight) {
      await this.inFlight;
    }
    const payload = this.pending;
    if (payload === null || this.disposed) return;
    this.setState("saving");
    this.inFlight = this.transport
      .save(payload)
      .then(() => {
        this.lastSynced = payload;
        // Si mientras se guardaba hubo otro cambio, ese sigue pendiente.
        if (this.pending === payload) {
          this.pending = null;
          this.writeLocal({ payload, synced: true });
        }
        this.attempt = 0;
        this.lastSavedAt = new Date();
        this.setState(this.pending === null ? "saved" : "saving");
        if (this.pending !== null) this.restartTimer(0);
      })
      .catch(() => {
        // Nunca se descarta lo escrito: queda pendiente y respaldado localmente.
        this.setState("error");
        const delay = this.retryDelaysMs[Math.min(this.attempt, this.retryDelaysMs.length - 1)];
        this.attempt++;
        this.restartTimer(delay);
      })
      .finally(() => {
        this.inFlight = null;
      });
    await this.inFlight;
  }

  private restartTimer(ms: number) {
    this.clearTimer();
    if (this.disposed) return;
    this.timer = this.timers.setTimeout(() => {
      this.timer = null;
      void this.push();
    }, ms);
  }

  private clearTimer() {
    if (this.timer !== null) {
      this.timers.clearTimeout(this.timer);
      this.timer = null;
    }
  }

  private setState(state: DraftSaveState) {
    this.state = state;
    this.options.onStateChange?.(state, this.lastSavedAt);
  }

  private readLocal(): LocalEnvelope | null {
    try {
      const raw = this.options.local?.read();
      return raw ? (JSON.parse(raw) as LocalEnvelope) : null;
    } catch {
      return null;
    }
  }

  private writeLocal(envelope: LocalEnvelope) {
    try {
      this.options.local?.write(JSON.stringify(envelope));
    } catch {
      // Sin almacenamiento local (modo privado): el guardado en servidor sigue funcionando.
    }
  }
}
