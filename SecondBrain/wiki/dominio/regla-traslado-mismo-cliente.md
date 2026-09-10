---
name: regla-traslado-mismo-cliente
description: Una unidad de equipo solo se traslada a áreas de su propio cliente. Construida el 2026-09-09 y sin verificar, porque la única prueba que la toca pasa en vacío
tags: [dominio, equipment, client, invariantes, fronteras-entre-modulos, "describe:malphasos"]
source: malphasos/src/main/java/com/malphasos/malphasos/equipment/application/services/clientEquipment/ClientEquipmentService.java
estado: incompleto
updated: 2026-09-09
---

# El traslado de una unidad no cruza de cliente

Construido el **2026-09-09** en `3c002b2`, rama `feat/relocation-same-client`, **sin mergear a `main`**.

## El agujero

`ClientEquipmentService.relocate` comprobaba **una sola cosa** del área de destino: que existiera y estuviera activa. Nada impedía mover una unidad al área de **otro cliente**.

Por qué importa más de lo que parece: [[migracion-equipment-hallazgos]] ya registraba que el traslado tiene evento propio, `ClientEquipmentRelocatedEvent`, porque **es el hecho que más importa de una unidad: cambia quién responde por ella**. Un traslado que cruza de cliente deja el historial de mantenimiento de esa unidad colgando de quien nunca la tuvo, y lo hace sin error, sin log y sin rastro.

## La regla

Una unidad solo se traslada a áreas del **mismo cliente**, incluidas las de **otras sedes** de ese cliente. Cruzar de cliente se rechaza.

Vive en `ClientEquipmentService.requireSameClient(...)`, y con ella van **seis reglas de este tipo** en el proyecto: dos en `client` —no abrir un área en una sede cerrada, no poner a nadie al frente de algo cerrado— y cuatro en `equipment` —modelo sobre asociación retirada, unidad de modelo retirado, área cerrada, y ésta—. Ver [[migracion-client-hallazgos]] y [[migracion-equipment-hallazgos]].

**Es la primera de las seis que no pregunta si algo está activo.** Las cinco anteriores existen porque una clave foránea comprueba que una fila exista y no que esté activa, y con borrado lógico esas dos cosas dejan de ser la misma ([[patron-soft-delete]]). Ésta es distinta: compara **dos clientes que ninguna tabla guarda juntos**.

## ⚠️ Está construida y sin verificar

**No hay ninguna prueba que ejerza esta guarda.** Es lo primero que hay que saber antes de confiar en ella, y no se suaviza aquí porque el valor de este wiki depende de eso.

`EquipmentChainServiceTest.Unidad.trasladar` —la única prueba que pasa por `relocate` con éxito— **pasa en vacío**:

- `areaService` es un `@Mock` de `ServiceAreaServicePort`. La prueba estuba `findById(AREA)` y **no estuba `findOwningClient`**.
- Mockito devuelve `null` para un `UUID` no estubado, en las **dos** llamadas: la del área de destino y la del área actual de la unidad.
- `Objects.equals(null, null)` es cierto, así que la comparación pasa y no se lanza nada.

La prueba comprueba lo que siempre comprobó —que el traslado emite `client-equipment.relocated`— y **no toca la regla nueva**. Añadir el `if` no la rompió porque la condición nunca se evalúa con dos valores distintos.

**Qué dice y qué no dice la batería en verde.** Medida el 2026-09-09 sobre `3c002b2`, borrando `target/surefire-reports` antes: **496 ejecuciones** (`<testcase>` de los XML, el conteo honesto según [[stack-spring-boot-4-particularidades]]), 41 clases, cero fallos y cero errores — **exactamente lo mismo que `main` en `01c3277`**, medido igual el mismo día. Es idéntico porque **no se añadió ni una sola prueba**. Ese verde confirma que no se rompió nada; **no confirma que la regla funcione**.

**El ciclo se saltó el paso central.** El reparto de trabajo es `desarrollador → tester → wikista`. En esta tanda **no intervino el `tester`, por decisión explícita del usuario**. No es un olvido y no debe leerse como tal, pero es exactamente por lo que esta nota dice "construida" y no "hecha".

Lo que falta para cerrarlo: una prueba que estube `findOwningClient` con dos clientes distintos y exija `CrossClientRelocationException`, otra con el mismo cliente y dos sedes distintas que exija que el traslado sí ocurra, y una de la capa REST que fije el 409 con `ERR_EQUIPMENT_010`.

## Las decisiones, con su porqué

| Decisión | Elegido | Por qué |
|---|---|---|
| Quién responde "¿de qué cliente es esta área?" | **`client`, en una sola llamada**: `ServiceAreaServicePort.findOwningClient(UUID)` | La alternativa era que `equipment` caminara área → sede → cliente **dos veces**, atándose a la estructura interna de otro contexto. El día que esa jerarquía cambie no debería obligar a tocar el módulo de equipos. Lo que cruza la frontera es un identificador, que es la regla del proyecto para referencias entre módulos |
| Llamada síncrona o evento | **Síncrona** | El traslado necesita la respuesta **antes** de decidir, y **un evento no contesta preguntas**: se publica y se va. Con eventos habría que permitir el traslado y deshacerlo después, que es un caso de uso distinto y más caro |
| Puerto nuevo o ampliar el existente | **Ampliar `ServiceAreaServicePort`** | `equipment` ya dependía de ese puerto **y del agregado `ServiceArea`**, así que un segundo contrato dedicado —al estilo de `PersonCommunicationPort`— no estrechaba nada y solo añadía un bean. **Y hubo una segunda razón, que es sobre nuestro proceso**: ver abajo |
| Dónde vive la regla | **En el servicio, no en el esquema** | Expresarla en SQL exigiría un `CHECK` cruzando **tres tablas** —unidad, área y sede— para comparar dos clientes que ninguna de ellas guarda junta. Es el mismo caso que "no abrir un área en una sede cerrada". Ver [[reglas-de-negocio-en-el-esquema]] |
| Qué se comprueba en el alta | **Nada** | No hay cliente previo que violar: es el área elegida la que **define** de qué cliente pasa a ser la unidad. Buscar simetría con el traslado aquí habría sido inventar una regla |
| Código de estado del rechazo | **409 con código propio `ERR_EQUIPMENT_010`**, no 400 | Los datos recibidos son válidos —el área existe, está abierta y el identificador es correcto—; lo que choca es el **estado** de la unidad. Compartir `INVALID_EQUIPMENT_DATA` impediría al llamante distinguirlo sin leer el mensaje, que es el defecto que este módulo ya corrigió una vez con los códigos 008 y 009. Precedente verificado: `PersonControllerAdvice` responde 409 a `KeycloakUserAlreadyExistsException` |
| Tipo de la excepción | **`RuntimeException` propia**, deliberadamente **no** `IllegalArgumentException` | El advice del módulo traduce esa familia entera a 400. Heredar de ella habría dado 400 en silencio y el javadoc de `CrossClientRelocationException` lo deja escrito |

## Una decisión de diseño empujada por la separación de agentes

La segunda razón para ampliar el puerto en vez de publicar uno nuevo **no es de arquitectura**: inyectar una dependencia nueva cambia el constructor de `ClientEquipmentService`, y eso **rompe la compilación de `EquipmentChainServiceTest`**, un archivo que el subagente `desarrollador` tiene prohibido tocar porque pertenece al `tester`.

Merece quedar escrito porque es un efecto que no se ve en el diff: **el reparto de dominios entre agentes tiene consecuencias sobre el diseño del código**, no solo sobre quién escribe qué. Aquí empujó hacia la opción que además era la correcta por otras razones; no hay garantía de que siempre coincida, y el día que no coincida hay que notarlo y decidir a mano en vez de dejarse arrastrar.

## El método: una frontera que impidió el arreglo cómodo

**El propio `desarrollador` detectó la prueba degenerada y la reportó sin poder arreglarla.** El archivo es del `tester`.

Este wiki ya registra que los defectos aparecen **al comparar**, no al leer —dos módulos entre sí, un documento contra el código— y no en el módulo donde viven. Éste añade una vía nueva: **apareció porque una frontera impidió taparlo en silencio**. Quien construye vio el hueco, no pudo cerrarlo, y la única salida fue escribirlo — en el cuerpo del commit, con las palabras "la regla queda SIN VERIFICAR".

La regla reutilizable: **una separación de dominios que impide el arreglo cómodo convierte un hallazgo privado en un hallazgo público.** Es un beneficio del reparto que no se buscaba al diseñarlo.

## Lo que el traslado hace hoy, paso a paso

Contado sobre el código de `3c002b2`, porque el coste no es evidente leyendo el `if`:

1. `requireActiveServiceArea(destino)` — 1 lectura del área de destino.
2. `findById(unidad)` — 1 lectura de la unidad.
3. `requireSameClient` → `findOwningClient(destino)` — 2 lecturas (área **otra vez** + sede).
4. `requireSameClient` → `findOwningClient(área actual)` — 2 lecturas (área + sede).
5. `relocateTo(...)` y `save(...)`.

Seis lecturas y una escritura, con **el área de destino leída dos veces** dentro de la misma transacción. Funciona; no es gratis.

## Defectos que quedaron sin arreglar

Todos verificados sobre el código de `3c002b2`. Están además en [[deuda-tecnica-y-riesgos]], sección de deuda propia.

- **`relocate` comprueba el área antes que la existencia de la unidad**, de modo que trasladar una unidad **inexistente** a un área cerrada responde **400 y no 404**. Es anterior a este cambio y **está fijado por una prueba**, `trasladarAAreaCerrada`, que usa un `UUID.randomUUID()` como identificador de unidad y nunca estuba su lectura: corregir el orden la rompe. Es el mismo tipo de defecto que este módulo ya corrigió en los códigos de error —un estado que dice una cosa y el resto que dice otra—.
- **Un traslado al área que la unidad ya ocupa recorre las seis lecturas y llega al `save`** para no emitir nada, porque `relocateTo` es idempotente. Ineficiente, no incorrecto.
- **`findByServiceArea` llama a `findById` descartando el resultado.** Funciona como comprobación de existencia y se lee como una línea muerta.
- **`findOwningClient` puede lanzar dos excepciones que el advice de `equipment` no mapea igual de bien**, y esto **no lo reportó nadie: salió de leer el javadoc del puerto al lado del catálogo de errores del módulo**. `HeadquarterNotFoundException` **no tiene manejador** en `EquipmentControllerAdvice` ni en `GlobalControllerAdvice`, así que saldría como **500**; y una `ServiceAreaNotFoundException` lanzada por el **área actual** de la unidad se traduce a 404 `ERR_EQUIPMENT_009`, que el llamante leerá como "el área de destino no existe". Las dos son hoy poco alcanzables —ambas columnas son `NOT NULL` con clave foránea— pero el contrato del puerto las declara por escrito.

## Notas relacionadas

[[dominio-equipo-mantenimiento]] · [[dominio-cliente]] · [[migracion-equipment-hallazgos]] · [[migracion-client-hallazgos]] · [[decisiones-tecnicas-malphasos]] · [[deuda-tecnica-y-riesgos]] · [[reglas-de-negocio-en-el-esquema]] · [[patron-catalogo-errores-por-contexto]] · [[manejo-global-excepciones]] · [[patron-soft-delete]] · [[arquitectura-hexagonal]]
