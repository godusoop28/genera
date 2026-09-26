"use client";

import { SignaturePad } from "@/components/signature/SignaturePad";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Card, CardHeader } from "@/components/ui/Card";
import { Input } from "@/components/ui/Input";
import { Modal } from "@/components/ui/Modal";
import { useToast } from "@/components/ui/Toast";
import {
  generateContract,
  getContractData,
  getContractDownload,
  getContractReadiness,
  listContracts,
  markContractDelivered,
  registerAutographSignature,
  reissueSigningLink,
  signAsIntermediary,
} from "@/lib/api/contracts";
import type {
  ContractCalculationsResponse,
  ContractGenerationResponse,
  ContractReadinessResponse,
  SigningLinkResponse,
} from "@/lib/api/types";
import { errorText } from "@/lib/errors";
import { contractStatusLabels, contractStatusTone, formatDate, formatDateTime, formatMoney } from "@/lib/labels";
import { useCan } from "@/lib/permissions";
import { CircleAlert, Copy, Download, FileText, Loader2, PenLine, Upload } from "lucide-react";
import { useCallback, useEffect, useRef, useState } from "react";
import type { ExpedienteContext } from "./page";

export function ContractTab({ expediente, reload }: ExpedienteContext) {
  const { showToast } = useToast();
  const can = useCan();
  const [readiness, setReadiness] = useState<ContractReadinessResponse | null>(null);
  const [calc, setCalc] = useState<ContractCalculationsResponse | null>(null);
  const [contracts, setContracts] = useState<ContractGenerationResponse[] | null>(null);
  const [links, setLinks] = useState<SigningLinkResponse[]>([]);
  const [busy, setBusy] = useState<string | null>(null);
  const [signing, setSigning] = useState<ContractGenerationResponse | null>(null);
  const [showSuperseded, setShowSuperseded] = useState(false);

  const load = useCallback(() => {
    getContractReadiness(expediente.id).then(setReadiness).catch(() => undefined);
    getContractData(expediente.id).then(setCalc).catch(() => undefined);
    listContracts(expediente.id)
      .then(setContracts)
      .catch((err) => showToast(errorText(err)));
  }, [expediente.id, showToast]);

  useEffect(() => {
    load();
  }, [load]);

  const refresh = async () => {
    load();
    await reload();
  };

  const generate = async (mode: "final" | "draft") => {
    setBusy(mode);
    try {
      const result = await generateContract(expediente.id, mode);
      setLinks(result.signingLinks);
      showToast(
        mode === "draft"
          ? "Borrador generado. Está marcado como INCOMPLETO y no se puede firmar."
          : "Contrato generado y enviado a firma. Comparte con cada firmante su liga personal.",
      );
      await refresh();
    } catch (err) {
      showToast(errorText(err));
    } finally {
      setBusy(null);
    }
  };

  const download = async (contractId: string, file: "pdf" | "docx" | "signed") => {
    const tab = window.open("about:blank", "_blank");
    try {
      const { url } = await getContractDownload(contractId, file);
      if (tab) tab.location.href = url;
    } catch (err) {
      tab?.close();
      showToast(errorText(err));
    }
  };

  const current = (contracts ?? []).filter((c) => c.status !== "SUPERSEDED");
  const superseded = (contracts ?? []).filter((c) => c.status === "SUPERSEDED");

  return (
    <div className="flex flex-col gap-6">
      <Card>
        <CardHeader
          title="Generar contrato"
          description="Se genera con el texto del modelo registrado ante PROFECO, llenado con los datos del expediente. Solo se puede enviar a firma si no falta nada; si faltan datos puedes generar un borrador para revisarlo, marcado como INCOMPLETO y bloqueado para firma."
        />
        {calc && Number(calc.price) > 0 ? (
          <dl className="mb-4 grid grid-cols-2 gap-3 text-sm sm:grid-cols-3">
            <Info k="Precio" v={formatMoney(calc.price)} />
            <Info k="Comisión (5%)" v={formatMoney(calc.commission)} />
            <Info k="Comisión + IVA" v={formatMoney(calc.totalCommissionWithVat)} />
            <Info k="Pena convencional" v={formatMoney(calc.penalty)} />
            <Info k="Exclusividad" v={`${calc.exclusivityDays} días (hasta ${formatDate(calc.exclusivityEndDate)})`} />
          </dl>
        ) : (
          <p className="mb-4 text-sm text-warning-text">Todavía no hay precio autorizado; captúralo en &quot;Datos del contrato&quot;.</p>
        )}
        {readiness && !readiness.ready ? (
          <div className="mb-4 rounded-lg bg-warning-bg px-3 py-2 text-sm text-warning-text">
            <p className="font-medium">Todavía no se puede enviar a firma. Falta:</p>
            <ul className="ml-5 mt-1 max-h-48 list-disc overflow-y-auto">
              {[...readiness.blockers, ...readiness.missingData].map((item) => (
                <li key={item}>{item}</li>
              ))}
            </ul>
          </div>
        ) : null}
        {can("CONTRACT_GENERATE") && expediente.correctable ? (
          <div className="flex flex-wrap gap-2">
            <Button onClick={() => generate("final")} disabled={busy !== null || !readiness?.ready}>
              {busy === "final" ? <Loader2 className="h-4 w-4 animate-spin" aria-hidden /> : <FileText className="h-4 w-4" aria-hidden />}
              Generar contrato y enviar a firma
            </Button>
            <Button variant="secondary" onClick={() => generate("draft")} disabled={busy !== null}>
              {busy === "draft" ? <Loader2 className="h-4 w-4 animate-spin" aria-hidden /> : null}
              Generar borrador para revisión
            </Button>
          </div>
        ) : null}
      </Card>

      {links.length > 0 ? (
        <Card>
          <CardHeader
            title="Ligas personales de firma"
            description="Cada firmante tiene su propia liga (solo sirve una vez y vence). Por seguridad solo se muestran ahora: cópialas y compártelas."
          />
          <SigningLinks links={links} />
        </Card>
      ) : null}

      {contracts === null ? <p className="text-sm text-muted">Cargando contratos…</p> : null}
      {current.map((c) => (
        <ContractCard
          key={c.id}
          contract={c}
          onDownload={download}
          onSignIntermediary={() => setSigning(c)}
          onChanged={refresh}
          onNewLink={(link) => setLinks((prev) => [...prev.filter((l) => l.signatureId !== link.signatureId), link])}
        />
      ))}

      {superseded.length > 0 ? (
        <Card>
          <button type="button" className="text-sm text-dark-gold hover:underline" onClick={() => setShowSuperseded((v) => !v)}>
            {showSuperseded ? "Ocultar" : "Ver"} versiones sin efecto ({superseded.length})
          </button>
          {showSuperseded ? (
            <ul className="mt-3 flex flex-col gap-2 text-sm">
              {superseded.map((c) => (
                <li key={c.id} className="flex flex-wrap items-center justify-between gap-2 rounded-lg border border-border px-3 py-2">
                  <span>
                    Versión {c.versionNumber} · {formatDateTime(c.generatedAt)} — <span className="text-muted">{c.supersededReason}</span>
                  </span>
                  {c.hasPdf ? (
                    <Button variant="ghost" size="sm" onClick={() => download(c.id, "pdf")}>
                      <Download className="h-4 w-4" aria-hidden /> PDF
                    </Button>
                  ) : null}
                </li>
              ))}
            </ul>
          ) : null}
        </Card>
      ) : null}

      {signing ? (
        <IntermediarySignModal
          contract={signing}
          onClose={() => setSigning(null)}
          onOpenPdf={() => download(signing.id, "pdf")}
          onSigned={async () => {
            setSigning(null);
            showToast("Firma de la intermediaria registrada.");
            await refresh();
          }}
        />
      ) : null}
    </div>
  );
}

function ContractCard({
  contract: c,
  onDownload,
  onSignIntermediary,
  onChanged,
  onNewLink,
}: {
  contract: ContractGenerationResponse;
  onDownload: (id: string, file: "pdf" | "docx" | "signed") => void;
  onSignIntermediary: () => void;
  onChanged: () => Promise<void>;
  onNewLink: (link: SigningLinkResponse) => void;
}) {
  const { showToast } = useToast();
  const can = useCan();
  const fileRef = useRef<HTMLInputElement>(null);
  const [deliveryMethod, setDeliveryMethod] = useState("En persona");
  const awaiting = c.status === "GENERATED" || c.status === "PARTIALLY_SIGNED";
  const intermediaryPending = c.signatures.some((s) => s.party === "INTERMEDIARY" && s.status === "PENDING");

  const upload = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    try {
      await registerAutographSignature(c.id, file);
      showToast("Contrato firmado a mano registrado como evidencia.");
      await onChanged();
    } catch (err) {
      showToast(errorText(err));
    } finally {
      if (fileRef.current) fileRef.current.value = "";
    }
  };

  return (
    <Card>
      <div className="flex flex-wrap items-start justify-between gap-2">
        <div>
          <p className="font-semibold text-obsessed">Contrato — versión {c.versionNumber}</p>
          <p className="text-xs text-muted">
            Generado {formatDateTime(c.generatedAt)} · {c.variantSummary}
          </p>
          {c.documentSha256 ? <p className="mt-1 break-all font-mono text-[11px] text-muted">Huella SHA-256: {c.documentSha256}</p> : null}
        </div>
        <Badge tone={contractStatusTone[c.status]}>{contractStatusLabels[c.status]}</Badge>
      </div>

      {c.status === "DRAFT_INCOMPLETE" && c.missingItems.length > 0 ? (
        <div className="mt-3 rounded-lg bg-warning-bg px-3 py-2 text-sm text-warning-text">
          <p className="flex items-center gap-1.5 font-medium">
            <CircleAlert className="h-4 w-4" aria-hidden /> Borrador incompleto: no se puede firmar
          </p>
          <p className="mt-1 text-xs">Pendientes: {c.missingItems.join("; ")}</p>
        </div>
      ) : null}

      <div className="mt-3 flex flex-wrap gap-2">
        {c.hasPdf ? (
          <Button variant="secondary" size="sm" onClick={() => onDownload(c.id, "pdf")}>
            <Download className="h-4 w-4" aria-hidden /> PDF
          </Button>
        ) : null}
        <Button variant="ghost" size="sm" onClick={() => onDownload(c.id, "docx")}>
          <Download className="h-4 w-4" aria-hidden /> Word
        </Button>
        {c.hasSignedPackage ? (
          <Button size="sm" onClick={() => onDownload(c.id, "signed")}>
            <Download className="h-4 w-4" aria-hidden /> Contrato firmado + constancia de firmas
          </Button>
        ) : null}
      </div>

      {c.signatures.length > 0 ? (
        <div className="mt-4">
          <p className="mb-2 text-xs font-medium uppercase tracking-wide text-muted">Firmas</p>
          <ul className="flex flex-col gap-2">
            {c.signatures.map((s) => (
              <li key={s.id} className="flex flex-wrap items-center justify-between gap-2 rounded-lg border border-border px-3 py-2 text-sm">
                <div>
                  <p className="text-obsessed">
                    {s.signerName} <span className="text-muted">· {s.signerCapacity}</span>
                  </p>
                  <p className="text-xs text-muted">
                    {s.status === "SIGNED"
                      ? `Firmó ${formatDateTime(s.signedAt)} · ${s.method === "AUTOGRAPH_SCAN" ? "firma autógrafa escaneada" : "firma electrónica"}${s.ipAddress ? ` · IP ${s.ipAddress}` : ""}`
                      : s.status === "VOIDED"
                        ? `Sin efecto: ${s.voidedReason ?? ""}`
                        : s.party === "INTERMEDIARY"
                          ? "Pendiente: firma desde el sistema un usuario autorizado"
                          : `Pendiente${s.linkExpiresAt ? ` · su liga vence ${formatDate(s.linkExpiresAt)}` : ""}${s.hasEmail ? " · se le envió por correo" : " · sin correo: compártele la liga"}`}
                  </p>
                </div>
                <div className="flex items-center gap-2">
                  <Badge tone={s.status === "SIGNED" ? "success" : s.status === "VOIDED" ? "neutral" : "warning"}>
                    {s.status === "SIGNED" ? "Firmada" : s.status === "VOIDED" ? "Sin efecto" : "Pendiente"}
                  </Badge>
                  {s.status === "PENDING" && s.party === "CLIENT" && awaiting && can("CONTRACT_GENERATE") ? (
                    <Button
                      variant="ghost"
                      size="sm"
                      onClick={async () => {
                        try {
                          onNewLink(await reissueSigningLink(s.id));
                          showToast("Liga nueva generada; la anterior dejó de funcionar.");
                        } catch (err) {
                          showToast(errorText(err));
                        }
                      }}
                    >
                      Liga nueva
                    </Button>
                  ) : null}
                </div>
              </li>
            ))}
          </ul>
        </div>
      ) : null}

      {awaiting && can("CONTRACT_SIGN") ? (
        <div className="mt-4 flex flex-wrap gap-2 border-t border-border pt-4">
          {intermediaryPending ? (
            <Button size="sm" onClick={onSignIntermediary}>
              <PenLine className="h-4 w-4" aria-hidden /> Firmar como intermediaria
            </Button>
          ) : null}
          <input ref={fileRef} type="file" accept="application/pdf,image/jpeg,image/png" className="hidden" onChange={upload} />
          <Button variant="secondary" size="sm" onClick={() => fileRef.current?.click()}>
            <Upload className="h-4 w-4" aria-hidden /> Cargar contrato firmado a mano (escaneado)
          </Button>
          <p className="w-full text-xs text-muted">
            La carga del contrato firmado a mano registra como firmadas todas las firmas pendientes de esta versión, con el archivo como evidencia.
          </p>
        </div>
      ) : null}

      {c.status === "SIGNED" && can("CONTRACT_GENERATE") ? (
        <div className="mt-4 flex flex-wrap items-end gap-2 border-t border-border pt-4">
          <Input
            label="¿Cómo se entregó al cliente su tanto firmado?"
            value={deliveryMethod}
            onChange={(e) => setDeliveryMethod(e.target.value)}
            containerClassName="min-w-[240px] flex-1"
          />
          <Button
            size="sm"
            onClick={async () => {
              try {
                await markContractDelivered(c.id, deliveryMethod.trim() || "En persona");
                showToast("Entrega registrada.");
                await onChanged();
              } catch (err) {
                showToast(errorText(err));
              }
            }}
          >
            Registrar entrega
          </Button>
        </div>
      ) : null}
      {c.status === "DELIVERED" ? <p className="mt-3 text-sm text-success-text">Entregado al cliente el {formatDateTime(c.deliveredAt)} ({c.deliveryMethod}).</p> : null}
    </Card>
  );
}

function SigningLinks({ links }: { links: SigningLinkResponse[] }) {
  const { showToast } = useToast();
  return (
    <ul className="flex flex-col gap-2">
      {links.map((l) => (
        <li key={l.signatureId} className="rounded-lg border border-border px-3 py-2 text-sm">
          <p className="text-obsessed">
            {l.signerName} <span className="text-muted">· {l.signerCapacity}</span>
          </p>
          <div className="mt-1 flex items-center gap-2">
            <code className="min-w-0 flex-1 truncate text-xs">{l.url}</code>
            <button
              type="button"
              aria-label="Copiar liga"
              className="text-muted hover:text-obsessed"
              onClick={() => {
                navigator.clipboard.writeText(l.url);
                showToast("Liga copiada.");
              }}
            >
              <Copy className="h-4 w-4" aria-hidden />
            </button>
          </div>
          <p className="text-xs text-muted">
            Vence el {formatDate(l.expiresAt)} · {l.emailed ? "También se envió por correo." : "No tiene correo registrado: compártela tú."}
          </p>
        </li>
      ))}
    </ul>
  );
}

function IntermediarySignModal({
  contract,
  onClose,
  onOpenPdf,
  onSigned,
}: {
  contract: ContractGenerationResponse;
  onClose: () => void;
  onOpenPdf: () => void;
  onSigned: () => Promise<void>;
}) {
  const { showToast } = useToast();
  const signature = contract.signatures.find((s) => s.party === "INTERMEDIARY" && s.status === "PENDING");
  const [typedName, setTypedName] = useState("");
  const [image, setImage] = useState<string | null>(null);
  const [accepted, setAccepted] = useState(false);
  const [busy, setBusy] = useState(false);

  return (
    <Modal
      open
      onClose={onClose}
      title="Firmar como intermediaria"
      size="lg"
      footer={
        <>
          <Button variant="ghost" onClick={onClose}>
            Cancelar
          </Button>
          <Button
            disabled={!typedName.trim() || !image || !accepted || busy}
            onClick={async () => {
              setBusy(true);
              try {
                await signAsIntermediary(contract.id, {
                  typedName,
                  signatureImageBase64: image ?? "",
                  documentSha256: contract.documentSha256 ?? "",
                  accepted,
                });
                await onSigned();
              } catch (err) {
                showToast(errorText(err));
              } finally {
                setBusy(false);
              }
            }}
          >
            {busy ? "Firmando…" : "Firmar contrato"}
          </Button>
        </>
      }
    >
      <p className="mb-3 text-sm text-muted">
        Firma en representación de la intermediaria: <strong className="text-obsessed">{signature?.signerName}</strong> ({signature?.signerCapacity}). Tu
        usuario queda registrado junto con la fecha, hora y la huella del documento.
      </p>
      <Button variant="secondary" size="sm" onClick={onOpenPdf} className="mb-4">
        <Download className="h-4 w-4" aria-hidden /> Leer el contrato (PDF)
      </Button>
      <Input
        label={`Escribe el nombre completo tal como aparece: ${signature?.signerName ?? ""}`}
        value={typedName}
        onChange={(e) => setTypedName(e.target.value)}
      />
      <div className="mt-4">
        <SignaturePad onChange={setImage} />
      </div>
      <label className="mt-4 flex items-start gap-2 text-sm text-obsessed">
        <input type="checkbox" checked={accepted} onChange={(e) => setAccepted(e.target.checked)} className="mt-0.5 h-4 w-4" />
        Leí el contrato versión {contract.versionNumber} (huella {contract.documentSha256?.slice(0, 12)}…) y lo firmo electrónicamente.
      </label>
    </Modal>
  );
}

function Info({ k, v }: { k: string; v: string }) {
  return (
    <div>
      <dt className="text-xs text-muted">{k}</dt>
      <dd className="font-medium text-obsessed">{v}</dd>
    </div>
  );
}
