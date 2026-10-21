"use client";

import { PropertyTypeSelect, propertyTypeError, usePropertyCatalog } from "@/components/property/PropertyTypeSelect";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Card, CardHeader } from "@/components/ui/Card";
import { Input } from "@/components/ui/Input";
import { Modal } from "@/components/ui/Modal";
import { ReasonModal } from "@/components/ui/ReasonModal";
import { Select } from "@/components/ui/Select";
import { Toggle } from "@/components/ui/Toggle";
import { useToast } from "@/components/ui/Toast";
import { listDocuments } from "@/lib/api/documents";
import {
  acceptProperty,
  assignAdvisor,
  classifyExpedienteProperty,
  correctExpediente,
  getClientData,
  listAssignableAdvisors,
  rejectProperty,
  signReception,
  type AdvisorOption,
  type ExpedienteConfiguration,
} from "@/lib/api/expedientes";
import { generatePublicLink, getPublicLinkStatus, revokePublicLink } from "@/lib/api/public-link";
import type { PublicLinkStatusResponse } from "@/lib/api/types";
import { errorText } from "@/lib/errors";
import { frontendLink } from "@/lib/share-links";
import {
  accreditationLabels,
  formatDate,
  formatDateTime,
  legalStatusLabels,
  operationLabels,
  personTypeLabels,
  propertyTypeLabels,
  roleLabels,
  signerCharacterLabels,
} from "@/lib/labels";
import { useCan } from "@/lib/permissions";
import { Copy, FileSignature, Home, Link2, Pencil, ShieldOff, ThumbsDown, ThumbsUp, UserCog } from "lucide-react";
import { useCallback, useEffect, useState } from "react";
import { DocumentTimingCard } from "./DocumentTimingCard";
import { PrivacyCard } from "./PrivacyCard";
import type { ExpedienteContext } from "./page";

export function SummaryTab({ expediente, participants, reload, freshPublicLink: newLink, setFreshPublicLink: setNewLink }: ExpedienteContext) {
  const { showToast } = useToast();
  const can = useCan();
  const [linkStatus, setLinkStatus] = useState<PublicLinkStatusResponse | null>(null);
  const [editing, setEditing] = useState(false);
  const [classifying, setClassifying] = useState(false);
  const pendingClassification = !expediente.propertySubtypeId;
  const [changingAdvisor, setChangingAdvisor] = useState(false);
  const [rejecting, setRejecting] = useState(false);
  const [busy, setBusy] = useState(false);
  // Controles de cambios #14 y #22: cuántos documentos están pendientes de entrega y qué servicios tiene.
  const [pendingDocs, setPendingDocs] = useState<number | null>(null);
  const [services, setServices] = useState<string | null>(null);

  useEffect(() => {
    listDocuments(expediente.id)
      .then((docs) => setPendingDocs(docs.filter((d) => d.markedPending).length))
      .catch(() => setPendingDocs(null));
    getClientData(expediente.id)
      .then((d) => setServices(d.availableServices ?? null))
      .catch(() => setServices(null));
  }, [expediente.id, expediente.updatedAt]);

  const loadLink = useCallback(() => {
    getPublicLinkStatus(expediente.id)
      .then((s) => setLinkStatus(s ?? null))
      .catch(() => undefined);
  }, [expediente.id]);

  useEffect(() => {
    loadLink();
  }, [loadLink]);

  const run = async (action: () => Promise<unknown>, success: string) => {
    setBusy(true);
    try {
      await action();
      showToast(success);
      await reload();
    } catch (err) {
      showToast(errorText(err));
    } finally {
      setBusy(false);
    }
  };

  const handleGenerateLink = async () => {
    setBusy(true);
    try {
      const link = await generatePublicLink(expediente.id);
      setNewLink(frontendLink(link.url, "carga"));
      showToast("Liga generada. Cópiala y compártela con el cliente; cualquier liga anterior dejó de funcionar.");
      loadLink();
      await reload();
    } catch (err) {
      showToast(errorText(err));
    } finally {
      setBusy(false);
    }
  };

  const handleRevoke = async () => {
    await run(() => revokePublicLink(expediente.id), "Liga revocada: el cliente ya no puede entrar con ella.");
    setNewLink(null);
    loadLink();
  };

  const linkState = !linkStatus
    ? { label: "Sin liga", tone: "neutral" as const }
    : linkStatus.usable
      ? { label: "Activa", tone: "success" as const }
      : linkStatus.revokedAt
        ? { label: "Revocada", tone: "danger" as const }
        : { label: "Vencida", tone: "warning" as const };

  return (
    <div className="flex flex-col gap-6">
      <Card>
        <CardHeader
          title="Datos del expediente"
          description="Cualquier corrección queda en la bitácora con quién la hizo y por qué; si ya había un contrato sin firmar, se genera una versión nueva."
        />
        <dl className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
          <Field label="Asesor asignado" value={expediente.assignedAdvisorName ?? "Sin asesor asignado"} />
          <Field label="Operación" value={operationLabels[expediente.operationType]} />
          <Field label="Tipo de cliente" value={personTypeLabels[expediente.personType]} />
          <Field label="Quién firma" value={signerCharacterLabels[expediente.signerCharacter]} />
          <Field label="Acreditación de la propiedad" value={accreditationLabels[expediente.accreditationType]} />
          <Field label="Tipo de inmueble" value={expediente.propertyTypeLabel ?? propertyTypeLabels[expediente.propertyCaseType]} />
          <Field label="Régimen de condominio" value={expediente.condominiumRegime ? "Sí" : "No"} />
          <Field label="Situación jurídica" value={legalStatusLabels[expediente.declaredLegalStatus]} />
          <Field label="Domicilio del inmueble" value={expediente.propertyAddress || "Se tomará de la escritura o el predial"} />
          {expediente.propertyReference ? <Field label="Referencia que ve el cliente" value={expediente.propertyReference} /> : null}
          <Field label="Servicios con los que cuenta" value={services?.trim() ? services : "Sin capturar (pestaña Datos del contrato)"} />
          <Field
            label="Documentos pendientes de entrega"
            value={pendingDocs === null ? "—" : pendingDocs === 0 ? "Ninguno" : `${pendingDocs} (no bloquean el avance)`}
          />
          <Field label="Creado" value={formatDateTime(expediente.createdAt)} />
          <Field label="Última actualización" value={formatDateTime(expediente.updatedAt)} />
          {expediente.decisionReason ? <Field label="Motivo de la decisión" value={expediente.decisionReason} /> : null}
        </dl>
        <div className="mt-4 flex flex-wrap gap-2">
          {can("EXPEDIENT_EDIT") && expediente.correctable ? (
            <Button variant="secondary" size="sm" onClick={() => setEditing(true)}>
              <Pencil className="h-4 w-4" aria-hidden /> Corregir datos
            </Button>
          ) : null}
          {/* Control de Cambios #26: un registro anterior al catálogo queda pendiente hasta que se clasifica. */}
          {can("EXPEDIENT_EDIT") ? (
            <Button variant={pendingClassification ? "primary" : "secondary"} size="sm" onClick={() => setClassifying(true)}>
              <Home className="h-4 w-4" aria-hidden /> {pendingClassification ? "Clasificar tipo de inmueble" : "Cambiar tipo de inmueble"}
            </Button>
          ) : null}
          {/* Control de Cambios #24: el administrador o directivo cambia el asesor en cualquier etapa. */}
          {can("EXPEDIENT_ADVISOR_ASSIGN") ? (
            <Button variant="secondary" size="sm" onClick={() => setChangingAdvisor(true)}>
              <UserCog className="h-4 w-4" aria-hidden /> Cambiar asesor
            </Button>
          ) : null}
        </div>
      </Card>

      <PrivacyCard expediente={expediente} reload={reload} />

      {can("PUBLIC_LINK_GENERATE") ? <DocumentTimingCard expediente={expediente} participants={participants} /> : null}

      <Card>
        <CardHeader
          title="Liga del cliente"
          description="El cliente no tiene cuenta: entra con esta liga. Cada liga vence y solo la más reciente funciona. Por seguridad solo se muestra al generarla."
        />
        <div className="mb-3 flex flex-wrap items-center gap-2 text-sm">
          <Badge tone={linkState.tone}>{linkState.label}</Badge>
          {linkStatus?.expiresAt && linkStatus.usable ? <span className="text-muted">Vence el {formatDate(linkStatus.expiresAt)}</span> : null}
          {linkStatus?.lastUsedAt ? <span className="text-muted">· Último acceso: {formatDateTime(linkStatus.lastUsedAt)}</span> : null}
        </div>
        {newLink ? (
          <div className="mb-3 flex items-center gap-2 rounded-lg border border-border bg-app-bg px-3 py-2 text-sm">
            <code className="min-w-0 flex-1 truncate">{newLink}</code>
            <button
              type="button"
              onClick={() => {
                navigator.clipboard.writeText(newLink);
                showToast("Liga copiada.");
              }}
              aria-label="Copiar liga"
              className="text-muted hover:text-obsessed"
            >
              <Copy className="h-4 w-4" aria-hidden />
            </button>
          </div>
        ) : null}
        {can("PUBLIC_LINK_GENERATE") ? (
          <div className="flex flex-wrap gap-2">
            <Button onClick={handleGenerateLink} disabled={busy} size="sm">
              <Link2 className="h-4 w-4" aria-hidden /> {linkStatus ? "Generar liga nueva" : "Generar liga"}
            </Button>
            {linkStatus?.usable ? (
              <Button variant="danger" size="sm" onClick={handleRevoke} disabled={busy}>
                <ShieldOff className="h-4 w-4" aria-hidden /> Revocar liga
              </Button>
            ) : null}
          </div>
        ) : null}
        <p className="mt-3 text-xs text-muted">
          Al abrirla, el cliente ve su nombre y el domicilio del inmueble parcialmente ocultos para confirmar que es su expediente.
        </p>
      </Card>

      {expediente.status === "DOCUMENTS_APPROVED" && can("RECEPTION_SIGN") ? (
        <Card>
          <CardHeader
            title="Recepción documental"
            description="Todos los documentos obligatorios están aceptados. Firma la recepción para pasar al contrato."
          />
          <Button onClick={() => run(() => signReception(expediente.id), "Recepción firmada.")} disabled={busy}>
            <FileSignature className="h-4 w-4" aria-hidden /> Firmar recepción
          </Button>
        </Card>
      ) : null}

      {can("PROPERTY_DECIDE") && (expediente.correctable || expediente.status === "CONTRACT_SIGNED") && expediente.status !== "DRAFT" ? (
        <Card>
          <CardHeader
            title="Decisión sobre el inmueble"
            description={
              expediente.status === "CONTRACT_SIGNED"
                ? "El contrato está firmado por todas las partes: ya se puede aceptar el inmueble."
                : "Para aceptar el inmueble primero debe estar firmado el contrato de intermediación. Rechazarlo es posible en cualquier momento."
            }
          />
          <div className="flex flex-wrap gap-2">
            {expediente.status === "CONTRACT_SIGNED" ? (
              <Button onClick={() => run(() => acceptProperty(expediente.id), "Inmueble aceptado.")} disabled={busy}>
                <ThumbsUp className="h-4 w-4" aria-hidden /> Aceptar inmueble
              </Button>
            ) : null}
            <Button variant="danger" onClick={() => setRejecting(true)} disabled={busy}>
              <ThumbsDown className="h-4 w-4" aria-hidden /> Rechazar inmueble
            </Button>
          </div>
        </Card>
      ) : null}

      <ReasonModal
        open={rejecting}
        title="Rechazar inmueble"
        label="Motivo del rechazo"
        minLength={5}
        danger
        confirmLabel="Rechazar"
        onCancel={() => setRejecting(false)}
        onConfirm={async (reason) => {
          await run(() => rejectProperty(expediente.id, reason), "Inmueble rechazado.");
          setRejecting(false);
        }}
      />

      {editing ? (
        <CorrectionModal
          initial={{
            personType: expediente.personType,
            signedByAttorney: expediente.signedByAttorney,
            accreditationType: expediente.accreditationType,
            condominiumRegime: expediente.condominiumRegime,
            propertySubtypeId: expediente.propertySubtypeId,
            propertyTypeDetail: expediente.propertyTypeDetail ?? "",
            operationType: expediente.operationType,
            declaredLegalStatus: expediente.declaredLegalStatus,
            propertyAddress: expediente.propertyAddress ?? "",
          }}
          currentSubtype={expediente.propertySubtypeId ? { id: expediente.propertySubtypeId, label: expediente.propertyTypeLabel ?? "" } : null}
          onClose={() => setEditing(false)}
          onSave={async (config, reason) => {
            await run(() => correctExpediente(expediente.id, config, reason), "Datos corregidos y registrados en la bitácora.");
            setEditing(false);
          }}
        />
      ) : null}

      {classifying ? (
        <ClassifyPropertyModal
          initialSubtypeId={expediente.propertySubtypeId}
          initialDetail={expediente.propertyTypeDetail ?? ""}
          currentLabel={expediente.propertyTypeLabel}
          onClose={() => setClassifying(false)}
          onSave={async (subtypeId, detail, reason) => {
            await run(() => classifyExpedienteProperty(expediente.id, subtypeId, detail, reason), "Tipo de inmueble actualizado; quedó en el historial.");
            setClassifying(false);
          }}
        />
      ) : null}

      {changingAdvisor ? (
        <AdvisorModal
          currentId={expediente.assignedAdvisorId}
          onClose={() => setChangingAdvisor(false)}
          onSave={async (advisorId, reason) => {
            await run(() => assignAdvisor(expediente.id, advisorId, reason), "Asesor cambiado; quedó registrado en el historial.");
            setChangingAdvisor(false);
          }}
        />
      ) : null}
    </div>
  );
}

/** Control de Cambios #24: cambio de asesor asignado (queda en el historial: anterior, nuevo, quién y cuándo). */
function AdvisorModal({
  currentId,
  onClose,
  onSave,
}: {
  currentId: string | null;
  onClose: () => void;
  onSave: (advisorId: string, reason: string) => Promise<void>;
}) {
  const [advisors, setAdvisors] = useState<AdvisorOption[] | null>(null);
  const [failed, setFailed] = useState(false);
  const [advisorId, setAdvisorId] = useState(currentId ?? "");
  const [reason, setReason] = useState("");
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    listAssignableAdvisors()
      .then(setAdvisors)
      .catch(() => setFailed(true));
  }, []);

  return (
    <Modal
      open
      onClose={onClose}
      title="Cambiar asesor asignado"
      footer={
        <>
          <Button variant="ghost" onClick={onClose}>
            Cancelar
          </Button>
          <Button
            disabled={busy || !advisorId || advisorId === currentId}
            onClick={async () => {
              setBusy(true);
              try {
                await onSave(advisorId, reason.trim());
              } finally {
                setBusy(false);
              }
            }}
          >
            Guardar
          </Button>
        </>
      }
    >
      {failed ? (
        <p className="text-sm text-danger-text">No se pudo cargar la lista de asesores. Cierra y vuelve a intentarlo.</p>
      ) : (
        <Select
          label="Asesor asignado"
          value={advisorId}
          disabled={advisors === null}
          onChange={(e) => setAdvisorId(e.target.value)}
          options={[
            { value: "", label: advisors === null ? "Cargando asesores…" : "Selecciona un asesor" },
            ...(advisors ?? []).map((a) => ({ value: a.id, label: a.roleCode === "ADVISOR" ? a.name : `${a.name} (${roleLabels[a.roleCode] ?? a.roleCode})` })),
          ]}
        />
      )}
      <label className="mb-1 mt-4 block text-sm font-medium text-obsessed">Motivo (opcional)</label>
      <textarea
        value={reason}
        onChange={(e) => setReason(e.target.value)}
        rows={2}
        maxLength={500}
        placeholder="Ej. El asesor anterior dejó la zona."
        className="w-full rounded-xl border border-border bg-card px-3.5 py-2.5 text-sm outline-none focus:border-gold"
      />
      <p className="mt-2 text-xs text-muted">El cambio queda en el historial del expediente con el asesor anterior, el nuevo, quién lo hizo y la fecha.</p>
    </Modal>
  );
}

/**
 * Control de Cambios #26: clasificar (o cambiar) el tipo de inmueble sin la corrección completa. El
 * servidor lo rechaza si cambia los documentos que se piden o el modelo de contrato.
 */
function ClassifyPropertyModal({
  initialSubtypeId,
  initialDetail,
  currentLabel,
  onClose,
  onSave,
}: {
  initialSubtypeId: string | null;
  initialDetail: string;
  currentLabel: string | null;
  onClose: () => void;
  onSave: (subtypeId: string, detail: string | undefined, reason: string) => Promise<void>;
}) {
  const { catalog } = usePropertyCatalog();
  const [subtypeId, setSubtypeId] = useState<string | null>(initialSubtypeId);
  const [detail, setDetail] = useState(initialDetail);
  const [reason, setReason] = useState("");
  const [busy, setBusy] = useState(false);
  const problem = propertyTypeError(catalog, subtypeId, detail, initialSubtypeId);

  return (
    <Modal
      open
      onClose={onClose}
      title="Tipo de inmueble"
      footer={
        <>
          <Button variant="ghost" onClick={onClose}>
            Cancelar
          </Button>
          <Button
            disabled={busy || Boolean(problem) || (subtypeId === initialSubtypeId && detail === initialDetail)}
            onClick={async () => {
              if (!subtypeId) return;
              setBusy(true);
              try {
                await onSave(subtypeId, detail.trim() || undefined, reason.trim());
              } finally {
                setBusy(false);
              }
            }}
          >
            Guardar
          </Button>
        </>
      }
    >
      {currentLabel ? <p className="mb-4 text-sm text-muted">Actual: {currentLabel}</p> : null}
      <PropertyTypeSelect
        subtypeId={subtypeId}
        detail={detail}
        current={initialSubtypeId && currentLabel ? { id: initialSubtypeId, label: currentLabel } : null}
        onChange={(v) => {
          setSubtypeId(v.subtypeId);
          setDetail(v.detail);
        }}
      />
      <label className="mb-1 mt-4 block text-sm font-medium text-obsessed">Motivo (opcional)</label>
      <textarea
        value={reason}
        onChange={(e) => setReason(e.target.value)}
        rows={2}
        placeholder="Ej. La escritura indica uso habitacional."
        className="w-full rounded-xl border border-border bg-card px-3.5 py-2.5 text-sm outline-none focus:border-gold"
      />
      <p className="mt-2 text-xs text-muted">
        Queda en el historial. Si el nuevo tipo cambia los documentos que se piden o el modelo de contrato, hazlo desde &quot;Corregir datos&quot;.
      </p>
    </Modal>
  );
}

function CorrectionModal({
  initial,
  currentSubtype,
  onClose,
  onSave,
}: {
  initial: ExpedienteConfiguration;
  currentSubtype: { id: string; label: string } | null;
  onClose: () => void;
  onSave: (config: ExpedienteConfiguration, reason: string) => Promise<void>;
}) {
  const { catalog } = usePropertyCatalog();
  const [config, setConfig] = useState(initial);
  const [reason, setReason] = useState("");
  const [busy, setBusy] = useState(false);
  const set = <K extends keyof ExpedienteConfiguration>(k: K, v: ExpedienteConfiguration[K]) => setConfig((c) => ({ ...c, [k]: v }));

  return (
    <Modal
      open
      onClose={onClose}
      title="Corregir datos del expediente"
      size="lg"
      footer={
        <>
          <Button variant="ghost" onClick={onClose}>
            Cancelar
          </Button>
          <Button
            disabled={busy || reason.trim().length < 5 || Boolean(config.propertySubtypeId && propertyTypeError(catalog, config.propertySubtypeId, config.propertyTypeDetail ?? "", currentSubtype?.id))}
            onClick={async () => {
              setBusy(true);
              try {
                await onSave(config, reason.trim());
              } finally {
                setBusy(false);
              }
            }}
          >
            Guardar corrección
          </Button>
        </>
      }
    >
      <p className="mb-4 rounded-lg bg-warning-bg px-3 py-2 text-sm text-warning-text">
        Si cambias el tipo de persona o la acreditación, la lista de documentos se recalcula. Si ya había un contrato sin firmar, queda sin
        efecto y hay que generar una versión nueva. Para cambiar el tipo de persona, primero ajusta los participantes.
      </p>
      <div className="grid gap-4 sm:grid-cols-2">
        <Select
          label="Tipo de cliente"
          value={config.personType}
          onChange={(e) => set("personType", e.target.value as ExpedienteConfiguration["personType"])}
          options={Object.entries(personTypeLabels).map(([value, label]) => ({ value, label }))}
        />
        <Select
          label="Acreditación de la propiedad"
          value={config.accreditationType}
          onChange={(e) => set("accreditationType", e.target.value as ExpedienteConfiguration["accreditationType"])}
          options={Object.entries(accreditationLabels).map(([value, label]) => ({ value, label }))}
        />
        <PropertyTypeSelect
          subtypeId={config.propertySubtypeId}
          detail={config.propertyTypeDetail ?? ""}
          current={currentSubtype}
          onChange={(v) => setConfig((c) => ({ ...c, propertySubtypeId: v.subtypeId, propertyTypeDetail: v.detail }))}
        />
        <Select
          label="Operación"
          value={config.operationType}
          onChange={(e) => set("operationType", e.target.value as ExpedienteConfiguration["operationType"])}
          options={Object.entries(operationLabels).map(([value, label]) => ({ value, label }))}
        />
        <Select
          label="Situación jurídica declarada"
          value={config.declaredLegalStatus}
          onChange={(e) => set("declaredLegalStatus", e.target.value as ExpedienteConfiguration["declaredLegalStatus"])}
          options={Object.entries(legalStatusLabels).map(([value, label]) => ({ value, label }))}
        />
        <Input
          label="Domicilio del inmueble"
          value={config.propertyAddress}
          onChange={(e) => set("propertyAddress", e.target.value)}
          containerClassName="sm:col-span-2"
        />
        <Toggle id="corr-condo" checked={config.condominiumRegime} onChange={(v) => set("condominiumRegime", v)} label="Régimen de condominio" />
        {config.personType === "FISICA" ? (
          <Toggle id="corr-att" checked={config.signedByAttorney} onChange={(v) => set("signedByAttorney", v)} label="Firma un apoderado" />
        ) : null}
      </div>
      <label className="mb-1 mt-4 block text-sm font-medium text-obsessed">Motivo de la corrección</label>
      <textarea
        value={reason}
        onChange={(e) => setReason(e.target.value)}
        rows={2}
        placeholder="Ej. El cliente aclaró que la propiedad se acredita con contrato privado."
        className="w-full rounded-xl border border-border bg-card px-3.5 py-2.5 text-sm outline-none focus:border-gold"
      />
    </Modal>
  );
}

function Field({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <dt className="text-xs font-medium tracking-wide text-muted uppercase">{label}</dt>
      <dd className="mt-0.5 text-sm text-obsessed">{value}</dd>
    </div>
  );
}
