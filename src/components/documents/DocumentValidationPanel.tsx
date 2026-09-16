import { Card, CardHeader } from "@/components/ui/Card";
import { CheckCircle2, Info, ShieldCheck } from "lucide-react";

const checklist = [
  { title: "Documento completo", description: "Debe verse íntegro, sin recortes." },
  { title: "Orientación vertical", description: "El documento debe estar en posición vertical." },
  { title: "Nitidez", description: "Fotos nítidas, sin movimiento ni desenfoque." },
  { title: "Sin reflejos excesivos", description: "Evita brillos que cubran el texto." },
  { title: "Texto visible", description: "Todo el contenido debe ser legible." },
  { title: "Formato admitido", description: "Formatos aceptados: JPG y PNG." },
  { title: "Conversión a PDF", description: "Las fotografías se convertirán automáticamente a PDF." },
];

export function DocumentValidationPanel() {
  return (
    <div className="flex flex-col gap-6">
      <Card>
        <CardHeader title="Validación automática" />
        <ul className="space-y-4">
          {checklist.map((item) => (
            <li key={item.title} className="flex items-start gap-3">
              <CheckCircle2 className="mt-0.5 h-4 w-4 shrink-0 text-dark-gold" aria-hidden />
              <div>
                <p className="text-sm font-medium text-obsessed">{item.title}</p>
                <p className="text-xs text-muted">{item.description}</p>
              </div>
            </li>
          ))}
        </ul>
      </Card>

      <div className="flex items-start gap-3 rounded-2xl bg-info-bg p-5">
        <Info className="mt-0.5 h-5 w-5 shrink-0 text-info-text" aria-hidden />
        <div>
          <p className="text-sm font-semibold text-info-text">
            La validación automática no es una aprobación
          </p>
          <p className="mt-1 text-sm text-info-text/80">
            Que una fotografía pase esta verificación solo confirma que es legible y está
            completa. CENTURY 21 Genera revisará y aceptará cada documento manualmente.
          </p>
        </div>
      </div>

      <div className="flex items-start gap-3 rounded-2xl bg-success-bg p-5">
        <ShieldCheck className="mt-0.5 h-5 w-5 shrink-0 text-success-text" aria-hidden />
        <div>
          <p className="text-sm font-semibold text-success-text">Tu información está segura</p>
          <p className="mt-1 text-sm text-success-text/80">
            Tus documentos serán utilizados únicamente para integrar este expediente.
          </p>
        </div>
      </div>
    </div>
  );
}
