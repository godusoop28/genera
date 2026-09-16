# CENTURY 21 Genera — Módulo 1 (prototipo)

Monorepo del prototipo del Módulo 1 (recepción documental, privacidad,
contrato de intermediación) de CENTURY 21 Genera.

```
.
├── frontend/    Next.js — interfaz interna y portal público de clientes
├── backend/     Spring Boot (Java 21) — API y lógica de negocio
├── reference/   Documentos legales y de marca originales (NO se alteran)
└── compose.yml  Postgres + MinIO + Mailpit para desarrollo local
```

Si prefieres que la carpeta raíz se llame "Proyecto Genera" u otro nombre,
puedes renombrarla manualmente desde el explorador de archivos o con
`mv`/`Rename-Item`: no afecta al repositorio Git, que vive en `.git/` dentro
de esta carpeta.

## Empezar

1. Backend: ver [`backend/README.md`](backend/README.md).
2. Frontend: ver [`frontend/CLAUDE.md`](frontend/CLAUDE.md) (o `AGENTS.md`
   dentro de `frontend/`).
3. Infra local (Postgres/MinIO/Mailpit): `docker compose up -d` desde esta
   carpeta.

## Documentos de referencia

`reference/` contiene el contrato de intermediación PROFECO, los avisos de
privacidad y el manual/paleta de marca tal como fueron entregados. El
backend copia lo que necesita a `backend/src/main/resources/legal-templates/`
para servirlo, pero el original en `reference/` nunca se modifica.
