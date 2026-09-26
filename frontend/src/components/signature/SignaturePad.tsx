"use client";

import { Button } from "@/components/ui/Button";
import { RotateCcw } from "lucide-react";
import { useRef, useState, type PointerEvent } from "react";

interface SignaturePadProps {
  /** Recibe la firma como PNG en base64 (data URL), o null al limpiar. */
  onChange: (dataUrl: string | null) => void;
  hint?: string;
}

/** Recuadro para trazar la firma con el dedo o el mouse. Exporta PNG con fondo blanco. */
export function SignaturePad({ onChange, hint }: SignaturePadProps) {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const drawing = useRef(false);
  const [hasStroke, setHasStroke] = useState(false);

  const context = () => canvasRef.current?.getContext("2d") ?? null;

  const position = (e: PointerEvent<HTMLCanvasElement>) => {
    const canvas = e.currentTarget;
    const rect = canvas.getBoundingClientRect();
    return { x: ((e.clientX - rect.left) * canvas.width) / rect.width, y: ((e.clientY - rect.top) * canvas.height) / rect.height };
  };

  const start = (e: PointerEvent<HTMLCanvasElement>) => {
    drawing.current = true;
    e.currentTarget.setPointerCapture(e.pointerId);
    const ctx = context();
    const { x, y } = position(e);
    ctx?.beginPath();
    ctx?.moveTo(x, y);
  };

  const move = (e: PointerEvent<HTMLCanvasElement>) => {
    if (!drawing.current) return;
    const ctx = context();
    if (!ctx) return;
    const { x, y } = position(e);
    ctx.strokeStyle = "#1f1f20";
    ctx.lineWidth = 2.5;
    ctx.lineCap = "round";
    ctx.lineJoin = "round";
    ctx.lineTo(x, y);
    ctx.stroke();
    setHasStroke(true);
  };

  const end = () => {
    if (!drawing.current) return;
    drawing.current = false;
    const canvas = canvasRef.current;
    if (!canvas) return;
    // Exporta sobre fondo blanco (el trazo transparente se vería negro en algunos visores).
    const out = document.createElement("canvas");
    out.width = canvas.width;
    out.height = canvas.height;
    const ctx = out.getContext("2d");
    if (!ctx) return;
    ctx.fillStyle = "#ffffff";
    ctx.fillRect(0, 0, out.width, out.height);
    ctx.drawImage(canvas, 0, 0);
    onChange(out.toDataURL("image/png"));
  };

  const clear = () => {
    const canvas = canvasRef.current;
    const ctx = context();
    if (canvas && ctx) ctx.clearRect(0, 0, canvas.width, canvas.height);
    setHasStroke(false);
    onChange(null);
  };

  return (
    <div>
      <div className="rounded-xl border border-dashed border-border bg-white">
        <canvas
          ref={canvasRef}
          width={600}
          height={200}
          className="h-40 w-full touch-none rounded-xl"
          onPointerDown={start}
          onPointerMove={move}
          onPointerUp={end}
          onPointerLeave={end}
        />
      </div>
      <div className="mt-2 flex items-center justify-between gap-2">
        <p className="text-xs text-muted">{hint ?? "Traza tu firma dentro del recuadro."}</p>
        <Button type="button" variant="ghost" size="sm" onClick={clear}>
          <RotateCcw className="h-3.5 w-3.5" aria-hidden /> Limpiar
        </Button>
      </div>
      {!hasStroke ? <p className="mt-1 text-xs text-warning-text">Todavía no has trazado tu firma.</p> : null}
    </div>
  );
}
