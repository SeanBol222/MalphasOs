---
name: antipatron-open-in-view
description: Por qué open-in-view enmascara errores de carga perezosa y qué hacer en su lugar
tags: [patron, backend, jpa, "reusable:no"]
source: descubierto al migrar person_hexagon
updated: 2026-08-28
---

# `open-in-view`: por qué apagarlo y qué se rompe al hacerlo

Spring Boot activa `spring.jpa.open-in-view` por omisión. Mantiene abierta la sesión de Hibernate durante **toda la petición HTTP**, incluido el renderizado de la respuesta.

## Por qué es un antipatrón

Suena cómodo y por eso viene activo, pero tiene tres costos:

1. **Retiene una conexión de base de datos durante toda la petición**, incluso mientras se serializa el JSON o se espera a un cliente lento. Bajo carga, el pool se agota antes de lo que la lógica justificaría.
2. **Esconde el problema de la carga perezosa.** Con la sesión abierta, cualquier acceso a una colección `LAZY` funciona, sin importar dónde ocurra. El código parece correcto cuando en realidad depende de un efecto colateral del framework.
3. **Provoca consultas invisibles.** Serializar una respuesta puede disparar consultas N+1 sin que aparezcan en ningún método de servicio, porque las lanza el serializador al recorrer el grafo.

## Lo que ocurrió en la práctica

`bolivarbioingenieria-app` **no declara transacciones en su capa de servicio** — `PersonService` no tiene una sola anotación `@Transactional` — y su adaptador de persistencia traduce a dominio colecciones perezosas. Eso solo funciona porque `open-in-view` está activo por defecto.

En MalphasOS la opción se desactivó desde el principio (ver [[decisiones-tecnicas-malphasos]]). Al migrar el adaptador de personas, la primera prueba contra PostgreSQL real falló de inmediato con `LazyInitializationException`. El defecto llevaba ahí desde siempre, oculto.

## Qué hacer en su lugar

- **Desactivar `open-in-view`** explícitamente. La molestia inicial es la señal de que hay algo que resolver.
- **Que los adaptadores de persistencia sean transaccionales por sí mismos**, con `@Transactional(readOnly = true)` en las lecturas, y no que dependan de que alguien haya abierto una transacción antes. Así funcionan igual desde un servicio, desde una prueba o desde una tarea programada.
- **Mitigar el N+1 con `@BatchSize`** en las colecciones. Cargar cien personas dispararía cien consultas adicionales, una por cada lista de correos; con lotes, unas pocas.
- Ojo con `JOIN FETCH` sobre dos colecciones de tipo `List` a la vez: Hibernate lanza `MultipleBatchFetchException`. Por eso aquí se optó por `@BatchSize` y no por un *entity graph*.

## Reutilizable en MalphasOS

`reusable:no` — es justamente lo que **no** se debe heredar. Esta nota existe para que, al migrar los módulos restantes, se espere el `LazyInitializationException` como parte normal del proceso y se resuelva con transacciones en el adaptador, no reactivando la opción.

## Notas relacionadas

[[migracion-person-hallazgos]] · [[decisiones-tecnicas-malphasos]] · [[deuda-tecnica-y-riesgos]] · [[dominio-persona-identidad]]
