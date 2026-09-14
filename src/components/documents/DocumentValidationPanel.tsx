import { Card, CardHeader } from "@/components/ui/Card";
import { CheckCircle2, ShieldCheck } from "lucide-react";

const checklist = [
  {
    title: "Calidad de imagen",
    description: "Fotos nítidas y sin reflejos.",
  },
  {
    title: "Orientación vertical",
    description: "El documento debe estar en posición vertical.",
  },
  {
    title: "Documento legible",
    description: "Todo el texto debe ser visible.",
  },
  {
    title: "Conversión a PDF",
    description: "Las fotografías se convertirán automáticamente a PDF.",
  },
];

const recommendations = [
  "Toma las fotos en un lugar bien iluminado.",
  "Asegúrate de que se vea completo el documento.",
  "Evita sombras o reflejos.",
  "Utiliza fotografías verticales.",
  "Formatos aceptados: JPG y PNG.",
];

export function DocumentValidationPanel() {
  return (
    <div className="flex flex-col gap-6">
      <Card>
        <CardHeader title="Validación automática" />
        <ul className="space-y-4">
          {checklist.map((item) => (
            <li key={item.title} className="flex items-start gap-3">
              <CheckCircle2 className="mt-0.5 h-4 w-4 shrink-0 text-teal" aria-hidden />
              <div>
                <p className="text-sm font-medium text-navy">{item.title}</p>
                <p className="text-xs text-muted">{item.description}</p>
              </div>
            </li>
          ))}
        </ul>
      </Card>

      <Card>
        <CardHeader title="Recomendaciones" />
        <ul className="space-y-2.5">
          {recommendations.map((tip) => (
            <li key={tip} className="flex items-start gap-2.5 text-sm text-navy/80">
              <span className="mt-1.5 h-1.5 w-1.5 shrink-0 rounded-full bg-teal" />
              {tip}
            </li>
          ))}
        </ul>
      </Card>

      <div className="flex items-start gap-3 rounded-2xl bg-success-bg p-5">
        <ShieldCheck className="mt-0.5 h-5 w-5 shrink-0 text-success-text" aria-hidden />
        <div>
          <p className="text-sm font-semibold text-success-text">
            Tu información está segura
          </p>
          <p className="mt-1 text-sm text-success-text/80">
            Tus documentos serán utilizados únicamente para integrar este expediente.
          </p>
        </div>
      </div>
    </div>
  );
}
