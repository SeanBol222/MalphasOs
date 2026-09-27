---
name: dominio-equipo-mantenimiento
description: equipment_hexagon — Equipment, EquipmentType, Brand, Manufacturer, Model, TechnicalVerification, MetrologicalData. El corazón del negocio de mantenimiento preventivo y la referencia arquitectónica principal
tags: [dominio, backend, mantenimiento-preventivo, nucleo-malphasos, "reusable:alta", "describe:ambos"]
source: Backend/sigma-bb/src/main/java/.../equipment_hexagon/
updated: 2026-09-26
---

# Dominio Equipo y Mantenimiento Preventivo (`equipment_hexagon`)

**Este es el hexágono más importante de todo el wiki para MalphasOS** — es el motor de dominio de mantenimientos preventivos, y a la vez la mejor referencia arquitectónica del repo (Generación 2 completa, ver [[evolucion-arquitectonica-crud-a-cqrs]]).

> **Migrado el 2026-09-02, en primera tanda.** El catálogo y el inventario —`Manufacturer`, `Brand`, `EquipmentType`, `Equipment`, `Model` y `ClientEquipment`— están completos en MalphasOS de esquema a REST. **`TechnicalVerification` y `MetrologicalData` no**: son la segunda tanda y lo único del backend que queda por construir.
>
> Se **reconstruyó, no se portó**, y siete defectos de esta parte del original quedaron corregidos por el camino. Lo que esta nota describe es el original; lo que MalphasOS hace de verdad está en [[migracion-equipment-hallazgos]]. **Donde las dos difieran, manda esa nota.**
>
> **Ampliación del 2026-09-09**: el traslado de una unidad ya no puede cruzar de cliente. Esta línea añadía que la regla **estaba construida sin verificar**; lo estuvo hasta el **2026-09-10**, cuando entraron 13 pruebas en `43de295` — corregido el 2026-09-12. Antes de tocar `ClientEquipmentService`, leer [[regla-traslado-mismo-cliente]].

## Modelo de dominio y relaciones

```
Manufacturer (name, countryId) ──┐
                                  ├──> Model (invima, manufacturerId, equipmentId)
Equipment (equipmentTypeId, brandId) ──┘
   ├──> EquipmentType (relación por id)
   └──> Brand (relación por id)

EquipmentType (nombre, definición técnica, recomendaciones de cuidado,
               voltaje, amperaje, tecnología predominante, verifiable, unitMaintenanceValue)
   ├── List<MetrologicalData>  (value: BigDecimal, type: String) — value object embebido
   └── Set<UUID> technicalVerification — relación M:N gestionada dentro del propio agregado

TechnicalVerification (description, verificationType) — agregado independiente
TechnicalVerificationEquipment — NO es entidad ni agregado; DTO de transporte para eventos/respuestas
```

Todas las relaciones entre agregados son **por UUID**, no referencias de objeto persistidas — DDD con agregados desacoplados. `EquipmentQueryService.attachRelations()` hace el "join" en memoria para hidratar `equipment.equipmentType`/`equipment.brand` solo en el lado de lectura.

## `TechnicalVerification` y `MetrologicalData` — el núcleo real del mantenimiento preventivo

- **`MetrologicalData`**: value object embebido en `EquipmentType` — parámetros metrológicos esperados/normativos para un tipo de equipo (rangos, unidades de medida a verificar).
- **`TechnicalVerification`**: catálogo independiente de tipos de verificación técnica (ej. "calibración eléctrica", "prueba de fuga"), reutilizable entre varios `EquipmentType` vía `Set<UUID>`.
- Un `EquipmentType` define qué verificaciones técnicas y qué datos metrológicos aplican; cada `Equipment` concreto hereda ese perfil de mantenimiento a través de su tipo. `unitMaintenanceValue` y `verifiable` sugieren costeo y flag de aplicabilidad de mantenimiento.

Esto conecta directamente con las tablas `orden_trabajo`, `reporte_servicio`, `protocolo_mantenimiento`, `verificacion_ingreso`, `verificacion_metrologica` en [[esquema-bd-v4]] — el ciclo completo de una orden de trabajo de mantenimiento.

## Patrón CQRS por commands — ver detalle en [[patron-cqrs-commands]]

Aplicado de forma no uniforme: `Equipment`/`Brand`/`Manufacturer`/`Model`/`EquipmentType` separan puertos read/write; `TechnicalVerificationService` usa un solo puerto pero igual usa Commands para escritura.

## Eventos de dominio por entidad

Cada agregado hereda `AggregateRoot` ([[aggregate-root-pattern]]) y registra eventos tipados por operación (`XCreatedEvent`/`XUpdatedEvent`/`XDeletedEvent`). Caso especial: `EquipmentType` también emite eventos de sub-colección (`MetrologicalDataCreated/Updated/DeletedEvent`, `TechnicalVerificationEquipmentCreated/Updated/DeletedEvent`) cuando se añaden/quitan datos metrológicos o verificaciones asociadas, con validación de duplicados vía `DomainException` pura (sin dependencia de Spring). Ver despacho completo en [[eventos-de-dominio]].

`EquipmentReportProviderAdapter` (en `infrastructure/input/listeners`, pese al nombre de carpeta) no es un listener reactivo real — funciona como agregador síncrono bajo demanda: dado un `modelId`, consulta en cascada `model → equipment → equipmentType → brand → manufacturer` y arma un DTO combinado para [[dominio-reportes]].

## Rutas REST expuestas

```
/v1/api/equipment            GET, GET/{id}, POST, PUT/{id}, PATCH/{id}, DELETE/{id}
/v1/api/manufacturers         GET, GET/{id}, POST, PUT/{id}, PATCH/{id}, DELETE/{id}
/v1/api/brands                GET, GET/{id}, POST, PUT/{id}, PATCH/{id}, DELETE/{id}
/v1/api/models                GET, GET/{id}, POST, PUT/{id}, PATCH/{id}, DELETE/{id}
/v1/api/equipment-types        GET, GET/{id}, POST, PUT/{id}, PATCH/{id}, DELETE/{id}
  /{id}/metrological-data          POST, DELETE, PUT (+ batch)
  /{id}/technical-verification     POST, DELETE, PUT (+ batch)
/v1/api/technical-verifications  GET, GET/{id}, POST, PUT/{id}, PATCH/{id}, DELETE/{id}
```

## La cadena del catálogo, vista desde el frontend (2026-09-26)

Las pantallas del catálogo dejaron clara una cosa que el modelo de dominio expresa pero que **nadie lee en un diagrama**: para registrar un equipo de un cliente hay que recorrer cinco piezas en orden, y **saltarse una deja el desplegable siguiente vacío sin explicación**.

```
Marca ──┐
        ├─→ Equipo (tipo × marca) ──┐
Tipo  ──┘                           ├─→ Modelo ──→ Equipo del cliente (en un área)
                    Fabricante ─────┘
```

Tres consecuencias que el frontend tuvo que resolver, y que valen para cualquier cliente del API:

- **Ninguna de las seis respuestas trae nombres, solo identificadores.** Una fila legible de modelos —«Tensiómetro · Welch Allyn · Medtronic»— se compone cruzando **cuatro** listas. Es la misma ausencia que en los encargados, y está anotada como deuda en [[deuda-tecnica-y-riesgos]].
- **«Equipo» significa dos cosas distintas** y es la confusión más probable del módulo: `Equipment` es una **categoría** —un tipo con una marca— y `ClientEquipment` es **una máquina**, con su serie. La pantalla lo dice en voz alta, porque quien no lo sepa buscará su tensiómetro en el catálogo.
- **La modalidad de verificación tiene ruta propia** (`PATCH /equipment-types/{id}/verification-mode`) y **no está en `EquipmentTypeUpdateRequest`**. Un formulario de edición que la incluyera parecería funcionar y el cambio se perdería en silencio: hay una prueba que fija su ausencia.

### La cadena no debe obligar a recorrerla (corregido el 2026-09-26, el mismo día)

La primera versión del frontend puso el **catálogo** como entrada de menú y dejó el registro de un equipo dentro del área. Funcionaba y **estaba mal enfocada**: lo que se consulta a diario es «qué equipos hay», no «qué marcas existen», y quien registra un equipo que acaba de llegar no debería abandonar el formulario, recorrer cuatro pantallas y volver a empezar.

Tres cambios, por decisión del usuario:

| Antes | Ahora |
|---|---|
| Menú: Clientes · Catálogo | Menú: Clientes · **Equipos** · Catálogo |
| Los equipos solo se veían área por área | **Listado de todos los equipos de cliente**, con serie, modelo, inventario, área y estado |
| El alta solo se entraba desde un área | **Dos caminos**: desde el área, o desde el listado eligiendo cliente → sede → área encadenados |
| Faltaba una pieza → al catálogo y volver | **Se crea desde el formulario**, y el modelo nuevo queda elegido |
| El catálogo era una página con las cinco piezas | **Cinco páginas**, con `Catálogo ▾` en el menú y subnavegación dentro. Corregido el mismo día: con treinta marcas, llegar a los fabricantes eran cuatro pantallas |

**El catálogo conserva su entrada propia**, y eso era parte de lo pedido: administrarlo es una tarea aparte y no puede exigir empezar a registrar un equipo para crear una marca.

**El panel que crea un modelo no pregunta por el «equipo del catálogo»: lo deduce.** Si la combinación de tipo y marca ya existe, la reutiliza; si no, la crea. Preguntarlo obligaría a explicar un concepto intermedio que a quien rellena el formulario no le dice nada, y duplicar combinaciones sería peor que ocultarlas.

**Y lo que crea, queda creado.** Son hasta cinco llamadas y el backend no las envuelve en ninguna transacción: si falla la última, la marca nueva ya existe. La pantalla lo dice, porque la alternativa es que alguien reintente a ciegas y acabe con la misma marca tres veces. **Es una razón concreta para que el backend ofreciera un alta compuesta**, y queda anotada.

**Lo que el alta de un equipo del cliente exige de verdad** es menos de lo que parece: `idModelo` y `serie`. El número de inventario, la fecha y el valor de compra son opcionales, porque un equipo se registra cuando llega y esos datos aparecen después. El frontend **no manda las claves vacías**: un cero no es «no se sabe».

**Instalar un equipo exige `equipment.assign`, no `equipment.write`** — igual que el traslado. Es del backend, y la distinción es buena: repartir una máquina a un área no es editar un catálogo. Ver [[modelo-de-permisos]].

## Reutilizable en MalphasOS

`reusable:alta` — **debería portarse casi completo**, y así se hizo con la primera tanda. El modelo de dominio (`Equipment`, `EquipmentType`, `Brand`, `Manufacturer`, `Model`, `TechnicalVerification`, `MetrologicalData`) es genérico y no acopla nada de facturación/gestión ajena al mantenimiento en sí. Es, junto con `location_hexagon`, la plantilla arquitectónica a seguir para todos los módulos nuevos de MalphasOS — no la de `client_hexagon`.

## Notas relacionadas

[[migracion-equipment-hallazgos]] · [[regla-traslado-mismo-cliente]] · [[arquitectura-frontend-malphasos]] · [[patron-cqrs-commands]] · [[aggregate-root-pattern]] · [[eventos-de-dominio]] · [[dominio-reportes]] · [[esquema-bd-v4]] · [[evolucion-arquitectonica-crud-a-cqrs]] · [[alcance-malphasos]] · [[checklist-reutilizacion]]
