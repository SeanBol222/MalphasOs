---
name: esquema-bd-v4
description: Esquema PostgreSQL actual — 27 tablas, convención de prefijos por tipo de dato, soft-delete universal
tags: [base-de-datos, "reusable:alta"]
source: DataBase/v4/initdb/A_Sigma_DB_V4.sql
updated: 2026-08-27
---

# Esquema de base de datos v4 (PostgreSQL)

27 tablas, generadas con Enterprise Architect. Convención de nombres consistente por prefijo de tipo: `k_` = key/id, `n_` = nombre/texto identificador, `t_` = texto libre, `b_` = boolean, `f_` = fecha, `d_/m_/v_/i_` = numérico, `s_` = JSON/serializado. Casi todas las tablas tienen `b_estado_activo boolean DEFAULT true` (soft-delete universal — ver [[patron-soft-delete]]). Todas las FKs son `ON DELETE No Action ON UPDATE No Action` (integridad estricta, sin cascadas) con su índice (`IXFK_*`) — buena práctica consistente.

## Ubicación

`pais` (PK varchar(3)) → `ciudad` (PK varchar(10), FK a pais) → `sede` (FK a ciudad y a cliente)

## Persona / identidad

`persona` (PK uuid `k_identificador`, cédula, nombres, `t_tipo_persona`/`t_segundo_tipo_persona` como discriminador de rol) → `correo_persona`, `telefono_persona` (1-a-muchos) → `encargado` (FK a persona + área_servicio + sede) → `representante_legal` (FK a persona + cliente).

## Cliente

`cliente` (PK varchar(11) = NIT/CC, FK a pais) → `correo_cliente`, `telefono_cliente` → `sede` (FK a cliente + ciudad) → `area_servicio` (FK a sede). `contrato` (independiente) y `cotizacion` (FK a equipo_cliente + contrato).

## Equipo

`fabricante` (FK a pais) → `marca` → `modelo` (FK a fabricante, a equipo) → `equipo` (FK a tipo_equipo, marca) → `equipo_cliente` (FK a área_servicio, modelo — el equipo concreto en manos de un cliente). `tipo_equipo` (catálogo maestro: voltaje, amperaje, tecnología, `b_verificable`, valor de mantenimiento). `dato_metrologico` (FK a tipo_equipo). `verificacion_tecnica`, `verificacion_tecnica_tipo_equipo` (N:M tipo_equipo ↔ verificación).

## Mantenimiento / órdenes — núcleo de "gestión de mantenimientos preventivos"

`orden_trabajo` (FK a persona=ingeniero; `n_periodicidad`, `t_estado_ejecucion` default `'CREATED'`) → `reporte_servicio` (FK a orden_trabajo + equipo_cliente) → `reporte_servicio_snapshot` (JSONB — snapshot inmutable, patrón de auditoría/historial) → `protocolo_mantenimiento` (FK a reporte_servicio), `verificacion_ingreso`, `verificacion_metrologica` (FK a reporte_servicio).

## Reutilizable en MalphasOS

`reusable:alta` — prácticamente **todo el schema actual ES el dominio de gestión** que MalphasOS va a extraer: quedaría casi completo (ubicación, persona, cliente, equipo, mantenimiento/órdenes), con las convenciones de prefijo y soft-delete adoptables como estándar desde el día uno. Ver mapeo detallado tabla-por-módulo en [[alcance-malphasos]] y [[checklist-reutilizacion]].

## Notas relacionadas

[[evolucion-esquema-v1-v4]] · [[patron-soft-delete]] · [[dominio-cliente]] · [[dominio-equipo-mantenimiento]] · [[dominio-persona-identidad]] · [[alcance-malphasos]]
