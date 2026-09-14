"use client";

import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { DocumentIcon } from "@/components/documents/DocumentIcon";
import { DocumentPreviewModal } from "@/components/documents/DocumentPreviewModal";
import { cn } from "@/lib/utils";
import type { DocumentRequirement } from "@/types/expediente";
import {
  AlertTriangle,
  BadgeCheck,
  Check,
  Eye,
  FileImage,
  FileText,
  ImagePlus,
  Loader2,
  ScanEye,
  Sparkles,
  Upload,
  X,
} from "lucide-react";
import { useEffect, useRef, useState, type ChangeEvent } from "react";

interface UploadedFile {
  id: string;
  name: string;
  url: string | null;
}

type CardStatus = "idle" | "reviewing" | "converting" | "converted" | "error";

interface DocumentUploadCardProps {
  doc: DocumentRequirement;
  allowErrorDemo?: boolean;
}

const verificationSteps = [
  "Detectando el documento en la imagen",
  "Verificando nitidez, orientación e iluminación",
  "Confirmando que el contenido es legible y correcto",
];

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
      <span className="inline-flex items-center gap-1.5 text-xs font-medium text-teal-dark">
        <ScanEye className="h-3.5 w-3.5 animate-pulse" aria-hidden />
        IA verificando...
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
  const [status, setStatus] = useState<CardStatus>("idle");
  const [previewOpen, setPreviewOpen] = useState(false);
  const [runKey, setRunKey] = useState(0);
  const inputRef = useRef<HTMLInputElement>(null);
  const runId = useRef(0);

  const runPipeline = () => {
    const currentRun = ++runId.current;
    setRunKey(currentRun);
    setStatus("reviewing");
    window.setTimeout(
      () => {
        if (runId.current !== currentRun) return;
        setStatus("converting");
        window.setTimeout(
          () => {
            if (runId.current !== currentRun) return;
            setStatus("converted");
          },
          randomDelay(650, 950),
        );
      },
      verificationSteps.length * 480 + 250,
    );
  };

  const handleAddFiles = (event: ChangeEvent<HTMLInputElement>) => {
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

  const handleRemoveFile = (id: string) => {
    setFiles((prev) => {
      const next = prev.filter((file) => file.id !== id);
      if (next.length === 0) {
        runId.current++;
        setStatus("idle");
      }
      return next;
    });
  };

  const handleSimulateError = () => {
    runId.current++;
    setStatus("error");
  };

  const handleReplace = () => {
    setFiles([]);
    setStatus("idle");
    inputRef.current?.click();
  };

  const isError = status === "error";
  const isConverted = status === "converted";
  const isBusy = status === "reviewing" || status === "converting";
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
          <div className="min-w-0">
            <div className="flex flex-wrap items-center gap-x-2 gap-y-1">
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
          <EmptyDropzone inputRef={inputRef} onSelect={handleAddFiles} />
        ) : isConverted ? (
          <PdfResultCard
            fileName={pdfName}
            pageCount={files.length > 1 ? files.length : undefined}
            onView={() => setPreviewOpen(true)}
          />
        ) : (
          <div className="flex flex-col gap-4">
            <PhotoGrid files={files} isError={isError} onRemove={handleRemoveFile} />
            {isBusy ? <AIVerificationSteps key={runKey} status={status} /> : null}
          </div>
        )}
      </div>

      <div className="mt-4 flex flex-wrap items-center gap-3">
        {isError ? (
          <Button size="sm" variant="secondary" onClick={handleReplace}>
            <Upload className="h-3.5 w-3.5" aria-hidden />
            Reemplazar fotografía
          </Button>
        ) : null}

        {hasFiles && !isError ? (
          <label className="inline-flex cursor-pointer items-center gap-1.5 text-xs font-medium text-teal-dark hover:underline">
            <ImagePlus className="h-3.5 w-3.5" aria-hidden />
            {isConverted ? "Agregar más fotografías" : "Agregar otra fotografía"}
            <input
              ref={inputRef}
              type="file"
              accept="image/jpeg,image/png"
              multiple
              className="hidden"
              onChange={handleAddFiles}
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

function EmptyDropzone({
  inputRef,
  onSelect,
}: {
  inputRef: React.RefObject<HTMLInputElement | null>;
  onSelect: (event: ChangeEvent<HTMLInputElement>) => void;
}) {
  return (
    <label className="flex cursor-pointer flex-col items-center justify-center gap-2 rounded-xl border-2 border-dashed border-border px-4 py-6 text-center transition-colors hover:border-teal/40 hover:bg-app-bg/60">
      <Upload className="h-5 w-5 text-muted" aria-hidden />
      <span className="text-sm font-medium text-navy">Toca para subir una o varias fotografías</span>
      <span className="text-xs text-muted">Formatos aceptados: JPG y PNG · puedes seleccionar varias a la vez</span>
      <input
        ref={inputRef}
        type="file"
        accept="image/jpeg,image/png"
        multiple
        className="hidden"
        onChange={onSelect}
      />
    </label>
  );
}

function PhotoGrid({
  files,
  isError,
  onRemove,
}: {
  files: UploadedFile[];
  isError: boolean;
  onRemove: (id: string) => void;
}) {
  return (
    <div className="grid grid-cols-3 gap-3 sm:grid-cols-4">
      {files.map((file, index) => (
        <div
          key={file.id}
          className={cn(
            "group/thumb relative flex aspect-[3/4] flex-col overflow-hidden rounded-xl border bg-white",
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
            Foto {index + 1}
          </span>
          <button
            type="button"
            onClick={() => onRemove(file.id)}
            aria-label="Quitar fotografía"
            className="absolute right-1 top-1 flex h-5 w-5 items-center justify-center rounded-full bg-navy/70 text-white opacity-0 transition-opacity group-hover/thumb:opacity-100"
          >
            <X className="h-3 w-3" aria-hidden />
          </button>
        </div>
      ))}
    </div>
  );
}

function AIVerificationSteps({ status }: { status: CardStatus }) {
  const [activeStep, setActiveStep] = useState(0);

  useEffect(() => {
    if (status !== "reviewing") return;
    const timers = verificationSteps.map((_, index) =>
      window.setTimeout(() => setActiveStep(index + 1), (index + 1) * 480),
    );
    return () => timers.forEach((timer) => window.clearTimeout(timer));
  }, [status]);

  if (status === "converting") {
    return (
      <div className="flex items-center gap-2.5 rounded-xl bg-teal-light/50 px-4 py-3">
        <BadgeCheck className="h-4 w-4 shrink-0 text-teal-dark" aria-hidden />
        <p className="text-sm text-navy">
          Documento verificado. Generando el PDF automáticamente...
        </p>
      </div>
    );
  }

  return (
    <div className="rounded-xl bg-app-bg/60 px-4 py-3.5">
      <p className="mb-2.5 flex items-center gap-1.5 text-xs font-medium text-teal-dark">
        <ScanEye className="h-3.5 w-3.5" aria-hidden />
        Verificando con inteligencia artificial
      </p>
      <ul className="space-y-2">
        {verificationSteps.map((step, index) => {
          const isDone = index < activeStep;
          const isActive = index === activeStep;
          return (
            <li key={step} className="flex items-center gap-2.5 text-xs">
              <span
                className={cn(
                  "flex h-4 w-4 shrink-0 items-center justify-center rounded-full",
                  isDone
                    ? "bg-teal text-white"
                    : isActive
                      ? "border-2 border-teal text-teal"
                      : "border-2 border-border text-transparent",
                )}
              >
                {isDone ? (
                  <Check className="h-2.5 w-2.5" aria-hidden />
                ) : isActive ? (
                  <Loader2 className="h-2.5 w-2.5 animate-spin" aria-hidden />
                ) : null}
              </span>
              <span className={isDone || isActive ? "text-navy" : "text-muted"}>{step}</span>
            </li>
          );
        })}
      </ul>
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
          {pageCount ? `${pageCount} páginas combinadas · ` : ""}Verificado y generado
          automáticamente
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
