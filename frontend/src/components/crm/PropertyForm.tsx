"use client";

import { errorText } from "@/components/crm/CrmUi";
import { PropertyTypeSelect, propertyTypeError, usePropertyCatalog } from "@/components/property/PropertyTypeSelect";
import { Button } from "@/components/ui/Button";
import { Card, CardHeader } from "@/components/ui/Card";
import { Input } from "@/components/ui/Input";
import { Select } from "@/components/ui/Select";
import { Toggle } from "@/components/ui/Toggle";
import type { AdvisorRouting } from "@/lib/api/crm";
import type { ListingStatus, Operation, PropertyData, PropertyType } from "@/lib/api/properties";
import { findSubtype } from "@/lib/api/property-catalog";
import { contractLabels, editableStatuses, listingStatusLabels, mexicanStates, operationLabels, subtypeLabel, typeLabels } from "@/lib/crm/property-labels";
import { useState, type FormEvent } from "react";

/** Valores del formulario como texto (los inputs numéricos vacíos son null al enviar). */
type FormState = {
  operation: Operation;
  propertyType: PropertyType;
  subtype: string;
  /** Control de Cambios #26: el tipo se elige en el catálogo de inmuebles. */
  catalogSubtypeId: string | null;
  propertyTypeDetail: string;
  contractType: string;
  status: ListingStatus;
  headline: string;
  description: string;
  price: string;
  currency: "MXN" | "USD";
  hidePrice: boolean;
  street: string;
  extNumber: string;
  neighborhood: string;
  municipality: string;
  state: string;
  postalCode: string;
  hideStreet: boolean;
  hideNumber: boolean;
  bedrooms: string;
  bathrooms: string;
  halfBathrooms: string;
  parking: string;
  landM2: string;
  builtM2: string;
  levels: string;
  age: string;
  maintenanceFee: string;
  commission: string;
  publishOnline: boolean;
  advisorUserId: string;
};

const text = (v: number | string | null | undefined) => (v === null || v === undefined ? "" : String(v));

function initialState(data?: PropertyData): FormState {
  return {
    operation: data?.operation ?? "venta",
    propertyType: data?.propertyType ?? "habitacional",
    subtype: data?.subtype ?? "casa",
    catalogSubtypeId: data?.catalogSubtypeId ?? null,
    propertyTypeDetail: data?.propertyTypeDetail ?? "",
    contractType: data?.contractType ?? "exclusiva",
    status: data?.status ?? "enPromocion",
    headline: data?.headline ?? "",
    description: data?.description ?? "",
    price: text(data ? (data.operation === "renta" ? data.rentPrice : data.salePrice) : null),
    currency: data?.currency ?? "MXN",
    hidePrice: data?.hidePrice ?? false,
    street: data?.street ?? "",
    extNumber: data?.extNumber ?? "",
    neighborhood: data?.neighborhood ?? "",
    municipality: data?.municipality ?? "",
    state: data?.state ?? "Morelos",
    postalCode: data?.postalCode ?? "",
    hideStreet: data?.hideStreet ?? false,
    hideNumber: data?.hideNumber ?? true,
    bedrooms: text(data?.bedrooms),
    bathrooms: text(data?.bathrooms),
    halfBathrooms: text(data?.halfBathrooms),
    parking: text(data?.parking),
    landM2: text(data?.landM2),
    builtM2: text(data?.builtM2),
    levels: text(data?.levels),
    age: text(data?.age),
    maintenanceFee: text(data?.maintenanceFee),
    commission: text(data?.commission),
    publishOnline: data?.publishOnline ?? true,
    advisorUserId: data?.advisorUserId ?? "",
  };
}

function num(value: string): number | null {
  const cleaned = value.replace(/[$,\s]/g, "");
  if (cleaned === "") return null;
  const n = Number(cleaned);
  return Number.isFinite(n) ? n : null;
}

function toData(f: FormState): PropertyData {
  const price = num(f.price);
  return {
    operation: f.operation,
    propertyType: f.propertyType,
    subtype: f.subtype,
    contractType: f.contractType || null,
    status: f.status,
    headline: f.headline.trim(),
    description: f.description.trim() || null,
    salePrice: f.operation === "venta" ? price : null,
    rentPrice: f.operation === "renta" ? price : null,
    currency: f.currency,
    hidePrice: f.hidePrice,
    street: f.street.trim() || null,
    extNumber: f.extNumber.trim() || null,
    neighborhood: f.neighborhood.trim(),
    municipality: f.municipality.trim(),
    state: f.state,
    postalCode: f.postalCode.trim() || null,
    hideStreet: f.hideStreet,
    hideNumber: f.hideNumber,
    bedrooms: num(f.bedrooms),
    bathrooms: num(f.bathrooms),
    halfBathrooms: num(f.halfBathrooms),
    parking: num(f.parking),
    landM2: num(f.landM2),
    builtM2: num(f.builtM2),
    levels: num(f.levels),
    age: num(f.age),
    maintenanceFee: num(f.maintenanceFee),
    commission: num(f.commission),
    publishOnline: f.publishOnline,
    advisorUserId: f.advisorUserId || null,
    catalogSubtypeId: f.catalogSubtypeId,
    propertyTypeDetail: f.propertyTypeDetail.trim() || null,
  };
}

const textareaClass =
  "w-full resize-y rounded-xl border border-border bg-card px-3.5 py-2.5 text-sm text-obsessed focus:border-gold focus:outline-none focus:ring-4 focus:ring-gold/20";

/**
 * Alta o edición de una propiedad. Al guardar, el backend la publica en
 * 21Online; si 21Online la rechaza, el motivo se muestra en la ficha.
 */
export function PropertyForm({
  initial,
  advisors,
  canChooseAdvisor,
  submitLabel,
  onSubmit,
  onCancel,
}: {
  initial?: PropertyData;
  advisors: AdvisorRouting[];
  canChooseAdvisor: boolean;
  submitLabel: string;
  onSubmit: (data: PropertyData) => Promise<void>;
  onCancel: () => void;
}) {
  const [form, setForm] = useState<FormState>(() => initialState(initial));
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const set = <K extends keyof FormState>(key: K) => (value: FormState[K]) => setForm((f) => ({ ...f, [key]: value }));

  const { catalog } = usePropertyCatalog();
  const typeProblem = propertyTypeError(catalog, form.catalogSubtypeId, form.propertyTypeDetail, initial?.catalogSubtypeId);
  const missing: string[] = [];
  if (typeProblem) missing.push("tipo de inmueble");
  if (!form.headline.trim()) missing.push("encabezado");
  if (num(form.price) === null || (num(form.price) ?? 0) <= 0) missing.push("precio");
  if (!form.neighborhood.trim()) missing.push("colonia");
  if (!form.municipality.trim()) missing.push("municipio");

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    if (missing.length > 0) return;
    setSaving(true);
    setError(null);
    try {
      await onSubmit(toData(form));
    } catch (err) {
      setError(errorText(err));
      setSaving(false);
    }
  };

  const isLand = form.subtype === "terreno";
  const legacyType = !form.catalogSubtypeId && initial ? `${subtypeLabel(initial.subtype)} (${typeLabels[initial.propertyType]})` : null;

  return (
    <form onSubmit={submit} className="space-y-5">
      <Card>
        <CardHeader title="Operación y tipo" description="Se publica en 21Online con estos mismos datos." />
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
          <Select
            label="Operación"
            value={form.operation}
            onChange={(e) => set("operation")(e.target.value as Operation)}
            options={(Object.keys(operationLabels) as Operation[]).map((o) => ({ value: o, label: operationLabels[o] }))}
          />
          <div className="sm:col-span-2">
            <PropertyTypeSelect
              subtypeId={form.catalogSubtypeId}
              detail={form.propertyTypeDetail}
              current={initial?.catalogSubtypeId ? { id: initial.catalogSubtypeId, label: "Tipo guardado" } : null}
              onChange={({ subtypeId, detail, subtype }) =>
                setForm((f) => ({
                  ...f,
                  catalogSubtypeId: subtypeId,
                  propertyTypeDetail: detail,
                  // Se publica en 21Online con el tipo y subtipo que corresponden a la subcategoría.
                  propertyType: (subtype?.online21Type as PropertyType | undefined) ?? f.propertyType,
                  subtype: subtype?.online21Subtype ?? f.subtype,
                }))
              }
              error={typeProblem && form.catalogSubtypeId ? typeProblem : undefined}
            />
            {legacyType ? <p className="mt-1 text-xs text-warning-text">Se capturó antes del catálogo como {legacyType}. Elige su tipo de inmueble.</p> : null}
            {findSubtype(catalog, form.catalogSubtypeId) ? (
              <p className="mt-1 text-xs text-muted">En 21Online: {subtypeLabel(form.subtype)} ({typeLabels[form.propertyType]})</p>
            ) : null}
          </div>
          <Select
            label="Contrato con el propietario"
            value={form.contractType}
            onChange={(e) => set("contractType")(e.target.value)}
            options={[{ value: "", label: "Sin especificar" }, ...Object.entries(contractLabels).map(([value, label]) => ({ value, label }))]}
          />
          <Select
            label="Estatus en 21Online"
            value={form.status}
            onChange={(e) => set("status")(e.target.value as ListingStatus)}
            options={editableStatuses.map((s) => ({ value: s, label: listingStatusLabels[s] }))}
          />
          {canChooseAdvisor ? (
            <Select
              label="Asesor responsable"
              value={form.advisorUserId}
              onChange={(e) => set("advisorUserId")(e.target.value)}
              options={[
                { value: "", label: "Sin asesor (queda a nombre de la llave de 21Online)" },
                ...advisors.map((a) => ({ value: a.userId, label: a.online21UserId ? a.name : `${a.name} · sin usuario de 21Online` })),
              ]}
            />
          ) : (
            <p className="self-end pb-2.5 text-sm text-muted">Quedará a tu nombre como asesor.</p>
          )}
        </div>
      </Card>

      <Card>
        <CardHeader title="Anuncio y precio" />
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
          <Input
            label="Encabezado del anuncio"
            required
            maxLength={200}
            placeholder="Ej. Casa con jardín y alberca en Burgos"
            value={form.headline}
            onChange={(e) => set("headline")(e.target.value)}
            containerClassName="sm:col-span-2 lg:col-span-3"
          />
          <div className="flex flex-col gap-1.5 sm:col-span-2 lg:col-span-3">
            <label htmlFor="property-description" className="text-sm font-medium text-obsessed">
              Descripción
            </label>
            <textarea id="property-description" rows={5} maxLength={8000} value={form.description} onChange={(e) => set("description")(e.target.value)} className={textareaClass} />
          </div>
          <Input
            label={form.operation === "renta" ? "Renta mensual" : "Precio de venta"}
            required
            inputMode="decimal"
            placeholder={form.operation === "renta" ? "15000" : "4500000"}
            value={form.price}
            onChange={(e) => set("price")(e.target.value)}
          />
          <Select
            label="Moneda"
            value={form.currency}
            onChange={(e) => set("currency")(e.target.value as "MXN" | "USD")}
            options={[
              { value: "MXN", label: "Pesos (MXN)" },
              { value: "USD", label: "Dólares (USD)" },
            ]}
          />
          <Input label="Comisión (%)" inputMode="decimal" placeholder="5" value={form.commission} onChange={(e) => set("commission")(e.target.value)} />
          {!isLand ? (
            <Input label="Cuota de mantenimiento" inputMode="decimal" value={form.maintenanceFee} onChange={(e) => set("maintenanceFee")(e.target.value)} />
          ) : null}
        </div>
        <div className="mt-4 grid grid-cols-1 gap-x-8 gap-y-2 sm:grid-cols-2">
          <Toggle id="publish-online" checked={form.publishOnline} onChange={set("publishOnline")} label="Publicar en internet" description="Portales y sitio de CENTURY 21 vía 21Online." />
          <Toggle id="hide-price" checked={form.hidePrice} onChange={set("hidePrice")} label="Ocultar precio en internet" />
        </div>
      </Card>

      <Card>
        <CardHeader title="Ubicación" description="Estado y municipio son obligatorios para 21Online." />
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
          <Input label="Calle" value={form.street} onChange={(e) => set("street")(e.target.value)} containerClassName="lg:col-span-2" />
          <Input label="Número" value={form.extNumber} onChange={(e) => set("extNumber")(e.target.value)} />
          <Input label="Colonia" required value={form.neighborhood} onChange={(e) => set("neighborhood")(e.target.value)} />
          <Input label="Municipio" required value={form.municipality} onChange={(e) => set("municipality")(e.target.value)} />
          <Select label="Estado" value={form.state} onChange={(e) => set("state")(e.target.value)} options={mexicanStates.map((s) => ({ value: s, label: s }))} />
          <Input label="Código postal" inputMode="numeric" maxLength={10} value={form.postalCode} onChange={(e) => set("postalCode")(e.target.value)} />
        </div>
        <div className="mt-4 grid grid-cols-1 gap-x-8 gap-y-2 sm:grid-cols-2">
          <Toggle id="hide-street" checked={form.hideStreet} onChange={set("hideStreet")} label="Ocultar la calle en internet" />
          <Toggle id="hide-number" checked={form.hideNumber} onChange={set("hideNumber")} label="Ocultar el número en internet" />
        </div>
      </Card>

      <Card>
        <CardHeader title="Características" />
        <div className="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-4">
          <Input label="Terreno (m²)" inputMode="decimal" value={form.landM2} onChange={(e) => set("landM2")(e.target.value)} />
          {!isLand ? (
            <>
              <Input label="Construcción (m²)" inputMode="decimal" value={form.builtM2} onChange={(e) => set("builtM2")(e.target.value)} />
              <Input label="Recámaras" inputMode="numeric" value={form.bedrooms} onChange={(e) => set("bedrooms")(e.target.value)} />
              <Input label="Baños completos" inputMode="decimal" value={form.bathrooms} onChange={(e) => set("bathrooms")(e.target.value)} />
              <Input label="Medios baños" inputMode="numeric" value={form.halfBathrooms} onChange={(e) => set("halfBathrooms")(e.target.value)} />
              <Input label="Estacionamientos" inputMode="numeric" value={form.parking} onChange={(e) => set("parking")(e.target.value)} />
              <Input label="Niveles" inputMode="numeric" value={form.levels} onChange={(e) => set("levels")(e.target.value)} />
              <Input label="Antigüedad (años)" inputMode="numeric" value={form.age} onChange={(e) => set("age")(e.target.value)} />
            </>
          ) : null}
        </div>
        <p className="mt-4 text-xs text-muted">Las fotos se suben en 21Online: su API no recibe archivos de imagen.</p>
      </Card>

      {error ? (
        <p role="alert" className="rounded-xl bg-danger-bg px-4 py-3 text-sm text-danger-text">
          {error}
        </p>
      ) : null}

      <div className="flex flex-col-reverse gap-2 sm:flex-row sm:items-center sm:justify-end">
        {missing.length > 0 ? <p className="text-sm text-muted sm:mr-auto">Falta: {missing.join(", ")}.</p> : null}
        <Button variant="secondary" type="button" onClick={onCancel}>
          Cancelar
        </Button>
        <Button type="submit" disabled={saving || missing.length > 0}>
          {saving ? "Guardando y enviando a 21Online…" : submitLabel}
        </Button>
      </div>
    </form>
  );
}
