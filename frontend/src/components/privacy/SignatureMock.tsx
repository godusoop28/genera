"use client";

import { Button } from "@/components/ui/Button";
import { PRIVACY_SIGNATURE_DISCLAIMER } from "@/data/privacy-reference";
import { RotateCcw } from "lucide-react";
import { useRef, useState, type PointerEvent } from "react";

interface SignatureMockProps {
  onSign: (dataUrl: string) => void;
  signed?: boolean;
}

// Firma dibujada en canvas, únicamente para fines de demostración visual.
// No integra ningún proveedor real de firma electrónica.
export function SignatureMock({ onSign, signed }: SignatureMockProps) {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const drawing = useRef(false);
  const [hasStroke, setHasStroke] = useState(Boolean(signed));

  const getContext = () => canvasRef.current?.getContext("2d") ?? null;

  const pointerPos = (e: PointerEvent<HTMLCanvasElement>) => {
    const rect = e.currentTarget.getBoundingClientRect();
    return { x: e.clientX - rect.left, y: e.clientY - rect.top };
  };

  const start = (e: PointerEvent<HTMLCanvasElement>) => {
    drawing.current = true;
    const ctx = getContext();
    const { x, y } = pointerPos(e);
    ctx?.beginPath();
    ctx?.moveTo(x, y);
  };

  const move = (e: PointerEvent<HTMLCanvasElement>) => {
    if (!drawing.current) return;
    const ctx = getContext();
    if (!ctx) return;
    const { x, y } = pointerPos(e);
    ctx.strokeStyle = "#252526";
    ctx.lineWidth = 2;
    ctx.lineCap = "round";
    ctx.lineTo(x, y);
    ctx.stroke();
    setHasStroke(true);
  };

  const end = () => {
    if (!drawing.current) return;
    drawing.current = false;
    const canvas = canvasRef.current;
    if (canvas) onSign(canvas.toDataURL());
  };

  const clear = () => {
    const canvas = canvasRef.current;
    const ctx = getContext();
    if (canvas && ctx) ctx.clearRect(0, 0, canvas.width, canvas.height);
    setHasStroke(false);
  };

  return (
    <div>
      <div className="rounded-xl border border-dashed border-border bg-app-bg/40">
        <canvas
          ref={canvasRef}
          width={480}
          height={160}
          className="h-40 w-full touch-none rounded-xl"
          onPointerDown={start}
          onPointerMove={move}
          onPointerUp={end}
          onPointerLeave={end}
        />
      </div>
      <div className="mt-2 flex items-center justify-between">
        <p className="text-xs text-muted">{PRIVACY_SIGNATURE_DISCLAIMER}</p>
        <Button type="button" variant="ghost" size="sm" onClick={clear}>
          <RotateCcw className="h-3.5 w-3.5" /> Limpiar
        </Button>
      </div>
      {!hasStroke ? <p className="mt-1 text-xs text-warning-text">Dibuja tu firma para continuar.</p> : null}
    </div>
  );
}
