"use client";

import { apiClient } from "@/lib/api/client";
import { formatDateTime, timeAgo } from "@/lib/crm/labels";
import { cn } from "@/lib/utils";
import { Bell } from "lucide-react";
import Link from "next/link";
import { useCallback, useEffect, useRef, useState } from "react";

interface InboxItem {
  id: string;
  kind: string;
  title: string;
  body: string | null;
  link: string | null;
  read: boolean;
  createdAt: string;
}

interface Inbox {
  unread: number;
  items: InboxItem[];
}

const POLL_MS = 60_000;

/**
 * Campana del encabezado: recordatorios de actividades de leads (CC#30) y
 * alertas de detractores NPS (CC#28). Se actualiza cada minuto.
 */
export function NotificationBell() {
  const [inbox, setInbox] = useState<Inbox | null>(null);
  const [open, setOpen] = useState(false);
  const panel = useRef<HTMLDivElement>(null);

  const load = useCallback(() => {
    apiClient
      .get<Inbox>("/internal/me/notifications")
      .then(setInbox)
      .catch(() => undefined);
  }, []);

  useEffect(() => {
    load();
    const timer = window.setInterval(load, POLL_MS);
    return () => window.clearInterval(timer);
  }, [load]);

  useEffect(() => {
    if (!open) return;
    const close = (e: MouseEvent) => {
      if (panel.current && !panel.current.contains(e.target as Node)) setOpen(false);
    };
    const esc = (e: KeyboardEvent) => e.key === "Escape" && setOpen(false);
    document.addEventListener("mousedown", close);
    document.addEventListener("keydown", esc);
    return () => {
      document.removeEventListener("mousedown", close);
      document.removeEventListener("keydown", esc);
    };
  }, [open]);

  const markRead = (id: string) => {
    apiClient
      .post<Inbox>(`/internal/me/notifications/${id}/read`)
      .then(setInbox)
      .catch(() => undefined);
  };

  const unread = inbox?.unread ?? 0;
  return (
    <div className="relative" ref={panel}>
      <button
        type="button"
        onClick={() => setOpen((v) => !v)}
        aria-label={unread ? `Avisos: ${unread} sin leer` : "Avisos"}
        aria-expanded={open}
        className="relative rounded-lg p-2 text-obsessed hover:bg-app-bg focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-gold/30"
      >
        <Bell className="h-5 w-5" aria-hidden />
        {unread ? (
          <span className="absolute right-1 top-1 flex h-4 min-w-4 items-center justify-center rounded-full bg-danger-text px-1 text-[0.6rem] font-semibold text-white">
            {unread > 9 ? "9+" : unread}
          </span>
        ) : null}
      </button>
      {open ? (
        <div className="absolute right-0 top-12 z-30 w-[min(24rem,calc(100vw-2rem))] overflow-hidden rounded-xl border border-border bg-card shadow-xl">
          <div className="flex items-center justify-between border-b border-border px-4 py-2.5">
            <span className="text-sm font-semibold text-obsessed">Avisos</span>
            {unread ? (
              <button
                type="button"
                className="text-xs font-medium text-dark-gold hover:text-obsessed"
                onClick={() =>
                  apiClient
                    .post<Inbox>("/internal/me/notifications/read-all")
                    .then(setInbox)
                    .catch(() => undefined)
                }
              >
                Marcar todo como leído
              </button>
            ) : null}
          </div>
          {!inbox || inbox.items.length === 0 ? (
            <p className="px-4 py-6 text-center text-sm text-muted">Sin avisos.</p>
          ) : (
            <ul className="max-h-[60vh] divide-y divide-border overflow-y-auto">
              {inbox.items.map((n) => {
                const content = (
                  <>
                    <span className="flex items-start gap-2">
                      {!n.read ? <span className="mt-1.5 h-2 w-2 shrink-0 rounded-full bg-gold" aria-label="Sin leer" /> : null}
                      <span className={cn("min-w-0 text-sm", n.read ? "text-muted" : "font-medium text-obsessed")}>{n.title}</span>
                    </span>
                    {n.body ? <span className="mt-0.5 line-clamp-3 block whitespace-pre-line text-xs text-muted">{n.body}</span> : null}
                    <span className="mt-1 block text-[0.7rem] text-muted" title={formatDateTime(n.createdAt)}>
                      {n.kind} · {timeAgo(n.createdAt)}
                    </span>
                  </>
                );
                return (
                  <li key={n.id}>
                    {n.link ? (
                      <Link
                        href={n.link}
                        onClick={() => {
                          markRead(n.id);
                          setOpen(false);
                        }}
                        className="block px-4 py-3 hover:bg-app-bg"
                      >
                        {content}
                      </Link>
                    ) : (
                      <button type="button" onClick={() => markRead(n.id)} className="block w-full px-4 py-3 text-left hover:bg-app-bg">
                        {content}
                      </button>
                    )}
                  </li>
                );
              })}
            </ul>
          )}
        </div>
      ) : null}
    </div>
  );
}
