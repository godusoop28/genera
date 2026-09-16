"use client";

import { defaultAppSettings, type AppSettings, type FontSizeId, type NotificationSettings, type ThemeId } from "@/types/settings";
import { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from "react";

export const SETTINGS_STORAGE_KEY = "c21genera-settings-v1";

function loadSettings(): AppSettings {
  if (typeof window === "undefined") return defaultAppSettings;
  try {
    const raw = window.localStorage.getItem(SETTINGS_STORAGE_KEY);
    if (!raw) return defaultAppSettings;
    const parsed = JSON.parse(raw) as Partial<AppSettings>;
    return {
      ...defaultAppSettings,
      ...parsed,
      notifications: { ...defaultAppSettings.notifications, ...parsed.notifications },
    };
  } catch {
    return defaultAppSettings;
  }
}

interface SettingsContextValue {
  settings: AppSettings;
  setTheme: (theme: ThemeId) => void;
  setFontSize: (fontSize: FontSizeId) => void;
  setNotification: (key: keyof NotificationSettings, value: boolean) => void;
}

const SettingsContext = createContext<SettingsContextValue | null>(null);

export function SettingsProvider({ children }: { children: ReactNode }) {
  const [settings, setSettings] = useState<AppSettings>(() => loadSettings());

  useEffect(() => {
    if (typeof window === "undefined") return;
    document.documentElement.setAttribute("data-theme", settings.theme);
    document.documentElement.setAttribute("data-font-size", settings.fontSize);
    try {
      window.localStorage.setItem(SETTINGS_STORAGE_KEY, JSON.stringify(settings));
    } catch {
      // Se ignora en el prototipo si el almacenamiento local no está disponible.
    }
  }, [settings]);

  const value = useMemo<SettingsContextValue>(
    () => ({
      settings,
      setTheme: (theme) => setSettings((prev) => ({ ...prev, theme })),
      setFontSize: (fontSize) => setSettings((prev) => ({ ...prev, fontSize })),
      setNotification: (key, value) =>
        setSettings((prev) => ({ ...prev, notifications: { ...prev.notifications, [key]: value } })),
    }),
    [settings],
  );

  return <SettingsContext.Provider value={value}>{children}</SettingsContext.Provider>;
}

export function useSettings(): SettingsContextValue {
  const ctx = useContext(SettingsContext);
  if (!ctx) throw new Error("useSettings debe usarse dentro de SettingsProvider");
  return ctx;
}
