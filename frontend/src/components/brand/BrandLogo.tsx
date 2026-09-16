import { cn } from "@/lib/utils";
import Image from "next/image";

interface BrandLogoProps {
  className?: string;
  /** "light": logo autorizado en gris, para fondos claros (login, portal cliente, documentos). */
  /** "dark": lockup dorado, para fondos oscuros (sidebar Obsessed Grey). */
  tone?: "light" | "dark";
  size?: "sm" | "md" | "lg";
}

const sizeClasses = {
  sm: "h-8",
  md: "h-10",
  lg: "h-14",
};

// Usa los assets reales extraídos de los documentos de marca proporcionados
// (ver /public/brand). No se reconstruye el logo mediante SVG inventado.
export function BrandLogo({ className, tone = "light", size = "md" }: BrandLogoProps) {
  const src =
    tone === "dark"
      ? "/brand/variants/09_logo_vertical_dorado.png"
      : "/brand/century21-genera-autorizado.png";
  const aspect = tone === "dark" ? "aspect-[187/195]" : "aspect-[1420/807]";

  return (
    <span className={cn("relative inline-block w-auto", sizeClasses[size], aspect, className)}>
      <Image
        src={src}
        alt="CENTURY 21 Genera"
        fill
        sizes="200px"
        className="object-contain"
        priority
      />
    </span>
  );
}
