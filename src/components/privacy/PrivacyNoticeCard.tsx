import { Card } from "@/components/ui/Card";
import {
  PRIVACY_INTEGRAL_NOTICE_URL,
  PRIVACY_INTRO,
  PRIVACY_RESPONSIBLE_ADDRESS,
  PRIVACY_RESPONSIBLE_COMMERCIAL_NAME,
  PRIVACY_RESPONSIBLE_EMAIL,
  PRIVACY_RESPONSIBLE_LEGAL_NAME,
  PRIVACY_RESPONSIBLE_PHONE,
  PRIVACY_RESPONSIBLE_RFC,
  PRIVACY_SECTIONS,
  PRIVACY_SUBTITLE,
  PRIVACY_TITLE,
} from "@/data/privacy-reference";
import { ExternalLink } from "lucide-react";

export function PrivacyNoticeCard() {
  return (
    <Card className="p-0 overflow-hidden">
      <div className="border-b border-border bg-app-bg/60 px-6 py-5">
        <h2 className="text-lg font-semibold text-obsessed">{PRIVACY_TITLE}</h2>
        <p className="text-sm text-muted">{PRIVACY_SUBTITLE}</p>
        <p className="mt-2 text-xs text-muted">
          {PRIVACY_RESPONSIBLE_COMMERCIAL_NAME} · {PRIVACY_RESPONSIBLE_LEGAL_NAME} · RFC{" "}
          {PRIVACY_RESPONSIBLE_RFC}
        </p>
        <p className="text-xs text-muted">
          {PRIVACY_RESPONSIBLE_ADDRESS} · {PRIVACY_RESPONSIBLE_EMAIL} · Tel. {PRIVACY_RESPONSIBLE_PHONE}
        </p>
      </div>

      <div className="px-6 py-5">
        <p className="text-sm text-obsessed/90">{PRIVACY_INTRO}</p>

        <div className="mt-5 space-y-4">
          {PRIVACY_SECTIONS.map((section) => (
            <div key={section.id}>
              <p className="text-sm font-semibold text-obsessed">{section.title}</p>
              <p className="mt-1 text-sm text-muted">{section.text}</p>
            </div>
          ))}
        </div>

        <a
          href="/legal/aviso-privacidad-integral.pdf"
          target="_blank"
          rel="noopener noreferrer"
          className="mt-5 inline-flex items-center gap-1.5 text-sm font-medium text-dark-gold hover:underline"
        >
          <ExternalLink className="h-3.5 w-3.5" />
          Ver aviso de privacidad integral
        </a>
        <p className="mt-1 text-xs text-muted">Referencia del documento: {PRIVACY_INTEGRAL_NOTICE_URL}</p>
      </div>
    </Card>
  );
}
