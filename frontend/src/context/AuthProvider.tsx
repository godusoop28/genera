"use client";

import { login as apiLogin, logout as apiLogout, me as apiMe } from "@/lib/api/auth";
import { ApiError, setAccessToken } from "@/lib/api/client";
import type { MeResponse } from "@/lib/api/types";
import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from "react";

// Autenticación REAL contra el backend (JWT), a diferencia de DemoAppProvider
// (que sigue existiendo solo para las páginas que todavía no consumen el
// backend real, ver AGENTS de este monorepo). El access token vive en memoria
// + sessionStorage (suficiente para un prototipo; nunca localStorage, para no
// sobrevivir más allá de la pestaña).

const ACCESS_TOKEN_KEY = "c21genera-access-token";
const REFRESH_TOKEN_KEY = "c21genera-refresh-token";

interface AuthContextValue {
  user: MeResponse | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  login: (email: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<MeResponse | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    const storedAccessToken = window.sessionStorage.getItem(ACCESS_TOKEN_KEY);
    Promise.resolve(storedAccessToken)
      .then((token) => {
        if (!token) return null;
        setAccessToken(token);
        return apiMe();
      })
      .then((profile) => {
        if (profile) setUser(profile);
      })
      .catch(() => {
        window.sessionStorage.removeItem(ACCESS_TOKEN_KEY);
        window.sessionStorage.removeItem(REFRESH_TOKEN_KEY);
        setAccessToken(null);
      })
      .finally(() => setIsLoading(false));
  }, []);

  const login = useCallback(async (email: string, password: string) => {
    const auth = await apiLogin(email, password);
    window.sessionStorage.setItem(ACCESS_TOKEN_KEY, auth.accessToken);
    window.sessionStorage.setItem(REFRESH_TOKEN_KEY, auth.refreshToken);
    setAccessToken(auth.accessToken);
    const profile = await apiMe();
    setUser(profile);
  }, []);

  const logout = useCallback(async () => {
    const refreshToken = window.sessionStorage.getItem(REFRESH_TOKEN_KEY);
    window.sessionStorage.removeItem(ACCESS_TOKEN_KEY);
    window.sessionStorage.removeItem(REFRESH_TOKEN_KEY);
    setAccessToken(null);
    setUser(null);
    if (refreshToken) {
      try {
        await apiLogout(refreshToken);
      } catch {
        // El token ya pudo haber expirado/sido revocado: no bloquea el logout local.
      }
    }
  }, []);

  const value = useMemo(
    () => ({ user, isAuthenticated: user !== null, isLoading, login, logout }),
    [user, isLoading, login, logout],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error("useAuth debe usarse dentro de AuthProvider");
  return ctx;
}

export function isUnauthorized(error: unknown): boolean {
  return error instanceof ApiError && error.status === 401;
}
