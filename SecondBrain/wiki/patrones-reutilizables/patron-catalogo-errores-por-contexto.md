---
name: patron-catalogo-errores-por-contexto
description: Catálogo enum + ControllerAdvice + ErrorResponse repetido por bounded context — buen aislamiento, boilerplate duplicado
tags: [patron, backend, excepciones, "reusable:media"]
source: Backend/sigma-bb/src/main/java/.../{client,person,location}_hexagon/
updated: 2026-08-27
---

# Patrón: catálogo de errores por bounded context

Cada hexágono (`client`, `person`, `location`) define su propio trío: `<Contexto>ErrorCatalog` (enum de códigos+mensajes), `<Contexto>ErrorResponse` (DTO), `<Contexto>GlobalControllerAdvice` (`@RestControllerAdvice(assignableTypes = {...})`, acotado a los controllers de ese contexto — no es "global" en el sentido literal). Ver el detalle completo, con las inconsistencias detectadas, en [[manejo-global-excepciones]].

## Trade-off del patrón

**Ventaja**: buen aislamiento — un hexágono puede evolucionar su catálogo de errores sin tocar los demás. **Costo**: el `Catalog`+`Response`+`Advice` tiene la misma forma casi idéntica repetida N veces, sin una interfaz o clase base compartida que fuerce la consistencia — lo que ya produjo inconsistencias reales (ver [[manejo-global-excepciones]]: `ClientErrorResponse` filtrándose al advice global, `UNKNOWN_ERROR` compartido entre catálogos que deberían ser independientes, ubicación distinta de las excepciones de dominio entre hexágonos).

## Reutilizable en MalphasOS

`reusable:media` — el aislamiento por bounded context vale la pena conservarlo, pero implementarlo con una interfaz/clase base común (`ErrorCatalog`, `ErrorResponse` genérico) para que el boilerplate no diverja como pasó aquí.

## Notas relacionadas

[[manejo-global-excepciones]] · [[arquitectura-hexagonal]]
