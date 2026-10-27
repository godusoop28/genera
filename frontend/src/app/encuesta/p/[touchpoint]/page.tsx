"use client";

import { SurveyWidget } from "@/components/surveys/SurveyUi";
import { Card } from "@/components/ui/Card";
import { Input } from "@/components/ui/Input";
import { ApiError } from "@/lib/api/client";
import { answerStaticSurvey, answerSurvey, commentSurvey, getStaticSurvey, type PublicSurvey } from "@/lib/api/surveys";
import { use, useEffect, useState } from "react";

/** QR fijo por punto de contacto (lonas, fichas técnicas, folletos de cierre). */
export default function StaticSurveyPage({ params }: PageProps<"/encuesta/p/[touchpoint]">) {
  const { touchpoint } = use(params);
  const [survey, setSurvey] = useState<PublicSurvey | null>(null);
  const [name, setName] = useState("");
  const [token, setToken] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    getStaticSurvey(touchpoint)
      .then(setSurvey)
      .catch((err) => setError(err instanceof ApiError && err.message ? err.message : "No pudimos cargar la encuesta."));
  }, [touchpoint]);

  return (
    <Card>
      {error ? (
        <p className="py-6 text-center text-sm text-muted">{error}</p>
      ) : !survey ? (
        <p className="py-6 text-center text-sm text-muted">Cargando…</p>
      ) : (
        <>
          <p className="mb-1 text-xs font-medium uppercase tracking-wide text-muted">{survey.touchpointLabel}</p>
          <h1 className="mb-5 text-xl font-semibold text-obsessed">¿Cómo te fue {survey.moment}?</h1>
          {!token ? <Input label="Tu nombre (opcional)" value={name} onChange={(e) => setName(e.target.value)} containerClassName="mb-5" /> : null}
          <SurveyWidget
            survey={survey}
            onAnswer={async (score) => {
              // Primera calificación: crea la respuesta; si la corrige, se usa la misma encuesta.
              if (token) return answerSurvey(token, score);
              const created = await answerStaticSurvey(touchpoint, score, name.trim() || undefined);
              setToken(created.token);
              return created;
            }}
            onComment={(comment) => (token ? commentSurvey(token, comment) : Promise.reject(new Error("Primero elige tu calificación")))}
          />
        </>
      )}
    </Card>
  );
}
