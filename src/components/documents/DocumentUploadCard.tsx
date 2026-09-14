"use client";

import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { cn } from "@/lib/utils";
import type { DocumentRequirement, DocumentStatus } from "@/types/expediente";
import {
  AlertTriangle,
  Check,
  FileImage,
  ImagePlus,
  Loader2,
  Upload,
} from "lucide-react";
import { useRef, useState, type ChangeEvent } from "react";

interface UploadedFile {
  id: string;
  name: string;
  url: string | null;
}

interface DocumentUploadCardProps {
  doc: DocumentRequirement;
  allowErrorDemo?: boolean;
}

const VALIDATION_DELAY_MIN = 800;
const VALIDATION_DELAY_MAX = 1200;

function randomDelay() {
  return VALIDATION_DELAY_MIN + Math.random() * (VALIDATION_DELAY_MAX - VALIDATION_DELAY_MIN);
}

function StatusPill({ status }: { status: DocumentStatus }) {
  if (status === "reviewing") {
    return (
      <span className="inline-flex items-center gap-1.5 text-xs font-medium text-muted">
        <Loader2 className="h-3.5 w-3.5 animate-spin" aria-hidden />
        Revisando...
      </span>
    );
  }
  if (status === "validated") {
    return (
      <span className="inline-flex items-center gap-1.5 text-xs font-medium text-success-text">
        <Check className="h-3.5 w-3.5" aria-hidden />
        Validado
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
  const [status, setStatus] = useState<DocumentStatus>("pending");
  const inputRef = useRef<HTMLInputElement>(null);

  const runValidation = () => {
    setStatus("reviewing");
    window.setTimeout(() => {
      setStatus("validated");
    }, randomDelay());
  };

  const handleSingleFile = (event: ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    if (!file) return;
    const url = file.type.startsWith("image/") ? URL.createObjectURL(file) : null;
    setFiles([{ id: `${file.name}-${Date.now()}`, name: file.name, url }]);
    runValidation();
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
    runValidation();
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
  const hasFiles = files.length > 0;

  return (
    <div
      className={cn(
        "rounded-2xl border bg-white p-5 shadow-sm transition-colors",
        isError ? "border-danger-text/50 ring-1 ring-danger-text/20" : "border-border",
      )}
    >
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <div className="flex items-center gap-2">
            <h3 className="text-sm font-semibold text-navy">{doc.name}</h3>
            {doc.required ? <Badge tone="teal">Obligatorio</Badge> : <Badge>Opcional</Badge>}
          </div>
          {doc.description ? (
            <p className="mt-1 text-sm text-muted">{doc.description}</p>
          ) : null}
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
        {doc.multiPage ? (
          <MultiFileArea
            files={files}
            inputRef={inputRef}
            onSelect={handleMultiFiles}
            isError={isError}
          />
        ) : (
          <SingleFileArea
            files={files}
            inputRef={inputRef}
            onSelect={handleSingleFile}
            isError={isError}
          />
        )}
      </div>

      <div className="mt-4 flex flex-wrap items-center gap-3">
        {isError ? (
          <Button size="sm" variant="secondary" onClick={handleReplace}>
            <Upload className="h-3.5 w-3.5" aria-hidden />
            Reemplazar fotografía
          </Button>
        ) : null}

        {allowErrorDemo && hasFiles && !isError ? (
          <button
            type="button"
            onClick={handleSimulateError}
            className="text-xs font-medium text-muted underline decoration-dotted hover:text-danger-text"
          >
            Simular imagen incorrecta (demo)
          </button>
        ) : null}
      </div>
    </div>
  );
}

interface FileAreaProps {
  files: UploadedFile[];
  inputRef: React.RefObject<HTMLInputElement | null>;
  onSelect: (event: ChangeEvent<HTMLInputElement>) => void;
  isError: boolean;
}

function SingleFileArea({ files, inputRef, onSelect, isError }: FileAreaProps) {
  const file = files[0];

  if (!file) {
    return (
      <label
        className={cn(
          "flex cursor-pointer flex-col items-center justify-center gap-2 rounded-xl border-2 border-dashed px-4 py-6 text-center transition-colors hover:bg-app-bg/60",
          "border-border",
        )}
      >
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

  return (
    <div
      className={cn(
        "flex items-center gap-3 rounded-xl border px-3.5 py-3",
        isError ? "border-danger-text/40 bg-danger-bg/40" : "border-border bg-app-bg/50",
      )}
    >
      <div className="flex h-14 w-14 shrink-0 items-center justify-center overflow-hidden rounded-lg bg-white border border-border">
        {file.url ? (
          // eslint-disable-next-line @next/next/no-img-element
          <img src={file.url} alt="" className="h-full w-full object-cover" />
        ) : (
          <FileImage className="h-5 w-5 text-muted" aria-hidden />
        )}
      </div>
      <div className="min-w-0 flex-1">
        <p className="truncate text-sm font-medium text-navy">{file.name}</p>
        <p className="text-xs text-muted">Archivo seleccionado</p>
      </div>
      <label className="shrink-0 cursor-pointer text-sm font-medium text-teal-dark hover:underline">
        Cambiar
        <input
          ref={inputRef}
          type="file"
          accept="image/jpeg,image/png"
          className="hidden"
          onChange={onSelect}
        />
      </label>
    </div>
  );
}

function MultiFileArea({ files, inputRef, onSelect, isError }: FileAreaProps) {
  const placeholders = ["Página 1", "Página 2", "Página 3"];

  return (
    <div>
      <div className="grid grid-cols-3 gap-3 sm:grid-cols-4">
        {files.length === 0
          ? placeholders.map((label) => (
              <div
                key={label}
                className="flex aspect-[3/4] flex-col items-center justify-center gap-1.5 rounded-xl border border-dashed border-border bg-app-bg/50 text-center"
              >
                <FileImage className="h-5 w-5 text-muted/60" aria-hidden />
                <span className="text-[11px] text-muted">{label}</span>
              </div>
            ))
          : files.map((file, index) => (
              <div
                key={file.id}
                className={cn(
                  "flex aspect-[3/4] flex-col overflow-hidden rounded-xl border bg-white",
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
              </div>
            ))}
      </div>
      <label className="mt-3 inline-flex cursor-pointer items-center gap-2 rounded-xl border border-border bg-white px-3.5 py-2 text-sm font-medium text-navy transition-colors hover:bg-app-bg">
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
