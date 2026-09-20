"use client";

import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Modal } from "@/components/ui/Modal";
import { Input } from "@/components/ui/Input";
import { Select } from "@/components/ui/Select";
import { useToast } from "@/components/ui/Toast";
import { ApiError } from "@/lib/api/client";
import { listRoles } from "@/lib/api/roles";
import { activateUser, createUser, deactivateUser, listUsers, updateUser } from "@/lib/api/users";
import type { BackendRoleCode, RoleResponse, UserResponse } from "@/lib/api/types";
import { initials } from "@/lib/utils";
import { Plus, UserCog } from "lucide-react";
import { useCallback, useEffect, useState } from "react";

export function UsersTable() {
  const { showToast } = useToast();
  const [users, setUsers] = useState<UserResponse[] | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [roles, setRoles] = useState<RoleResponse[]>([]);
  const [createOpen, setCreateOpen] = useState(false);
  const [editUser, setEditUser] = useState<UserResponse | null>(null);
  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [roleCode, setRoleCode] = useState<BackendRoleCode>("ADVISOR");
  const [saving, setSaving] = useState(false);

  const loadUsers = useCallback(() => {
    listUsers()
      .then((page) => {
        setUsers(page.items);
        setLoadError(null);
      })
      .catch((err) => {
        setLoadError(err instanceof ApiError ? `No se pudo cargar la lista de usuarios (${err.status}).` : "Error de conexión.");
      });
  }, []);

  useEffect(() => {
    loadUsers();
    listRoles()
      .then(setRoles)
      .catch(() => undefined);
  }, [loadUsers]);

  const openCreate = () => {
    setName("");
    setEmail("");
    setPassword("");
    setRoleCode("ADVISOR");
    setCreateOpen(true);
  };

  const handleCreate = async () => {
    if (!name.trim() || !email.trim() || password.length < 8) return;
    setSaving(true);
    try {
      await createUser(name.trim(), email.trim(), password, roleCode);
      setCreateOpen(false);
      showToast("Usuario creado.");
      loadUsers();
    } catch (err) {
      showToast(err instanceof ApiError ? `No se pudo crear el usuario (${err.status}): ${err.message}` : "Error de conexión.");
    } finally {
      setSaving(false);
    }
  };

  const openEdit = (user: UserResponse) => {
    setEditUser(user);
    setName(user.name);
    setEmail(user.email);
    setRoleCode(user.roleCode);
  };

  const handleEditSave = async () => {
    if (!editUser || !name.trim() || !email.trim()) return;
    setSaving(true);
    try {
      await updateUser(editUser.id, name.trim(), email.trim(), roleCode);
      setEditUser(null);
      showToast("Usuario actualizado.");
      loadUsers();
    } catch (err) {
      showToast(err instanceof ApiError ? `No se pudo actualizar (${err.status}): ${err.message}` : "Error de conexión.");
    } finally {
      setSaving(false);
    }
  };

  const toggleStatus = async (user: UserResponse) => {
    try {
      if (user.status === "ACTIVE") {
        await deactivateUser(user.id);
        showToast("Usuario desactivado.");
      } else {
        await activateUser(user.id);
        showToast("Usuario activado.");
      }
      loadUsers();
    } catch (err) {
      showToast(err instanceof ApiError ? `No se pudo actualizar (${err.status}): ${err.message}` : "Error de conexión.");
    }
  };

  const roleOptions = roles.map((r) => ({ label: r.name, value: r.code }));

  return (
    <div>
      <div className="mb-4 flex justify-end">
        <Button size="sm" onClick={openCreate}>
          <Plus className="h-4 w-4" /> Crear usuario
        </Button>
      </div>

      <div className="overflow-x-auto rounded-2xl border border-border bg-card">
        <table className="w-full min-w-[720px] text-left text-sm">
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
            {loadError ? (
              <tr>
                <td colSpan={5} className="px-5 py-4">
                  <p className="mb-2 text-sm text-danger-text">{loadError}</p>
                  <Button size="sm" variant="secondary" onClick={loadUsers}>
                    Reintentar
                  </Button>
                </td>
              </tr>
            ) : users === null ? (
              <tr>
                <td colSpan={5} className="px-5 py-4 text-sm text-muted">
                  Cargando usuarios…
                </td>
              </tr>
            ) : (
              users.map((user) => (
                <tr key={user.id}>
                  <td className="px-5 py-3.5">
                    <div className="flex items-center gap-3">
                      <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-brand-ink text-xs font-semibold text-gold">
                        {initials(user.name)}
                      </div>
                      <div>
                        <span className="block font-medium text-obsessed">{user.name}</span>
                        <span className="block text-xs text-muted">{user.email}</span>
                      </div>
                    </div>
                  </td>
                  <td className="px-5 py-3.5 text-muted">{user.roleName}</td>
                  <td className="px-5 py-3.5">
                    <Badge tone={user.status === "ACTIVE" ? "success" : "neutral"}>
                      {user.status === "ACTIVE" ? "Activo" : "Inactivo"}
                    </Badge>
                  </td>
                  <td className="px-5 py-3.5 text-muted">
                    {user.lastActivityAt ? new Date(user.lastActivityAt).toLocaleString("es-MX") : "Sin actividad"}
                  </td>
                  <td className="px-5 py-3.5">
                    <div className="flex flex-wrap gap-2">
                      <Button variant="ghost" size="sm" onClick={() => openEdit(user)}>
                        <UserCog className="h-3.5 w-3.5" /> Editar
                      </Button>
                      <Button
                        variant={user.status === "ACTIVE" ? "danger" : "secondary"}
                        size="sm"
                        onClick={() => toggleStatus(user)}
                      >
                        {user.status === "ACTIVE" ? "Desactivar" : "Activar"}
                      </Button>
                    </div>
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>

      <Modal open={createOpen} onClose={() => setCreateOpen(false)} title="Crear usuario">
        <div className="flex flex-col gap-4">
          <Input label="Nombre completo" value={name} onChange={(e) => setName(e.target.value)} />
          <Input label="Correo" type="email" value={email} onChange={(e) => setEmail(e.target.value)} />
          <Input
            label="Contraseña temporal"
            type="password"
            hint="Mínimo 8 caracteres."
            value={password}
            onChange={(e) => setPassword(e.target.value)}
          />
          <Select label="Rol" value={roleCode} onChange={(e) => setRoleCode(e.target.value as BackendRoleCode)} options={roleOptions} />
          <div className="mt-2 flex justify-end gap-3">
            <Button variant="secondary" onClick={() => setCreateOpen(false)}>
              Cancelar
            </Button>
            <Button onClick={handleCreate} disabled={saving || !name.trim() || !email.trim() || password.length < 8}>
              Crear
            </Button>
          </div>
        </div>
      </Modal>

      <Modal open={Boolean(editUser)} onClose={() => setEditUser(null)} title="Editar usuario">
        <div className="flex flex-col gap-4">
          <Input label="Nombre completo" value={name} onChange={(e) => setName(e.target.value)} />
          <Input label="Correo" type="email" value={email} onChange={(e) => setEmail(e.target.value)} />
          <Select label="Rol" value={roleCode} onChange={(e) => setRoleCode(e.target.value as BackendRoleCode)} options={roleOptions} />
          <div className="mt-2 flex justify-end gap-3">
            <Button variant="secondary" onClick={() => setEditUser(null)}>
              Cancelar
            </Button>
            <Button onClick={handleEditSave} disabled={saving || !name.trim() || !email.trim()}>
              Guardar
            </Button>
          </div>
        </div>
      </Modal>
    </div>
  );
}
