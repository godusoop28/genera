"use client";

import { SurveyWidget } from "@/components/surveys/SurveyUi";
import { Card } from "@/components/ui/Card";
import { ApiError } from "@/lib/api/client";
import { answerSurvey, commentSurvey, getPublicSurvey, type PublicSurvey } from "@/lib/api/surveys";
import { use, useEffect, useState } from "react";

/** Encuesta NPS enviada al cliente (WhatsApp, correo o liga del asesor). */
export default function SurveyPage({ params }: PageProps<"/encuesta/[token]">) {
  const { token } = use(params);
  const [survey, setSurvey] = useState<PublicSurvey | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    getPublicSurvey(token)
      .then(setSurvey)
      .catch((err) => setError(err instanceof ApiError && err.status === 404 ? "Esta encuesta no existe o la liga está incompleta." : "No pudimos cargar la encuesta. Revisa tu conexión e intenta de nuevo."));
  }, [token]);

  return (
    <Card>
      {error ? (
        <p className="py-6 text-center text-sm text-muted">{error}</p>
      ) : !survey ? (
        <p className="py-6 text-center text-sm text-muted">Cargando…</p>
      ) : (
        <>
          <p className="mb-1 text-xs font-medium uppercase tracking-wide text-muted">{survey.touchpointLabel}</p>
          <h1 className="mb-5 text-xl font-semibold text-obsessed">
            {survey.clientFirstName ? `Hola ${survey.clientFirstName}, ` : ""}¿cómo te fue {survey.moment}?
          </h1>
          <SurveyWidget survey={survey} onAnswer={(score) => answerSurvey(token, score)} onComment={(comment) => commentSurvey(token, comment)} />
        </>
      )}
    </Card>
  );
}
