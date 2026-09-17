// Traduce el ExpedienteConfig del formulario (mock, ver src/types/expediente.ts)
// a los enums reales que espera el backend (ver backend/src/main/java/com/c21genera/expedientes/*.java).
import type {
  BackendAccreditationType,
  BackendParticipantRole,
  BackendPersonType,
  BackendPropertyCaseType,
  BackendPropertyLegalStatus,
  BackendSignerCharacter,
} from "@/lib/api/types";
import type {
  AccreditationType,
  OwnerCount,
  PersonType,
  PropertyLegalStatus,
  PropertyType,
  SignerCharacter,
} from "@/types/expediente";
import type { CreateExpedienteParticipant } from "@/lib/api/expedientes";

export function toBackendPersonType(value: PersonType): BackendPersonType {
  return value === "fisica" ? "FISICA" : "MORAL";
}

export function toBackendSignerCharacter(value: SignerCharacter): BackendSignerCharacter {
  if (value === "propietario") return "PROPIETARIO";
  if (value === "copropietario") return "COPROPIETARIO";
  return "APODERADO";
}

export function toBackendAccreditationType(value: AccreditationType): BackendAccreditationType {
  return value === "escritura_publica" ? "ESCRITURA_PUBLICA" : "CONTRATO_PRIVADO";
}

export function toBackendPropertyCaseType(value: PropertyType): BackendPropertyCaseType {
  return value === "vivienda" ? "HOUSING" : "RESIDENTIAL_LAND";
}

export function toBackendLegalStatus(value: PropertyLegalStatus): BackendPropertyLegalStatus {
  if (value === "libre_gravamen") return "LIBRE_GRAVAMEN";
  if (value === "con_gravamen") return "CON_GRAVAMEN";
  return "EN_REVISION";
}

/** El primer propietario usa el nombre capturado; el resto son placeholders editables después. */
export function buildBackendParticipants(ownerName: string, ownerCount: OwnerCount): CreateExpedienteParticipant[] {
  const role: BackendParticipantRole = "OWNER";
  const participants: CreateExpedienteParticipant[] = [{ role, fullName: ownerName }];
  for (let i = 2; i <= ownerCount; i += 1) {
    participants.push({ role: "CO_OWNER", fullName: `Copropietario ${i} (pendiente de captura)` });
  }
  return participants;
}
