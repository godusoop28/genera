"use client";

import { Card, CardHeader } from "@/components/ui/Card";
import { useSettings } from "@/context/SettingsProvider";
import { cn } from "@/lib/utils";
import { THEME_OPTIONS, type ThemeId } from "@/types/settings";
import { Check } from "lucide-react";

const swatches: Record<ThemeId, { bg: string; card: string; accent: string; text: string }> = {
  light: { bg: "#f4f4f4", card: "#ffffff", accent: "#beaf87", text: "#252526" },
  dark: { bg: "#17181a", card: "#232427", accent: "#beaf87", text: "#f2f1ee" },
  gold: { bg: "#faf6ee", card: "#fffdf9", accent: "#a19276", text: "#2b2418" },
  contrast: { bg: "#ffffff", card: "#ffffff", accent: "#000000", text: "#000000" },
};

export function ThemeSwitcher() {
  const { settings, setTheme } = useSettings();

  return (
    <Card>
      <CardHeader title="Tema" description="Elige la apariencia de la aplicación." />
      <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
        {THEME_OPTIONS.map((option) => {
          const swatch = swatches[option.id];
          const isActive = settings.theme === option.id;
          return (
            <button
              key={option.id}
              type="button"
              onClick={() => setTheme(option.id)}
              aria-pressed={isActive}
              className={cn(
                "flex flex-col gap-2 rounded-2xl border-2 p-3 text-left transition-colors",
                isActive ? "border-gold" : "border-border hover:border-gold/50",
              )}
            >
              <span
                className="relative flex h-14 w-full items-center justify-center overflow-hidden rounded-lg border border-black/5"
                style={{ backgroundColor: swatch.bg }}
              >
                <span
                  className="absolute bottom-1.5 left-1.5 right-1.5 h-6 rounded-md shadow-sm"
                  style={{ backgroundColor: swatch.card }}
                />
                <span
                  className="absolute right-2 top-2 h-3 w-3 rounded-full"
                  style={{ backgroundColor: swatch.accent }}
                />
                {isActive ? (
                  <span className="absolute left-2 top-2 flex h-4 w-4 items-center justify-center rounded-full bg-gold text-obsessed">
                    <Check className="h-3 w-3" />
                  </span>
                ) : null}
              </span>
              <span className="text-sm font-medium text-obsessed">{option.label}</span>
              <span className="text-xs text-muted">{option.description}</span>
            </button>
          );
        })}
      </div>
    </Card>
  );
}
