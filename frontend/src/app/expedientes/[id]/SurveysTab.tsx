"use client";

import { GenerateSurveyModal, SurveyHistoryList, npsTone } from "@/components/surveys/SurveyUi";
import { Button } from "@/components/ui/Button";
import { Card, CardHeader } from "@/components/ui/Card";
import { StateMessage } from "@/components/ui/StateMessage";
import { getExpedienteSurveys, type SurveyHistory } from "@/lib/api/surveys";
import { Send } from "lucide-react";
import { useCallback, useEffect, useState } from "react";
import type { ExpedienteContext } from "./page";

/**
 * Control de Cambios #28, parte 5: historial de encuestas NPS del expediente
 * (punto de contacto, calificación, comentario, fecha y canal) y activación
 * manual para interacciones presenciales.
 */
export function SurveysTab({ expediente }: ExpedienteContext) {
  const [history, setHistory] = useState<SurveyHistory | null>(null);
  const [error, setError] = useState(false);
  const [open, setOpen] = useState(false);

  const load = useCallback(() => {
    getExpedienteSurveys(expediente.id)
      .then((h) => {
        setHistory(h);
        setError(false);
      })
      .catch(() => setError(true));
  }, [expediente.id]);

  useEffect(() => {
    load();
  }, [load]);

  const answered = history?.surveys.filter((s) => s.score !== null) ?? [];
  const promoters = answered.filter((s) => s.category === "PROMOTER").length;
  const detractors = answered.filter((s) => s.category === "DETRACTOR").length;
  const nps = answered.length ? Math.round(((promoters - detractors) * 1000) / answered.length) / 10 : null;

  return (
    <div className="space-y-5">
      <Card>
        <CardHeader
          title="Satisfacción del cliente (NPS)"
          description="Encuestas automáticas en cada etapa y las que genera el asesor en interacciones presenciales."
          action={
            <Button size="sm" onClick={() => setOpen(true)}>
              <Send className="h-4 w-4" aria-hidden /> Enviar encuesta
            </Button>
          }
        />
        {history?.canSeeScores && nps !== null ? (
          <div className="mb-4 flex flex-wrap items-baseline gap-x-6 gap-y-1 rounded-xl bg-app-bg px-4 py-3 text-sm">
            <span>
              NPS del cliente: <strong className={`text-lg font-semibold ${npsTone(nps)}`}>{nps}</strong>
            </span>
            <span className="text-muted">
              {answered.length} respuesta{answered.length === 1 ? "" : "s"} · 🟢 {promoters} · 🟡 {answered.length - promoters - detractors} · 🔴 {detractors}
            </span>
          </div>
        ) : null}
        {error ? (
          <StateMessage kind="error" title="No se pudo cargar" description="Intenta de nuevo en un momento." />
        ) : !history ? (
          <p className="text-sm text-muted">Cargando…</p>
        ) : (
          <SurveyHistoryList surveys={history.surveys} canSeeScores={history.canSeeScores} />
        )}
      </Card>
      <GenerateSurveyModal open={open} onClose={() => setOpen(false)} target={{ expedienteId: expediente.id }} onCreated={load} />
    </div>
  );
}
