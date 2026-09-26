"use client";

import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Card, CardHeader } from "@/components/ui/Card";
import { useToast } from "@/components/ui/Toast";
import { ApiError } from "@/lib/api/client";
import { addClosingNote, completeClosingTask, getClosingCase, getClosingNotes, getClosingTasks } from "@/lib/api/closing";
import { getComplianceChecklist } from "@/lib/api/compliance";
import type { ClosingCaseResponse, ClosingNoteResponse, ClosingTaskResponse, ComplianceChecklistResponse } from "@/lib/api/types";
import { errorText } from "@/lib/errors";
import { closingStatusLabels, complianceLabels, formatDate, formatDateTime, label } from "@/lib/labels";
import { useCan } from "@/lib/permissions";
import { CheckCircle2, Circle } from "lucide-react";
import { useCallback, useEffect, useState } from "react";
import type { ExpedienteContext } from "./page";

export function FollowUpTab({ expediente }: ExpedienteContext) {
  const { showToast } = useToast();
  const can = useCan();
  const [compliance, setCompliance] = useState<ComplianceChecklistResponse | null>(null);
  const [closing, setClosing] = useState<ClosingCaseResponse | "not_started" | null>(null);
  const [tasks, setTasks] = useState<ClosingTaskResponse[]>([]);
  const [notes, setNotes] = useState<ClosingNoteResponse[]>([]);
  const [newNote, setNewNote] = useState("");

  const load = useCallback(() => {
    getComplianceChecklist(expediente.id).then(setCompliance).catch(() => undefined);
    getClosingCase(expediente.id)
      .then((c) => {
        setClosing(c);
        getClosingTasks(expediente.id).then(setTasks).catch(() => undefined);
        getClosingNotes(expediente.id).then(setNotes).catch(() => undefined);
      })
      .catch((err) => {
        if (err instanceof ApiError && err.status === 404) setClosing("not_started");
      });
  }, [expediente.id]);

  useEffect(() => {
    load();
  }, [load]);

  return (
    <div className="flex flex-col gap-6">
      <Card>
        <CardHeader title="Seguimiento posterior a la firma" description="Se abre automáticamente cuando el contrato queda firmado por todas las partes." />
        {closing === null ? <p className="text-sm text-muted">Cargando…</p> : null}
        {closing === "not_started" ? <p className="text-sm text-muted">Todavía no inicia: el contrato no se ha firmado por todas las partes.</p> : null}
        {closing && closing !== "not_started" ? (
          <>
            <Badge tone={closing.status === "COMPLETED" ? "success" : "info"} className="mb-3">
              {label(closingStatusLabels, closing.status)}
            </Badge>
            <ul className="flex flex-col gap-2">
              {tasks.map((t) => (
                <li key={t.id} className="flex flex-wrap items-center justify-between gap-2 rounded-lg border border-border px-3 py-2 text-sm">
                  <div className="flex items-start gap-2">
                    {t.done ? (
                      <CheckCircle2 className="mt-0.5 h-4 w-4 shrink-0 text-success-text" aria-hidden />
                    ) : (
                      <Circle className="mt-0.5 h-4 w-4 shrink-0 text-muted" aria-hidden />
                    )}
                    <div>
                      <p className="text-obsessed">{t.title}</p>
                      <p className="text-xs text-muted">
                        {t.done ? `Hecho ${formatDateTime(t.doneAt)}${t.doneNote ? ` — ${t.doneNote}` : ""}` : t.dueDate ? `Fecha límite: ${formatDate(t.dueDate)}` : "Pendiente"}
                      </p>
                    </div>
                  </div>
                  {!t.done && can("EXPEDIENT_EDIT") ? (
                    <Button
                      size="sm"
                      variant="secondary"
                      onClick={async () => {
                        try {
                          await completeClosingTask(expediente.id, t.id);
                          showToast("Tarea marcada como hecha.");
                          load();
                        } catch (err) {
                          showToast(errorText(err));
                        }
                      }}
                    >
                      Marcar como hecha
                    </Button>
                  ) : null}
                </li>
              ))}
            </ul>
            <p className="mb-2 mt-5 text-sm font-medium text-obsessed">Notas</p>
            <ul className="mb-3 flex flex-col gap-2">
              {notes.map((n) => (
                <li key={n.id} className="rounded-lg border border-border px-3 py-2 text-sm">
                  {n.note}
                  <p className="mt-1 text-xs text-muted">{formatDateTime(n.createdAt)}</p>
                </li>
              ))}
            </ul>
            {can("EXPEDIENT_EDIT") ? (
              <div className="flex gap-2">
                <input
                  value={newNote}
                  onChange={(e) => setNewNote(e.target.value)}
                  placeholder="Agregar nota…"
                  className="flex-1 rounded-lg border border-border bg-card px-3 py-2 text-sm outline-none focus:border-gold"
                />
                <Button
                  size="sm"
                  disabled={!newNote.trim()}
                  onClick={async () => {
                    try {
                      await addClosingNote(expediente.id, newNote.trim());
                      setNewNote("");
                      load();
                    } catch (err) {
                      showToast(errorText(err));
                    }
                  }}
                >
                  Agregar
                </Button>
              </div>
            ) : null}
          </>
        ) : null}
      </Card>

      <Card>
        <CardHeader title="Control documental de cumplimiento" description="Verificación interna; no constituye una garantía legal frente al cliente." />
        {!compliance ? (
          <p className="text-sm text-muted">Cargando…</p>
        ) : (
          <div className="flex flex-col gap-2">
            <Badge tone={compliance.allPassed ? "success" : "warning"} className="mb-2 w-fit">
              {compliance.allPassed ? "Todo en orden" : "Hay puntos pendientes"}
            </Badge>
            {compliance.items.map((item) => (
              <div key={item.code} className="flex items-center justify-between gap-2 rounded-lg border border-border px-3 py-2 text-sm">
                <div>
                  <p className="font-medium text-obsessed">{label(complianceLabels, item.code)}</p>
                  <p className="text-xs text-muted">{item.detail}</p>
                </div>
                <Badge tone={item.passed ? "success" : "warning"}>{item.passed ? "Cumplido" : "Pendiente"}</Badge>
              </div>
            ))}
          </div>
        )}
      </Card>
    </div>
  );
}
