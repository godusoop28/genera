"use client";

import { DocumentIcon } from "@/components/documents/DocumentIcon";
import { DocumentPreviewModal } from "@/components/documents/DocumentPreviewModal";
import { DocumentStatusBadge } from "@/components/documents/DocumentStatusBadge";
import { RejectDocumentModal } from "@/components/documents/RejectDocumentModal";
import { ReturnDocumentModal } from "@/components/documents/ReturnDocumentModal";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Modal } from "@/components/ui/Modal";
import { useToast } from "@/components/ui/Toast";
import { useDemoApp } from "@/context/DemoAppProvider";
import { makeActivity } from "@/lib/expediente-factory";
import type { DocumentRequirement, ReturnReason } from "@/types/expediente";
import { Check, Eye, RotateCcw, XCircle } from "lucide-react";
import { useState } from "react";

interface DocumentReviewRowProps {
  expedienteId: string;
  requirement: DocumentRequirement;
  reviewerName: string;
  canReview: boolean;
}

export function DocumentReviewRow({ expedienteId, requirement, reviewerName, canReview }: DocumentReviewRowProps) {
  const { getExpediente, updateExpediente } = useDemoApp();
  const { showToast } = useToast();
  const [previewOpen, setPreviewOpen] = useState(false);
  const [returnOpen, setReturnOpen] = useState(false);
  const [rejectOpen, setRejectOpen] = useState(false);
  const [acceptOpen, setAcceptOpen] = useState(false);

  const exp = getExpediente(expedienteId);
  const doc = exp?.documents[requirement.id];
  const status = doc?.status ?? "pending";
  const canAct = canReview && (status === "uploaded" || status === "processing" || status === "ready_for_review");

  const applyReview = (decision: "accepted" | "returned" | "rejected", reason?: ReturnReason, comment?: string) => {
    updateExpediente(expedienteId, (current) => {
      const currentDoc = current.documents[requirement.id];
      if (!currentDoc) return current;
      const statusMap = { accepted: "accepted", returned: "returned", rejected: "rejected" } as const;
      const activityLabel =
        decision === "accepted"
          ? `Documento aceptado: ${requirement.name}.`
          : decision === "returned"
            ? `Documento devuelto para corrección: ${requirement.name}.`
            : `Documento rechazado: ${requirement.name}.`;
      const activityType =
        decision === "accepted" ? "documento_aceptado" : decision === "returned" ? "documento_devuelto" : "documento_rechazado";

      const nextDocuments = {
        ...current.documents,
        [requirement.id]: {
          ...currentDoc,
          status: statusMap[decision],
          review: { decision, reason, comment, reviewedAt: new Date().toISOString(), reviewedBy: reviewerName },
        },
      };

      const requiredIds = current.documentRequirements.filter((r) => r.required).map((r) => r.id);
      const allAccepted = requiredIds.every((id) => nextDocuments[id]?.status === "accepted");
      let nextStatus = current.status;
      if (decision === "returned") {
        nextStatus = "corrections_requested";
      } else if (allAccepted) {
        nextStatus = "documents_approved";
      } else if (current.status === "documents_received") {
        nextStatus = "under_review";
      }

      return {
        ...current,
        status: nextStatus,
        documents: nextDocuments,
        activity: [makeActivity(activityType, activityLabel), ...current.activity],
      };
    });
  };

  return (
    <div className="flex flex-col gap-3 rounded-xl border border-border bg-white p-4 sm:flex-row sm:items-center sm:justify-between">
      <div className="flex items-start gap-3">
        <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-app-bg text-muted">
          <DocumentIcon docId={requirement.id} category={requirement.category} className="h-4 w-4" />
        </div>
        <div>
          <div className="flex flex-wrap items-center gap-2">
            <p className="text-sm font-medium text-obsessed">{requirement.name}</p>
            {requirement.required ? <Badge tone="gold">Obligatorio</Badge> : <Badge>Opcional</Badge>}
          </div>
          <div className="mt-1 flex items-center gap-2">
            <DocumentStatusBadge status={status} />
            {doc?.review?.reason ? (
              <span className="text-xs text-muted">Motivo: {doc.review.reason.replace(/_/g, " ")}</span>
            ) : null}
          </div>
        </div>
      </div>

      <div className="flex flex-wrap items-center gap-2">
        {doc && doc.pages.length > 0 ? (
          <Button variant="ghost" size="sm" onClick={() => setPreviewOpen(true)}>
            <Eye className="h-3.5 w-3.5" /> Ver
          </Button>
        ) : null}
        {canAct ? (
          <>
            <Button size="sm" onClick={() => setAcceptOpen(true)}>
              <Check className="h-3.5 w-3.5" /> Aceptar
            </Button>
            <Button variant="secondary" size="sm" onClick={() => setReturnOpen(true)}>
              <RotateCcw className="h-3.5 w-3.5" /> Solicitar corrección
            </Button>
            <Button variant="danger" size="sm" onClick={() => setRejectOpen(true)}>
              <XCircle className="h-3.5 w-3.5" /> Rechazar
            </Button>
          </>
        ) : null}
      </div>

      <DocumentPreviewModal open={previewOpen} title={requirement.name} onClose={() => setPreviewOpen(false)} pageCount={doc?.pages.length || 1} />

      <ReturnDocumentModal
        open={returnOpen}
        documentName={requirement.name}
        onClose={() => setReturnOpen(false)}
        onConfirm={(reason, comment) => {
          applyReview("returned", reason, comment);
          setReturnOpen(false);
          showToast("Documento devuelto para corrección.");
        }}
      />

      <RejectDocumentModal
        open={rejectOpen}
        documentName={requirement.name}
        onClose={() => setRejectOpen(false)}
        onConfirm={(comment) => {
          applyReview("rejected", undefined, comment);
          setRejectOpen(false);
          showToast("Documento rechazado.");
        }}
      />

      <Modal
        open={acceptOpen}
        onClose={() => setAcceptOpen(false)}
        title="Aceptar documento"
        footer={
          <>
            <Button variant="secondary" onClick={() => setAcceptOpen(false)}>
              Cancelar
            </Button>
            <Button
              onClick={() => {
                applyReview("accepted");
                setAcceptOpen(false);
                showToast("Documento aceptado.");
              }}
            >
              Confirmar aceptación
            </Button>
          </>
        }
      >
        <p className="text-sm text-muted">
          ¿Confirmas que <span className="font-medium text-obsessed">{requirement.name}</span> cumple
          los requisitos y puede aceptarse?
        </p>
      </Modal>
    </div>
  );
}
