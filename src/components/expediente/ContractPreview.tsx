import { Card, CardHeader } from "@/components/ui/Card";
import { Button } from "@/components/ui/Button";
import { contractMockText } from "@/data/mock-expediente";
import { Maximize2 } from "lucide-react";

interface ContractPreviewProps {
  onOpen: () => void;
}

export function ContractPreview({ onOpen }: ContractPreviewProps) {
  return (
    <Card>
      <CardHeader
        title="Vista previa del contrato"
        action={
          <Button variant="secondary" size="sm" onClick={onOpen}>
            <Maximize2 className="h-3.5 w-3.5" aria-hidden />
            Abrir en PDF
          </Button>
        }
      />

      <button
        type="button"
        onClick={onOpen}
        className="block w-full overflow-hidden rounded-xl border border-border bg-app-bg/40 p-4 text-left transition-shadow hover:shadow-sm"
      >
        <div className="mx-auto max-h-72 max-w-xs overflow-hidden rounded-sm bg-white p-5 shadow-sm">
          <h3 className="mb-3 text-center text-[11px] font-bold tracking-wide text-navy">
            CONTRATO DE COMPRAVENTA
          </h3>
          <p className="line-clamp-[10] whitespace-pre-line text-justify text-[9px] leading-relaxed text-navy/80">
            {contractMockText}
          </p>
        </div>
      </button>
    </Card>
  );
}
