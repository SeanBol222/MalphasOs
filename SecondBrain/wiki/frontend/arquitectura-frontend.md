---
name: arquitectura-frontend
description: React 19 + TS + Vite, proyecto en etapa de bootstrap — organización por tipo técnico, sin estado global ni UI kit todavía
tags: [frontend, "reusable:baja", "describe:original"]
source: Frontend/src/
estado: incompleto
updated: 2026-09-13
---

# Arquitectura del frontend

React 19.2 + TypeScript, bootstrapeado con Vite 8, `react-router-dom` v7 para ruteo. **Proyecto en etapa muy temprana**: solo `App.tsx`, dos páginas (`Login.tsx`, `Dashboard.tsx`), un módulo `auth/` y `services/api.ts`.

- Sin librería de estado global (solo Context API para auth, ver [[integracion-keycloak-frontend]]).
- Sin UI kit detectado (no Material UI/Tailwind/etc. en `package.json`).
- Sin cliente HTTP dedicado — usa `fetch` nativo envuelto en un helper propio (`apiFetch`).
- Estructura por **tipo técnico**, no por feature: `src/pages/`, `src/auth/`, `src/services/`, `src/assets/`. No hay todavía separación por dominio (client, equipment, etc.) — esperable dado el tamaño actual del frontend.
- ESLint 10 + `eslint-plugin-react-hooks` + `eslint-plugin-react-refresh`.

## Reutilizable en MalphasOS

> **Corregido el 2026-09-13.** Esta sección decía que el starter —Vite + React 19 + TS + react-router v7— era «una base moderna y válida para arrancar MalphasOS». **Dejó de serlo ese día**: MalphasOS eligió **Angular**, de modo que nada de este stack se porta. Ver [[arquitectura-frontend-malphasos]] y, para el porqué, `Documentation/wiki/documentos/declaracion-diseno-frontend.md`.

`reusable:baja` — el stack no se reutiliza. **Lo que sí siguió valiendo es lo que esta nota acertó**: que aquí **no había un sistema de diseño ni una convención de organización por feature que copiar**, y que decidirlos era responsabilidad de MalphasOS. Las dos cosas se decidieron el 2026-09-13, y en las dos se hizo lo contrario de lo que el original hacía:

- **Organización por módulo de negocio**, con los nombres del backend, en vez de por tipo técnico (`pages/`, `services/`, `auth/`).
- **Sistema de diseño propio**, derivado del manual de marca, que no existía cuando se escribió esta nota.

El patrón de autenticación se conserva **como patrón, no como código** — ver la corrección en [[integracion-keycloak-frontend]].

## Notas relacionadas

[[integracion-keycloak-frontend]] · [[arquitectura-frontend-malphasos]] · [[sistema-de-diseno-malphasos]] · [[stack-tecnologico]]
