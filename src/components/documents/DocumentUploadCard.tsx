"use client";

import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { DocumentIcon } from "@/components/documents/DocumentIcon";
import { DocumentPreviewModal } from "@/components/documents/DocumentPreviewModal";
import { useDemoApp } from "@/context/DemoAppProvider";
import { cn } from "@/lib/utils";
import type { DocumentRequirement, DocumentPage } from "@/types/expediente";
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

type LocalPipelineStatus = "idle" | "verifying" | "converting" | "done" | "error";

interface DocumentUploadCardProps {
  expedienteId: string;
  requirement: DocumentRequirement;
  allowErrorDemo?: boolean;
}

const verificationSteps = [
  "Analizando imagen...",
  "Verificando orientación...",
  "Verificando legibilidad...",
  "Identificando documento...",
];

function randomDelay(min: number, max: number) {
  return min + Math.random() * (max - min);
}

function StatusPill({ status }: { status: LocalPipelineStatus }) {
  if (status === "verifying") {
    return (
      <span className="inline-flex items-center gap-1.5 text-xs font-medium text-dark-gold">
        <ScanEye className="h-3.5 w-3.5 animate-pulse" aria-hidden />
        Validación automática...
      </span>
    );
  }
  if (status === "converting") {
    return (
      <span className="inline-flex items-center gap-1.5 text-xs font-medium text-dark-gold">
        <Sparkles className="h-3.5 w-3.5 animate-pulse" aria-hidden />
        Preparando PDF...
      </span>
    );
  }
  if (status === "done") {
    return (
      <span className="inline-flex items-center gap-1.5 text-xs font-medium text-success-text">
        <Check className="h-3.5 w-3.5" aria-hidden />
        Documento listo
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

export function DocumentUploadCard({
  expedienteId,
  requirement,
  allowErrorDemo = false,
}: DocumentUploadCardProps) {
  const { getExpediente, updateExpediente } = useDemoApp();
  const exp = getExpediente(expedienteId);
  const uploaded = exp?.documents[requirement.id];
  const pages = uploaded?.pages ?? [];

  const [status, setStatus] = useState<LocalPipelineStatus>(
    uploaded && uploaded.status !== "pending" ? "done" : "idle",
  );
  const [previewOpen, setPreviewOpen] = useState(false);
  const [runKey, setRunKey] = useState(0);
  const inputRef = useRef<HTMLInputElement>(null);
  const runId = useRef(0);

  const setDocStatus = (docStatus: "uploaded" | "processing" | "ready_for_review") => {
    updateExpediente(expedienteId, (current) => ({
      ...current,
      documents: {
        ...current.documents,
        [requirement.id]: {
          requirementId: requirement.id,
          pages: current.documents[requirement.id]?.pages ?? [],
          status: docStatus,
          uploadedAt: new Date().toISOString(),
        },
      },
    }));
  };

  const setPages = (updater: (prev: DocumentPage[]) => DocumentPage[]) => {
    updateExpediente(expedienteId, (current) => {
      const existing = current.documents[requirement.id];
      const nextPages = updater(existing?.pages ?? []);
      return {
        ...current,
        documents: {
          ...current.documents,
          [requirement.id]: {
            requirementId: requirement.id,
            status: existing?.status ?? "uploaded",
            pages: nextPages,
            uploadedAt: existing?.uploadedAt ?? new Date().toISOString(),
          },
        },
      };
    });
  };

  const runPipeline = () => {
    const currentRun = ++runId.current;
    setRunKey(currentRun);
    setStatus("verifying");
    setDocStatus("uploaded");
    window.setTimeout(
      () => {
        if (runId.current !== currentRun) return;
        setStatus("converting");
        setDocStatus("processing");
        window.setTimeout(
          () => {
            if (runId.current !== currentRun) return;
            setStatus("done");
            setDocStatus("ready_for_review");
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
    const readers = selected.map(
      (file) =>
        new Promise<DocumentPage>((resolve) => {
          const reader = new FileReader();
          reader.onload = () =>
            resolve({ id: `${file.name}-${Date.now()}-${Math.random()}`, name: file.name, dataUrl: String(reader.result) });
          reader.readAsDataURL(file);
        }),
    );
    Promise.all(readers).then((newPages) => {
      setPages((prev) => [...prev, ...newPages]);
      runPipeline();
    });
    event.target.value = "";
  };

  const handleRemoveFile = (id: string) => {
    setPages((prev) => {
      const next = prev.filter((p) => p.id !== id);
      if (next.length === 0) {
        runId.current++;
        setStatus("idle");
        setDocStatus("uploaded");
      }
      return next;
    });
  };

  const handleSimulateError = () => {
    runId.current++;
    setStatus("error");
  };

  const handleReplace = () => {
    setPages(() => []);
    setStatus("idle");
    inputRef.current?.click();
  };

  const isError = status === "error";
  const isDone = status === "done";
  const isBusy = status === "verifying" || status === "converting";
  const hasFiles = pages.length > 0;
  const reviewDecision = uploaded?.review?.decision;

  return (
    <div
      className={cn(
        "rounded-2xl border bg-card p-5 shadow-sm transition-all duration-200",
        isError
          ? "border-danger-text/50 ring-1 ring-danger-text/20"
          : isDone
            ? "border-success-text/25"
            : "border-border hover:shadow-md",
      )}
    >
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="flex items-start gap-3">
          <div
            className={cn(
              "flex h-10 w-10 shrink-0 items-center justify-center rounded-xl",
              isDone ? "bg-success-bg text-success-text" : "bg-gold/15 text-dark-gold",
            )}
          >
            <DocumentIcon docId={requirement.id} category={requirement.category} className="h-5 w-5" />
          </div>
          <div className="min-w-0">
            <div className="flex flex-wrap items-center gap-x-2 gap-y-1">
              <h3 className="text-sm font-semibold text-obsessed">{requirement.name}</h3>
              {requirement.required ? <Badge tone="gold">Obligatorio</Badge> : <Badge>Opcional</Badge>}
              {reviewDecision === "returned" ? <Badge tone="warning">Devuelto</Badge> : null}
            </div>
            {requirement.description ? (
              <p className="mt-1 text-sm text-muted">{requirement.description}</p>
            ) : null}
          </div>
        </div>
        {hasFiles ? <StatusPill status={status} /> : null}
      </div>

      {uploaded?.review?.decision === "returned" ? (
        <div className="mt-4 flex items-start gap-2.5 rounded-xl bg-warning-bg px-4 py-3">
          <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0 text-warning-text" aria-hidden />
          <div className="text-sm text-warning-text">
            <p className="font-medium">Devuelto para corrección</p>
            {uploaded.review.comment ? <p className="mt-0.5">{uploaded.review.comment}</p> : null}
          </div>
        </div>
      ) : null}

      {isError ? (
        <div className="mt-4 flex items-start gap-2.5 rounded-xl bg-danger-bg px-4 py-3">
          <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0 text-danger-text" aria-hidden />
          <p className="text-sm text-danger-text">
            La fotografía parece estar inclinada o borrosa. Toma nuevamente la fotografía en
            posición vertical y con buena iluminación.
          </p>
        </div>
      ) : null}

      <div className="mt-4">
        {!hasFiles ? (
          <EmptyDropzone inputRef={inputRef} onSelect={handleAddFiles} />
        ) : isDone ? (
          <PdfResultCard pageCount={pages.length} onView={() => setPreviewOpen(true)} />
        ) : (
          <div className="flex flex-col gap-4">
            <PhotoGrid pages={pages} isError={isError} onRemove={handleRemoveFile} />
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
          <label className="inline-flex cursor-pointer items-center gap-1.5 text-xs font-medium text-dark-gold hover:underline">
            <ImagePlus className="h-3.5 w-3.5" aria-hidden />
            {isDone ? "Agregar más fotografías" : "Agregar otra fotografía"}
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

        {allowErrorDemo && isDone ? (
          <button
            type="button"
            onClick={handleSimulateError}
            className="text-xs font-medium text-muted underline decoration-dotted hover:text-danger-text"
          >
            Simular error de validación (demo)
          </button>
        ) : null}
      </div>

      <DocumentPreviewModal
        open={previewOpen}
        title={requirement.name}
        onClose={() => setPreviewOpen(false)}
        pageCount={pages.length || 1}
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
    <label className="flex cursor-pointer flex-col items-center justify-center gap-2 rounded-xl border-2 border-dashed border-border px-4 py-6 text-center transition-colors hover:border-gold/50 hover:bg-app-bg/60">
      <Upload className="h-5 w-5 text-muted" aria-hidden />
      <span className="text-sm font-medium text-obsessed">Tomar o seleccionar fotografía</span>
      <span className="text-xs text-muted">
        Formatos aceptados: JPG y PNG · puedes seleccionar varias a la vez
      </span>
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
  pages,
  isError,
  onRemove,
}: {
  pages: DocumentPage[];
  isError: boolean;
  onRemove: (id: string) => void;
}) {
  return (
    <div className="grid grid-cols-3 gap-3 sm:grid-cols-4">
      {pages.map((page, index) => (
        <div
          key={page.id}
          className={cn(
            "group/thumb relative flex aspect-[3/4] flex-col overflow-hidden rounded-xl border bg-card",
            isError ? "border-danger-text/40" : "border-border",
          )}
        >
          <div className="flex-1 overflow-hidden bg-app-bg/50">
            {page.dataUrl ? (
              // eslint-disable-next-line @next/next/no-img-element
              <img src={page.dataUrl} alt="" className="h-full w-full object-cover" />
            ) : (
              <div className="flex h-full items-center justify-center">
                <FileImage className="h-5 w-5 text-muted" aria-hidden />
              </div>
            )}
          </div>
          <span className="px-1.5 py-1 text-center text-[11px] text-muted">Foto {index + 1}</span>
          <button
            type="button"
            onClick={() => onRemove(page.id)}
            aria-label="Quitar fotografía"
            className="absolute right-1 top-1 flex h-5 w-5 items-center justify-center rounded-full bg-brand-ink/70 text-white opacity-0 transition-opacity group-hover/thumb:opacity-100"
          >
            <X className="h-3 w-3" aria-hidden />
          </button>
        </div>
      ))}
    </div>
  );
}

function AIVerificationSteps({ status }: { status: LocalPipelineStatus }) {
  const [activeStep, setActiveStep] = useState(0);

  useEffect(() => {
    if (status !== "verifying") return;
    const timers = verificationSteps.map((_, index) =>
      window.setTimeout(() => setActiveStep(index + 1), (index + 1) * 480),
    );
    return () => timers.forEach((timer) => window.clearTimeout(timer));
  }, [status]);

  if (status === "converting") {
    return (
      <div className="flex items-center gap-2.5 rounded-xl bg-gold/10 px-4 py-3">
        <BadgeCheck className="h-4 w-4 shrink-0 text-dark-gold" aria-hidden />
        <p className="text-sm text-obsessed">Documento verificado. Preparando PDF...</p>
      </div>
    );
  }

  return (
    <div className="rounded-xl bg-app-bg/60 px-4 py-3.5">
      <p className="mb-2.5 flex items-center gap-1.5 text-xs font-medium text-dark-gold">
        <ScanEye className="h-3.5 w-3.5" aria-hidden />
        Validación automática
      </p>
      <ul className="space-y-2">
        {verificationSteps.map((step, index) => {
          const isStepDone = index < activeStep;
          const isActive = index === activeStep;
          return (
            <li key={step} className="flex items-center gap-2.5 text-xs">
              <span
                className={cn(
                  "flex h-4 w-4 shrink-0 items-center justify-center rounded-full",
                  isStepDone
                    ? "bg-dark-gold text-white"
                    : isActive
                      ? "border-2 border-dark-gold text-dark-gold"
                      : "border-2 border-border text-transparent",
                )}
              >
                {isStepDone ? (
                  <Check className="h-2.5 w-2.5" aria-hidden />
                ) : isActive ? (
                  <Loader2 className="h-2.5 w-2.5 animate-spin" aria-hidden />
                ) : null}
              </span>
              <span className={isStepDone || isActive ? "text-obsessed" : "text-muted"}>{step}</span>
            </li>
          );
        })}
      </ul>
    </div>
  );
}

function PdfResultCard({ pageCount, onView }: { pageCount: number; onView: () => void }) {
  return (
    <div className="flex items-center gap-3.5 rounded-xl border border-success-text/20 bg-success-bg/60 px-4 py-3.5">
      <div className="flex h-12 w-12 shrink-0 items-center justify-center rounded-lg bg-card text-danger-text shadow-sm">
        <FileText className="h-6 w-6" aria-hidden />
      </div>
      <div className="min-w-0 flex-1">
        <p className="truncate text-sm font-semibold text-obsessed">Documento listo</p>
        <p className="text-xs text-success-text">
          {pageCount > 1 ? `${pageCount} páginas combinadas · ` : ""}Validación automática superada
        </p>
      </div>
      <button
        type="button"
        onClick={onView}
        className="inline-flex shrink-0 items-center gap-1.5 rounded-lg border border-border bg-card px-3 py-1.5 text-xs font-medium text-obsessed transition-colors hover:bg-app-bg"
      >
        <Eye className="h-3.5 w-3.5" aria-hidden />
        Ver
      </button>
    </div>
  );
}
