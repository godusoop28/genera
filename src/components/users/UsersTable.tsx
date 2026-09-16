"use client";

import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Modal } from "@/components/ui/Modal";
import { Input } from "@/components/ui/Input";
import { Select } from "@/components/ui/Select";
import { useToast } from "@/components/ui/Toast";
import { useDemoApp } from "@/context/DemoAppProvider";
import { ROLES } from "@/data/permissions";
import { generateId } from "@/lib/expediente-factory";
import { initials } from "@/lib/utils";
import type { InternalUser, RoleId } from "@/types/expediente";
import { Plus, UserCog } from "lucide-react";
import { useState } from "react";

export function UsersTable() {
  const { users, addUser, updateUser } = useDemoApp();
  const { showToast } = useToast();
  const [createOpen, setCreateOpen] = useState(false);
  const [editUser, setEditUser] = useState<InternalUser | null>(null);
  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const [roleId, setRoleId] = useState<RoleId>("asesor");

  const openCreate = () => {
    setName("");
    setEmail("");
    setRoleId("asesor");
    setCreateOpen(true);
  };

  const handleCreate = () => {
    if (!name.trim()) return;
    addUser({
      id: generateId("user"),
      name: name.trim(),
      email: email.trim() || undefined,
      roleId,
      status: "active",
      lastActivity: "Recién creado",
    });
    setCreateOpen(false);
    showToast("Usuario creado (simulado).");
  };

  const openEdit = (user: InternalUser) => {
    setEditUser(user);
    setName(user.name);
    setEmail(user.email ?? "");
    setRoleId(user.roleId);
  };

  const handleEditSave = () => {
    if (!editUser) return;
    updateUser(editUser.id, (u) => ({ ...u, name: name.trim() || u.name, email: email.trim() || undefined, roleId }));
    setEditUser(null);
    showToast("Usuario actualizado (simulado).");
  };

  const toggleStatus = (user: InternalUser) => {
    updateUser(user.id, (u) => ({ ...u, status: u.status === "active" ? "inactive" : "active" }));
    showToast(user.status === "active" ? "Usuario desactivado." : "Usuario activado.");
  };

  return (
    <div>
      <div className="mb-4 flex justify-end">
        <Button size="sm" onClick={openCreate}>
          <Plus className="h-4 w-4" /> Crear usuario
        </Button>
      </div>

      <div className="overflow-hidden rounded-2xl border border-border bg-white">
        <table className="w-full text-left text-sm">
          <thead className="border-b border-border bg-app-bg text-xs uppercase tracking-wide text-muted">
            <tr>
              <th className="px-5 py-3 font-medium">Nombre</th>
              <th className="px-5 py-3 font-medium">Rol</th>
              <th className="px-5 py-3 font-medium">Estado</th>
              <th className="px-5 py-3 font-medium">Última actividad</th>
              <th className="px-5 py-3 font-medium">Acciones</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-border">
            {users.map((user) => {
              const role = ROLES.find((r) => r.id === user.roleId);
              return (
                <tr key={user.id}>
                  <td className="px-5 py-3.5">
                    <div className="flex items-center gap-3">
                      <div className="flex h-8 w-8 items-center justify-center rounded-full bg-obsessed text-xs font-semibold text-gold">
                        {initials(user.name)}
                      </div>
                      <span className="font-medium text-obsessed">{user.name}</span>
                    </div>
                  </td>
                  <td className="px-5 py-3.5 text-muted">{role?.name ?? user.roleId}</td>
                  <td className="px-5 py-3.5">
                    <Badge tone={user.status === "active" ? "success" : "neutral"}>
                      {user.status === "active" ? "Activo" : "Inactivo"}
                    </Badge>
                  </td>
                  <td className="px-5 py-3.5 text-muted">{user.lastActivity}</td>
                  <td className="px-5 py-3.5">
                    <div className="flex flex-wrap gap-2">
                      <Button variant="ghost" size="sm" onClick={() => openEdit(user)}>
                        <UserCog className="h-3.5 w-3.5" /> Editar
                      </Button>
                      <Button
                        variant={user.status === "active" ? "danger" : "secondary"}
                        size="sm"
                        onClick={() => toggleStatus(user)}
                      >
                        {user.status === "active" ? "Desactivar" : "Activar"}
                      </Button>
                    </div>
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>

      <Modal open={createOpen} onClose={() => setCreateOpen(false)} title="Crear usuario">
        <div className="flex flex-col gap-4">
          <Input label="Nombre completo" value={name} onChange={(e) => setName(e.target.value)} />
          <Input label="Correo (opcional)" value={email} onChange={(e) => setEmail(e.target.value)} />
          <Select
            label="Rol"
            value={roleId}
            onChange={(e) => setRoleId(e.target.value as RoleId)}
            options={ROLES.map((r) => ({ label: r.name, value: r.id }))}
          />
          <div className="mt-2 flex justify-end gap-3">
            <Button variant="secondary" onClick={() => setCreateOpen(false)}>
              Cancelar
            </Button>
            <Button onClick={handleCreate}>Crear</Button>
          </div>
        </div>
      </Modal>

      <Modal open={Boolean(editUser)} onClose={() => setEditUser(null)} title="Editar usuario">
        <div className="flex flex-col gap-4">
          <Input label="Nombre completo" value={name} onChange={(e) => setName(e.target.value)} />
          <Input label="Correo (opcional)" value={email} onChange={(e) => setEmail(e.target.value)} />
          <Select
            label="Rol"
            value={roleId}
            onChange={(e) => setRoleId(e.target.value as RoleId)}
            options={ROLES.map((r) => ({ label: r.name, value: r.id }))}
          />
          <div className="mt-2 flex justify-end gap-3">
            <Button variant="secondary" onClick={() => setEditUser(null)}>
              Cancelar
            </Button>
            <Button onClick={handleEditSave}>Guardar</Button>
          </div>
        </div>
      </Modal>
    </div>
  );
}
