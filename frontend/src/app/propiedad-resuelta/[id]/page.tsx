"use client";

import { LoadError } from "@/components/crm/CrmUi";
import { PageContainer } from "@/components/layout/PageContainer";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Card, CardHeader } from "@/components/ui/Card";
import { Input } from "@/components/ui/Input";
import { Modal } from "@/components/ui/Modal";
import { ReasonModal } from "@/components/ui/ReasonModal";
import { Select } from "@/components/ui/Select";
import { useToast } from "@/components/ui/Toast";
import {
  approvalLabels,
  approveResolvedProject,
  commercializationLabels,
  fetchResolvedFile,
  getResolvedProject,
  grantResolvedAccess,
  incorporateResolvedProject,
  listResolvedStaff,
  removeResolvedDocument,
  revokeResolvedAccess,
  updateResolvedProject,
  uploadResolvedDocument,
  withdrawResolvedApproval,
  type ResolvedProjectDetail,
} from "@/lib/api/resolved";
import { errorText } from "@/lib/errors";
import { formatDateTime } from "@/lib/labels";
import { formatMoneyWithCurrency } from "@/lib/money";
import {
  ArrowLeft,
  CheckCircle2,
  Download,
  Eye,
  Handshake,
  Trash2,
  Upload,
  UserMinus,
  UserPlus,
} from "lucide-react";
import Link from "next/link";
import { use, useCallback, useEffect, useRef, useState } from "react";

/**
 * Control de Cambios #42: un proyecto de Propiedad Resuelta. El backend devuelve 404 si el usuario
 * no tiene acceso, y valida cada archivo que se abre o descarga.
 */
export default function ResolvedProjectPage({
  params,
}: PageProps<"/propiedad-resuelta/[id]">) {
  const { id } = use(params);
  const { showToast } = useToast();
  const [detail, setDetail] = useState<ResolvedProjectDetail | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [modal, setModal] = useState<
    "edit" | "approve" | "withdraw" | "incorporate" | "upload" | "grant" | null
  >(null);

  const load = useCallback(() => {
    getResolvedProject(id)
      .then((d) => {
        setDetail(d);
        setError(null);
      })
      .catch((err) => setError(errorText(err)));
  }, [id]);

  useEffect(() => {
    load();
  }, [load]);

  const run = async (
    action: () => Promise<ResolvedProjectDetail | void>,
    success: string,
  ) => {
    setBusy(true);
    try {
      const result = await action();
      if (result) setDetail(result);
      else load();
      showToast(success);
      setModal(null);
    } catch (err) {
      showToast(errorText(err));
    } finally {
      setBusy(false);
    }
  };

  const openFile = async (
    documentId: string,
    download: boolean,
    filename: string,
  ) => {
    try {
      const blob = await fetchResolvedFile(documentId, download);
      const url = URL.createObjectURL(blob);
      if (download) {
        const a = document.createElement("a");
        a.href = url;
        a.download = filename;
        a.click();
      } else {
        window.open(url, "_blank", "noopener");
      }
      setTimeout(() => URL.revokeObjectURL(url), 60_000);
    } catch (err) {
      showToast(errorText(err));
    }
  };

  if (error) {
    return (
      <PageContainer title="Propiedad Resuelta">
        <LoadError message={error} onRetry={load} />
      </PageContainer>
    );
  }
  if (!detail) {
    return (
      <PageContainer title="Propiedad Resuelta">
        <p className="text-sm text-muted">Cargando…</p>
      </PageContainer>
    );
  }

  const p = detail.project;
  const admin = p.access.admin;
  const approved = p.approvalStatus === "APPROVED";
  const incorporated = p.commercializationStatus === "INCORPORATED";

  return (
    <PageContainer
      title={p.name}
      subtitle={p.location ?? "Proyecto de Propiedad Resuelta"}
      action={
        <Link
          href="/propiedad-resuelta"
          className="inline-flex items-center gap-1 text-sm text-dark-gold hover:underline"
        >
          <ArrowLeft className="h-4 w-4" aria-hidden /> Todos los proyectos
        </Link>
      }
    >
      <div className="flex flex-col gap-6">
        <Card>
          <div className="mb-3 flex flex-wrap gap-2">
            <Badge tone="gold">Origen: Propiedad Resuelta</Badge>
            <Badge tone={approved ? "success" : "warning"}>
              {approvalLabels[p.approvalStatus]}
            </Badge>
            <Badge tone={incorporated ? "info" : "neutral"}>
              {commercializationLabels[p.commercializationStatus]}
            </Badge>
          </div>
          {p.description ? (
            <p className="mb-3 whitespace-pre-wrap text-sm text-obsessed">
              {p.description}
            </p>
          ) : null}
          <dl className="grid gap-3 text-sm sm:grid-cols-2 lg:grid-cols-4">
            <Info
              label="Inversión estimada"
              value={formatMoneyWithCurrency(p.estimatedInvestment)}
            />
            <Info
              label="Valor estimado"
              value={formatMoneyWithCurrency(p.estimatedValue)}
            />
            <Info label="Documentos" value={String(detail.documents.length)} />
            <Info
              label="Creado"
              value={`${formatDateTime(p.createdAt)}${p.createdByName ? ` · ${p.createdByName}` : ""}`}
            />
            {approved ? (
              <Info
                label="Aprobado"
                value={`${formatDateTime(p.approvedAt)} · ${p.approvedByName ?? ""}${p.approvalNote ? ` · ${p.approvalNote}` : ""}`}
              />
            ) : null}
            {incorporated ? (
              <Info
                label="Incorporado a comercialización"
                value={`${formatDateTime(p.incorporatedAt)} · ${p.incorporatedByName ?? ""}${p.linkedReference ? ` · ref. ${p.linkedReference}` : ""}`}
              />
            ) : null}
          </dl>
          {admin ? (
            <div className="mt-4 flex flex-wrap gap-2">
              <Button
                size="sm"
                variant="secondary"
                onClick={() => setModal("edit")}
                disabled={busy}
              >
                Editar datos
              </Button>
              {!approved ? (
                <Button
                  size="sm"
                  onClick={() => setModal("approve")}
                  disabled={busy}
                >
                  <CheckCircle2 className="h-4 w-4" aria-hidden /> Aprobar para
                  comercialización
                </Button>
              ) : null}
              {approved && !incorporated ? (
                <>
                  <Button
                    size="sm"
                    onClick={() => setModal("incorporate")}
                    disabled={busy}
                  >
                    <Handshake className="h-4 w-4" aria-hidden /> Incorporar a
                    comercialización C21 Genera
                  </Button>
                  <Button
                    size="sm"
                    variant="ghost"
                    onClick={() => setModal("withdraw")}
                    disabled={busy}
                  >
                    Retirar aprobación
                  </Button>
                </>
              ) : null}
            </div>
          ) : null}
          {!approved ? (
            <p className="mt-3 text-xs text-muted">
              Mientras esté pendiente de aprobación, el proyecto se prepara y
              revisa aquí, pero no puede pasar al flujo de comercialización de
              CENTURY 21 Genera.
            </p>
          ) : !incorporated ? (
            <p className="mt-3 text-xs text-muted">
              Aprobado: puede incorporarse a comercialización cuando se decida.
              Aprobarlo no lo publica.
            </p>
          ) : (
            <p className="mt-3 text-xs text-muted">
              Conserva su origen Propiedad Resuelta; sus documentos y métricas
              siguen aquí, por separado. Incorporarlo no dio acceso a nadie más.
            </p>
          )}
        </Card>

        <Card>
          <CardHeader
            title="Documentos del proyecto"
            description="Separados de los documentos de CENTURY 21 y de los archivos de prueba. Abrir o descargar se registra en la bitácora del proyecto."
          />
          {p.access.upload ? (
            <Button
              size="sm"
              variant="secondary"
              className="mb-4"
              onClick={() => setModal("upload")}
            >
              <Upload className="h-4 w-4" aria-hidden /> Subir documento
            </Button>
          ) : null}
          {detail.documents.length === 0 ? (
            <p className="text-sm text-muted">Aún no hay documentos.</p>
          ) : (
            <ul className="divide-y divide-border">
              {detail.documents.map((d) => (
                <li
                  key={d.id}
                  className="flex flex-wrap items-center gap-3 py-3"
                >
                  <div className="min-w-0 flex-1">
                    <p className="break-words text-sm font-medium text-obsessed">
                      {d.name}
                    </p>
                    <p className="text-xs text-muted">
                      {d.category ? `${d.category} · ` : ""}
                      {(d.sizeBytes / 1024 / 1024).toFixed(2)} MB ·{" "}
                      {formatDateTime(d.uploadedAt)}
                      {d.uploadedByName ? ` · ${d.uploadedByName}` : ""}
                    </p>
                  </div>
                  <Button
                    size="sm"
                    variant="ghost"
                    onClick={() =>
                      openFile(d.id, false, d.originalFilename ?? d.name)
                    }
                  >
                    <Eye className="h-4 w-4" aria-hidden /> Ver
                  </Button>
                  {p.access.download ? (
                    <Button
                      size="sm"
                      variant="ghost"
                      onClick={() =>
                        openFile(d.id, true, d.originalFilename ?? d.name)
                      }
                    >
                      <Download className="h-4 w-4" aria-hidden /> Descargar
                    </Button>
                  ) : null}
                  {admin ? (
                    <Button
                      size="sm"
                      variant="ghost"
                      onClick={() =>
                        run(
                          () => removeResolvedDocument(d.id),
                          "Documento retirado (se conserva para auditoría).",
                        )
                      }
                    >
                      <Trash2 className="h-4 w-4" aria-hidden />
                    </Button>
                  ) : null}
                </li>
              ))}
            </ul>
          )}
        </Card>

        {admin && detail.grants ? (
          <Card>
            <CardHeader
              title="Accesos al proyecto"
              description="Por defecto nadie más lo ve. Ver no incluye descargar ni subir: se otorgan por separado. Retirar un acceso corta también los enlaces directos."
            />
            <Button
              size="sm"
              variant="secondary"
              className="mb-4"
              onClick={() => setModal("grant")}
            >
              <UserPlus className="h-4 w-4" aria-hidden /> Dar acceso a un
              usuario
            </Button>
            {detail.grants.length === 0 ? (
              <p className="text-sm text-muted">
                Solo tú (dirección general) ves este proyecto.
              </p>
            ) : (
              <ul className="divide-y divide-border">
                {detail.grants.map((g) => (
                  <li
                    key={g.id}
                    className="flex flex-wrap items-center gap-3 py-3 text-sm"
                  >
                    <div className="min-w-0 flex-1">
                      <p className="font-medium text-obsessed">
                        {g.userName}{" "}
                        {g.revokedAt ? (
                          <Badge tone="neutral">Retirado</Badge>
                        ) : null}
                      </p>
                      <p className="text-xs text-muted">
                        Ver{g.canDownload ? ", descargar" : ""}
                        {g.canUpload ? ", subir archivos" : ""} · otorgado por{" "}
                        {g.grantedByName} el {formatDateTime(g.grantedAt)}
                        {g.revokedAt
                          ? ` · retirado por ${g.revokedByName} el ${formatDateTime(g.revokedAt)}`
                          : ""}
                      </p>
                    </div>
                    {!g.revokedAt ? (
                      <Button
                        size="sm"
                        variant="ghost"
                        disabled={busy}
                        onClick={() =>
                          run(
                            () => revokeResolvedAccess(p.id, g.id),
                            `Se retiró el acceso de ${g.userName}.`,
                          )
                        }
                      >
                        <UserMinus className="h-4 w-4" aria-hidden /> Retirar
                        acceso
                      </Button>
                    ) : null}
                  </li>
                ))}
              </ul>
            )}
          </Card>
        ) : null}

        {admin && detail.activity ? (
          <Card>
            <CardHeader title="Bitácora del proyecto" />
            <ul className="flex flex-col gap-2 text-sm">
              {detail.activity.map((a, i) => (
                <li key={i} className="text-obsessed">
                  <span className="text-muted">
                    {formatDateTime(a.occurredAt)} ·{" "}
                  </span>
                  {a.actorName ? <strong>{a.actorName}: </strong> : null}
                  {a.detail}
                </li>
              ))}
            </ul>
          </Card>
        ) : null}
      </div>

      {modal === "edit" ? (
        <EditModal
          detail={detail}
          busy={busy}
          onClose={() => setModal(null)}
          onSave={(input) =>
            run(() => updateResolvedProject(p.id, input), "Datos guardados.")
          }
        />
      ) : null}
      <ReasonModal
        open={modal === "approve"}
        title="Aprobar para comercialización"
        description="El proyecto queda listo para incorporarse al flujo de CENTURY 21 Genera cuando se decida. Aprobarlo no lo publica ni da acceso a nadie."
        label="Nota (opcional)"
        minLength={0}
        confirmLabel="Aprobar"
        onCancel={() => setModal(null)}
        onConfirm={(note) =>
          run(
            () => approveResolvedProject(p.id, note),
            "Proyecto aprobado para comercialización.",
          )
        }
      />
      <ReasonModal
        open={modal === "withdraw"}
        title="Retirar aprobación"
        description="El proyecto vuelve a «Pendiente de aprobación». Queda en la bitácora."
        label="Motivo"
        minLength={10}
        confirmLabel="Retirar aprobación"
        danger
        onCancel={() => setModal(null)}
        onConfirm={(note) =>
          run(
            () => withdrawResolvedApproval(p.id, note),
            "La aprobación se retiró.",
          )
        }
      />
      {modal === "incorporate" ? (
        <IncorporateModal
          busy={busy}
          onClose={() => setModal(null)}
          onConfirm={(note, ref) =>
            run(
              () => incorporateResolvedProject(p.id, note, ref),
              "Proyecto incorporado a comercialización.",
            )
          }
        />
      ) : null}
      {modal === "upload" ? (
        <UploadModal
          projectId={p.id}
          onClose={() => setModal(null)}
          onUploaded={() => {
            setModal(null);
            load();
          }}
        />
      ) : null}
      {modal === "grant" ? (
        <GrantModal
          busy={busy}
          onClose={() => setModal(null)}
          onConfirm={(userId, dl, up) =>
            run(
              () => grantResolvedAccess(p.id, userId, dl, up),
              "Acceso otorgado.",
            )
          }
        />
      ) : null}
    </PageContainer>
  );
}

function Info({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <dt className="text-xs font-medium uppercase tracking-wide text-muted">
        {label}
      </dt>
      <dd className="mt-0.5 break-words text-obsessed">{value}</dd>
    </div>
  );
}

function EditModal({
  detail,
  busy,
  onClose,
  onSave,
}: {
  detail: ResolvedProjectDetail;
  busy: boolean;
  onClose: () => void;
  onSave: (input: {
    name: string;
    description: string;
    location: string;
    estimatedInvestment: number | null;
    estimatedValue: number | null;
  }) => void;
}) {
  const p = detail.project;
  const [name, setName] = useState(p.name);
  const [location, setLocation] = useState(p.location ?? "");
  const [description, setDescription] = useState(p.description ?? "");
  const [investment, setInvestment] = useState(
    p.estimatedInvestment?.toString() ?? "",
  );
  const [value, setValue] = useState(p.estimatedValue?.toString() ?? "");
  const num = (v: string) =>
    v.trim() === "" || Number.isNaN(Number(v.replace(/,/g, "")))
      ? null
      : Number(v.replace(/,/g, ""));
  return (
    <Modal
      open
      onClose={onClose}
      title="Datos del proyecto"
      footer={
        <>
          <Button variant="ghost" onClick={onClose} disabled={busy}>
            Cancelar
          </Button>
          <Button
            disabled={busy || name.trim().length < 3}
            onClick={() =>
              onSave({
                name,
                location,
                description,
                estimatedInvestment: num(investment),
                estimatedValue: num(value),
              })
            }
          >
            Guardar
          </Button>
        </>
      }
    >
      <div className="flex flex-col gap-4 text-sm">
        <Input
          label="Nombre"
          value={name}
          onChange={(e) => setName(e.target.value)}
          maxLength={160}
        />
        <Input
          label="Ubicación"
          value={location}
          onChange={(e) => setLocation(e.target.value)}
          maxLength={300}
        />
        <label className="flex flex-col gap-1.5">
          <span className="font-medium text-obsessed">Descripción</span>
          <textarea
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            rows={3}
            className="rounded-xl border border-border bg-card px-3.5 py-2.5 text-obsessed outline-none focus:border-gold"
          />
        </label>
        <Input
          label="Inversión estimada (MXN)"
          inputMode="decimal"
          value={investment}
          onChange={(e) => setInvestment(e.target.value)}
        />
        <Input
          label="Valor estimado (MXN)"
          inputMode="decimal"
          value={value}
          onChange={(e) => setValue(e.target.value)}
        />
      </div>
    </Modal>
  );
}

function IncorporateModal({
  busy,
  onClose,
  onConfirm,
}: {
  busy: boolean;
  onClose: () => void;
  onConfirm: (note: string, ref: string) => void;
}) {
  const [note, setNote] = useState("");
  const [ref, setRef] = useState("");
  return (
    <Modal
      open
      onClose={onClose}
      title="Incorporar a comercialización C21 Genera"
      footer={
        <>
          <Button variant="ghost" onClick={onClose} disabled={busy}>
            Cancelar
          </Button>
          <Button disabled={busy} onClick={() => onConfirm(note, ref)}>
            Incorporar
          </Button>
        </>
      }
    >
      <div className="flex flex-col gap-4 text-sm">
        <p className="text-muted">
          Registra que el proyecto pasa al flujo de comercialización. No publica
          nada, no mueve ni duplica archivos y no da acceso a otros usuarios: el
          expediente o la propiedad de CENTURY 21 se dan de alta por el flujo
          normal y aquí se anota su referencia.
        </p>
        <Input
          label="Referencia en C21 Genera (opcional)"
          value={ref}
          onChange={(e) => setRef(e.target.value)}
          placeholder="Ej. folio EXP-2026-000123 o clave del inmueble"
          maxLength={160}
        />
        <Input
          label="Nota (opcional)"
          value={note}
          onChange={(e) => setNote(e.target.value)}
        />
      </div>
    </Modal>
  );
}

function UploadModal({
  projectId,
  onClose,
  onUploaded,
}: {
  projectId: string;
  onClose: () => void;
  onUploaded: () => void;
}) {
  const { showToast } = useToast();
  const [name, setName] = useState("");
  const [category, setCategory] = useState("");
  const [busy, setBusy] = useState(false);
  const fileRef = useRef<HTMLInputElement>(null);
  const upload = async (file: File) => {
    setBusy(true);
    try {
      await uploadResolvedDocument(projectId, file, name, category);
      showToast("Documento guardado en el proyecto.");
      onUploaded();
    } catch (err) {
      showToast(errorText(err));
      setBusy(false);
    }
  };
  return (
    <Modal
      open
      onClose={onClose}
      title="Subir documento"
      footer={
        <>
          <Button variant="ghost" onClick={onClose} disabled={busy}>
            Cancelar
          </Button>
          <Button disabled={busy} onClick={() => fileRef.current?.click()}>
            <Upload className="h-4 w-4" aria-hidden />{" "}
            {busy ? "Subiendo…" : "Elegir archivo"}
          </Button>
        </>
      }
    >
      <div className="flex flex-col gap-4 text-sm">
        <p className="text-muted">
          PDF, imagen, Word, Excel, PowerPoint, CSV, DWG o ZIP, hasta 50 MB.
        </p>
        <Input
          label="Nombre (opcional; si no, el del archivo)"
          value={name}
          onChange={(e) => setName(e.target.value)}
          maxLength={200}
        />
        <Input
          label="Categoría (opcional)"
          value={category}
          onChange={(e) => setCategory(e.target.value)}
          placeholder="Ej. Escrituras, Planos, Licencias"
          maxLength={80}
        />
        <input
          ref={fileRef}
          type="file"
          className="hidden"
          onChange={(e) => {
            const file = e.target.files?.[0];
            if (file) void upload(file);
          }}
        />
      </div>
    </Modal>
  );
}

function GrantModal({
  busy,
  onClose,
  onConfirm,
}: {
  busy: boolean;
  onClose: () => void;
  onConfirm: (userId: string, download: boolean, upload: boolean) => void;
}) {
  const [staff, setStaff] = useState<
    { id: string; name: string; roleCode: string }[] | null
  >(null);
  const [userId, setUserId] = useState("");
  const [download, setDownload] = useState(false);
  const [upload, setUpload] = useState(false);
  useEffect(() => {
    listResolvedStaff()
      .then(setStaff)
      .catch(() => setStaff([]));
  }, []);
  return (
    <Modal
      open
      onClose={onClose}
      title="Dar acceso a un usuario"
      footer={
        <>
          <Button variant="ghost" onClick={onClose} disabled={busy}>
            Cancelar
          </Button>
          <Button
            disabled={busy || !userId}
            onClick={() => onConfirm(userId, download, upload)}
          >
            Dar acceso
          </Button>
        </>
      }
    >
      <div className="flex flex-col gap-4 text-sm">
        <p className="text-muted">
          Solo para este proyecto. Siempre incluye ver sus datos y documentos;
          descargar y subir se eligen aparte.
        </p>
        <Select
          label="Usuario"
          value={userId}
          onChange={(e) => setUserId(e.target.value)}
          options={[
            {
              value: "",
              label: staff === null ? "Cargando…" : "Elige un usuario",
            },
            ...(staff ?? []).map((s) => ({ value: s.id, label: s.name })),
          ]}
        />
        <label className="flex items-center gap-2 text-obsessed">
          <input
            type="checkbox"
            className="h-4 w-4 accent-dark-gold"
            checked={download}
            onChange={(e) => setDownload(e.target.checked)}
          />{" "}
          Puede descargar archivos
        </label>
        <label className="flex items-center gap-2 text-obsessed">
          <input
            type="checkbox"
            className="h-4 w-4 accent-dark-gold"
            checked={upload}
            onChange={(e) => setUpload(e.target.checked)}
          />{" "}
          Puede subir archivos
        </label>
      </div>
    </Modal>
  );
}
