"use client";

import { Button } from "@/components/ui/Button";
import { Card, CardHeader } from "@/components/ui/Card";
import { useToast } from "@/components/ui/Toast";
import { getVisitInstructions, updateVisitInstructions, type VisitInstructionsResponse } from "@/lib/api/contracts";
import { errorText } from "@/lib/errors";
import { formatDateTime } from "@/lib/labels";
import { useCan } from "@/lib/permissions";
import { Loader2, Save } from "lucide-react";
import { useEffect, useState } from "react";

/**
 * Control de Cambios #21: el administrador actualiza el texto estándar de "Instrucciones para
 * visitas" sin liberar código. Los expedientes nuevos (o con el campo vacío) empiezan con él.
 */
export function VisitInstructionsCard() {
  const can = useCan();
  const { showToast } = useToast();
  const [current, setCurrent] = useState<VisitInstructionsResponse | null>(null);
  const [text, setText] = useState("");
  const [saving, setSaving] = useState(false);
  const allowed = can("SETTINGS_MANAGE");

  useEffect(() => {
    if (!allowed) return;
    getVisitInstructions()
      .then((v) => {
        setCurrent(v);
        setText(v.text);
      })
      .catch(() => undefined);
  }, [allowed]);

  if (!allowed) return null;

  return (
    <Card>
      <CardHeader
        title="Instrucciones para visitas (texto estándar)"
        description="Con este texto empieza el campo en cada expediente; el asesor lo puede ajustar. Así queda en el contrato."
      />
      {current === null ? (
        <p className="text-sm text-muted">Cargando…</p>
      ) : (
        <div className="flex flex-col gap-3">
          <textarea
            value={text}
            onChange={(e) => setText(e.target.value)}
            rows={6}
            maxLength={3000}
            className="w-full rounded-xl border border-border bg-card px-3.5 py-2.5 text-sm text-obsessed outline-none focus:border-gold"
          />
          <div className="flex flex-wrap items-center justify-between gap-2">
            <span className="text-xs text-muted">
              {current.updatedByName ? `Última actualización: ${current.updatedByName}, ${formatDateTime(current.updatedAt)}` : "Texto definido por C21 Genera"}
            </span>
            <Button
              size="sm"
              disabled={saving || text.trim().length < 20 || text.trim() === current.text}
              onClick={async () => {
                setSaving(true);
                try {
                  const saved = await updateVisitInstructions(text.trim());
                  setCurrent(saved);
                  setText(saved.text);
                  showToast("Texto estándar actualizado.");
                } catch (err) {
                  showToast(errorText(err));
                } finally {
                  setSaving(false);
                }
              }}
            >
              {saving ? <Loader2 className="h-4 w-4 animate-spin" aria-hidden /> : <Save className="h-4 w-4" aria-hidden />} Guardar
            </Button>
          </div>
        </div>
      )}
    </Card>
  );
}
