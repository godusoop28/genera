import {
  Building2,
  FileText,
  Fingerprint,
  Home,
  Landmark,
  ReceiptText,
  ScrollText,
  ShieldCheck,
} from "lucide-react";

const iconById: Record<string, typeof FileText> = {
  "ine-1": Fingerprint,
  "ine-2": Fingerprint,
  fiscal: Landmark,
  escritura: ScrollText,
  domicilio: Home,
  gravamen: ShieldCheck,
  predial: ReceiptText,
  poder: ScrollText,
  condominio: Building2,
};

interface DocumentIconProps {
  docId: string;
  className?: string;
}

export function DocumentIcon({ docId, className }: DocumentIconProps) {
  const Icon = iconById[docId] ?? FileText;
  return <Icon className={className} aria-hidden />;
}
