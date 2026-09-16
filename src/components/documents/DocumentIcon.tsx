import type { DocumentCategory } from "@/types/expediente";
import {
  Building2,
  FileText,
  Fingerprint,
  Home,
  Landmark,
  Mail,
  ReceiptText,
  ScrollText,
  ShieldCheck,
} from "lucide-react";

const iconById: Record<string, typeof FileText> = {
  fiscal: Landmark,
  escritura: ScrollText,
  domicilio: Home,
  gravamen: ShieldCheck,
  predial: ReceiptText,
  poder: ScrollText,
  condominio: Building2,
};

const iconByCategory: Record<DocumentCategory, typeof FileText> = {
  identidad: Fingerprint,
  fiscal: Landmark,
  propiedad: Home,
  cumplimiento: ShieldCheck,
  contrato: ScrollText,
  anexos: FileText,
  cierre: Mail,
};

interface DocumentIconProps {
  docId: string;
  category?: DocumentCategory;
  className?: string;
}

export function DocumentIcon({ docId, category, className }: DocumentIconProps) {
  const Icon = iconById[docId] ?? (category ? iconByCategory[category] : undefined) ?? FileText;
  return <Icon className={className} aria-hidden />;
}
