"use client";

import { Badge } from "@/components/ui/Badge";
import { Card, CardHeader } from "@/components/ui/Card";
import { DocumentPreviewModal } from "@/components/documents/DocumentPreviewModal";
import type { ReceivedDocument } from "@/types/expediente";
import { Eye } from "lucide-react";
import { useState } from "react";

interface DocumentsTableProps {
  documents: ReceivedDocument[];
}

export function DocumentsTable({ documents }: DocumentsTableProps) {
  const [selected, setSelected] = useState<ReceivedDocument | null>(null);

  return (
    <Card>
      <CardHeader
        title="Documentos recibidos"
        description={`${documents.length} de ${documents.length} documentos recibidos y procesados correctamente.`}
      />

      <table className="hidden w-full text-left text-sm md:table">
        <thead className="border-b border-border">
          <tr className="text-xs font-medium uppercase tracking-wide text-muted">
            <th className="py-2.5 pr-4">Documento</th>
            <th className="py-2.5 pr-4">Fecha de recepción</th>
            <th className="py-2.5 pr-4">Estatus</th>
            <th className="py-2.5 pr-4 text-right">Acciones</th>
          </tr>
        </thead>
        <tbody>
          {documents.map((doc) => (
            <tr key={doc.id} className="border-b border-border last:border-0">
              <td className="py-3 pr-4 font-medium text-navy">{doc.name}</td>
              <td className="py-3 pr-4 text-muted">{doc.receivedAt}</td>
              <td className="py-3 pr-4">
                <Badge tone={doc.status === "Validado" ? "success" : "teal"}>{doc.status}</Badge>
              </td>
              <td className="py-3 pr-4 text-right">
                <button
                  onClick={() => setSelected(doc)}
                  className="inline-flex items-center gap-1 text-sm font-medium text-teal-dark hover:underline"
                >
                  <Eye className="h-3.5 w-3.5" aria-hidden />
                  Ver
                </button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>

      <div className="space-y-3 md:hidden">
        {documents.map((doc) => (
          <div key={doc.id} className="rounded-xl border border-border p-4">
            <div className="flex items-start justify-between gap-3">
              <p className="text-sm font-medium text-navy">{doc.name}</p>
              <Badge tone={doc.status === "Validado" ? "success" : "teal"}>{doc.status}</Badge>
            </div>
            <div className="mt-2 flex items-center justify-between">
              <span className="text-xs text-muted">{doc.receivedAt}</span>
              <button
                onClick={() => setSelected(doc)}
                className="inline-flex items-center gap-1 text-sm font-medium text-teal-dark hover:underline"
              >
                <Eye className="h-3.5 w-3.5" aria-hidden />
                Ver
              </button>
            </div>
          </div>
        ))}
      </div>

      <DocumentPreviewModal document={selected} onClose={() => setSelected(null)} />
    </Card>
  );
}
