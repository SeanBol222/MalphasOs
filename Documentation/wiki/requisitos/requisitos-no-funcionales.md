---
name: requisitos-no-funcionales
description: Los 23 RNF de los apartados 3.3 y 3.8 de la ERS, con su estado (solo RNF-23 implementado) y la discrepancia de umbrales de tiempo
tags: [requisitos, rnf, rendimiento]
fuente: "Documentation/IEEE830/IEEE830.tex, apartados 3.3, 3.7 y 3.8; Documentation/TraceabilityMatrix/MatrizDeTrazabilidad.tex, seccion 2"
estado: vigente
updated: 2026-09-05
---

# Requisitos no funcionales (RNF-01 a RNF-23)

23 requisitos. La ERS **nunca marca su estado** — el apartado 3.2 aclara explícitamente que `\estadoImplementado`/`\estadoPrevisto` "se usan solo en este apartado" (el de los RF). La [[matriz-de-trazabilidad]] es la primera evaluación de estado de los RNF, hecha el 5 de septiembre de 2026. **Resultado: de 23, solo uno tiene implementación.**

## Dónde vive cada uno en el `.tex`

16 tienen bloque propio con nivel de complejidad: 10 en el apartado 3.3 (RNF-01, 03, 04, 09, 10, 18, 19, 20, 21, 22) y 6 en el 3.8, "Otros requisitos" (RNF-02, 05, 06, 07, 08, 11). Los 7 restantes (RNF-12 a RNF-17 y RNF-23) se citan por su código dentro de 3.1.1, 3.4 y 3.7 — con enunciado suficiente para identificarlos, pero sin bloque propio.

## Tabla completa

| Código | Requisito | MoSCoW | Estado | Evidencia |
|---|---|---|---|---|
| RNF-01 | Diligenciar OT en ≤3 min | Could Have | Previsto | Depende de la OT y de una interfaz de usuario; ninguna existe |
| RNF-02 | Minimizar entrada manual (listas, autocompletado, selección múltiple) | Sin asignar | Previsto | Hay API REST, no interfaz de usuario |
| RNF-03 | Registrar una OT en ≤2 s | Could Have | Previsto | No existe la OT |
| RNF-04 | Cargar lista de equipos en ≤2 s | Could Have | Previsto | El endpoint existe (`GET /v1/api/service-areas/{id}/equipments`) pero no hay pruebas de carga que fijen un tiempo |
| RNF-05 | Reducir carga cognitiva (una sola entrada de datos generales) | Sin asignar | Previsto | Depende de la OT |
| RNF-06 | Propagar datos generales de la OT a todos los reportes | Should Have | Previsto | Depende de la OT y del reporte |
| RNF-07 | Impedir editar manualmente datos autocompletados | Sin asignar | Previsto | Depende de una interfaz con autocompletado |
| RNF-08 | Exportación consistente y sin pérdida de estructura | Could Have | Previsto | No hay generación de documentos |
| RNF-09 | Cargar lista de reportes en ≤2 s | Sin asignar | Previsto | No existe el reporte |
| RNF-10 | Actualizar historial de equipo en ≤2 s | Won't Have | Previsto | No existe el historial (RF-26, RF-27) |
| RNF-11 | Almacenar imágenes sin pérdida de calidad | Won't Have | Previsto | No hay referencia a imágenes ni almacenamiento binario en el código |
| RNF-12 | Accesibilidad web | Should Have | Previsto | Hay backend, no interfaz de usuario |
| RNF-13 | Diseño responsivo | Should Have | Previsto | No hay interfaz de usuario |
| RNF-14 | Interacción por pantalla táctil | Should Have | Previsto | No hay interfaz de usuario |
| RNF-15 | Usabilidad, procesos en ≤6 pasos | Could Have | Previsto | No hay interfaz de usuario cuyos pasos contar |
| RNF-16 | Componentes de UI (listas, autocompletado, selección múltiple) | Should Have | Previsto | Son componentes de una interfaz que no existe |
| RNF-17 | Mensajes de error claros | Should Have | Previsto | Cada módulo tiene su catálogo de errores propio (`ClientControllerAdvice`, `EquipmentControllerAdvice`, `LocationControllerAdvice`, `PersonControllerAdvice`, `GlobalControllerAdvice`); falta la interfaz que traduzca esos códigos a un mensaje legible |
| RNF-18 | Actualizar inventario en ≤2 s | Won't Have | Previsto | No existe el módulo de inventario |
| RNF-19 | Monitorear alertas cada 24 h | Won't Have | Previsto | No hay `@EnableScheduling` ni tarea programada |
| RNF-20 | Registrar reporte en ≤3 min | Could Have | Previsto | No existe el reporte |
| RNF-21 | Disponibilidad ≥99 % en horario operativo | Could Have | Previsto | No hay entorno de producción ni medición; `/actuator/health` es una sonda para un monitor futuro, no una medición del 99 % |
| RNF-22 | Generar OT desde cotización en ≤2 s | Sin asignar | Previsto | Depende de la cotización y de la OT |
| RNF-23 | JWT para gestión de sesiones | Should Have | **Implementado** | `SecurityConfig` configura `oauth2ResourceServer().jwt(...)` y valida los JWT de Keycloak; `KeycloakRoleConverter` traduce sus roles |

RNF-23 es la misma pieza de infraestructura que sostiene RF-49 y RF-50 — ver [[rf-usuarios-seguridad]].

## La discrepancia de umbrales de rendimiento, nunca conciliada

Tres escalas de tiempo distintas conviven bajo la promesa de "rendimiento" sin que ningún documento las reconcilie:

| Umbral | Dónde aparece |
|---|---|
| **3 segundos** | OBJ-07 y CE-08 (la constitución); también el apartado 3.3 de la ERS ("tiempos de consulta inferiores a 3 segundos") |
| **2 segundos** | RNF-03, RNF-04, RNF-09, RNF-10, RNF-18, RNF-22 — los requisitos que más concretamente miden tiempos de respuesta |
| **3 minutos** | RNF-01 y RNF-20 — para procesos de usuario (diligenciar una OT, registrar un reporte), no para consultas |

2 segundos satisface un umbral de 3, así que las cifras son compatibles entre sí en la práctica — pero la ERS **no lo dice en ningún lugar**; esta lectura es de quien contrasta el documento, no un hecho que el documento declare. Ver [[defectos-conocidos-de-la-ers]].

## Notas relacionadas

[[estado-de-implementacion]] · [[requisitos-de-dominio]] · [[defectos-conocidos-de-la-ers]] · [[rf-usuarios-seguridad]]
