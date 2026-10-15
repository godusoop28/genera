# Proyecto Genera — monorepo

Este repositorio contiene tres subproyectos independientes:

- `frontend/` — prototipo Next.js (Módulo 1). Ver `frontend/CLAUDE.md` /
  `frontend/AGENTS.md` para instrucciones específicas de Next.js al trabajar
  ahí (esas instrucciones son generadas/actualizadas por `next dev` y solo
  aplican dentro de `frontend/`).
- `backend/` — API Spring Boot (Módulo 1). Ver `backend/README.md` para
  arquitectura, módulos y comandos.
- `mobile/` — app Flutter (Android/iOS) para el personal interno; usa el mismo
  backend y replica las pantallas de la web. Ver `mobile/README.md`.
- `reference/` — documentos originales de marca y legales de CENTURY 21
  Genera. Son fuentes de solo lectura: nunca se modifican.

No ejecutes comandos de un subproyecto desde la raíz (por ejemplo `npm`,
`mvnw` o `flutter`); usa `cd frontend`, `cd backend` o `cd mobile` primero.
