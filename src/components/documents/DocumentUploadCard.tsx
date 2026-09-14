"use client";

import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { DocumentIcon } from "@/components/documents/DocumentIcon";
import { DocumentPreviewModal } from "@/components/documents/DocumentPreviewModal";
import { cn } from "@/lib/utils";
import type { DocumentRequirement } from "@/types/expediente";
import {
  AlertTriangle,
  Check,
  Eye,
  FileImage,
  FileText,
  ImagePlus,
  Loader2,
  Sparkles,
  Upload,
} from "lucide-react";
import { useRef, useState, type ChangeEvent } from "react";

interface UploadedFile {
  id: string;
  name: string;
  url: string | null;
}

type CardStatus = "pending" | "reviewing" | "converting" | "converted" | "error";

interface DocumentUploadCardProps {
  doc: DocumentRequirement;
  allowErrorDemo?: boolean;
}

function randomDelay(min: number, max: number) {
  return min + Math.random() * (max - min);
}

function slugify(name: string) {
  return name
    .toLowerCase()
    .normalize("NFD")
    .replace(/[̀-ͯ]/g, "")
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/(^-|-$)/g, "");
}

function StatusPill({ status }: { status: CardStatus }) {
  if (status === "reviewing") {
    return (
      <span className="inline-flex items-center gap-1.5 text-xs font-medium text-muted">
        <Loader2 className="h-3.5 w-3.5 animate-spin" aria-hidden />
        Revisando calidad...
      </span>
    );
  }
  if (status === "converting") {
    return (
      <span className="inline-flex items-center gap-1.5 text-xs font-medium text-teal-dark">
        <Sparkles className="h-3.5 w-3.5 animate-pulse" aria-hidden />
        Convirtiendo a PDF...
      </span>
    );
  }
  if (status === "converted") {
    return (
      <span className="inline-flex items-center gap-1.5 text-xs font-medium text-success-text">
        <Check className="h-3.5 w-3.5" aria-hidden />
        Convertido a PDF
      </span>
    );
  }
  if (status === "error") {
    return (
      <span className="inline-flex items-center gap-1.5 text-xs font-medium text-danger-text">
        <AlertTriangle className="h-3.5 w-3.5" aria-hidden />
        Requiere corrección
      </span>
    );
  }
  return null;
}

export function DocumentUploadCard({ doc, allowErrorDemo = false }: DocumentUploadCardProps) {
  const [files, setFiles] = useState<UploadedFile[]>([]);
  const [status, setStatus] = useState<CardStatus>("pending");
  const [previewOpen, setPreviewOpen] = useState(false);
  const inputRef = useRef<HTMLInputElement>(null);

  const runPipeline = () => {
    setStatus("reviewing");
    window.setTimeout(() => {
      setStatus("converting");
      window.setTimeout(() => {
        setStatus("converted");
      }, randomDelay(700, 1000));
    }, randomDelay(700, 1000));
  };

  const handleSingleFile = (event: ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    if (!file) return;
    const url = file.type.startsWith("image/") ? URL.createObjectURL(file) : null;
    setFiles([{ id: `${file.name}-${Date.now()}`, name: file.name, url }]);
    runPipeline();
    event.target.value = "";
  };

  const handleMultiFiles = (event: ChangeEvent<HTMLInputElement>) => {
    const selected = Array.from(event.target.files ?? []);
    if (selected.length === 0) return;
    const newFiles: UploadedFile[] = selected.map((file) => ({
      id: `${file.name}-${Date.now()}-${Math.random()}`,
      name: file.name,
      url: file.type.startsWith("image/") ? URL.createObjectURL(file) : null,
    }));
    setFiles((prev) => [...prev, ...newFiles]);
    runPipeline();
    event.target.value = "";
  };

  const handleSimulateError = () => {
    setStatus("error");
  };

  const handleReplace = () => {
    setFiles([]);
    setStatus("pending");
    inputRef.current?.click();
  };

  const isError = status === "error";
  const isConverted = status === "converted";
  const hasFiles = files.length > 0;
  const pdfName = `${slugify(doc.name)}.pdf`;

  return (
    <div
      className={cn(
        "rounded-2xl border bg-white p-5 shadow-sm transition-all duration-200",
        isError
          ? "border-danger-text/50 ring-1 ring-danger-text/20"
          : isConverted
            ? "border-success-text/25"
            : "border-border hover:shadow-md",
      )}
    >
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="flex items-start gap-3">
          <div
            className={cn(
              "flex h-10 w-10 shrink-0 items-center justify-center rounded-xl",
              isConverted ? "bg-success-bg text-success-text" : "bg-teal-light text-teal-dark",
            )}
          >
            <DocumentIcon docId={doc.id} className="h-5 w-5" />
          </div>
          <div>
            <div className="flex items-center gap-2">
              <h3 className="text-sm font-semibold text-navy">{doc.name}</h3>
              {doc.required ? <Badge tone="teal">Obligatorio</Badge> : <Badge>Opcional</Badge>}
            </div>
            {doc.description ? (
              <p className="mt-1 text-sm text-muted">{doc.description}</p>
            ) : null}
          </div>
        </div>
        {hasFiles ? <StatusPill status={status} /> : null}
      </div>

      {isError ? (
        <div className="mt-4 flex items-start gap-2.5 rounded-xl bg-danger-bg px-4 py-3">
          <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0 text-danger-text" aria-hidden />
          <p className="text-sm text-danger-text">
            La fotografía parece estar inclinada. Toma nuevamente la fotografía en posición
            vertical.
          </p>
        </div>
      ) : null}

      <div className="mt-4">
        {!hasFiles ? (
          doc.multiPage ? (
            <MultiFileEmptyArea inputRef={inputRef} onSelect={handleMultiFiles} />
          ) : (
            <SingleFileEmptyArea inputRef={inputRef} onSelect={handleSingleFile} />
          )
        ) : isConverted ? (
          <PdfResultCard
            fileName={pdfName}
            pageCount={files.length > 1 ? files.length : undefined}
            onView={() => setPreviewOpen(true)}
          />
        ) : doc.multiPage ? (
          <MultiFileProcessingArea files={files} status={status} />
        ) : (
          <SingleFileProcessingArea files={files} status={status} />
        )}
      </div>

      <div className="mt-4 flex flex-wrap items-center gap-3">
        {isError ? (
          <Button size="sm" variant="secondary" onClick={handleReplace}>
            <Upload className="h-3.5 w-3.5" aria-hidden />
            Reemplazar fotografía
          </Button>
        ) : null}

        {isConverted && doc.multiPage ? (
          <label className="inline-flex cursor-pointer items-center gap-1.5 text-xs font-medium text-teal-dark hover:underline">
            <ImagePlus className="h-3.5 w-3.5" aria-hidden />
            Agregar más fotografías
            <input
              ref={inputRef}
              type="file"
              accept="image/jpeg,image/png"
              multiple
              className="hidden"
              onChange={handleMultiFiles}
            />
          </label>
        ) : null}

        {allowErrorDemo && isConverted ? (
          <button
            type="button"
            onClick={handleSimulateError}
            className="text-xs font-medium text-muted underline decoration-dotted hover:text-danger-text"
          >
            Simular imagen incorrecta (demo)
          </button>
        ) : null}
      </div>

      <DocumentPreviewModal
        open={previewOpen}
        title={doc.name}
        onClose={() => setPreviewOpen(false)}
        pageCount={files.length > 1 ? files.length : 1}
      />
    </div>
  );
}

function PdfResultCard({
  fileName,
  pageCount,
  onView,
}: {
  fileName: string;
  pageCount?: number;
  onView: () => void;
}) {
  return (
    <div className="flex items-center gap-3.5 rounded-xl border border-success-text/20 bg-success-bg/60 px-4 py-3.5">
      <div className="flex h-12 w-12 shrink-0 items-center justify-center rounded-lg bg-white text-danger-text shadow-sm">
        <FileText className="h-6 w-6" aria-hidden />
      </div>
      <div className="min-w-0 flex-1">
        <p className="truncate text-sm font-semibold text-navy">{fileName}</p>
        <p className="text-xs text-success-text">
          {pageCount ? `${pageCount} páginas combinadas · ` : ""}Generado automáticamente
        </p>
      </div>
      <button
        type="button"
        onClick={onView}
        className="inline-flex shrink-0 items-center gap-1.5 rounded-lg border border-border bg-white px-3 py-1.5 text-xs font-medium text-navy transition-colors hover:bg-app-bg"
      >
        <Eye className="h-3.5 w-3.5" aria-hidden />
        Ver PDF
      </button>
    </div>
  );
}

function SingleFileEmptyArea({
  inputRef,
  onSelect,
}: {
  inputRef: React.RefObject<HTMLInputElement | null>;
  onSelect: (event: ChangeEvent<HTMLInputElement>) => void;
}) {
  return (
    <label className="flex cursor-pointer flex-col items-center justify-center gap-2 rounded-xl border-2 border-dashed border-border px-4 py-6 text-center transition-colors hover:border-teal/40 hover:bg-app-bg/60">
      <Upload className="h-5 w-5 text-muted" aria-hidden />
      <span className="text-sm font-medium text-navy">Toca para subir una fotografía</span>
      <span className="text-xs text-muted">Formatos aceptados: JPG y PNG</span>
      <input
        ref={inputRef}
        type="file"
        accept="image/jpeg,image/png"
        className="hidden"
        onChange={onSelect}
      />
    </label>
  );
}

function SingleFileProcessingArea({
  files,
  status,
}: {
  files: UploadedFile[];
  status: CardStatus;
}) {
  const file = files[0];
  const isError = status === "error";

  return (
    <div
      className={cn(
        "flex items-center gap-3 rounded-xl border px-3.5 py-3",
        isError ? "border-danger-text/40 bg-danger-bg/40" : "border-border bg-app-bg/50",
      )}
    >
      <div className="relative flex h-14 w-14 shrink-0 items-center justify-center overflow-hidden rounded-lg border border-border bg-white">
        {file.url ? (
          // eslint-disable-next-line @next/next/no-img-element
          <img src={file.url} alt="" className="h-full w-full object-cover" />
        ) : (
          <FileImage className="h-5 w-5 text-muted" aria-hidden />
        )}
        {status === "reviewing" || status === "converting" ? (
          <div className="absolute inset-0 flex items-center justify-center bg-navy/40">
            <Loader2 className="h-4 w-4 animate-spin text-white" aria-hidden />
          </div>
        ) : null}
      </div>
      <div className="min-w-0 flex-1">
        <p className="truncate text-sm font-medium text-navy">{file.name}</p>
        <p className="text-xs text-muted">Archivo seleccionado</p>
      </div>
    </div>
  );
}

function MultiFileEmptyArea({
  inputRef,
  onSelect,
}: {
  inputRef: React.RefObject<HTMLInputElement | null>;
  onSelect: (event: ChangeEvent<HTMLInputElement>) => void;
}) {
  const placeholders = ["Página 1", "Página 2", "Página 3"];

  return (
    <div>
      <div className="grid grid-cols-3 gap-3 sm:grid-cols-4">
        {placeholders.map((label) => (
          <div
            key={label}
            className="flex aspect-[3/4] flex-col items-center justify-center gap-1.5 rounded-xl border border-dashed border-border bg-app-bg/50 text-center"
          >
            <FileImage className="h-5 w-5 text-muted/60" aria-hidden />
            <span className="text-[11px] text-muted">{label}</span>
          </div>
        ))}
      </div>
      <label className="mt-3 inline-flex cursor-pointer items-center gap-2 rounded-xl border border-border bg-white px-3.5 py-2 text-sm font-medium text-navy transition-colors hover:border-teal/40 hover:bg-app-bg">
        <ImagePlus className="h-4 w-4" aria-hidden />
        Agregar fotografías
        <input
          ref={inputRef}
          type="file"
          accept="image/jpeg,image/png"
          multiple
          className="hidden"
          onChange={onSelect}
        />
      </label>
    </div>
  );
}

function MultiFileProcessingArea({
  files,
  status,
}: {
  files: UploadedFile[];
  status: CardStatus;
}) {
  const isError = status === "error";
  const isBusy = status === "reviewing" || status === "converting";

  return (
    <div className="grid grid-cols-3 gap-3 sm:grid-cols-4">
      {files.map((file, index) => (
        <div
          key={file.id}
          className={cn(
            "relative flex aspect-[3/4] flex-col overflow-hidden rounded-xl border bg-white",
            isError ? "border-danger-text/40" : "border-border",
          )}
        >
          <div className="flex-1 overflow-hidden bg-app-bg/50">
            {file.url ? (
              // eslint-disable-next-line @next/next/no-img-element
              <img src={file.url} alt="" className="h-full w-full object-cover" />
            ) : (
              <div className="flex h-full items-center justify-center">
                <FileImage className="h-5 w-5 text-muted" aria-hidden />
              </div>
            )}
          </div>
          <span className="px-1.5 py-1 text-center text-[11px] text-muted">
            Página {index + 1}
          </span>
          {isBusy ? (
            <div className="absolute inset-0 flex items-center justify-center bg-navy/40">
              <Loader2 className="h-4 w-4 animate-spin text-white" aria-hidden />
            </div>
          ) : null}
        </div>
      ))}
    </div>
  );
}
