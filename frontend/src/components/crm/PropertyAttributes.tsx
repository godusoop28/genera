"use client";

import { useApi } from "@/components/crm/CrmUi";
import { Card, CardHeader } from "@/components/ui/Card";
import { Input } from "@/components/ui/Input";
import { Select } from "@/components/ui/Select";
import {
  AMENITIES_KEY,
  VIRTUAL_TOUR_KEY,
  getPropertyAttributes,
  type AttributeGroup,
  type PropertyAttribute,
  type PropertyAttributeCatalog,
} from "@/lib/api/properties";
import { formatPrice } from "@/lib/crm/labels";
import { useId } from "react";

// Atributos adicionales de 21Online (características, contrato y publicación).
// El catálogo lo da el backend (online21.ListingAttributes); los valores viajan
// con la misma clave de 21Online y el backend los valida.

export type Extras = Record<string, unknown>;

const groupTitles: Record<AttributeGroup, { title: string; description?: string }> = {
  FEATURES: { title: "Más características", description: "Se sincronizan con 21Online en ambos sentidos." },
  CONTRACT: {
    title: "Contrato y comisión",
    description: "Los marcados con * 21Online los recibe pero no los devuelve: aquí se conserva lo último que se capturó.",
  },
  PUBLISHING: { title: "Publicación en internet" },
};

export function usePropertyAttributes() {
  return useApi(() => getPropertyAttributes(), []);
}

function amenitiesOf(extras: Extras): Record<string, unknown> {
  const a = extras[AMENITIES_KEY];
  return a && typeof a === "object" ? (a as Record<string, unknown>) : {};
}

function text(v: unknown): string {
  return v === null || v === undefined ? "" : String(v);
}

const textareaClass =
  "w-full resize-y rounded-xl border border-border bg-card px-3.5 py-2.5 text-sm text-obsessed focus:border-gold focus:outline-none focus:ring-4 focus:ring-gold/20";

/** Campos del formulario de alta/edición. Un campo vacío se borra también en 21Online. */
export function PropertyAttributesFields({ catalog, value, onChange }: { catalog: PropertyAttributeCatalog; value: Extras; onChange: (next: Extras) => void }) {
  const set = (key: string, v: unknown) => {
    const next = { ...value };
    if (v === "" || v === null || v === undefined) delete next[key];
    else next[key] = v;
    onChange(next);
  };
  const amenities = amenitiesOf(value);
  const setAmenity = (key: string, v: unknown) => {
    const next = { ...amenities };
    if (v === "" || v === false || v === null || v === undefined) delete next[key];
    else next[key] = v;
    set(AMENITIES_KEY, Object.keys(next).length > 0 ? next : undefined);
  };

  return (
    <>
      {(["FEATURES", "CONTRACT", "PUBLISHING"] as AttributeGroup[]).map((group) => (
        <Card key={group}>
          <CardHeader title={groupTitles[group].title} description={groupTitles[group].description} />
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {catalog.attributes
              .filter((a) => a.group === group)
              .map((a) => (
                <AttributeField key={a.key} attribute={a} value={value[a.key]} onChange={(v) => set(a.key, v)} />
              ))}
            {group === "PUBLISHING" ? (
              <Input
                label="Recorrido virtual (liga)"
                type="url"
                placeholder="https://"
                value={text(amenities[VIRTUAL_TOUR_KEY])}
                onChange={(e) => setAmenity(VIRTUAL_TOUR_KEY, e.target.value.trim())}
              />
            ) : null}
          </div>
          {group === "FEATURES" ? (
            <fieldset className="mt-5">
              <legend className="text-sm font-medium text-obsessed">Amenidades</legend>
              <div className="mt-2 grid grid-cols-2 gap-x-4 gap-y-2 sm:grid-cols-3 lg:grid-cols-4">
                {catalog.amenities.map((a) => (
                  <label key={a.key} className="flex min-h-9 items-center gap-2 text-sm text-obsessed">
                    <input
                      type="checkbox"
                      className="h-4 w-4 rounded border-border accent-gold"
                      checked={amenities[a.key] === true}
                      onChange={(e) => setAmenity(a.key, e.target.checked)}
                    />
                    {a.label}
                  </label>
                ))}
              </div>
            </fieldset>
          ) : null}
        </Card>
      ))}
    </>
  );
}

function AttributeField({ attribute: a, value, onChange }: { attribute: PropertyAttribute; value: unknown; onChange: (v: unknown) => void }) {
  const id = useId();
  const label = a.writeOnly ? `${a.label} *` : a.label;
  switch (a.kind) {
    case "BOOLEAN":
      return (
        <Select
          label={label}
          value={value === true ? "true" : value === false ? "false" : ""}
          onChange={(e) => onChange(e.target.value === "" ? undefined : e.target.value === "true")}
          options={[
            { value: "", label: "Sin especificar" },
            { value: "true", label: "Sí" },
            { value: "false", label: "No" },
          ]}
        />
      );
    case "CHOICE": {
      const current = text(value);
      const known = a.options.some((o) => o.value === current);
      return (
        <Select
          label={label}
          value={current}
          onChange={(e) => onChange(e.target.value)}
          options={[
            { value: "", label: "Sin especificar" },
            // Un valor de 21Online fuera de la lista se muestra tal cual para no perderlo.
            ...(current && !known ? [{ value: current, label: current }] : []),
            ...a.options,
          ]}
        />
      );
    }
    case "LONG_TEXT":
      return (
        <div className="flex flex-col gap-1.5 sm:col-span-2 lg:col-span-3">
          <label htmlFor={id} className="text-sm font-medium text-obsessed">
            {label}
          </label>
          <textarea id={id} rows={4} maxLength={a.maxLength || undefined} value={text(value)} onChange={(e) => onChange(e.target.value)} className={textareaClass} />
        </div>
      );
    case "DATE":
      return <Input label={label} type="date" value={text(value).slice(0, 10)} onChange={(e) => onChange(e.target.value)} />;
    case "NUMBER":
    case "INTEGER":
      return <Input label={label} inputMode={a.kind === "INTEGER" ? "numeric" : "decimal"} value={text(value)} onChange={(e) => onChange(e.target.value.trim())} />;
    case "URL":
      return <Input label={label} type="url" placeholder="https://" value={text(value)} onChange={(e) => onChange(e.target.value.trim())} />;
    default:
      return <Input label={label} maxLength={a.maxLength || undefined} value={text(value)} onChange={(e) => onChange(e.target.value)} />;
  }
}

/** Valor legible de un atributo para la ficha. */
function display(a: PropertyAttribute, v: unknown): string {
  if (a.kind === "BOOLEAN") return v === true ? "Sí" : "No";
  if (a.kind === "CHOICE") return a.options.find((o) => o.value === v)?.label ?? text(v);
  if (a.key === "precioEnContrato") return formatPrice(Number(v));
  return text(v);
}

/** Resumen de solo lectura en la ficha de la propiedad. */
export function PropertyAttributesSummary({ catalog, extras }: { catalog: PropertyAttributeCatalog; extras: Extras }) {
  const amenities = amenitiesOf(extras);
  const amenityLabels = catalog.amenities.filter((a) => amenities[a.key] === true).map((a) => a.label);
  const tour = text(amenities[VIRTUAL_TOUR_KEY]);
  const groups = (["FEATURES", "CONTRACT", "PUBLISHING"] as AttributeGroup[])
    .map((g) => ({ g, rows: catalog.attributes.filter((a) => a.group === g && extras[a.key] !== undefined && extras[a.key] !== null && a.kind !== "LONG_TEXT") }))
    .filter((x) => x.rows.length > 0);
  const english = extras["descripcionIngles"];
  if (groups.length === 0 && amenityLabels.length === 0 && !tour && !english) {
    return null;
  }
  return (
    <Card>
      <CardHeader title="Más datos de 21Online" />
      <div className="space-y-5">
        {groups.map(({ g, rows }) => (
          <div key={g}>
            <h3 className="mb-2 text-xs font-semibold uppercase tracking-wide text-muted">{groupTitles[g].title}</h3>
            <dl className="grid grid-cols-1 gap-x-6 gap-y-2 text-sm sm:grid-cols-2">
              {rows.map((a) => (
                <div key={a.key} className="grid grid-cols-[minmax(0,1fr)_minmax(0,1fr)] gap-3">
                  <dt className="text-muted">{a.label}</dt>
                  <dd className="min-w-0 break-words text-obsessed">
                    {a.kind === "URL" ? (
                      <a href={text(extras[a.key])} target="_blank" rel="noreferrer" className="font-medium text-dark-gold hover:text-obsessed">
                        Abrir
                      </a>
                    ) : (
                      display(a, extras[a.key])
                    )}
                  </dd>
                </div>
              ))}
            </dl>
          </div>
        ))}
        {amenityLabels.length > 0 ? (
          <div>
            <h3 className="mb-2 text-xs font-semibold uppercase tracking-wide text-muted">Amenidades</h3>
            <p className="text-sm text-obsessed">{amenityLabels.join(" · ")}</p>
          </div>
        ) : null}
        {tour ? (
          <a href={tour} target="_blank" rel="noreferrer" className="inline-block text-sm font-medium text-dark-gold hover:text-obsessed">
            Ver recorrido virtual
          </a>
        ) : null}
        {english ? (
          <div>
            <h3 className="mb-2 text-xs font-semibold uppercase tracking-wide text-muted">Descripción en inglés</h3>
            <p className="whitespace-pre-line text-sm text-obsessed">{text(english)}</p>
          </div>
        ) : null}
      </div>
    </Card>
  );
}

/** Galería de fotos de 21Online (solo lectura). */
export function PropertyPhotos({ photos }: { photos: string[] }) {
  if (photos.length === 0) {
    return null;
  }
  return (
    <Card>
      <CardHeader title={`Fotos (${photos.length})`} description="Se suben y ordenan en 21Online." />
      <ul className="grid grid-cols-2 gap-2 sm:grid-cols-3 lg:grid-cols-4">
        {photos.map((url, i) => (
          <li key={url}>
            <a href={url} target="_blank" rel="noreferrer" className="block overflow-hidden rounded-xl bg-app-bg">
              {/* eslint-disable-next-line @next/next/no-img-element */}
              <img src={url} alt={`Foto ${i + 1}`} loading="lazy" className="aspect-[4/3] w-full object-cover" />
            </a>
          </li>
        ))}
      </ul>
    </Card>
  );
}
