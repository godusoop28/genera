"use client";

import { LoadError, StatCard, useApi } from "@/components/crm/CrmUi";
import { PageContainer } from "@/components/layout/PageContainer";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { Input } from "@/components/ui/Input";
import { Modal } from "@/components/ui/Modal";
import { StateMessage } from "@/components/ui/StateMessage";
import { useToast } from "@/components/ui/Toast";
import {
  approvalLabels,
  commercializationLabels,
  createResolvedProject,
  listResolvedProjects,
} from "@/lib/api/resolved";
import { errorText } from "@/lib/errors";
import { formatDate } from "@/lib/labels";
import { formatMoneyWithCurrency } from "@/lib/money";
import {
  CheckCircle2,
  Clock,
  FileText,
  FolderLock,
  Handshake,
  Plus,
} from "lucide-react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";

/**
 * Control de Cambios #42: apartado «Propiedad Resuelta», separado de los expedientes de CENTURY 21
 * Genera y de los archivos de prueba. Las métricas son solo de estos proyectos.
 */
export default function ResolvedPropertyPage() {
  const { data, error, reload } = useApi(listResolvedProjects, []);
  const [creating, setCreating] = useState(false);

  return (
    <PageContainer
      title="Propiedad Resuelta"
      subtitle="Proyectos, documentos y métricas de Propiedad Resuelta, separados de CENTURY 21 Genera. Acceso privado: solo quien autorice la dirección general."
      action={
        data?.admin ? (
          <Button onClick={() => setCreating(true)}>
            <Plus className="h-4 w-4" aria-hidden /> Nuevo proyecto
          </Button>
        ) : null
      }
    >
      {error ? (
        <LoadError message={error} onRetry={reload} />
      ) : !data ? (
        <p className="text-sm text-muted">Cargando…</p>
      ) : (
        <>
          <div className="mb-6 grid gap-4 sm:grid-cols-2 xl:grid-cols-5">
            <StatCard
              icon={FolderLock}
              value={data.metrics.projects}
              label="Proyectos"
            />
            <StatCard
              icon={Clock}
              tone="warning"
              value={data.metrics.pendingApproval}
              label="Pendientes de aprobación"
            />
            <StatCard
              icon={CheckCircle2}
              tone="success"
              value={data.metrics.approved}
              label="Aprobados para comercializar"
            />
            <StatCard
              icon={Handshake}
              tone="info"
              value={data.metrics.incorporated}
              label="Incorporados a C21 Genera"
            />
            <StatCard
              icon={FileText}
              value={data.metrics.documents}
              label="Documentos"
            />
          </div>
          <p className="mb-4 text-sm text-muted">
            Inversión estimada:{" "}
            <strong className="text-obsessed">
              {formatMoneyWithCurrency(data.metrics.estimatedInvestment)}
            </strong>{" "}
            · Valor estimado:{" "}
            <strong className="text-obsessed">
              {formatMoneyWithCurrency(data.metrics.estimatedValue)}
            </strong>
            . Los indicadores definitivos se definirán con la dirección.
          </p>
          {data.projects.length === 0 ? (
            <StateMessage
              kind="empty"
              title={
                data.admin
                  ? "Aún no hay proyectos"
                  : "No tienes proyectos autorizados"
              }
              description={
                data.admin
                  ? "Crea el primer proyecto de Propiedad Resuelta para guardar sus documentos y llevar sus métricas por separado."
                  : "La dirección general te dará acceso a los proyectos que debas consultar."
              }
            />
          ) : (
            <div className="grid gap-4 lg:grid-cols-2">
              {data.projects.map((p) => (
                <Link
                  key={p.id}
                  href={`/propiedad-resuelta/${p.id}`}
                  className="block rounded-xl focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-gold/40"
                >
                  <Card className="h-full transition-colors hover:border-gold/60">
                    <div className="flex flex-wrap items-start justify-between gap-2">
                      <div className="min-w-0">
                        <p className="break-words text-base font-semibold text-obsessed">
                          {p.name}
                        </p>
                        {p.location ? (
                          <p className="text-sm text-muted">{p.location}</p>
                        ) : null}
                      </div>
                      <div className="flex flex-wrap gap-1.5">
                        <Badge
                          tone={
                            p.approvalStatus === "APPROVED"
                              ? "success"
                              : "warning"
                          }
                        >
                          {approvalLabels[p.approvalStatus]}
                        </Badge>
                        {p.commercializationStatus === "INCORPORATED" ? (
                          <Badge tone="info">
                            {commercializationLabels.INCORPORATED}
                          </Badge>
                        ) : null}
                      </div>
                    </div>
                    <p className="mt-3 text-xs text-muted">
                      {p.documentCount} documento(s) · creado el{" "}
                      {formatDate(p.createdAt)}
                      {p.access.admin
                        ? ""
                        : ` · tu acceso: ver${p.access.download ? ", descargar" : ""}${p.access.upload ? ", subir" : ""}`}
                    </p>
                  </Card>
                </Link>
              ))}
            </div>
          )}
        </>
      )}
      {creating ? (
        <CreateProjectModal onClose={() => setCreating(false)} />
      ) : null}
    </PageContainer>
  );
}

function CreateProjectModal({ onClose }: { onClose: () => void }) {
  const { showToast } = useToast();
  const router = useRouter();
  const [name, setName] = useState("");
  const [location, setLocation] = useState("");
  const [description, setDescription] = useState("");
  const [busy, setBusy] = useState(false);

  const create = async () => {
    setBusy(true);
    try {
      const created = await createResolvedProject({
        name,
        location,
        description,
      });
      router.push(`/propiedad-resuelta/${created.project.id}`);
    } catch (err) {
      showToast(errorText(err));
      setBusy(false);
    }
  };

  return (
    <Modal
      open
      onClose={onClose}
      title="Nuevo proyecto de Propiedad Resuelta"
      footer={
        <>
          <Button variant="ghost" onClick={onClose} disabled={busy}>
            Cancelar
          </Button>
          <Button onClick={create} disabled={busy || name.trim().length < 3}>
            Crear proyecto
          </Button>
        </>
      }
    >
      <div className="flex flex-col gap-4 text-sm">
        <p className="text-muted">
          Queda «Pendiente de aprobación» y solo tú lo ves hasta que des acceso
          a alguien.
        </p>
        <Input
          label="Nombre del proyecto"
          value={name}
          onChange={(e) => setName(e.target.value)}
          autoFocus
          maxLength={160}
        />
        <Input
          label="Ubicación (opcional)"
          value={location}
          onChange={(e) => setLocation(e.target.value)}
          maxLength={300}
        />
        <label className="flex flex-col gap-1.5">
          <span className="font-medium text-obsessed">
            Descripción (opcional)
          </span>
          <textarea
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            rows={3}
            className="rounded-xl border border-border bg-card px-3.5 py-2.5 text-obsessed outline-none focus:border-gold"
          />
        </label>
      </div>
    </Modal>
  );
}
