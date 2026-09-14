"use client";

import { Badge } from "@/components/ui/Badge";
import { Card, CardHeader } from "@/components/ui/Card";
import { DocumentIcon } from "@/components/documents/DocumentIcon";
import { DocumentPreviewModal } from "@/components/documents/DocumentPreviewModal";
import type { ReceivedDocument } from "@/types/expediente";
import { CheckCircle2, Eye, FileText } from "lucide-react";
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
        description={`${documents.length} de ${documents.length} documentos recibidos y convertidos a PDF automáticamente.`}
      />

      <div className="grid grid-cols-1 gap-3.5 sm:grid-cols-2">
        {documents.map((doc) => (
          <button
            key={doc.id}
            onClick={() => setSelected(doc)}
            className="group flex items-center gap-3.5 rounded-2xl border border-border bg-white p-4 text-left shadow-sm transition-all duration-200 hover:-translate-y-0.5 hover:border-teal/30 hover:shadow-md"
          >
            <div className="relative flex h-12 w-12 shrink-0 items-center justify-center rounded-xl bg-danger-bg text-danger-text">
              <FileText className="h-6 w-6" aria-hidden />
              <span className="absolute -bottom-1.5 -right-1.5 flex h-5 w-5 items-center justify-center rounded-full border-2 border-white bg-teal-light p-0.5 text-teal-dark">
                <DocumentIcon docId={doc.id} className="h-full w-full" />
              </span>
            </div>
            <div className="min-w-0 flex-1">
              <p className="truncate text-sm font-semibold text-navy">{doc.name}</p>
              <p className="mt-0.5 text-xs text-muted">Recibido {doc.receivedAt}</p>
            </div>
            <div className="flex shrink-0 flex-col items-end gap-1.5">
              <Badge tone="success">
                <CheckCircle2 className="h-3 w-3" aria-hidden />
                PDF
              </Badge>
              <span className="inline-flex items-center gap-1 text-xs font-medium text-teal-dark opacity-0 transition-opacity group-hover:opacity-100">
                <Eye className="h-3 w-3" aria-hidden />
                Ver
              </span>
            </div>
          </button>
        ))}
      </div>

      <DocumentPreviewModal
        open={selected !== null}
        title={selected?.name}
        onClose={() => setSelected(null)}
        pageCount={selected?.id === "escritura" ? 3 : 1}
      />
    </Card>
  );
}
