import type { InternalUser } from "@/types/expediente";
import { ORG_LEGAL_REPRESENTATIVE } from "@/data/organization";

// Personal interno de demostración. Los clientes/propietarios NUNCA aparecen
// aquí: no tienen cuenta, no inician sesión (ver AGENTS.md / prompt maestro §9).
export const demoUsers: InternalUser[] = [
  {
    id: "user-jorge",
    name: ORG_LEGAL_REPRESENTATIVE,
    roleId: "administrador",
    status: "active",
    lastActivity: "Hace 5 minutos",
  },
  {
    id: "user-ana",
    name: "Ana Rodríguez",
    roleId: "asesor",
    status: "active",
    lastActivity: "Hace 2 horas",
  },
  {
    id: "user-carlos",
    name: "Carlos Mendoza",
    roleId: "revisor",
    status: "active",
    lastActivity: "Ayer",
  },
];

export const CURRENT_DEMO_USER = demoUsers[0];
