import type { PermissionId, Role } from "@/types/expediente";

export const ALL_PERMISSIONS: { id: PermissionId; label: string; group: string }[] = [
  { id: "crear_expediente", label: "Crear expediente", group: "Expedientes" },
  { id: "ver_expedientes_propios", label: "Consultar expedientes propios", group: "Expedientes" },
  { id: "ver_todos_expedientes", label: "Consultar todos los expedientes", group: "Expedientes" },
  { id: "generar_liga", label: "Generar liga de cliente", group: "Expedientes" },
  { id: "revisar_documentos", label: "Revisar documentos", group: "Documentos" },
  { id: "aceptar_documentos", label: "Aceptar documentos", group: "Documentos" },
  { id: "devolver_documentos", label: "Devolver documentos", group: "Documentos" },
  { id: "rechazar_documentos", label: "Rechazar documentos", group: "Documentos" },
  { id: "editar_datos_extraidos", label: "Editar datos extraídos", group: "Documentos" },
  { id: "preparar_contrato", label: "Preparar contrato", group: "Contrato" },
  { id: "generar_contrato", label: "Generar contrato", group: "Contrato" },
  { id: "enviar_correo", label: "Enviar documentos por correo", group: "Comunicación" },
  { id: "firmar_recepcion", label: "Firmar recepción documental", group: "Cumplimiento" },
  { id: "decidir_inmueble", label: "Aceptar/rechazar inmueble", group: "Cumplimiento" },
  { id: "gestionar_usuarios", label: "Gestionar usuarios", group: "Administración" },
  { id: "gestionar_permisos", label: "Gestionar permisos", group: "Administración" },
];

const ADMIN_PERMISSIONS: PermissionId[] = ALL_PERMISSIONS.map((p) => p.id);

const ADVISOR_PERMISSIONS: PermissionId[] = [
  "crear_expediente",
  "ver_expedientes_propios",
  "generar_liga",
  "revisar_documentos",
  "editar_datos_extraidos",
  "preparar_contrato",
  "enviar_correo",
];

const REVIEWER_PERMISSIONS: PermissionId[] = [
  "ver_expedientes_propios",
  "ver_todos_expedientes",
  "revisar_documentos",
  "aceptar_documentos",
  "devolver_documentos",
  "rechazar_documentos",
  "editar_datos_extraidos",
];

export const ROLES: Role[] = [
  {
    id: "administrador",
    name: "Administrador / Representante legal",
    description:
      "Control total del sistema. Único rol que puede aceptar o rechazar un inmueble y gestionar usuarios y permisos.",
    permissions: ADMIN_PERMISSIONS,
  },
  {
    id: "asesor",
    name: "Asesor",
    description: "Da seguimiento a sus propios expedientes, genera ligas y prepara el contrato.",
    permissions: ADVISOR_PERMISSIONS,
  },
  {
    id: "revisor",
    name: "Revisor documental",
    description: "Revisa, acepta, devuelve o rechaza documentos recibidos de los clientes.",
    permissions: REVIEWER_PERMISSIONS,
  },
];

export function roleHasPermission(roleId: string, permission: PermissionId): boolean {
  const role = ROLES.find((r) => r.id === roleId);
  return role ? role.permissions.includes(permission) : false;
}
