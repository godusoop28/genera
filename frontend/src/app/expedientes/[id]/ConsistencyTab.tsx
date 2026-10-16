"use client";

import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Card, CardHeader } from "@/components/ui/Card";
import { Modal } from "@/components/ui/Modal";
import { useToast } from "@/components/ui/Toast";
import { getCondominiumAreas, getDataConflicts, resolveConflict, runConsistencyCheck } from "@/lib/api/extraction";
import type { CondominiumAreaComparison, DataConflictResponse } from "@/lib/api/types";
import { errorText } from "@/lib/errors";
import { formatDateTime } from "@/lib/labels";
import { useCan } from "@/lib/permissions";
import { Loader2, Lock, RefreshCw, ScanSearch, ShieldCheck } from "lucide-react";
import { useCallback, useEffect, useState } from "react";
import type { ExpedienteContext } from "./page";

export function ConsistencyTab({ expediente, reload }: ExpedienteContext) {
  const { showToast } = useToast();
  const can = useCan();
  const [conflicts, setConflicts] = useState<DataConflictResponse[] | null>(null);
  const [areas, setAreas] = useState<CondominiumAreaComparison[]>([]);
  const [running, setRunning] = useState(false);
  const [resolving, setResolving] = useState<DataConflictResponse | null>(null);
  // Control de Cambios #17: solo un directivo o administrador autoriza, con su contraseña.
  const canAuthorize = can("INCONSISTENCY_AUTHORIZE");

  const load = useCallback(() => {
    getDataConflicts(expediente.id)
      .then(setConflicts)
      .catch((err) => showToast(errorText(err)));
    getCondominiumAreas(expediente.id)
      .then(setAreas)
      .catch(() => setAreas([]));
  }, [expediente.id, showToast]);

  useEffect(() => {
    load();
  }, [load]);

  const severityOrder = { CRITICAL: 0, WARNING: 1, INFO: 2 } as const;
  const open = (conflicts ?? [])
    .filter((c) => !c.resolved)
    .sort((a, b) => severityOrder[a.severity ?? "WARNING"] - severityOrder[b.severity ?? "WARNING"]);
  const criticalCount = open.filter((c) => c.severity === "CRITICAL").length;
  const authorized = (conflicts ?? []).filter((c) => c.resolved && !c.resolvedAutomatically);
  const closedAutomatically = (conflicts ?? []).filter((c) => c.resolved && c.resolvedAutomatically);

  return (
    <div className="flex flex-col gap-6">
      <Card>
        <CardHeader
          title="Consistencia entre documentos"
          description="Compara nombres, CURP, RFC, propietarios, domicilio, clave catastral, folio real y superficies entre escritura, predial, régimen de condominio, plano, identificaciones y los datos capturados. Cada diferencia es una POSIBLE inconsistencia: queda pendiente de autorización de un directivo; solo las críticas (evidencia fuerte de otra persona u otro inmueble) impiden enviar el contrato a firma."
        />
        <div className="mb-4 flex flex-wrap items-center gap-3">
          <Badge tone={open.length === 0 ? "success" : criticalCount > 0 ? "danger" : "warning"}>
            {open.length === 0
              ? "Sin diferencias pendientes"
              : criticalCount > 0
                ? `${criticalCount} crítica(s) · ${open.length} pendiente(s) de autorización`
                : `${open.length} pendiente(s) de autorización, ninguna crítica`}
          </Badge>
          <Button
            variant="secondary"
            size="sm"
            disabled={running}
            onClick={async () => {
              setRunning(true);
              try {
                setConflicts(await runConsistencyCheck(expediente.id));
                setAreas(await getCondominiumAreas(expediente.id));
                showToast("Comparación actualizada.");
              } catch (err) {
                showToast(errorText(err));
              } finally {
                setRunning(false);
              }
            }}
          >
            {running ? <RefreshCw className="h-4 w-4 animate-spin" aria-hidden /> : <ScanSearch className="h-4 w-4" aria-hidden />}
            Volver a comparar
          </Button>
        </div>
        <p className="mb-3 text-xs text-muted">
          La comparación usa los datos que se leen automáticamente de cada documento. Si la lectura automática no está disponible, revisa los
          documentos visualmente.
        </p>
        {!canAuthorize && open.length > 0 ? (
          <p className="mb-3 flex items-center gap-2 rounded-lg bg-app-bg px-3 py-2 text-xs text-muted">
            <Lock className="h-3.5 w-3.5 shrink-0" aria-hidden /> Autorizar una inconsistencia requiere a un directivo o administrador de C21 Genera (con su
            contraseña).
          </p>
        ) : null}
        {conflicts === null ? <p className="text-sm text-muted">Cargando…</p> : null}
        <ul className="flex flex-col gap-2">
          {open.map((c) => (
            <li
              key={c.id}
              className={`rounded-lg border px-3 py-2 text-sm ${
                c.severity === "CRITICAL"
                  ? "border-danger-text/30 bg-danger-bg"
                  : c.severity === "INFO"
                    ? "border-border bg-app-bg"
                    : "border-warning-text/30 bg-warning-bg"
              }`}
            >
              <div className="mb-1 flex flex-wrap gap-1.5">
                <Badge tone={c.severity === "CRITICAL" ? "danger" : c.severity === "INFO" ? "neutral" : "warning"}>
                  {c.severity === "CRITICAL" ? "Crítica: bloquea la firma" : c.severity === "INFO" ? "Probablemente igual" : "Revisar"}
                </Badge>
                <Badge tone="neutral">Pendiente de autorización</Badge>
              </div>
              <p className={c.severity === "CRITICAL" ? "text-danger-text" : c.severity === "INFO" ? "text-muted" : "text-warning-text"}>
                {c.description}
              </p>
              <div className="mt-2 flex items-center justify-between gap-2">
                <span className="text-xs text-muted">Detectada {formatDateTime(c.detectedAt)}</span>
                {canAuthorize ? (
                  <Button size="sm" variant="secondary" onClick={() => setResolving(c)}>
                    <ShieldCheck className="h-4 w-4" aria-hidden /> Autorizar inconsistencia
                  </Button>
                ) : (
                  <Button size="sm" variant="secondary" disabled title="Requiere autorización de un directivo">
                    <Lock className="h-4 w-4" aria-hidden /> Requiere autorización de un directivo
                  </Button>
                )}
              </div>
            </li>
          ))}
        </ul>
      </Card>

      {areas.length > 0 ? <CondominiumAreasCard rows={areas} /> : null}

      {authorized.length > 0 ? (
        <Card>
          <CardHeader
            title="Bitácora de inconsistencias autorizadas"
            description="Quién autorizó, cuándo y qué inconsistencia. Sirve como evidencia ante cualquier aclaración."
          />
          <ul className="flex flex-col gap-2 text-sm">
            {authorized.map((c) => (
              <li key={c.id} className="rounded-lg border border-border px-3 py-2">
                <p className="text-obsessed">{c.description}</p>
                <p className="mt-1 text-xs text-muted">
                  <span className="font-medium text-success-text">Autorizada{c.resolvedByName ? ` por ${c.resolvedByName}` : ""}</span> ·{" "}
                  {formatDateTime(c.resolvedAt)}
                  {c.resolutionNote ? ` · Motivo: ${c.resolutionNote}` : ""}
                </p>
              </li>
            ))}
          </ul>
        </Card>
      ) : null}

      {closedAutomatically.length > 0 ? (
        <Card>
          <CardHeader title="Diferencias que ya coinciden" />
          <ul className="flex flex-col gap-2 text-sm">
            {closedAutomatically.map((c) => (
              <li key={c.id} className="rounded-lg border border-border px-3 py-2">
                <p className="text-obsessed">{c.description}</p>
                <p className="mt-1 text-xs text-muted">Se cerró sola: los datos ya coinciden · {formatDateTime(c.resolvedAt)}</p>
              </li>
            ))}
          </ul>
        </Card>
      ) : null}

      {resolving ? (
        <AuthorizeModal
          conflict={resolving}
          onCancel={() => setResolving(null)}
          onConfirm={async (note, password) => {
            await resolveConflict(resolving.id, note, password);
            showToast("Inconsistencia autorizada: queda registrada con tu nombre, fecha y hora.");
            setResolving(null);
            load();
            await reload();
          }}
        />
      ) : null}
    </div>
  );
}

/** Control de Cambios #15: m² de terreno e indivisos del Régimen de condominio contra Escritura y Predial. */
function CondominiumAreasCard({ rows }: { rows: CondominiumAreaComparison[] }) {
  const cell = (v: string | null) => (v ? v : <span className="text-muted">sin dato</span>);
  return (
    <Card>
      <CardHeader
        title="Cotejo del régimen de condominio"
        description="Superficie de terreno y de indivisos que reporta el Régimen de condominio, comparada con la Escritura y el Predial."
      />
      <div className="overflow-x-auto">
        <table className="w-full min-w-[560px] text-left text-sm">
          <thead>
            <tr className="border-b border-border text-xs text-muted">
              <th className="py-2 pr-3 font-medium">Dato</th>
              <th className="py-2 pr-3 font-medium">Régimen de condominio</th>
              <th className="py-2 pr-3 font-medium">Escritura</th>
              <th className="py-2 pr-3 font-medium">Predial</th>
              <th className="py-2 font-medium">¿Coincide?</th>
            </tr>
          </thead>
          <tbody>
            {rows.map((r) => (
              <tr key={r.key} className="border-b border-border last:border-0">
                <td className="py-2 pr-3 text-obsessed">{r.label}</td>
                <td className="py-2 pr-3 tabular-nums">{cell(r.condominiumRegime)}</td>
                <td className="py-2 pr-3 tabular-nums">{cell(r.deed)}</td>
                <td className="py-2 pr-3 tabular-nums">{cell(r.propertyTax)}</td>
                <td className="py-2">
                  {r.consistent ? <Badge tone="success">✅ Consistente</Badge> : <Badge tone="warning">⚠️ Difiere</Badge>}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <p className="mt-2 text-xs text-muted">
        Una diferencia no bloquea el expediente: aparece arriba como inconsistencia pendiente de autorización. &quot;Sin dato&quot; significa que no se
        leyó del documento o que todavía no se carga.
      </p>
    </Card>
  );
}

function AuthorizeModal({
  conflict,
  onCancel,
  onConfirm,
}: {
  conflict: DataConflictResponse;
  onCancel: () => void;
  onConfirm: (note: string, password: string) => Promise<void>;
}) {
  const [note, setNote] = useState("");
  const [password, setPassword] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const valid = note.trim().length >= 10 && password.length > 0;
  const confirm = async () => {
    setBusy(true);
    setError(null);
    try {
      await onConfirm(note.trim(), password);
    } catch (err) {
      setError(errorText(err));
      setPassword("");
    } finally {
      setBusy(false);
    }
  };
  return (
    <Modal
      open
      onClose={onCancel}
      title="Autorizar inconsistencia"
      footer={
        <>
          <Button variant="ghost" onClick={onCancel} disabled={busy}>
            Cancelar
          </Button>
          <Button onClick={confirm} disabled={!valid || busy}>
            {busy ? <Loader2 className="h-4 w-4 animate-spin" aria-hidden /> : <ShieldCheck className="h-4 w-4" aria-hidden />} Autorizar
          </Button>
        </>
      }
    >
      <form
        className="flex flex-col gap-4 text-sm"
        onSubmit={(e) => {
          e.preventDefault();
          if (valid) void confirm();
        }}
      >
        <p className="rounded-lg bg-app-bg px-3 py-2 text-obsessed">{conflict.description}</p>
        <p className="text-muted">
          Esta decisión tiene implicaciones legales: quedará registrada como &quot;Autorizada por&quot; tu nombre, con fecha y hora, en la bitácora del
          expediente.
        </p>
        <label className="flex flex-col gap-1.5">
          <span className="font-medium text-obsessed">¿Por qué se autoriza?</span>
          <textarea
            value={note}
            onChange={(e) => setNote(e.target.value)}
            rows={3}
            placeholder="Ej. El predial está a nombre de uno de los copropietarios."
            className="rounded-lg border border-border bg-card px-3 py-2 text-obsessed outline-none focus:border-gold"
          />
        </label>
        <label className="flex flex-col gap-1.5">
          <span className="font-medium text-obsessed">Tu contraseña</span>
          <input
            type="password"
            autoComplete="current-password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            className="min-h-10 rounded-lg border border-border bg-card px-3 text-obsessed"
          />
        </label>
        {error ? <p className="text-danger-text">{error}</p> : null}
      </form>
    </Modal>
  );
}
