"use client";

import { Input } from "@/components/ui/Input";
import { type CatalogCategory, type CatalogSubtype, findSubtype, listPropertyCatalog, subtypeLabel } from "@/lib/api/property-catalog";
import { cn } from "@/lib/utils";
import { ChevronDown } from "lucide-react";
import { useEffect, useId, useState } from "react";

// El catálogo cambia poco: una sola petición por carga de página para todos los selectores.
const cached: Record<"active" | "all", Promise<CatalogCategory[]> | null> = { active: null, all: null };

/**
 * Control de Cambios #26: catálogo vigente (solo lo que se ofrece en capturas nuevas) o, con
 * includeInactive, también lo desactivado (para filtros y reportes de registros históricos).
 */
export function usePropertyCatalog(includeInactive = false): { catalog: CatalogCategory[] | null; error: boolean } {
  const [catalog, setCatalog] = useState<CatalogCategory[] | null>(null);
  const [error, setError] = useState(false);
  useEffect(() => {
    let alive = true;
    const key = includeInactive ? "all" : "active";
    const request = cached[key] ?? (cached[key] = listPropertyCatalog(includeInactive));
    request
      .then((c) => alive && setCatalog(c))
      .catch(() => {
        cached[key] = null;
        if (alive) setError(true);
      });
    return () => {
      alive = false;
    };
  }, [includeInactive]);
  return { catalog, error };
}

/** Tras editar el catálogo en Configuración, los selectores vuelven a pedirlo. */
export function invalidatePropertyCatalog() {
  cached.active = null;
  cached.all = null;
}

/**
 * Filtros de cartera por categoría y subcategoría (Control de Cambios #26). subtypeId "none" =
 * pendientes de clasificación.
 */
export function PropertyTypeFilters({
  categoryId,
  subtypeId,
  onChange,
}: {
  categoryId: string | null;
  subtypeId: string | null;
  onChange: (value: { categoryId: string | null; subtypeId: string | null }) => void;
}) {
  const { catalog } = usePropertyCatalog(true);
  const category = catalog?.find((c) => c.id === categoryId) ?? null;
  const selectClass =
    "h-11 w-full appearance-none rounded-xl border border-border bg-card px-3.5 pr-10 text-sm text-obsessed focus:border-gold focus:outline-none focus:ring-4 focus:ring-gold/20";
  return (
    <>
      <div className="relative">
        <select
          aria-label="Filtrar por categoría de inmueble"
          className={selectClass}
          value={categoryId ?? ""}
          onChange={(e) => onChange({ categoryId: e.target.value || null, subtypeId: null })}
        >
          <option value="">Todas las categorías</option>
          {(catalog ?? []).map((c) => (
            <option key={c.id} value={c.id}>
              {c.name}
              {c.active ? "" : " (inactiva)"}
            </option>
          ))}
        </select>
        <ChevronDown className="pointer-events-none absolute right-3.5 top-1/2 h-4 w-4 -translate-y-1/2 text-muted" aria-hidden />
      </div>
      <div className="relative">
        <select
          aria-label="Filtrar por subcategoría de inmueble"
          className={selectClass}
          value={subtypeId ?? ""}
          onChange={(e) => onChange({ categoryId, subtypeId: e.target.value || null })}
        >
          <option value="">{category ? `Todo ${category.name}` : "Todas las subcategorías"}</option>
          <option value="none">Pendientes de clasificación</option>
          {(category ? [category] : (catalog ?? [])).map((c) => (
            <optgroup key={c.id} label={c.name}>
              {c.subtypes.map((s) => (
                <option key={s.id} value={s.id}>
                  {s.name}
                  {s.active ? "" : " (inactiva)"}
                </option>
              ))}
            </optgroup>
          ))}
        </select>
        <ChevronDown className="pointer-events-none absolute right-3.5 top-1/2 h-4 w-4 -translate-y-1/2 text-muted" aria-hidden />
      </div>
    </>
  );
}

interface PropertyTypeSelectProps {
  subtypeId: string | null;
  detail: string;
  onChange: (value: { subtypeId: string | null; detail: string; subtype: CatalogSubtype | null }) => void;
  label?: string;
  /** Subcategoría ya guardada que se desactivó después: se sigue mostrando para no perderla. */
  current?: { id: string; label: string } | null;
  error?: string;
  className?: string;
}

/**
 * Control de Cambios #26: menú de tipo de inmueble. Las categorías son
 * encabezados y debajo aparecen TODAS sus subcategorías (se recorre con
 * desplazamiento, sin tener que buscar). Solo se puede elegir una
 * subcategoría; la categoría se asigna sola. "Otro — especificar" pide describirlo.
 */
export function PropertyTypeSelect({ subtypeId, detail, onChange, label = "Tipo de inmueble", current, error, className }: PropertyTypeSelectProps) {
  const { catalog, error: loadError } = usePropertyCatalog();
  const id = useId();
  const selected = findSubtype(catalog, subtypeId);
  const keepsInactive = Boolean(current && subtypeId === current.id && !selected);

  return (
    <div className={cn("flex flex-col gap-3", className)}>
      <div className="flex flex-col gap-1.5">
        <label htmlFor={id} className="text-sm font-medium text-obsessed">
          {label}
        </label>
        <div className="relative">
          <select
            id={id}
            value={subtypeId ?? ""}
            disabled={!catalog}
            aria-invalid={error ? true : undefined}
            onChange={(e) => {
              const next = findSubtype(catalog, e.target.value || null);
              onChange({ subtypeId: e.target.value || null, detail: next?.requiresDetail ? detail : "", subtype: next });
            }}
            className={cn(
              "w-full appearance-none rounded-xl border bg-card px-3.5 py-2.5 pr-10 text-sm text-obsessed transition-colors focus:border-gold focus:outline-none focus:ring-4 focus:ring-gold/20 disabled:opacity-60",
              error ? "border-danger-text" : "border-border",
            )}
          >
            <option value="" disabled>
              {catalog ? "Selecciona el tipo de inmueble" : loadError ? "No se pudo cargar el catálogo" : "Cargando catálogo…"}
            </option>
            {keepsInactive && current ? <option value={current.id}>{current.label} (ya no se ofrece)</option> : null}
            {(catalog ?? []).map((category) => (
              <optgroup key={category.id} label={category.name}>
                {category.subtypes.map((s) => (
                  <option key={s.id} value={s.id}>
                    {s.name}
                  </option>
                ))}
              </optgroup>
            ))}
          </select>
          <ChevronDown className="pointer-events-none absolute right-3.5 top-1/2 h-4 w-4 -translate-y-1/2 text-muted" aria-hidden />
        </div>
        {selected ? (
          <p className="text-xs text-muted">{subtypeLabel(selected, detail)}</p>
        ) : error ? (
          <p role="alert" className="text-xs text-danger-text">
            {error}
          </p>
        ) : null}
      </div>
      {selected?.requiresDetail ? (
        <Input
          label="Describe el tipo de inmueble"
          value={detail}
          maxLength={160}
          required
          placeholder="Ej. Capilla, gasolinera, escuela"
          onChange={(e) => onChange({ subtypeId, detail: e.target.value, subtype: selected })}
        />
      ) : null}
    </div>
  );
}

/** Validación del formulario: subcategoría elegida y, si es "Otro", su descripción. */
export function propertyTypeError(catalog: CatalogCategory[] | null, subtypeId: string | null, detail: string, currentId?: string | null): string | null {
  if (!subtypeId) return "Selecciona el tipo de inmueble (la subcategoría, no solo la categoría).";
  const subtype = findSubtype(catalog, subtypeId);
  if (!subtype && subtypeId !== currentId) return "Selecciona un tipo de inmueble del catálogo.";
  if (subtype?.requiresDetail && !detail.trim()) return "Describe el tipo de inmueble.";
  return null;
}
