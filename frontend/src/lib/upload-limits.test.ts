import { describe, expect, it, vi } from "vitest";

// El servidor no está disponible en la prueba: se usan los mismos límites por defecto del backend.
vi.mock("@/lib/api/client", () => ({
  apiClient: { get: vi.fn(async () => Promise.reject(new TypeError("sin red"))) },
}));

const { DEFAULT_UPLOAD_LIMITS, UploadValidationError, prepareUploads } = await import("./upload-limits");

function fileOfSize(name: string, type: string, bytes: number): File {
  return new File([new Uint8Array(bytes)], name, { type });
}

describe("prepareUploads (límites sincronizados con el backend)", () => {
  it("acepta un PDF de más de 10 MB dentro del nuevo límite de 40 MB", async () => {
    const deed = fileOfSize("escritura.pdf", "application/pdf", 15 * 1024 * 1024);
    await expect(prepareUploads([deed])).resolves.toHaveLength(1);
  });

  it("rechaza ANTES de subir un archivo mayor al límite, con un mensaje claro", async () => {
    const huge = fileOfSize("escaneo.pdf", "application/pdf", DEFAULT_UPLOAD_LIMITS.maxFileSizeBytes + 1);
    await expect(prepareUploads([huge])).rejects.toBeInstanceOf(UploadValidationError);
    await expect(prepareUploads([huge])).rejects.toThrow(/máximo es 40 MB/);
  });

  it("acepta WEBP y rechaza formatos que el servidor no admite", async () => {
    await expect(prepareUploads([fileOfSize("foto.webp", "image/webp", 1000)])).resolves.toHaveLength(1);
    await expect(prepareUploads([fileOfSize("nota.txt", "text/plain", 10)])).rejects.toThrow(/no es un formato aceptado/);
  });
});
