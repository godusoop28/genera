import type { Metadata } from "next";
import { Geist, Geist_Mono } from "next/font/google";
import { ToastProvider } from "@/components/ui/Toast";
import { DemoAppProvider } from "@/context/DemoAppProvider";
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

export default function RootLayout({ children }: LayoutProps<"/">) {
  return (
    <html
      lang="es"
      className={`${geistSans.variable} ${geistMono.variable} h-full antialiased`}
    >
      <body className="min-h-full flex flex-col bg-app-bg text-obsessed">
        <DemoAppProvider>
          <ToastProvider>{children}</ToastProvider>
        </DemoAppProvider>
      </body>
    </html>
  );
}
