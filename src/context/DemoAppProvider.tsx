"use client";

import { demoExpedientes } from "@/data/demo-expedientes";
import { demoUsers } from "@/data/demo-users";
import type { Expediente, InternalUser } from "@/types/expediente";
import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from "react";

const STORAGE_KEY = "c21genera-demo-state-v1";

interface PersistedState {
  expedientes: Expediente[];
  users: InternalUser[];
}

function stripBlobs(expedientes: Expediente[]): Expediente[] {
  // No persistimos las imágenes cargadas (dataUrl) en localStorage: solo el
  // estado de cada documento (status, revisión). Evita superar la cuota de
  // almacenamiento del navegador con fotos de demo.
  return expedientes.map((exp) => ({
    ...exp,
    documents: Object.fromEntries(
      Object.entries(exp.documents).map(([key, doc]) => [key, { ...doc, pages: [] }]),
    ),
  }));
}

function loadInitialState(): PersistedState {
  if (typeof window === "undefined") {
    return { expedientes: demoExpedientes, users: demoUsers };
  }
  try {
    const raw = window.localStorage.getItem(STORAGE_KEY);
    if (!raw) return { expedientes: demoExpedientes, users: demoUsers };
    const parsed = JSON.parse(raw) as PersistedState;
    if (!parsed.expedientes?.length) return { expedientes: demoExpedientes, users: demoUsers };
    return parsed;
  } catch {
    return { expedientes: demoExpedientes, users: demoUsers };
  }
}

interface DemoAppContextValue {
  expedientes: Expediente[];
  users: InternalUser[];
  currentUser: InternalUser;
  isAuthenticated: boolean;
  login: () => void;
  logout: () => void;
  getExpediente: (id: string) => Expediente | undefined;
  getExpedienteByLink: (linkId: string) => Expediente | undefined;
  addExpediente: (exp: Expediente) => void;
  updateExpediente: (id: string, updater: (exp: Expediente) => Expediente) => void;
  addUser: (user: InternalUser) => void;
  updateUser: (id: string, updater: (user: InternalUser) => InternalUser) => void;
  resetDemoData: () => void;
}

const DemoAppContext = createContext<DemoAppContextValue | null>(null);

export function DemoAppProvider({ children }: { children: ReactNode }) {
  const [expedientes, setExpedientes] = useState<Expediente[]>(() => loadInitialState().expedientes);
  const [users, setUsers] = useState<InternalUser[]>(() => loadInitialState().users);
  const [isAuthenticated, setIsAuthenticated] = useState(
    () => typeof window !== "undefined" && window.sessionStorage.getItem("c21genera-demo-auth") === "1",
  );

  useEffect(() => {
    if (typeof window === "undefined") return;
    try {
      window.localStorage.setItem(
        STORAGE_KEY,
        JSON.stringify({ expedientes: stripBlobs(expedientes), users }),
      );
    } catch {
      // Cuota excedida u otro error de almacenamiento local: se ignora en el prototipo.
    }
  }, [expedientes, users]);

  const login = useCallback(() => {
    setIsAuthenticated(true);
    if (typeof window !== "undefined") window.sessionStorage.setItem("c21genera-demo-auth", "1");
  }, []);

  const logout = useCallback(() => {
    setIsAuthenticated(false);
    if (typeof window !== "undefined") window.sessionStorage.removeItem("c21genera-demo-auth");
  }, []);

  const getExpediente = useCallback(
    (id: string) => expedientes.find((e) => e.id === id),
    [expedientes],
  );

  const getExpedienteByLink = useCallback(
    (linkId: string) => expedientes.find((e) => e.linkId === linkId),
    [expedientes],
  );

  const addExpediente = useCallback((exp: Expediente) => {
    setExpedientes((prev) => [exp, ...prev]);
  }, []);

  const updateExpediente = useCallback((id: string, updater: (exp: Expediente) => Expediente) => {
    setExpedientes((prev) =>
      prev.map((exp) =>
        exp.id === id ? { ...updater(exp), updatedAt: new Date().toISOString() } : exp,
      ),
    );
  }, []);

  const addUser = useCallback((user: InternalUser) => {
    setUsers((prev) => [...prev, user]);
  }, []);

  const updateUser = useCallback((id: string, updater: (user: InternalUser) => InternalUser) => {
    setUsers((prev) => prev.map((u) => (u.id === id ? updater(u) : u)));
  }, []);

  const resetDemoData = useCallback(() => {
    setExpedientes(demoExpedientes);
    setUsers(demoUsers);
  }, []);

  const currentUser = users[0];

  const value = useMemo(
    () => ({
      expedientes,
      users,
      currentUser,
      isAuthenticated,
      login,
      logout,
      getExpediente,
      getExpedienteByLink,
      addExpediente,
      updateExpediente,
      addUser,
      updateUser,
      resetDemoData,
    }),
    [
      expedientes,
      users,
      currentUser,
      isAuthenticated,
      login,
      logout,
      getExpediente,
      getExpedienteByLink,
      addExpediente,
      updateExpediente,
      addUser,
      updateUser,
      resetDemoData,
    ],
  );

  return <DemoAppContext.Provider value={value}>{children}</DemoAppContext.Provider>;
}

export function useDemoApp(): DemoAppContextValue {
  const ctx = useContext(DemoAppContext);
  if (!ctx) throw new Error("useDemoApp debe usarse dentro de DemoAppProvider");
  return ctx;
}
