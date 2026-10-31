"use client";

import { PhotoPicker } from "@/components/photos/PhotoPicker";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Card, CardHeader } from "@/components/ui/Card";
import { StateMessage } from "@/components/ui/StateMessage";
import { useToast } from "@/components/ui/Toast";
import { listExpedientePhotos, removeExpedientePhoto, savePhotoSelection, uploadExpedientePhotos, type PhotosResponse, type PropertyPhoto } from "@/lib/api/photos";
import { errorText } from "@/lib/errors";
import { useCan } from "@/lib/permissions";
import { ArrowDown, ArrowUp, Download, Star, Trash2 } from "lucide-react";
import { useCallback, useEffect, useState } from "react";
import type { ExpedienteContext } from "./page";

const dateTime = new Intl.DateTimeFormat("es-MX", { day: "numeric", month: "short", year: "numeric", hour: "2-digit", minute: "2-digit", timeZone: "America/Mexico_City" });

/**
 * Control de Cambios #33: fotos del inmueble que subió el cliente desde su liga y las que sube el
 * asesor. El asesor elige cuáles se usan en el portal, la principal y el orden; la selección queda
 * guardada en el expediente. Subir o seleccionar no publica nada por sí solo.
 */
export function PhotosTab({ expediente }: ExpedienteContext) {
  const can = useCan();
  const canEdit = can("EXPEDIENT_EDIT");
  const { showToast } = useToast();
  const [data, setData] = useState<PhotosResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  // Selección en edición: ids en orden + principal.
  const [selection, setSelection] = useState<string[]>([]);
  const [mainId, setMainId] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  const apply = useCallback((response: PhotosResponse) => {
    setData(response);
    const selected = response.photos.filter((p) => p.selectedForPortal).sort((a, b) => (a.portalOrder ?? 0) - (b.portalOrder ?? 0));
    setSelection(selected.map((p) => p.id));
    setMainId(selected.find((p) => p.mainPhoto)?.id ?? null);
  }, []);

  useEffect(() => {
    listExpedientePhotos(expediente.id)
      .then(apply)
      .catch((err) => setError(errorText(err)));
  }, [expediente.id, apply]);

  if (error) return <StateMessage kind="error" title="No se pudieron cargar las fotos" description={error} />;
  if (!data) return <StateMessage kind="loading" title="Cargando fotografías…" />;

  const byId = Object.fromEntries(data.photos.map((p) => [p.id, p]));
  const saved = data.photos.filter((p) => p.selectedForPortal).sort((a, b) => (a.portalOrder ?? 0) - (b.portalOrder ?? 0));
  const effectiveMain = mainId ?? selection[0] ?? null;
  const dirty =
    saved.map((p) => p.id).join() !== selection.join() || (saved.find((p) => p.mainPhoto)?.id ?? saved[0]?.id ?? null) !== effectiveMain;

  const toggle = (id: string) => setSelection((s) => (s.includes(id) ? s.filter((x) => x !== id) : [...s, id]));
  const move = (index: number, delta: number) =>
    setSelection((s) => {
      const next = [...s];
      const target = index + delta;
      if (target < 0 || target >= next.length) return s;
      [next[index], next[target]] = [next[target], next[index]];
      return next;
    });

  const save = async () => {
    setSaving(true);
    try {
      apply(await savePhotoSelection(expediente.id, selection.map((id) => ({ photoId: id, main: id === effectiveMain }))));
      showToast("Selección para el portal guardada.");
    } catch (err) {
      showToast(errorText(err));
    } finally {
      setSaving(false);
    }
  };

  const remove = async (photo: PropertyPhoto) => {
    try {
      apply(await removeExpedientePhoto(expediente.id, photo.id));
      showToast("Foto retirada.");
    } catch (err) {
      showToast(errorText(err));
    }
  };

  const clientCount = data.photos.filter((p) => p.uploadedBy === "CLIENT").length;

  return (
    <div className="flex flex-col gap-6">
      {canEdit ? (
        <Card>
          <CardHeader
            title="Agregar fotografías para el portal"
            description="Sube tus propias fotos del inmueble, aunque el cliente no haya subido ninguna. Se guardan en el expediente, separadas de los documentos."
          />
          <PhotoPicker
            limits={data.limits}
            buttonLabel="Agregar fotografías para el portal"
            onUpload={async (files, onProgress) => {
              const response = await uploadExpedientePhotos(expediente.id, files, onProgress);
              setData(response);
            }}
          />
        </Card>
      ) : null}

      <Card>
        <CardHeader
          title={`Fotografías del inmueble (${data.photos.length})`}
          description={
            clientCount > 0
              ? `${clientCount} subida${clientCount === 1 ? "" : "s"} por el cliente desde su liga; el resto, por el equipo.`
              : "El cliente todavía no sube fotos desde su liga."
          }
        />
        {data.photos.length === 0 ? (
          <p className="text-sm text-muted">Aún no hay fotografías.</p>
        ) : (
          <ul className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-4">
            {data.photos.map((photo) => {
              const selected = selection.includes(photo.id);
              return (
                <li key={photo.id} className={`overflow-hidden rounded-xl border ${selected ? "border-gold ring-2 ring-gold/30" : "border-border"} bg-card`}>
                  <a href={photo.url ?? "#"} target="_blank" rel="noopener noreferrer">
                    {/* eslint-disable-next-line @next/next/no-img-element */}
                    <img src={photo.url ?? ""} alt={photo.originalFilename ?? "Foto"} loading="lazy" className="aspect-[4/3] w-full object-cover" />
                  </a>
                  <div className="flex flex-col gap-1.5 p-2 text-xs">
                    <div className="flex flex-wrap items-center gap-1">
                      <Badge tone={photo.uploadedBy === "CLIENT" ? "info" : "neutral"}>{photo.uploadedBy === "CLIENT" ? "Cliente" : photo.uploadedByName ?? "Asesor"}</Badge>
                      {selected ? <Badge tone="success">Portal #{selection.indexOf(photo.id) + 1}</Badge> : null}
                      {selected && effectiveMain === photo.id ? <Badge tone="warning">Principal</Badge> : null}
                    </div>
                    <span className="text-muted">{dateTime.format(new Date(photo.uploadedAt))}</span>
                    {canEdit ? (
                      <div className="flex flex-wrap items-center gap-2">
                        <label className="inline-flex items-center gap-1.5">
                          <input type="checkbox" checked={selected} onChange={() => toggle(photo.id)} />
                          Usar en el portal
                        </label>
                        <button type="button" onClick={() => remove(photo)} className="ml-auto text-muted hover:text-danger-text" aria-label="Retirar foto">
                          <Trash2 className="h-3.5 w-3.5" aria-hidden />
                        </button>
                      </div>
                    ) : null}
                  </div>
                </li>
              );
            })}
          </ul>
        )}
      </Card>

      <Card>
        <CardHeader
          title="Selección para el portal"
          description="Estas fotos, en este orden y con esta principal, son las que se usan al publicar el inmueble por el flujo autorizado. Guardar la selección no publica nada."
        />
        {selection.length === 0 ? (
          <p className="text-sm text-muted">No hay fotos seleccionadas. Marca &quot;Usar en el portal&quot; en las que quieras usar.</p>
        ) : (
          <ol className="flex flex-col gap-2">
            {selection.map((id, index) => {
              const photo = byId[id];
              if (!photo) return null;
              return (
                <li key={id} className="flex items-center gap-3 rounded-lg border border-border p-2">
                  <span className="w-6 text-center text-sm font-semibold text-muted">{index + 1}</span>
                  {/* eslint-disable-next-line @next/next/no-img-element */}
                  <img src={photo.url ?? ""} alt="" className="h-12 w-16 rounded object-cover" />
                  <span className="min-w-0 flex-1 truncate text-sm">{photo.originalFilename ?? "Foto"}</span>
                  {canEdit ? (
                    <div className="flex items-center gap-1">
                      <Button size="sm" variant={effectiveMain === id ? "primary" : "ghost"} onClick={() => setMainId(id)} aria-label="Foto principal">
                        <Star className="h-4 w-4" aria-hidden /> {effectiveMain === id ? "Principal" : "Hacer principal"}
                      </Button>
                      <Button size="sm" variant="ghost" onClick={() => move(index, -1)} disabled={index === 0} aria-label="Subir">
                        <ArrowUp className="h-4 w-4" aria-hidden />
                      </Button>
                      <Button size="sm" variant="ghost" onClick={() => move(index, 1)} disabled={index === selection.length - 1} aria-label="Bajar">
                        <ArrowDown className="h-4 w-4" aria-hidden />
                      </Button>
                    </div>
                  ) : null}
                  <a href={photo.url ?? "#"} download className="text-muted hover:text-obsessed" aria-label="Descargar">
                    <Download className="h-4 w-4" aria-hidden />
                  </a>
                </li>
              );
            })}
          </ol>
        )}
        {canEdit ? (
          <div className="mt-3 flex flex-wrap items-center gap-3">
            <Button onClick={save} disabled={!dirty || saving}>
              {saving ? "Guardando…" : "Guardar selección"}
            </Button>
            {dirty ? <span className="text-xs text-warning-text">Hay cambios sin guardar.</span> : null}
          </div>
        ) : null}
      </Card>
    </div>
  );
}
