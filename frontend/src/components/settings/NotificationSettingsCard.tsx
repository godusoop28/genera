import { Card, CardHeader } from "@/components/ui/Card";

const advisorEvents = [
  "El cliente cargó todos sus documentos obligatorios",
  "El cliente cargó la corrección de un documento devuelto",
  "Un archivo no parece ser el documento solicitado o no es legible",
  "La documentación quedó aprobada",
  "El contrato quedó firmado por todas las partes",
  "El inmueble fue aceptado o rechazado",
];

const clientEvents = [
  "Un documento fue devuelto o rechazado, con el motivo y qué corregir",
  "Una foto no se pudo leer y debe tomarse de nuevo",
  "Su documentación quedó completa",
  "Su liga personal para firmar el contrato",
  "El contrato quedó firmado",
];

export function NotificationSettingsCard() {
  return (
    <Card>
      <CardHeader
        title="Notificaciones por correo"
        description="El sistema envía correos automáticamente cuando ocurre alguno de estos eventos. Cada envío, y cualquier error de entrega, queda registrado en la Bitácora del expediente."
      />
      <div className="grid gap-4 text-sm">
        <div>
          <p className="mb-1 font-medium text-obsessed">Al asesor del expediente</p>
          <ul className="ml-5 list-disc text-muted">
            {advisorEvents.map((e) => (
              <li key={e}>{e}</li>
            ))}
          </ul>
        </div>
        <div>
          <p className="mb-1 font-medium text-obsessed">Al cliente</p>
          <ul className="ml-5 list-disc text-muted">
            {clientEvents.map((e) => (
              <li key={e}>{e}</li>
            ))}
          </ul>
        </div>
      </div>
    </Card>
  );
}
