// Las fotos de celular pesan 3-12 MB y el servidor acepta hasta 10 MB por
// archivo. Antes de subirlas se reducen a un tamaño que sigue siendo
// perfectamente legible para la revisión (el backend tampoco usa más de
// 2400 px). Si algo falla, se sube el archivo original sin tocar.

const MAX_LONG_SIDE_PX = 2400;
const SHRINK_ABOVE_BYTES = 1.5 * 1024 * 1024;
const JPEG_QUALITY = 0.88;

export function shrinkImages(files: File[]): Promise<File[]> {
  return Promise.all(files.map(shrinkImage));
}

async function shrinkImage(file: File): Promise<File> {
  if (!/^image\/(jpeg|png|webp)$/.test(file.type) || file.size <= SHRINK_ABOVE_BYTES || typeof createImageBitmap !== "function") {
    return file;
  }
  try {
    // "from-image" aplica la orientación EXIF: el resultado ya queda derecho.
    const bitmap = await createImageBitmap(file, { imageOrientation: "from-image" });
    const scale = Math.min(1, MAX_LONG_SIDE_PX / Math.max(bitmap.width, bitmap.height));
    const canvas = document.createElement("canvas");
    canvas.width = Math.max(1, Math.round(bitmap.width * scale));
    canvas.height = Math.max(1, Math.round(bitmap.height * scale));
    const ctx = canvas.getContext("2d");
    if (!ctx) {
      bitmap.close();
      return file;
    }
    ctx.fillStyle = "#fff";
    ctx.fillRect(0, 0, canvas.width, canvas.height);
    ctx.drawImage(bitmap, 0, 0, canvas.width, canvas.height);
    bitmap.close();
    const blob = await new Promise<Blob | null>((resolve) => canvas.toBlob(resolve, "image/jpeg", JPEG_QUALITY));
    if (!blob || blob.size >= file.size) return file;
    return new File([blob], `${file.name.replace(/\.[^.]+$/, "")}.jpg`, { type: "image/jpeg", lastModified: file.lastModified });
  } catch {
    return file;
  }
}
