---
name: patron-soft-delete
description: b_estado_activo boolean en casi toda tabla — borrado lógico universal en vez de DELETE físico
tags: [patron, base-de-datos, "reusable:alta"]
source: DataBase/v4/initdb/A_Sigma_DB_V4.sql
updated: 2026-08-27
---

# Patrón: soft-delete universal (`b_estado_activo`)

Casi toda tabla del esquema ([[esquema-bd-v4]]) tiene `b_estado_activo boolean DEFAULT true`. El "delete" de un recurso en la capa de dominio ([[dominio-cliente]], [[dominio-equipo-mantenimiento]]) marca este flag en vez de ejecutar un `DELETE` físico — preserva historial e integridad referencial (coherente con que todas las FKs sean `ON DELETE No Action`, ver [[esquema-bd-v4]]).

Este flag no nació completo: en v1 varias tablas no lo tenían, se generalizó progresivamente hasta v4 (ver [[evolucion-esquema-v1-v4]]).

## Reutilizable en MalphasOS

`reusable:alta` — adoptar `estado_activo` (o el nombre equivalente) en toda tabla desde el primer script de MalphasOS, en vez de agregarlo después como pasó aquí. Es especialmente importante para el dominio de mantenimiento (no se quiere perder el historial de equipos/órdenes de trabajo dados de baja).

## Notas relacionadas

[[esquema-bd-v4]] · [[evolucion-esquema-v1-v4]] · [[dominio-cliente]] · [[dominio-equipo-mantenimiento]]
