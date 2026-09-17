// Cliente API mínimo para el futuro backend (ver /backend). Todavía NO se usa
// en la aplicación: el prototipo sigue funcionando sobre mocks/localStorage
// (ver src/context/DemoAppProvider.tsx). Se deja listo para que la
// integración real solo tenga que sustituir las llamadas a DemoAppProvider
// por estas funciones, sin tener que diseñar la capa de transporte desde cero.

const API_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080/api/v1";

// El access token vive en memoria (lo fija AuthProvider tras login/refresh) para
// que las funciones de src/lib/api/*.ts no tengan que recibirlo explícitamente
// en cada llamada. Nunca se persiste aquí: AuthProvider decide dónde guardarlo
// (sessionStorage) para sobrevivir a un refresh de página.
let currentAccessToken: string | null = null;

export function setAccessToken(token: string | null) {
  currentAccessToken = token;
}

export class ApiError extends Error {
  constructor(
    message: string,
    public readonly status: number,
    public readonly code?: string,
  ) {
    super(message);
    this.name = "ApiError";
  }
}

interface RequestOptions extends Omit<RequestInit, "body"> {
  body?: unknown;
  accessToken?: string;
}

async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { body, accessToken, headers, ...rest } = options;
  const token = accessToken ?? currentAccessToken;

  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...rest,
    headers: {
      "Content-Type": "application/json",
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...headers,
    },
    body: body !== undefined ? JSON.stringify(body) : undefined,
  });

  if (response.status === 204) {
    return undefined as T;
  }

  const data = await response.json().catch(() => null);

  if (!response.ok) {
    // El backend responde errores en formato ProblemDetail
    // ({ title, status, code, detail, traceId }).
    throw new ApiError(data?.detail ?? data?.title ?? response.statusText, response.status, data?.code);
  }

  return data as T;
}

export const apiClient = {
  get: <T>(path: string, options?: RequestOptions) => request<T>(path, { ...options, method: "GET" }),
  post: <T>(path: string, body?: unknown, options?: RequestOptions) =>
    request<T>(path, { ...options, method: "POST", body }),
  patch: <T>(path: string, body?: unknown, options?: RequestOptions) =>
    request<T>(path, { ...options, method: "PATCH", body }),
  delete: <T>(path: string, options?: RequestOptions) => request<T>(path, { ...options, method: "DELETE" }),
};
