"use client";

import { DocumentReviewRow } from "@/components/documents/DocumentReviewRow";
import { Button } from "@/components/ui/Button";
import { Card, CardHeader } from "@/components/ui/Card";
import { Input } from "@/components/ui/Input";
import { Modal } from "@/components/ui/Modal";
import { useDemoApp } from "@/context/DemoAppProvider";
import { generateId } from "@/lib/expediente-factory";
import { roleHasPermission } from "@/data/permissions";
import type { DocumentCategory, Expediente } from "@/types/expediente";
import { Plus } from "lucide-react";
import { useState } from "react";

const categoryLabels: Record<DocumentCategory, string> = {
  identidad: "Identidad",
  fiscal: "Fiscal",
  propiedad: "Propiedad",
  cumplimiento: "Cumplimiento / contractual",
  contrato: "Contrato",
  anexos: "Anexos",
  cierre: "Cierre",
};

const categoryOrder: DocumentCategory[] = ["identidad", "fiscal", "propiedad", "anexos"];

export function DocumentBank({ expediente }: { expediente: Expediente }) {
  const { currentUser, updateExpediente } = useDemoApp();
  const canReview = roleHasPermission(currentUser.roleId, "aceptar_documentos");
  const [addOpen, setAddOpen] = useState(false);
  const [newDocName, setNewDocName] = useState("");

  const visibleRequirements = expediente.documentRequirements.filter((r) => r.required || r.custom);

  if (visibleRequirements.length === 0) {
    return (
      <Card>
        <p className="text-sm text-muted">
          Sin documentos requeridos todavía. Configura el expediente para generar la lista.
        </p>
      </Card>
    );
  }

  const handleAddCustom = () => {
    if (!newDocName.trim()) return;
    const id = generateId("doc-extra");
    updateExpediente(expediente.id, (current) => ({
      ...current,
      documentRequirements: [
        ...current.documentRequirements,
        { id, name: newDocName.trim(), category: "anexos", required: false, custom: true },
      ],
    }));
    setNewDocName("");
    setAddOpen(false);
  };

  return (
    <div className="flex flex-col gap-6">
      {categoryOrder.map((category) => {
        const items = visibleRequirements.filter((r) => r.category === category);
        if (items.length === 0) return null;
        return (
          <Card key={category}>
            <CardHeader title={categoryLabels[category]} />
            <div className="flex flex-col gap-3">
              {items.map((req) => (
                <DocumentReviewRow
                  key={req.id}
                  expedienteId={expediente.id}
                  requirement={req}
                  reviewerName={currentUser.name}
                  canReview={canReview}
                />
              ))}
            </div>
          </Card>
        );
      })}

      <div>
        <Button variant="secondary" size="sm" onClick={() => setAddOpen(true)}>
          <Plus className="h-3.5 w-3.5" /> Agregar documento adicional
        </Button>
      </div>

      <Modal open={addOpen} onClose={() => setAddOpen(false)} title="Agregar documento adicional">
        <div className="flex flex-col gap-4">
          <Input
            label="Nombre del documento"
            value={newDocName}
            onChange={(e) => setNewDocName(e.target.value)}
            placeholder="Ej. Identificación del cónyuge"
          />
          <div className="flex justify-end gap-3">
            <Button variant="secondary" onClick={() => setAddOpen(false)}>
              Cancelar
            </Button>
            <Button onClick={handleAddCustom}>Agregar</Button>
          </div>
        </div>
      </Modal>
    </div>
  );
}
