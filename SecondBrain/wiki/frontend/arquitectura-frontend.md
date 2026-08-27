---
name: arquitectura-frontend
description: React 19 + TS + Vite, proyecto en etapa de bootstrap — organización por tipo técnico, sin estado global ni UI kit todavía
tags: [frontend, "reusable:media"]
source: Frontend/src/
estado: incompleto
updated: 2026-08-27
---

# Arquitectura del frontend

React 19.2 + TypeScript, bootstrapeado con Vite 8, `react-router-dom` v7 para ruteo. **Proyecto en etapa muy temprana**: solo `App.tsx`, dos páginas (`Login.tsx`, `Dashboard.tsx`), un módulo `auth/` y `services/api.ts`.

- Sin librería de estado global (solo Context API para auth, ver [[integracion-keycloak-frontend]]).
- Sin UI kit detectado (no Material UI/Tailwind/etc. en `package.json`).
- Sin cliente HTTP dedicado — usa `fetch` nativo envuelto en un helper propio (`apiFetch`).
- Estructura por **tipo técnico**, no por feature: `src/pages/`, `src/auth/`, `src/services/`, `src/assets/`. No hay todavía separación por dominio (client, equipment, etc.) — esperable dado el tamaño actual del frontend.
- ESLint 10 + `eslint-plugin-react-hooks` + `eslint-plugin-react-refresh`.

## Reutilizable en MalphasOS

`reusable:media` — el starter kit (Vite + React 19 + TS + react-router v7) es una base moderna y válida para arrancar MalphasOS, pero **no hay todavía un sistema de diseño ni convención de organización por feature que copiar** — es responsabilidad de MalphasOS decidir eso desde cero. Lo que sí es directamente portable y maduro es el patrón de autenticación completo, ver [[integracion-keycloak-frontend]].

## Notas relacionadas

[[integracion-keycloak-frontend]] · [[stack-tecnologico]]
