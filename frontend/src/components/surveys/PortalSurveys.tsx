"use client";

import { SurveyWidget } from "@/components/surveys/SurveyUi";
import { Card, CardHeader } from "@/components/ui/Card";
import { answerSurvey, commentSurvey, getPortalSurveys, type PublicSurvey } from "@/lib/api/surveys";
import { useCallback, useEffect, useState } from "react";

/**
 * Control de Cambios #28, canal "dentro del sistema": encuestas pendientes que
 * aparecen en el portal del cliente. Se vuelve a consultar cada minuto y al
 * terminar un paso (evento "portal-surveys-refresh"). Las que el cliente ya
 * respondió se quedan visibles (con el agradecimiento) hasta que recarga.
 */
export function PortalSurveys({ token }: { token: string }) {
  const [items, setItems] = useState<PublicSurvey[]>([]);

  const load = useCallback(() => {
    getPortalSurveys(token)
      .then((pending) =>
        setItems((prev) => {
          const known = new Set(prev.map((s) => s.token));
          return [...prev, ...pending.filter((s) => s.token && !known.has(s.token))];
        }),
      )
      .catch(() => undefined);
  }, [token]);

  useEffect(() => {
    load();
    const timer = window.setInterval(load, 60_000);
    window.addEventListener("portal-surveys-refresh", load);
    return () => {
      window.clearInterval(timer);
      window.removeEventListener("portal-surveys-refresh", load);
    };
  }, [load]);

  if (items.length === 0) return null;
  return (
    <div className="mt-6 space-y-4">
      {items.map((s) => (
        <Card key={s.token} className="border-gold/50">
          <CardHeader title="Tu opinión nos importa" description={`${s.touchpointLabel} · menos de un minuto`} />
          <SurveyWidget compact survey={s} onAnswer={(score) => answerSurvey(s.token as string, score)} onComment={(comment) => commentSurvey(s.token as string, comment)} />
        </Card>
      ))}
    </div>
  );
}
