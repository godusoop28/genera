"use client";

import { Card, CardHeader } from "@/components/ui/Card";
import { useSettings } from "@/context/SettingsProvider";
import { cn } from "@/lib/utils";
import { FONT_SIZE_OPTIONS } from "@/types/settings";

const previewSizePx: Record<string, number> = { sm: 13, md: 15, lg: 17, xl: 19 };

export function FontSizeControl() {
  const { settings, setFontSize } = useSettings();

  return (
    <Card>
      <CardHeader
        title="Tamaño de fuente"
        description="Ajusta el tamaño del texto y la interfaz en todo el sistema."
      />
      <div className="flex flex-wrap gap-2">
        {FONT_SIZE_OPTIONS.map((option) => {
          const isActive = settings.fontSize === option.id;
          return (
            <button
              key={option.id}
              type="button"
              onClick={() => setFontSize(option.id)}
              aria-pressed={isActive}
              className={cn(
                "flex min-w-[92px] flex-col items-center gap-1.5 rounded-xl border-2 px-4 py-3 transition-colors",
                isActive ? "border-gold bg-gold/10" : "border-border hover:border-gold/50",
              )}
            >
              <span
                className="font-semibold text-obsessed"
                style={{ fontSize: `${previewSizePx[option.id]}px` }}
              >
                Aa
              </span>
              <span className="text-xs text-muted">{option.label}</span>
            </button>
          );
        })}
      </div>
      <p className="mt-4 text-sm text-obsessed">
        Así se verá el texto normal en el resto de la aplicación con esta configuración.
      </p>
    </Card>
  );
}
