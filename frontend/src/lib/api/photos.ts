// CC#33: fotografías del inmueble desde el expediente (staff).
import { apiClient, uploadFiles, type UploadProgress } from "@/lib/api/client";
import type { PhotosResponse } from "@/lib/api/public";

export type { PhotoLimits, PhotosResponse, PropertyPhoto } from "@/lib/api/public";

export function listExpedientePhotos(expedienteId: string) {
  return apiClient.get<PhotosResponse>(`/internal/expedientes/${expedienteId}/photos`);
}

export function uploadExpedientePhotos(expedienteId: string, files: File[], onProgress?: (progress: UploadProgress) => void) {
  return uploadFiles<PhotosResponse>(`/internal/expedientes/${expedienteId}/photos`, "files", files, onProgress);
}

export function savePhotoSelection(expedienteId: string, items: Array<{ photoId: string; main: boolean }>) {
  return apiClient.put<PhotosResponse>(`/internal/expedientes/${expedienteId}/photos/selection`, { items });
}

export function removeExpedientePhoto(expedienteId: string, photoId: string) {
  return apiClient.delete<PhotosResponse>(`/internal/expedientes/${expedienteId}/photos/${photoId}`);
}
