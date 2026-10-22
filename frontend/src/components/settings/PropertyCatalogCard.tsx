"use client";

import { invalidatePropertyCatalog } from "@/components/property/PropertyTypeSelect";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Card, CardHeader } from "@/components/ui/Card";
import { Input } from "@/components/ui/Input";
import { Modal } from "@/components/ui/Modal";
import { Select } from "@/components/ui/Select";
import { Toggle } from "@/components/ui/Toggle";
import { useToast } from "@/components/ui/Toast";
import {
  caseTypeLabels,
  createCatalogCategory,
  createCatalogSubtype,
  listPropertyCatalog,
  updateCatalogCategory,
  updateCatalogSubtype,
  type CatalogCategory,
  type CatalogSubtype,
  type CatalogSubtypeInput,
} from "@/lib/api/property-catalog";
import type { BackendPropertyCaseType } from "@/lib/api/types";
import { subtypeLabel as online21SubtypeLabel, subtypesByType } from "@/lib/crm/property-labels";
import { errorText } from "@/lib/errors";
import { useCan } from "@/lib/permissions";
import { ChevronDown, ChevronRight, Pencil, Plus } from "lucide-react";
import { useCallback, useEffect, useState } from "react";

type CategoryForm = { id: string | null; name: string; sortOrder: string; active: boolean };
type SubtypeForm = { id: string | null } & Omit<CatalogSubtypeInput, "sortOrder"> & { sortOrder: string };

/**
 * Control de Cambios #26: el administrador agrega, edita o desactiva categorías y subcategorías
 * del catálogo de inmuebles sin cambios de código. Nada se borra: lo desactivado deja de
 * ofrecerse en capturas nuevas, pero los registros que ya lo tienen lo conservan.
 */
export function PropertyCatalogCard() {
  const can = useCan();
  const allowed = can("PROPERTY_CATALOG_MANAGE");
  const { showToast } = useToast();
  const [catalog, setCatalog] = useState<CatalogCategory[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [open, setOpen] = useState<Set<string>>(() => new Set());
  const [categoryForm, setCategoryForm] = useState<CategoryForm | null>(null);
  const [subtypeForm, setSubtypeForm] = useState<SubtypeForm | null>(null);
  const [saving, setSaving] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);

  const load = useCallback(() => {
    listPropertyCatalog(true)
      .then((c) => {
        setCatalog(c);
        setError(null);
      })
      .catch((err) => setError(errorText(err)));
  }, []);

  useEffect(() => {
    if (allowed) load();
  }, [allowed, load]);

  if (!allowed) return null;

  const toggle = (id: string) =>
    setOpen((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });

  const saved = (message: string) => {
    invalidatePropertyCatalog();
    showToast(message);
    load();
  };

  const saveCategory = async () => {
    if (!categoryForm) return;
    setSaving(true);
    setFormError(null);
    const body = { name: categoryForm.name.trim(), sortOrder: Number(categoryForm.sortOrder) || 0, active: categoryForm.active };
    try {
      if (categoryForm.id) await updateCatalogCategory(categoryForm.id, body);
      else await createCatalogCategory(body);
      setCategoryForm(null);
      saved("Categoría guardada.");
    } catch (err) {
      setFormError(errorText(err));
    } finally {
      setSaving(false);
    }
  };

  const saveSubtype = async () => {
    if (!subtypeForm) return;
    setSaving(true);
    setFormError(null);
    const { id, sortOrder, ...rest } = subtypeForm;
    const body: CatalogSubtypeInput = { ...rest, name: rest.name.trim(), sortOrder: Number(sortOrder) || 0, keyCode: rest.keyCode?.trim() || null };
    try {
      if (id) await updateCatalogSubtype(id, body);
      else await createCatalogSubtype(body);
      setSubtypeForm(null);
      saved("Subcategoría guardada.");
    } catch (err) {
      setFormError(errorText(err));
    } finally {
      setSaving(false);
    }
  };

  const nextOrder = (items: { sortOrder: number }[]) => String((items.reduce((m, i) => Math.max(m, i.sortOrder), 0) || 0) + 10);

  const editSubtype = (s: CatalogSubtype) => {
    setFormError(null);
    setSubtypeForm({
      id: s.id,
      categoryId: s.categoryId,
      name: s.name,
      sortOrder: String(s.sortOrder),
      active: s.active,
      requiresDetail: s.requiresDetail,
      caseType: s.caseType,
      online21Type: s.online21Type,
      online21Subtype: s.online21Subtype,
      keyCode: s.keyCode,
    });
  };

  return (
    <Card>
      <CardHeader
        title="Catálogo de inmuebles"
        description="Categorías y subcategorías del menú “Tipo de inmueble”. Desactivar no borra nada: deja de ofrecerse en capturas nuevas."
        action={
          <Button
            size="sm"
            variant="secondary"
            onClick={() => {
              setFormError(null);
              setCategoryForm({ id: null, name: "", sortOrder: nextOrder(catalog ?? []), active: true });
            }}
            disabled={!catalog}
          >
            <Plus className="h-4 w-4" aria-hidden /> Categoría
          </Button>
        }
      />
      {error ? (
        <p role="alert" className="text-sm text-danger-text">
          {error}{" "}
          <button type="button" className="underline" onClick={load}>
            Reintentar
          </button>
        </p>
      ) : !catalog ? (
        <p className="text-sm text-muted">Cargando catálogo…</p>
      ) : (
        <ul className="divide-y divide-border rounded-xl border border-border">
          {catalog.map((category) => {
            const expanded = open.has(category.id);
            return (
              <li key={category.id}>
                <div className="flex items-center gap-2 px-3 py-2.5">
                  <button
                    type="button"
                    onClick={() => toggle(category.id)}
                    aria-expanded={expanded}
                    className="flex min-w-0 flex-1 items-center gap-2 text-left text-sm font-medium text-obsessed focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-gold/40"
                  >
                    {expanded ? <ChevronDown className="h-4 w-4 shrink-0" aria-hidden /> : <ChevronRight className="h-4 w-4 shrink-0" aria-hidden />}
                    <span className="truncate">{category.name}</span>
                    <span className="shrink-0 text-xs font-normal text-muted">({category.subtypes.length})</span>
                    {category.active ? null : <Badge tone="neutral">Inactiva</Badge>}
                  </button>
                  <Button
                    size="sm"
                    variant="ghost"
                    aria-label={`Editar la categoría ${category.name}`}
                    onClick={() => {
                      setFormError(null);
                      setCategoryForm({ id: category.id, name: category.name, sortOrder: String(category.sortOrder), active: category.active });
                    }}
                  >
                    <Pencil className="h-3.5 w-3.5" aria-hidden />
                  </Button>
                </div>
                {expanded ? (
                  <div className="border-t border-border bg-app-bg/50 px-3 py-2">
                    <ul className="divide-y divide-border">
                      {category.subtypes.map((s) => (
                        <li key={s.id} className="flex items-center gap-2 py-2 pl-6">
                          <span className="min-w-0 flex-1 text-sm text-obsessed">
                            {s.name}
                            <span className="block text-xs text-muted">
                              {caseTypeLabels[s.caseType]} · 21Online: {online21SubtypeLabel(s.online21Subtype)}
                              {s.requiresDetail ? " · pide descripción" : ""}
                              {s.keyCode ? ` · clave: ${s.keyCode}` : " · sin letras de clave"}
                            </span>
                          </span>
                          {s.active ? null : <Badge tone="neutral">Inactiva</Badge>}
                          <Button size="sm" variant="ghost" aria-label={`Editar ${s.name}`} onClick={() => editSubtype(s)}>
                            <Pencil className="h-3.5 w-3.5" aria-hidden />
                          </Button>
                        </li>
                      ))}
                    </ul>
                    <Button
                      size="sm"
                      variant="ghost"
                      className="mt-1"
                      onClick={() => {
                        setFormError(null);
                        setSubtypeForm({
                          id: null,
                          categoryId: category.id,
                          name: "",
                          sortOrder: nextOrder(category.subtypes),
                          active: true,
                          requiresDetail: false,
                          caseType: "COMMERCIAL",
                          online21Type: "comercial",
                          online21Subtype: "inmueble-productivo",
                          keyCode: null,
                        });
                      }}
                    >
                      <Plus className="h-4 w-4" aria-hidden /> Subcategoría en {category.name}
                    </Button>
                  </div>
                ) : null}
              </li>
            );
          })}
        </ul>
      )}

      {categoryForm ? (
        <Modal
          open
          onClose={() => setCategoryForm(null)}
          title={categoryForm.id ? "Editar categoría" : "Nueva categoría"}
          footer={
            <>
              <Button variant="ghost" onClick={() => setCategoryForm(null)}>
                Cancelar
              </Button>
              <Button onClick={saveCategory} disabled={saving || !categoryForm.name.trim()}>
                {saving ? "Guardando…" : "Guardar"}
              </Button>
            </>
          }
        >
          <div className="flex flex-col gap-4">
            <Input label="Nombre" value={categoryForm.name} maxLength={80} onChange={(e) => setCategoryForm({ ...categoryForm, name: e.target.value })} />
            <Input
              label="Orden en el menú"
              type="number"
              value={categoryForm.sortOrder}
              onChange={(e) => setCategoryForm({ ...categoryForm, sortOrder: e.target.value })}
              hint="Número menor = más arriba."
            />
            <Toggle
              id="category-active"
              checked={categoryForm.active}
              onChange={(v) => setCategoryForm({ ...categoryForm, active: v })}
              label="Se ofrece en capturas nuevas"
              description="Si la desactivas, sus subcategorías dejan de aparecer en el menú; los registros existentes no cambian."
            />
            {formError ? (
              <p role="alert" className="rounded-lg bg-danger-bg px-3 py-2 text-sm text-danger-text">
                {formError}
              </p>
            ) : null}
          </div>
        </Modal>
      ) : null}

      {subtypeForm ? (
        <Modal
          open
          onClose={() => setSubtypeForm(null)}
          title={subtypeForm.id ? "Editar subcategoría" : "Nueva subcategoría"}
          size="lg"
          footer={
            <>
              <Button variant="ghost" onClick={() => setSubtypeForm(null)}>
                Cancelar
              </Button>
              <Button onClick={saveSubtype} disabled={saving || !subtypeForm.name.trim()}>
                {saving ? "Guardando…" : "Guardar"}
              </Button>
            </>
          }
        >
          <div className="grid gap-4 sm:grid-cols-2">
            <Input label="Nombre" value={subtypeForm.name} maxLength={80} onChange={(e) => setSubtypeForm({ ...subtypeForm, name: e.target.value })} />
            <Select
              label="Categoría"
              value={subtypeForm.categoryId}
              onChange={(e) => setSubtypeForm({ ...subtypeForm, categoryId: e.target.value })}
              options={(catalog ?? []).map((c) => ({ value: c.id, label: c.name }))}
            />
            <Select
              label="Documentos y contrato"
              value={subtypeForm.caseType}
              onChange={(e) => setSubtypeForm({ ...subtypeForm, caseType: e.target.value as BackendPropertyCaseType })}
              options={(Object.keys(caseTypeLabels) as BackendPropertyCaseType[]).map((c) => ({ value: c, label: caseTypeLabels[c] }))}
              containerClassName="sm:col-span-2"
            />
            <Select
              label="Tipo en 21Online"
              value={subtypeForm.online21Type}
              onChange={(e) => {
                const type = e.target.value as "habitacional" | "comercial";
                setSubtypeForm({ ...subtypeForm, online21Type: type, online21Subtype: subtypesByType[type][0] });
              }}
              options={[
                { value: "habitacional", label: "Habitacional" },
                { value: "comercial", label: "Comercial" },
              ]}
            />
            <Select
              label="Subtipo en 21Online"
              value={subtypeForm.online21Subtype}
              onChange={(e) => setSubtypeForm({ ...subtypeForm, online21Subtype: e.target.value })}
              options={subtypesByType[subtypeForm.online21Type].map((s) => ({ value: s, label: online21SubtypeLabel(s) }))}
            />
            <Input
              label="Letras en la clave"
              hint="1 a 3 letras, p. ej. C para Casa (CVEA03). Vacío: no se genera clave automática."
              value={subtypeForm.keyCode ?? ""}
              maxLength={3}
              onChange={(e) => setSubtypeForm({ ...subtypeForm, keyCode: e.target.value.toUpperCase().replace(/[^A-Z]/g, "") })}
            />
            <Input
              label="Letras en la clave"
              hint="1 a 3 letras, p. ej. C para Casa (CVEA03). Vacío: no se genera clave automática."
              value={subtypeForm.keyCode ?? ""}
              maxLength={3}
              onChange={(e) => setSubtypeForm({ ...subtypeForm, keyCode: e.target.value.toUpperCase().replace(/[^A-Z]/g, "") })}
            />
            <Input
              label="Orden en el menú"
              type="number"
              value={subtypeForm.sortOrder}
              onChange={(e) => setSubtypeForm({ ...subtypeForm, sortOrder: e.target.value })}
            />
            <div className="flex flex-col gap-3 sm:col-span-2">
              <Toggle
                id="subtype-detail"
                checked={subtypeForm.requiresDetail}
                onChange={(v) => setSubtypeForm({ ...subtypeForm, requiresDetail: v })}
                label="Pide describir el inmueble"
                description="Como “Otro — especificar”: al elegirla hay que escribir de qué tipo es."
              />
              <Toggle
                id="subtype-active"
                checked={subtypeForm.active}
                onChange={(v) => setSubtypeForm({ ...subtypeForm, active: v })}
                label="Se ofrece en capturas nuevas"
                description="Los expedientes y propiedades que ya la tienen la conservan aunque la desactives."
              />
            </div>
            {subtypeForm.id ? (
              <p className="text-xs text-muted sm:col-span-2">
                Cambiar el tratamiento de documentos y contrato solo aplica a capturas nuevas; los expedientes existentes conservan el suyo.
              </p>
            ) : null}
            {formError ? (
              <p role="alert" className="rounded-lg bg-danger-bg px-3 py-2 text-sm text-danger-text sm:col-span-2">
                {formError}
              </p>
            ) : null}
          </div>
        </Modal>
      ) : null}
    </Card>
  );
}
