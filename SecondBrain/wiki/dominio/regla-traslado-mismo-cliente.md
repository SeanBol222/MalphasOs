---
name: regla-traslado-mismo-cliente
description: Una unidad de equipo solo se traslada a áreas de su propio cliente. Construida el 2026-09-09, verificada el 2026-09-10 con 13 pruebas y en main desde 1ef55cf; la nota la dio por sin verificar y luego por sin mergear hasta el 2026-09-12
tags: [dominio, equipment, client, invariantes, fronteras-entre-modulos, "describe:malphasos"]
source: malphasos/src/main/java/com/malphasos/malphasos/equipment/application/services/clientEquipment/ClientEquipmentService.java
estado: estable
updated: 2026-09-13
---

# El traslado de una unidad no cruza de cliente

Construido el **2026-09-09** en `3c002b2` y **verificado el 2026-09-10** en `43de295`, los dos en la rama `feat/relocation-same-client`.

> **Corrección del 2026-09-12, más tarde el mismo día.** Esta línea decía que la rama seguía **sin mergear a `main`**, y también lo decían el `CONVENCIONES.md` de la raíz y [[checklist-reutilizacion]]. **Ya no es cierto**: `feat/relocation-same-client` entró en `main` por `1ef55cf`, y la pasada de wiki que la documentó por `55e0a5d`. `main` está hoy en **`55e0a5d`** y no en `01c3277`, y su batería es **509**, no 496 — **y eso también caducó: el 2026-09-13 `main` está en `97ef74f` con 580**, ver el `CONVENCIONES.md` de la raíz —no se remidió: `git diff 43de295 main -- malphasos/` sale vacío, así que el código es el ya medido—. Se detectó al comprobar de qué desciende `feat/work-order-schema`, no leyendo esta nota.
>
> **Todo lo que sigue se escribió cuando la rama estaba fuera** y se conserva tal cual, con los identificadores de commit intactos: los commits son los mismos, solo cambió dónde viven.

## El agujero

`ClientEquipmentService.relocate` comprobaba **una sola cosa** del área de destino: que existiera y estuviera activa. Nada impedía mover una unidad al área de **otro cliente**.

Por qué importa más de lo que parece: [[migracion-equipment-hallazgos]] ya registraba que el traslado tiene evento propio, `ClientEquipmentRelocatedEvent`, porque **es el hecho que más importa de una unidad: cambia quién responde por ella**. Un traslado que cruza de cliente deja el historial de mantenimiento de esa unidad colgando de quien nunca la tuvo, y lo hace sin error, sin log y sin rastro.

## La regla

Una unidad solo se traslada a áreas del **mismo cliente**, incluidas las de **otras sedes** de ese cliente. Cruzar de cliente se rechaza.

Vive en `ClientEquipmentService.requireSameClient(...)`, y con ella van **seis reglas de este tipo** en el proyecto: dos en `client` —no abrir un área en una sede cerrada, no poner a nadie al frente de algo cerrado— y cuatro en `equipment` —modelo sobre asociación retirada, unidad de modelo retirado, área cerrada, y ésta—. Ver [[migracion-client-hallazgos]] y [[migracion-equipment-hallazgos]].

**Ampliación del 2026-09-12, actualizada el 2026-09-13**: aquí decía «siguen siendo **seis** construidas, y el esquema de órdenes de trabajo deja **siete más previstas**, lo que llevaría el total a trece». Cierto ese día. Hoy van **trece de trece**: las seis de siempre más las **siete** de órdenes de trabajo. La última en llegar fue la regla 1 —que el área del equipo sea de la sede de la orden—, que **no estaba prevista sino omitida**: se dio por construida hasta que alguien contrastó la lista contra el servicio, y se cerró el mismo día (`0cf56c5`). Ver la tabla de estado en [[dominio-orden-trabajo]].

**Y la predicción de esta nota se cumplió, literalmente.** Decía que una de las siete —que el equipo sea del cliente de la orden— «es hermana de ésta y probablemente reutilice el mismo `findOwningClient`». Reutiliza exactamente ese puerto, en `WorkOrderService.requireEquipmentBelongsTo`. Es la mejor justificación que ha dado el proyecto de publicar el contrato como **puerto síncrono** en vez de como evento: la segunda pregunta llegó cuatro días después y no hubo que tocar `client` para contestarla.

**Es la primera de las seis que no pregunta si algo está activo.** Las cinco anteriores existen porque una clave foránea comprueba que una fila exista y no que esté activa, y con borrado lógico esas dos cosas dejan de ser la misma ([[patron-soft-delete]]). Ésta es distinta: compara **dos clientes que ninguna tabla guarda juntos**.

## ~~⚠️ Está construida y sin verificar~~ — verificada el 2026-09-10

> **Corrección del 2026-09-12.** Lo que sigue era cierto el 2026-09-09 y **dejó de serlo el 2026-09-10**, cuando el `tester` pasó por esta rama en `43de295`. La sección se conserva entera en vez de reescribirse: **es el registro de lo que costó saltarse el paso central del ciclo**, y ése es hoy su valor. Lo que cambió:
>
> - **La regla está verificada.** La prueba degenerada está reparada —`trasladar` estuba ahora `findOwningClient` para el área actual y para la de destino, con el mismo cliente— y entran **13 pruebas nuevas**, contadas sobre el diff de `43de295`: 4 en `ServiceAreaServiceTest`, 7 en `EquipmentChainServiceTest`, 1 en `EquipmentRestAdapterTest` y 1 en el archivo nuevo `ClientEquipmentRelocationPersistenceTest`, que arma dos sedes reales del mismo cliente contra PostgreSQL y comprueba la fila por JDBC tras el traslado.
> - **Los tres huecos que esta nota pedía cerrar están cerrados**: el rechazo con `CrossClientRelocationException`, el traslado permitido entre dos sedes del mismo cliente, y el 409 con `ERR_EQUIPMENT_010` en la capa REST.
> - **Los dos casos frágiles quedaron fijados**, y son los que dan valor a la tanda: trasladar a **otra sede del mismo cliente** se permite —quien compare sedes en vez de clientes pasa todo lo demás y solo falla ahí—, y un área **inactiva que además es de otro cliente** responde 400 por cerrada y no 409 por cliente, porque el área se comprueba antes.
> - **Batería sobre `43de295`**, medida aquí el 2026-09-12 con `./mvnw test` y `rm -rf target/surefire-reports` antes: **509** elementos `<testcase>` —507 por el atributo `tests=`, 386 por los `.txt`—, **42 clases**, cero fallos, cero errores, cero omitidas. Es 496 + 13, y aquí sí cuadra la suma porque es la misma rama con pruebas encima, no dos ramas distintas.
> - **Reparar la prueba ciega no destapó ningún defecto: la guarda funcionaba.** Es lo menos obvio de todo esto y tiene sección propia más abajo.

### Lo que esta sección decía el 2026-09-09, conservado

**No hay ninguna prueba que ejerza esta guarda.** Es lo primero que hay que saber antes de confiar en ella, y no se suaviza aquí porque el valor de este wiki depende de eso.

`EquipmentChainServiceTest.Unidad.trasladar` —la única prueba que pasa por `relocate` con éxito— **pasa en vacío**:

- `areaService` es un `@Mock` de `ServiceAreaServicePort`. La prueba estuba `findById(AREA)` y **no estuba `findOwningClient`**.
- Mockito devuelve `null` para un `UUID` no estubado, en las **dos** llamadas: la del área de destino y la del área actual de la unidad.
- `Objects.equals(null, null)` es cierto, así que la comparación pasa y no se lanza nada.

La prueba comprueba lo que siempre comprobó —que el traslado emite `client-equipment.relocated`— y **no toca la regla nueva**. Añadir el `if` no la rompió porque la condición nunca se evalúa con dos valores distintos.

**Qué dice y qué no dice la batería en verde.** Medida el 2026-09-09 sobre `3c002b2`, borrando `target/surefire-reports` antes: **496 ejecuciones** (`<testcase>` de los XML, el conteo honesto según [[stack-spring-boot-4-particularidades]]), 41 clases, cero fallos y cero errores — **exactamente lo mismo que `main` en `01c3277`**, medido igual el mismo día. Es idéntico porque **no se añadió ni una sola prueba**. Ese verde confirma que no se rompió nada; **no confirma que la regla funcione**.

**El ciclo se saltó el paso central.** El reparto de trabajo es `desarrollador → tester → wikista`. En esta tanda **no intervino el `tester`, por decisión explícita del usuario**. No es un olvido y no debe leerse como tal, pero es exactamente por lo que esta nota dice "construida" y no "hecha".

Lo que falta para cerrarlo: una prueba que estube `findOwningClient` con dos clientes distintos y exija `CrossClientRelocationException`, otra con el mismo cliente y dos sedes distintas que exija que el traslado sí ocurra, y una de la capa REST que fije el 409 con `ERR_EQUIPMENT_010`.

*(Fin de lo conservado. Las tres se escribieron el 2026-09-10 en `43de295`, tal como estaban pedidas.)*

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

**Cierre del 2026-09-12**: el circuito se completó. El hallazgo, escrito en el commit y en este wiki el 2026-09-09, lo recogió el `tester` al día siguiente y lo cerró en `43de295`. El coste del reparto —que quien ve el hueco no puede taparlo— se pagó en **un día de retraso**; a cambio quedó por escrito en tres sitios en vez de arreglado en silencio en uno.

## Reparar una prueba ciega y no encontrar nada detrás (2026-09-10)

Lo esperable al reparar una prueba que pasaba en vacío es que, al empezar a ejercer la regla de verdad, salte algo. **Aquí no saltó nada: la guarda funcionaba tal como estaba escrita.** El riesgo era la **ceguera**, no un defecto camuflado.

Merece quedar escrito por dos razones opuestas y las dos útiles:

- **Para calibrar la alarma.** La próxima vez que aparezca una prueba que pasa en vacío en este proyecto, se sabrá que al menos una de ellas no escondía ningún fallo. No convierte el hallazgo en inocuo, pero evita tratarlo como si ya hubiera un defecto confirmado detrás.
- **Para no ablandar la exigencia de repararla.** Que no hubiera nada detrás **no reduce la gravedad del hueco**: la batería podía quedarse verde para siempre sin ejercer la regla ni una vez. El problema es la señal falsa, no el bug que pudiera esconder.

**Por qué Mockito no lo delata.** `MockitoExtension` en modo estricto —`STRICT_STUBS`, el de por defecto— vigila **los estubados que sobran**: falla si se prepara una respuesta que nadie llega a pedir. **No vigila lo contrario**, que es lo que pasaba aquí: una llamada **sin estubar** a `findOwningClient(UUID)` no es un error para Mockito, devuelve el valor por defecto del tipo —`null` para un `UUID`— y sigue. Con `Objects.equals(null, null)` la comparación era cierta y la guarda no llegaba a decidir nada. **La herramienta que se supone que aprieta las pruebas es ciega justo a esta forma de degeneración**, y por eso hace falta leer el doble, no confiar en que el modo estricto avise.

## Lo que el traslado hace hoy, paso a paso

Contado sobre el código de `3c002b2`, porque el coste no es evidente leyendo el `if`:

1. `requireActiveServiceArea(destino)` — 1 lectura del área de destino.
2. `findById(unidad)` — 1 lectura de la unidad.
3. `requireSameClient` → `findOwningClient(destino)` — 2 lecturas (área **otra vez** + sede).
4. `requireSameClient` → `findOwningClient(área actual)` — 2 lecturas (área + sede).
5. `relocateTo(...)` y `save(...)`.

Seis lecturas y una escritura, con **el área de destino leída dos veces** dentro de la misma transacción. Funciona; no es gratis.

## Defectos que quedaron sin arreglar

Todos verificados sobre el código de `3c002b2`, y **recomprobados el 2026-09-12 sobre `43de295`**: los cuatro siguen ahí, porque la tanda del `tester` no toca producción. Están además en [[deuda-tecnica-y-riesgos]], sección de deuda propia.

- **`relocate` comprueba el área antes que la existencia de la unidad**, de modo que trasladar una unidad **inexistente** a un área cerrada responde **400 y no 404**. Es anterior a este cambio y **está fijado por una prueba**, `trasladarAAreaCerrada`, que usa un `UUID.randomUUID()` como identificador de unidad y nunca estuba su lectura: corregir el orden la rompe. Es el mismo tipo de defecto que este módulo ya corrigió en los códigos de error —un estado que dice una cosa y el resto que dice otra—. **Ampliación del 2026-09-12**: desde `43de295` lo fijan **dos** pruebas, no una. La nueva, `trasladarUnidadInexistenteConAreaCerrada`, lo hace además **explícito en su nombre y en su javadoc**, describiendo el orden actual como lo intencionado. Corregir el orden ya no rompe una prueba de refilón: contradice una que lo afirma. Eso hace el arreglo más caro y más honesto a la vez, y conviene saberlo antes de abrir la corrección.
- **Un traslado al área que la unidad ya ocupa recorre las seis lecturas y llega al `save`** para no emitir nada, porque `relocateTo` es idempotente. Ineficiente, no incorrecto.
- **`findByServiceArea` llama a `findById` descartando el resultado.** Funciona como comprobación de existencia y se lee como una línea muerta.
- **`findOwningClient` puede lanzar dos excepciones que el advice de `equipment` no mapea igual de bien**, y esto **no lo reportó nadie: salió de leer el javadoc del puerto al lado del catálogo de errores del módulo**. `HeadquarterNotFoundException` **no tiene manejador** en `EquipmentControllerAdvice` ni en `GlobalControllerAdvice`, así que saldría como **500**; y una `ServiceAreaNotFoundException` lanzada por el **área actual** de la unidad se traduce a 404 `ERR_EQUIPMENT_009`, que el llamante leerá como "el área de destino no existe". Las dos son hoy poco alcanzables —ambas columnas son `NOT NULL` con clave foránea— pero el contrato del puerto las declara por escrito.

  > **Precisión del 2026-09-12**, que la redacción de arriba no daba: el 500 **hoy es inalcanzable por el API**, no solo poco probable. `area_servicio.k_id_sede` es `NOT NULL` con `FOREIGN KEY ... REFERENCES sede`, y **ninguna sede se borra de verdad** —borrado lógico universal, y los puertos de `client` no declaran `delete`—, así que un área siempre apunta a una sede que existe, por construcción. Sigue siendo un hueco del contrato de error; **no es un fallo explotable**. El `tester` lo confirmó el 2026-09-10 y **decidió no escribir la prueba REST a propósito**: fijar ese 500 con una prueba lo habría consagrado como el comportamiento esperado. Lo que sí dejó escrito es que `ServiceAreaService.findOwningClient` lanza `HeadquarterNotFoundException` cuando la sede falta (`findOwningClientDeSedeInexistente`), que es una prueba del contrato del puerto y no del código de estado.

## Notas relacionadas

[[dominio-equipo-mantenimiento]] · [[dominio-cliente]] · [[dominio-orden-trabajo]] · [[congelar-una-referencia-historica]] · [[migracion-equipment-hallazgos]] · [[migracion-client-hallazgos]] · [[decisiones-tecnicas-malphasos]] · [[deuda-tecnica-y-riesgos]] · [[reglas-de-negocio-en-el-esquema]] · [[patron-catalogo-errores-por-contexto]] · [[manejo-global-excepciones]] · [[patron-soft-delete]] · [[arquitectura-hexagonal]]
