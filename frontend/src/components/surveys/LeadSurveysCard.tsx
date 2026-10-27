"use client";

import { GenerateSurveyModal, SurveyHistoryList } from "@/components/surveys/SurveyUi";
import { Button } from "@/components/ui/Button";
import { Card, CardHeader } from "@/components/ui/Card";
import type { LeadItem } from "@/lib/api/crm";
import { getLeadSurveys, type SurveyHistory } from "@/lib/api/surveys";
import { Send } from "lucide-react";
import { useCallback, useEffect, useState } from "react";

/** Control de Cambios #28: encuestas de las interacciones con el prospecto (visita, propuesta, negociación…). */
export function LeadSurveysCard({ lead, canManage }: { lead: LeadItem; canManage: boolean }) {
  const [history, setHistory] = useState<SurveyHistory | null>(null);
  const [open, setOpen] = useState(false);

  const load = useCallback(() => {
    getLeadSurveys(lead.id)
      .then(setHistory)
      .catch(() => undefined);
  }, [lead.id]);

  useEffect(() => {
    load();
  }, [load]);

  return (
    <Card>
      <CardHeader
        title="Encuestas de satisfacción"
        action={
          canManage ? (
            <Button size="sm" variant="secondary" onClick={() => setOpen(true)}>
              <Send className="h-4 w-4" aria-hidden /> Enviar
            </Button>
          ) : null
        }
      />
      {history ? <SurveyHistoryList surveys={history.surveys} canSeeScores={history.canSeeScores} /> : <p className="text-sm text-muted">Cargando…</p>}
      <GenerateSurveyModal
        open={open}
        onClose={() => setOpen(false)}
        target={{ leadId: lead.id }}
        defaultContact={{ name: lead.name, phone: lead.phone, email: lead.email }}
        onCreated={load}
      />
    </Card>
  );
}
