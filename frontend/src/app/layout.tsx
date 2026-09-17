import type { Metadata } from "next";
import { Geist, Geist_Mono } from "next/font/google";
import { ToastProvider } from "@/components/ui/Toast";
import { AuthProvider } from "@/context/AuthProvider";
import { DemoAppProvider } from "@/context/DemoAppProvider";
import { SettingsProvider, SETTINGS_STORAGE_KEY } from "@/context/SettingsProvider";
import "./globals.css";

const geistSans = Geist({
  variable: "--font-geist-sans",
  subsets: ["latin"],
});

const geistMono = Geist_Mono({
  variable: "--font-geist-mono",
  subsets: ["latin"],
});

export const metadata: Metadata = {
  title: "CENTURY 21 Genera · Gestión documental",
  description:
    "Prototipo visual de gestión documental y contratos — CENTURY 21 Genera (Módulo 1).",
};

// Aplica el tema/tamaño de fuente guardados antes del primer pintado, para
// evitar un parpadeo (FOUC) al cargar la página con un tema no predeterminado.
const noFlashScript = `
(function () {
  try {
    var raw = window.localStorage.getItem(${JSON.stringify(SETTINGS_STORAGE_KEY)});
    var settings = raw ? JSON.parse(raw) : null;
    var theme = (settings && settings.theme) || "light";
    var fontSize = (settings && settings.fontSize) || "md";
    document.documentElement.setAttribute("data-theme", theme);
    document.documentElement.setAttribute("data-font-size", fontSize);
  } catch (e) {}
})();
`;

export default function RootLayout({ children }: LayoutProps<"/">) {
  return (
    <html
      lang="es"
      className={`${geistSans.variable} ${geistMono.variable} h-full antialiased`}
      suppressHydrationWarning
    >
      <head>
        <script dangerouslySetInnerHTML={{ __html: noFlashScript }} />
      </head>
      <body className="min-h-full flex flex-col bg-app-bg text-obsessed" suppressHydrationWarning>
        <SettingsProvider>
          <AuthProvider>
            <DemoAppProvider>
              <ToastProvider>{children}</ToastProvider>
            </DemoAppProvider>
          </AuthProvider>
        </SettingsProvider>
      </body>
    </html>
  );
}
