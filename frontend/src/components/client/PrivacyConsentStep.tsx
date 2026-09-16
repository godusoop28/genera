"use client";

import { PrivacyNoticeCard } from "@/components/privacy/PrivacyNoticeCard";
import { SignatureMock } from "@/components/privacy/SignatureMock";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { useDemoApp } from "@/context/DemoAppProvider";
import { makeActivity } from "@/lib/expediente-factory";
import { PRIVACY_CONSENT_TEXT, PRIVACY_SECONDARY_OPT_OUT_TEXT } from "@/data/privacy-reference";
import type { Expediente } from "@/types/expediente";
import { useState } from "react";

export function PrivacyConsentStep({ expediente, onContinue }: { expediente: Expediente; onContinue: () => void }) {
  const { updateExpediente } = useDemoApp();
  const [mainConsent, setMainConsent] = useState(expediente.privacyConsent.mainConsent);
  const [secondaryOptOut, setSecondaryOptOut] = useState(!expediente.privacyConsent.secondaryConsent);
  const [signatureDataUrl, setSignatureDataUrl] = useState<string | undefined>(
    expediente.privacyConsent.signatureDataUrl,
  );

  const canContinue = mainConsent && Boolean(signatureDataUrl);

  const handleContinue = () => {
    const now = new Date().toISOString();
    updateExpediente(expediente.id, (current) => ({
      ...current,
      status: current.status === "waiting_privacy" ? "waiting_documents" : current.status,
      privacyConsent: {
        mainConsent,
        secondaryConsent: !secondaryOptOut,
        signedAt: now,
        signatureDataUrl,
      },
      activity: [
        makeActivity("aviso_aceptado", `${current.ownerName} aceptó el aviso de privacidad.`, now),
        ...current.activity,
      ],
    }));
    onContinue();
  };

  return (
    <div className="flex flex-col gap-6">
      <PrivacyNoticeCard />

      <Card>
        <label className="flex items-start gap-3">
          <input
            type="checkbox"
            checked={mainConsent}
            onChange={(e) => setMainConsent(e.target.checked)}
            className="mt-0.5 h-4 w-4 shrink-0 rounded border-border accent-[var(--color-c21-dark-gold)]"
          />
          <span className="text-sm text-obsessed">{PRIVACY_CONSENT_TEXT}</span>
        </label>

        <div className="mt-4 border-t border-border pt-4">
          <label className="flex items-start gap-3">
            <input
              type="checkbox"
              checked={secondaryOptOut}
              onChange={(e) => setSecondaryOptOut(e.target.checked)}
              className="mt-0.5 h-4 w-4 shrink-0 rounded border-border accent-[var(--color-c21-dark-gold)]"
            />
            <span className="text-sm text-obsessed">{PRIVACY_SECONDARY_OPT_OUT_TEXT}</span>
          </label>
          <p className="mt-1.5 pl-7 text-xs text-muted">
            Esta preferencia es independiente del consentimiento principal: no bloquea el proceso.
          </p>
        </div>

        <div className="mt-5 border-t border-border pt-4">
          <p className="mb-2 text-sm font-medium text-obsessed">Firma del titular</p>
          <SignatureMock onSign={setSignatureDataUrl} signed={Boolean(signatureDataUrl)} />
        </div>
      </Card>

      <div className="flex justify-end">
        <Button size="lg" disabled={!canContinue} onClick={handleContinue}>
          Continuar
        </Button>
      </div>
    </div>
  );
}
