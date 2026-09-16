export type ThemeId = "light" | "dark" | "gold" | "contrast";
export type FontSizeId = "sm" | "md" | "lg" | "xl";

export interface ThemeOption {
  id: ThemeId;
  label: string;
  description: string;
}

export interface FontSizeOption {
  id: FontSizeId;
  label: string;
}

export const THEME_OPTIONS: ThemeOption[] = [
  { id: "light", label: "Claro", description: "Fondo claro, ideal para uso diurno." },
  { id: "dark", label: "Oscuro", description: "Fondo oscuro, reduce el brillo en ambientes con poca luz." },
  { id: "gold", label: "Dorado", description: "Superficies cálidas con acento premium." },
  { id: "contrast", label: "Alto contraste", description: "Máximo contraste para mejor legibilidad." },
];

export const FONT_SIZE_OPTIONS: FontSizeOption[] = [
  { id: "sm", label: "Pequeño" },
  { id: "md", label: "Normal" },
  { id: "lg", label: "Grande" },
  { id: "xl", label: "Extra grande" },
];

export interface NotificationSettings {
  documentsReceived: boolean;
  correctionsRequested: boolean;
  weeklySummary: boolean;
}

export interface AppSettings {
  theme: ThemeId;
  fontSize: FontSizeId;
  notifications: NotificationSettings;
}

export const defaultAppSettings: AppSettings = {
  theme: "light",
  fontSize: "md",
  notifications: {
    documentsReceived: true,
    correctionsRequested: true,
    weeklySummary: false,
  },
};
