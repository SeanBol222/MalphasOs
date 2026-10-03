---
name: dominio-equipo-mantenimiento
description: equipment_hexagon — Equipment, EquipmentType, Brand, Manufacturer, Model, TechnicalVerification, MetrologicalData. El corazón del negocio de mantenimiento preventivo y la referencia arquitectónica principal
tags: [dominio, backend, mantenimiento-preventivo, nucleo-malphasos, "reusable:alta", "describe:ambos"]
source: Backend/sigma-bb/src/main/java/.../equipment_hexagon/
updated: 2026-10-03
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
- **Las verificaciones tienen ruta propia** (`PATCH /equipment-types/{id}/verifications`) y **no están en `EquipmentTypeUpdateRequest`**. Un formulario de edición que las incluyera parecería funcionar y el cambio se perdería en silencio: hay una prueba que fija su ausencia. (La ruta se llamaba `/verification-mode` y recibía **una** modalidad: cierto hasta el **2026-10-03**.)

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

## La segunda tanda arranca: con qué y cuántas veces se verifica (2026-09-27)

> **Corregido el 2026-10-03, y es una corrección de modelo y no de redacción.** Todo este apartado da
> por supuesto que **un tipo de equipo mide una sola cosa**: habla de «la» modalidad, de
> `tipo_equipo.i_cantidad_datos` y de puntos que cuelgan del tipo con su unidad. Eso fue cierto desde
> `V8` hasta `V10`, y dejó de serlo: lo que se verifica vive ahora un nivel más abajo. Se conserva
> porque explica de dónde viene el modelo de hoy y qué se aprendió por el camino —las dos trampas de
> `CHECK` siguen enteras—, pero **lo que describe ya no es el estado**. Para eso, el apartado siguiente.

El javadoc de `EquipmentType` lo tenía anunciado desde el 2026-09-02 —«los datos metrológicos y las verificaciones técnicas llegarán con la segunda tanda»— y lo que abrió la puerta fue una petición concreta: **poder llenar el reporte**. Un tipo decía *cómo* se verifica y no *con qué* ni *cuántas veces*.

`V8__verification_points.sql` añadió dos cosas a `tipo_equipo` y una tabla:

| Dato | Dónde (hasta `V10`) | Regla |
|---|---|---|
| **Cuántas lecturas** | `tipo_equipo.i_cantidad_datos` | 1 a 100, y **solo** con modalidad constante |
| **En qué valores** | tabla `punto_verificacion`, colgando del tipo | al menos uno con modalidad constante; ninguno sin ella |

**Las lecturas son por punto, no en total.** Se verifica a 50, a 100 y a 150 mmHg, y en cada valor se toman las lecturas declaradas: con 3 lecturas y 2 puntos, el reporte lleva **6** datos. Confundirlo daría un reporte con un tercio de lo que hacía falta, y por eso lo dice la pantalla y lo dice la columna. **Esto sigue valiendo igual**; lo único que cambió es de quién es la cifra.

**Viven en el tipo y no en cada equipo**, por decisión del usuario: todos los tensiómetros de un tipo se verifican igual, y configurarlo una vez sirve para mil equipos. **Eso también sigue valiendo.**

### Dos trampas que costaron un rato y valen para lo que venga

**Un `CHECK` se satisface con `NULL`, no solo con `TRUE`.** La primera versión encadenaba tres ramas con `OR`, y para un tipo no verificable con cantidad fijada daba `NULL OR NULL OR FALSE` = `NULL`: **pasaba**. Lo delató la prueba escrita justo para esa rama. Va con `CASE`, que siempre devuelve `TRUE` o `FALSE`.

**Añadir un `CHECK` se aplica también a lo que ya está.** En una instalación con tipos constantes ya registrados, la migración habría **fallado al arrancar**. Rellena antes con `1` —la afirmación más débil posible; cualquier valor mayor sería inventarse una práctica— y lo dice en voz alta.

**Y el `@Transactional` del adaptador se puso antes de que doliera.** El tipo tiene una colección perezosa, que es exactamente lo que hizo fallar a `work-order` el 2026-09-13. Verificado quitándolo: `LazyInitializationException` en tres pruebas. Es la primera vez que la convención se aplica por adelantado en lugar de después del defecto.

### Y la otra mitad de la segunda tanda se construyó fuera de este módulo (2026-09-27)

El *resultado* de verificar —lo que aquí se anunciaba como «verificaciones técnicas y datos metrológicos»— **existe ya, y no vive en `equipment`**: es `dato_verificacion`, y cuelga del reporte de servicio. El criterio, escrito en `V9`: la lectura se toma durante el servicio y se imprime en el reporte de ese servicio, así que el dato pertenece al reporte y no al catálogo. Este módulo sigue diciendo **cómo** se verifica; quien dice **qué salió** es [[dominio-reporte-servicio]].

## Un tipo se verifica en VARIAS magnitudes (2026-10-03)

**Lo destapó el usuario con un contraejemplo de una línea**: «hay equipos que pueden tener dos tipos de
verificación o hasta más, por ejemplo un termohigrómetro, pues mi temperatura y mi higrometría». El
modelo de `V8` no lo sabía expresar, y la consecuencia práctica era que **un termohigrómetro había que
registrarlo como dos tipos de equipo** —dos fichas técnicas, dos valores de mantenimiento, dos hojas de
vida— cuando es un aparato con un reporte.

`V10__verification_magnitudes.sql` mete un nivel en medio. Antes: `tipo_equipo → punto_verificacion`.
Ahora:

```
tipo_equipo → verificacion_tipo_equipo → punto_verificacion
```

y **lo que era del tipo pasa a ser de cada verificación**: la modalidad, la cantidad de lecturas y la
unidad. Al punto le queda lo único que es suyo, el valor. El diagrama completo, en
[[esquema-bd-malphasos]].

### Qué se movió y qué desapareció

| Dato | Antes | Ahora |
|---|---|---|
| Modalidad | `tipo_equipo.n_tipo_verificacion`, una por aparato | una **por verificación** |
| Lecturas por punto | `tipo_equipo.i_cantidad_datos`, una por aparato | una **por verificación** |
| Unidad | `punto_verificacion.n_unidad`, **repetida en cada punto** | una **por verificación**, y el punto la hereda |
| «Se verifica» | `tipo_equipo.b_verificable` | **derivado**: tiene al menos una verificación activa |

**`b_verificable` no se sustituyó por nada, y no se perdió nada.** `V5` lo ató a la modalidad con un
`CHECK` que obligaba a que fueran de la mano, de modo que el booleano era exactamente
`n_tipo_verificacion IS NOT NULL`: redundante por construcción. Ahora «se verifica» se cuenta.

### La unidad sube de nivel, y eso cierra un agujero

Era la queja concreta del usuario —«para qué tener que repetir mmHg»—, y al subirla apareció que no era
solo molestia: con la unidad en cada punto, **dos puntos hermanos podían contradecirse**, 50 mmHg y
100 kPa en la misma verificación, sin que nada lo impidiera. Declarándola una vez, ese estado dejó de
ser expresable.

**Y el catálogo metrológico es cerrado**, por decisión del usuario sobre cuatro preguntas: magnitud de
un catálogo, unidad de un catálogo filtrado por la magnitud, modalidad por verificación y cantidad de
lecturas por verificación. El motivo de que las unidades sean lista y no texto es concreto: el índice de
unicidad compara la unidad **como texto**, así que `°C` escrito con el signo de grado (U+00B0) y con el
indicador ordinal masculino (U+00BA) eran dos unidades distintas para la base y **la misma a la vista**.

`V10` siembra **20 magnitudes y 46 unidades** y **no hay pantalla que las administre**, igual que los
países de `V7`. Queda como deuda en [[deuda-tecnica-y-riesgos]], no como olvido.

### Tres cosas que el esquema hace cumplir y antes no podía

- **La unidad pertenece a la magnitud.** La foránea de una verificación apunta al par
  `(magnitud, unidad)` **a la vez**, de modo que elegir %HR para una verificación de temperatura es
  **imposible de escribir**, no solo de rechazar. Es la primera foránea compuesta de este módulo.
- **Un tipo no declara dos veces la misma magnitud.** Índice único parcial sobre
  `(tipo, magnitud) WHERE b_estado_activo`. Dos verificaciones de temperatura en el mismo aparato son
  la misma escrita dos veces, y nada diría cuál vale.
- **El punto pertenece a su verificación.** `V9` decía que el esquema no puede exigir que un punto sea
  del tipo del equipo reportado —son cuatro saltos hasta `tipo_equipo`— y eso sigue siendo verdad; pero
  que el punto sea de **su** verificación es un solo salto, y una foránea compuesta lo impone.

Lo que el esquema sigue sin poder exigir es «al menos un punto» con modalidad constante: no ve la otra
tabla. Esa regla vive en `TypeVerification`.

### Y el precio: la trampa del vaciado, que aquí estaba latente

El índice único parcial cobra el mismo precio que en `report`: **Hibernate vacía los `INSERT` antes que
los `UPDATE`**, así que redeclarar una magnitud —la fila vieja pasa a inactiva, la nueva nace activa—
deja las dos activas en ese instante y el índice salta. Se arregla con un `saveAndFlush` intermedio en
el adaptador, igual que en el reporte.

**Lo revelador es por qué nadie lo había visto.** El problema existía ya con el índice de los puntos
desde el 2026-09-26, y **ninguna prueba lo ejercía**: todas las reconfiguraciones de la batería
cambiaban el **valor** del punto, y como el índice es parcial, la fila retirada dejaba de competir. Con
la clave en `(tipo, magnitud)` —que **no cambia** al reconfigurar— salta en el caso normal, y la prueba
escrita para perseguirlo falló a la primera. Segundo módulo que paga esta trampa; ver
[[stack-spring-boot-4-particularidades]].

### Lo que queda de la segunda tanda

**Solo el vencimiento de calibración** —cuándo caduca, que es lo que las alertas de RF-40 necesitan— y
no hay dónde guardarlo. Está registrado en [[deuda-tecnica-y-riesgos]].

Dos cosas de este módulo se ejercen desde fuera y conviene saberlo antes de tocarlas:

- **`TypeVerification.puntosActivos()` es lo que el reporte consulta** para comprobar que una lectura
  señala un punto vigente **de esa verificación**. Retirar un punto no rompe los reportes viejos
  —siguen apuntando a la fila retirada, que no se borra— pero **impide tomar lecturas nuevas en él**.
  Y retirar una verificación **arrastra sus puntos**: dejarlos activos los habría seguido ofreciendo
  desde algo que ya no se verifica.
- **`i_cantidad_datos` es el tope por punto de su verificación**, y el servicio de reportes lo usa para
  dos cosas: rechazar la lectura número N+1 y **exigir las N al cerrar**. Bajarlo en un tipo con
  reportes abiertos dejaría reportes que no se pueden cerrar sin volver a registrar su verificación.

## Reutilizable en MalphasOS

`reusable:alta` — **debería portarse casi completo**, y así se hizo con la primera tanda. El modelo de dominio (`Equipment`, `EquipmentType`, `Brand`, `Manufacturer`, `Model`, `TechnicalVerification`, `MetrologicalData`) es genérico y no acopla nada de facturación/gestión ajena al mantenimiento en sí. Es, junto con `location_hexagon`, la plantilla arquitectónica a seguir para todos los módulos nuevos de MalphasOS — no la de `client_hexagon`.

## Notas relacionadas

[[migracion-equipment-hallazgos]] · [[regla-traslado-mismo-cliente]] · [[arquitectura-frontend-malphasos]] · [[patron-cqrs-commands]] · [[aggregate-root-pattern]] · [[eventos-de-dominio]] · [[dominio-reportes]] · [[esquema-bd-malphasos]] · [[esquema-bd-v4]] · [[evolucion-arquitectonica-crud-a-cqrs]] · [[alcance-malphasos]] · [[checklist-reutilizacion]]
