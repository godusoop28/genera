"use client";

import { cn } from "@/lib/utils";
import { useState } from "react";

export interface ChartDatum {
  label: string;
  value: number;
}

const compact = new Intl.NumberFormat("es-MX", { notation: "compact", maximumFractionDigits: 1 });

/**
 * Columnas de una sola serie (p. ej. prospectos por mes). Columna ≤24px con remate redondeado, base
 * común, cuadrícula tenue, valor solo en el máximo y en el último periodo, tooltip al pasar el
 * cursor o enfocar, y la tabla con todos los valores.
 */
export function ColumnChart({ data, title, unit }: { data: ChartDatum[]; title: string; unit: string }) {
  const [active, setActive] = useState<number | null>(null);
  const max = Math.max(1, ...data.map((d) => d.value));
  const top = niceMax(max);
  const ticks = [0, top / 2, top];
  const maxIndex = data.findIndex((d) => d.value === max);
  const height = 160;

  return (
    <figure className="space-y-3">
      <div className="relative" role="img" aria-label={`${title}: ${data.map((d) => `${d.label} ${d.value}`).join(", ")}`}>
        <div className="flex">
          <div className="relative mr-2 w-8 shrink-0 text-right text-[0.7rem] tabular-nums text-muted" style={{ height }} aria-hidden>
            {ticks.map((t) => (
              <span key={t} className="absolute right-0 -translate-y-1/2" style={{ top: height - (t / top) * height }}>
                {compact.format(t)}
              </span>
            ))}
          </div>
          <div className="relative min-w-0 flex-1" style={{ height }}>
            {ticks.map((t) => (
              <div key={t} className="absolute inset-x-0 border-t border-border" style={{ top: height - (t / top) * height }} aria-hidden />
            ))}
            <div className="absolute inset-0 flex items-end gap-[2px]">
              {data.map((d, i) => {
                const h = (d.value / top) * height;
                const labeled = d.value > 0 && (i === maxIndex || i === data.length - 1);
                return (
                  <button
                    key={d.label}
                    type="button"
                    className="group relative flex h-full flex-1 flex-col items-center justify-end focus-visible:outline-none"
                    onMouseEnter={() => setActive(i)}
                    onMouseLeave={() => setActive(null)}
                    onFocus={() => setActive(i)}
                    onBlur={() => setActive(null)}
                    aria-label={`${d.label}: ${d.value} ${unit}`}
                  >
                    {labeled ? <span className="mb-1 text-[0.7rem] font-semibold tabular-nums text-obsessed">{d.value}</span> : null}
                    <span
                      className={cn(
                        "block w-full max-w-6 rounded-t-[4px] bg-gold transition-opacity",
                        active !== null && active !== i && "opacity-50",
                        "group-focus-visible:ring-2 group-focus-visible:ring-obsessed",
                      )}
                      style={{ height: Math.max(d.value > 0 ? 2 : 0, h) }}
                    />
                    {active === i ? (
                      <span className="pointer-events-none absolute bottom-full z-10 mb-1 whitespace-nowrap rounded-lg border border-border bg-card px-2.5 py-1.5 text-xs text-obsessed shadow-md">
                        <span className="block text-muted">{d.label}</span>
                        <span className="font-semibold tabular-nums">
                          {d.value} {unit}
                        </span>
                      </span>
                    ) : null}
                  </button>
                );
              })}
            </div>
          </div>
        </div>
        <div className="ml-10 mt-1 flex gap-[2px] text-[0.65rem] text-muted" aria-hidden>
          {data.map((d, i) => (
            <span key={d.label} className="flex-1 truncate text-center">
              {i % 2 === 0 || data.length <= 6 ? d.label : ""}
            </span>
          ))}
        </div>
      </div>
      <DataTable data={data} unit={unit} />
    </figure>
  );
}

/** Barras horizontales de una sola serie, de mayor a menor, con el valor en la punta. */
export function BarList({ data, unit, empty }: { data: ChartDatum[]; unit: string; empty: string }) {
  const [active, setActive] = useState<number | null>(null);
  if (data.length === 0) return <p className="py-4 text-center text-sm text-muted">{empty}</p>;
  const max = Math.max(1, ...data.map((d) => d.value));
  const total = data.reduce((s, d) => s + d.value, 0);
  return (
    <ul className="space-y-2.5">
      {data.map((d, i) => (
        <li key={d.label}>
          <button
            type="button"
            className="relative grid w-full grid-cols-[minmax(0,9rem)_minmax(0,1fr)] items-center gap-3 rounded-lg text-left focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-gold/50"
            onMouseEnter={() => setActive(i)}
            onMouseLeave={() => setActive(null)}
            onFocus={() => setActive(i)}
            onBlur={() => setActive(null)}
            aria-label={`${d.label}: ${d.value} ${unit} (${Math.round((d.value / total) * 100)}%)`}
          >
            <span className="truncate text-sm text-obsessed" title={d.label}>
              {d.label}
            </span>
            <span className="flex items-center gap-2">
              <span
                className={cn("block h-4 max-w-full rounded-r-[4px] bg-gold transition-opacity", active !== null && active !== i && "opacity-50")}
                style={{ width: `${Math.max(2, (d.value / max) * 85)}%` }}
              />
              <span className="shrink-0 text-xs font-semibold tabular-nums text-obsessed">{d.value}</span>
            </span>
            {active === i ? (
              <span className="pointer-events-none absolute right-0 top-full z-10 mt-1 whitespace-nowrap rounded-lg border border-border bg-card px-2.5 py-1.5 text-xs text-obsessed shadow-md">
                {d.label}: <span className="font-semibold tabular-nums">{d.value}</span> {unit} · {Math.round((d.value / total) * 100)}%
              </span>
            ) : null}
          </button>
        </li>
      ))}
    </ul>
  );
}

function DataTable({ data, unit }: { data: ChartDatum[]; unit: string }) {
  return (
    <details className="text-sm">
      <summary className="cursor-pointer text-xs font-medium text-muted hover:text-obsessed">Ver como tabla</summary>
      <table className="mt-2 w-full text-left text-sm">
        <thead className="text-xs text-muted">
          <tr>
            <th scope="col" className="py-1 font-medium">Periodo</th>
            <th scope="col" className="py-1 text-right font-medium">{unit.charAt(0).toUpperCase() + unit.slice(1)}</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-border">
          {data.map((d) => (
            <tr key={d.label}>
              <td className="py-1 text-obsessed">{d.label}</td>
              <td className="py-1 text-right tabular-nums text-obsessed">{d.value}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </details>
  );
}

/** Tope "redondo" del eje (1, 2, 5, 10, 20…) para que las marcas queden en números limpios. */
function niceMax(value: number): number {
  const exponent = Math.pow(10, Math.floor(Math.log10(value)));
  for (const step of [1, 2, 5, 10]) {
    if (step * exponent >= value) return step * exponent;
  }
  return 10 * exponent;
}
