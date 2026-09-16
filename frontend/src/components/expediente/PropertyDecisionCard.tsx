"use client";

import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Card, CardHeader } from "@/components/ui/Card";
import { Modal } from "@/components/ui/Modal";
import { useToast } from "@/components/ui/Toast";
import { useDemoApp } from "@/context/DemoAppProvider";
import { makeActivity } from "@/lib/expediente-factory";
import { roleHasPermission } from "@/data/permissions";
import type { Expediente } from "@/types/expediente";
import { CheckCircle2, Circle, ThumbsDown, ThumbsUp } from "lucide-react";
import { useState } from "react";

function Requirement({ done, label }: { done: boolean; label: string }) {
  return (
    <li className="flex items-center gap-2.5 text-sm">
      {done ? (
        <CheckCircle2 className="h-4 w-4 shrink-0 text-success-text" aria-hidden />
      ) : (
        <Circle className="h-4 w-4 shrink-0 text-muted" aria-hidden />
      )}
      <span className={done ? "text-obsessed" : "text-muted"}>{label}</span>
    </li>
  );
}

export function PropertyDecisionCard({ expediente }: { expediente: Expediente }) {
  const { currentUser, updateExpediente } = useDemoApp();
  const { showToast } = useToast();
  const [acceptOpen, setAcceptOpen] = useState(false);
  const [rejectOpen, setRejectOpen] = useState(false);
  const [reason, setReason] = useState("");

  const canDecide = roleHasPermission(currentUser.roleId, "decidir_inmueble");

  const requiredDocs = expediente.documentRequirements.filter((r) => r.required);
  const allDocsAccepted =
    requiredDocs.length > 0 && requiredDocs.every((r) => expediente.documents[r.id]?.status === "accepted");
  const requirements = [
    { done: expediente.privacyConsent.mainConsent, label: "Aviso de privacidad aceptado" },
    { done: requiredDocs.some((r) => expediente.documents[r.id]), label: "Documentos recibidos" },
    { done: allDocsAccepted, label: "Documentos obligatorios aceptados" },
    { done: Boolean(expediente.receptionSignedAt), label: "Recepción firmada" },
  ];
  const canDecideNow = requirements.every((r) => r.done);

  const decision = expediente.propertyDecision.decision;

  const handleAccept = () => {
    const now = new Date().toISOString();
    updateExpediente(expediente.id, (current) => ({
      ...current,
      status: "property_accepted",
      propertyDecision: { decision: "accepted", decidedAt: now, decidedBy: currentUser.name },
      activity: [makeActivity("inmueble_aceptado", "El inmueble fue aceptado para continuar al proceso comercial.", now), ...current.activity],
    }));
    setAcceptOpen(false);
    showToast("Propiedad aceptada.");
  };

  const handleReject = () => {
    const now = new Date().toISOString();
    updateExpediente(expediente.id, (current) => ({
      ...current,
      status: "property_rejected",
      propertyDecision: { decision: "rejected", reason, decidedAt: now, decidedBy: currentUser.name },
      activity: [makeActivity("inmueble_rechazado", `El inmueble fue rechazado. Motivo: ${reason}`, now), ...current.activity],
    }));
    setRejectOpen(false);
    setReason("");
    showToast("Propiedad rechazada.");
  };

  if (decision === "accepted") {
    return (
      <Card className="border-success-text/30 bg-success-bg/40">
        <CardHeader title="Propiedad aceptada" action={<Badge tone="success">Aceptada</Badge>} />
        <p className="text-sm text-obsessed">Lista para alta en Gestión de propiedades.</p>
        <p className="mt-1 text-xs text-muted">Esta funcionalidad corresponde al Módulo 2.</p>
        <Button className="mt-4" disabled>
          Crear propiedad — Disponible en Módulo 2
        </Button>
        <div className="mt-4 rounded-xl border border-dashed border-border p-4 text-xs text-muted">
          <p className="font-medium text-obsessed">Siguiente etapa</p>
          <p className="mt-1">Gestión de propiedad y conexión con campañas — disponible en módulos posteriores.</p>
        </div>
      </Card>
    );
  }

  if (decision === "rejected") {
    return (
      <Card className="border-danger-text/30 bg-danger-bg/30">
        <CardHeader title="Propiedad rechazada" action={<Badge tone="danger">Rechazada</Badge>} />
        <p className="text-sm text-obsessed">Motivo: {expediente.propertyDecision.reason}</p>
      </Card>
    );
  }

  return (
    <Card>
      <CardHeader title="Decisión del inmueble" />
      <ul className="mb-5 space-y-2">
        {requirements.map((r) => (
          <Requirement key={r.label} done={r.done} label={r.label} />
        ))}
      </ul>
      {!canDecide ? (
        <p className="text-xs text-muted">Solo el administrador / representante legal puede tomar esta decisión.</p>
      ) : (
        <div className="flex flex-wrap gap-3">
          <Button disabled={!canDecideNow} onClick={() => setAcceptOpen(true)}>
            <ThumbsUp className="h-4 w-4" /> Aceptar inmueble
          </Button>
          <Button variant="danger" disabled={!canDecideNow} onClick={() => setRejectOpen(true)}>
            <ThumbsDown className="h-4 w-4" /> Rechazar inmueble
          </Button>
        </div>
      )}

      <Modal
        open={acceptOpen}
        onClose={() => setAcceptOpen(false)}
        title="Aceptar inmueble"
        footer={
          <>
            <Button variant="secondary" onClick={() => setAcceptOpen(false)}>
              Cancelar
            </Button>
            <Button onClick={handleAccept}>Confirmar</Button>
          </>
        }
      >
        <p className="text-sm text-muted">
          ¿Confirmas que el inmueble puede continuar al proceso de comercialización?
        </p>
      </Modal>

      <Modal
        open={rejectOpen}
        onClose={() => setRejectOpen(false)}
        title="Rechazar inmueble"
        footer={
          <>
            <Button variant="secondary" onClick={() => setRejectOpen(false)}>
              Cancelar
            </Button>
            <Button variant="danger" onClick={handleReject} disabled={!reason.trim()}>
              Rechazar
            </Button>
          </>
        }
      >
        <div className="flex flex-col gap-1.5">
          <label className="text-sm font-medium text-obsessed" htmlFor="reject-property-reason">
            Motivo
          </label>
          <textarea
            id="reject-property-reason"
            value={reason}
            onChange={(e) => setReason(e.target.value)}
            rows={3}
            className="w-full rounded-xl border border-border bg-card px-3.5 py-2.5 text-sm text-obsessed focus:border-gold focus:outline-none focus:ring-4 focus:ring-gold/20"
          />
        </div>
      </Modal>
    </Card>
  );
}
