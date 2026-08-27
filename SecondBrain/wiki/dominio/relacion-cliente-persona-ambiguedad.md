---
name: relacion-cliente-persona-ambiguedad
description: Manager (client_hexagon) no referencia Person en el modelo de dominio — ambigüedad de diseño a resolver explícitamente en MalphasOS
tags: [dominio, backend, deuda-de-diseno, decision-pendiente, "reusable:no"]
source: Backend/sigma-bb/src/main/java/.../client_hexagon/domain/model/manager_model/, person_hexagon/domain/model/person_model/
estado: inconsistente
updated: 2026-08-27
---

# Ambigüedad de diseño: Client/Manager vs Person

**No hay relación directa entre `Manager` (dentro de [[dominio-cliente]]) y `Person` (dentro de [[dominio-persona-identidad]]) en el modelo de dominio.** `Manager` es anémico e independiente: solo `identificadorEncargado (UUID)`, `tipoEncargado`, `estadoActivo` — no extiende ni referencia `Person`.

Es posible que la asociación exista solo a nivel de entidad JPA/base de datos (no confirmado en los archivos de dominio revisados), o que conceptualmente sean el mismo humano gestionado por dos módulos separados sin un vínculo formal explícito en el código de dominio.

## Por qué esto importa para MalphasOS

Si MalphasOS necesita que un "encargado"/gestor de mantenimiento sea también un "usuario del sistema" con login (lo cual es casi seguro, dado que [[dominio-persona-identidad]] ya integra con Keycloak para crear usuarios), **hay que decidir explícitamente desde el diseño inicial** si:

1. `Manager` (o su equivalente en MalphasOS) referencia `Person.identificador` directamente, o
2. se fusionan en un solo concepto de dominio, o
3. se mantienen separados a propósito con un mapeo explícito documentado (no implícito como aquí).

No copiar la ambigüedad actual — es deuda de diseño, no una decisión intencional documentada en el código fuente.

## Reutilizable en MalphasOS

`reusable:no` — esto es explícitamente lo que **no** se debe replicar. Es una decisión de diseño pendiente que MalphasOS debe resolver de forma explícita y documentada desde el principio, no heredarla sin más.

## Notas relacionadas

[[dominio-cliente]] · [[dominio-persona-identidad]] · [[alcance-malphasos]] · [[checklist-reutilizacion]]
