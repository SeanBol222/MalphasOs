---
name: evolucion-esquema-v1-v4
description: v1 a v4 es endurecimiento de un mismo modelo (mismas 27 tablas), no un rediseño conceptual
tags: [base-de-datos, historia, "reusable:media"]
source: DataBase/v1/initdb/, DataBase/v4/initdb/A_Sigma_DB_V4.sql
updated: 2026-08-27
---

# Evolución del esquema v1 → v4

Los nombres y el conjunto de tablas son **idénticos** entre v1 y v4 (27 tablas en ambas) — no hubo cambio de modelo conceptual. Los cambios son de endurecimiento:

1. Se añadió `b_estado_activo boolean DEFAULT true` a prácticamente todas las tablas que no lo tenían — el patrón de soft-delete se generalizó con el tiempo, no nació completo (ver [[patron-soft-delete]]).
2. `equipo.k_id_equipo` pasó de `varchar(10)` a `uuid` — estandarización de PKs a UUID en toda la base (antes había mezcla varchar/uuid).
3. Se agregaron columnas PK explícitas donde antes faltaban (`correo_cliente.k_id_correo_cliente`, `correo_persona.k_id_correo_persona` — antes dependían solo de la FK).
4. Se endureció `NOT NULL` en varias columnas antes nullable (ej. `area_servicio.n_nombre_area`, `ciudad.k_id_pais`).
5. Comentarios de columna reescritos/estandarizados.

`C_Data_init.sql` (antes `Data_init.sql`) es un seed de datos maestros (países, ciudades) para desarrollo, no datos transaccionales. El git status muestra que los tres scripts de `v4/initdb/` fueron renombrados con prefijos `A_`/`B_`/`C_` — es un cambio de orden de ejecución (init scripts de Postgres corren en orden alfabético), no un cambio de contenido: A = schema de la app, B = crea la BD de Keycloak, C = seed data.

## Lección para MalphasOS

Empezar el schema de MalphasOS ya con lo que v4 tardó 4 iteraciones en converger: PKs UUID desde el inicio (no varchar), `b_estado_activo` en toda tabla que lo necesite desde el primer script, y NOT NULL correcto desde el diseño inicial en vez de endurecerlo después.

## Notas relacionadas

[[esquema-bd-v4]] · [[patron-soft-delete]] · [[docker-compose]]
