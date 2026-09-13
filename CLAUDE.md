# MalphasOS — workspace root

MalphasOS es la aplicación de **gestión de clientes** y **gestión de mantenimientos preventivos** extraída de `bolivarbioingenieria-app`.

```
MalphasOS/
├── malphasos/     -> el proyecto real: backend Spring Boot 4.1.1, Java 21, groupId com.malphasos
├── SecondBrain/   -> wiki técnico de referencia (patrón LLM Wiki). NO es código del proyecto
└── docker/        -> Keycloak (realm de desarrollo) y init de PostgreSQL
```

El código de MalphasOS vive **exclusivamente** en `malphasos/`.

## Antes de decidir nada de arquitectura, dominio o patrones

Consulta **`SecondBrain/`**: 49 notas interconectadas. **Reenfocado el 2026-09-09**: nació para decidir qué portar de `bolivarbioingenieria-app` —pregunta ya contestada, la migración terminó— y hoy responde cómo funciona MalphasOS y qué falta por construir. Cada nota declara con la etiqueta `describe:*` si habla de MalphasOS, del sistema original, o del camino de uno al otro.

Punto de entrada: `SecondBrain/index.md` (catálogo) y `SecondBrain/CLAUDE.md` (convenciones del wiki). Las notas que más se usan:

- `wiki/malphasos/hoja-de-ruta-producto.md` — **qué falta por construir**, backend y frontend, ordenado por dependencias. Empieza por aquí si vas a abrir un módulo nuevo.
- `wiki/malphasos/checklist-reutilizacion.md` — el registro **cerrado** de la migración. Dice qué se hizo y cuándo; no es una lista de pendientes.
- `wiki/malphasos/decisiones-tecnicas-malphasos.md` — toda decisión tomada, con su porqué.
- `wiki/malphasos/modelo-de-permisos.md` — quién puede hacer qué, y por qué se decide en un solo sitio.
- `wiki/patrones-reutilizables/deuda-tecnica-y-riesgos.md` — **68** defectos conocidos del original (decía 57; contados fila a fila el 2026-09-08, recontados el 2026-09-09 en 63 y en **68** el 2026-09-12, con las cinco inconsistencias de `orden_trabajo`), más **25** filas de deuda propia en dos secciones aparte, de las cuales **17 siguen abiertas** — contado fila a fila el 2026-09-13. **Esa cifra cuenta lo registrado, no lo pendiente**: las filas resueltas se tachan y se quedan, porque el porqué de un defecto sigue valiendo después de arreglarlo. (Eran 22 hasta el 2026-09-12, 23 y 24 ese mismo día, y 25 el 2026-09-13 con la persistencia de `work-order` sin pruebas. La regla de la sede **no subió ni bajó el total**: su fila sustituyó a la de «las siete reglas pendientes», y al cerrarse pasó de abierta a tachada sin desaparecer.) **Consultar antes de tocar cualquier pieza.**
- `wiki/malphasos/migracion-person-hallazgos.md` y `migracion-location-hallazgos.md` — qué apareció al migrar cada módulo.
- `wiki/arquitectura/evolucion-arquitectonica-crud-a-cqrs.md` — Generación 1 (CRUD anémico, no replicar) vs Generación 2 (agregados + eventos, el patrón a seguir).

El original está en `/home/sean-omarchy/Documents/UDistrital/SeptimoSemestre/IngenieriaDeRequerimientos/bolivarbioingenieria-app` y es **inmutable**: se lee, nunca se escribe.

## Estado (actualizado el 2026-09-13)

| Módulo | Estado |
|---|---|
| `bootstrap` | Configuración transversal, seguridad, OpenAPI, manejo de excepciones, más `ApiAuthority` —el vocabulario de 19 autoridades y la expansión del administrador—. (Este archivo decía que `ApiAuthority` vivía **solo fuera de `main`**: **falso desde el 2026-09-09**, entró por `e6dda32`.) |
| `shared/domain/events` | Contrato de eventos de dominio + despachador in-process |
| `person` | Completo, más `PersonCommunicationPort` publicado hacia otros módulos y la sincronización con Keycloak al dar de baja y al editar |
| `location` | Completo: esquema, dominio, aplicación, persistencia, REST |
| `client` | Completo: esquema, cuatro agregados, aplicación, persistencia y REST |
| `equipment` | Completo en primera tanda: esquema, seis agregados, aplicación, persistencia y REST. **Falta la segunda tanda**: verificaciones técnicas y datos metrológicos. El traslado no cruza de cliente — regla construida el 2026-09-09, **verificada el 2026-09-10** con 13 pruebas y **en `main` desde `1ef55cf`**; esta tabla la dio por «construida y sin verificar» hasta el **2026-09-12** y por «en rama sin mergear» hasta **más tarde ese mismo día**. Ver [[regla-traslado-mismo-cliente]] |
| `work-order` | **Completo en sus cuatro tandas**, 2026-09-12 y 13: esquema `V6`, agregado de Generación 2 con siete eventos, servicio con las reglas cruzadas, persistencia con conciliación del alcance y **nueve** operaciones REST. **Mergeado entero** el 2026-09-13, por `e69437d`, `1adc2fc` y `b9563a9`. Las **siete reglas** que el esquema dejó al servicio están construidas, pero **nada prueba su persistencia**. De sus siete requisitos cuentan **tres**: los otros cuatro describen un formulario. Ver [[dominio-orden-trabajo]] |

Migraciones: `V1__baseline`, `V2__person`, `V3__location`, `V4__client`, `V5__equipment_catalog` y `V6__work_order`. (Este archivo hablaba de **cinco**: cierto hasta el **2026-09-12**. Y situaba `V6` **fuera de `main`**: cierto hasta el **2026-09-13**, entró con `97ef74f`.)

**Corrección del 2026-09-09**: este archivo hablaba de **dos ramas de código fuera de `main`**, `feat/permission-model` y `fix/person-identity-sync`. **Las dos están dentro** desde ese día —`e6dda32` y `258cd81`— y se borraron tras el merge.

**Corrección del 2026-09-13**: la tabla de abajo situaba `main` en `55e0a5d` con **509** pruebas y listaba `feat/work-order-schema` como la única rama fuera. Dejó de ser cierto el 2026-09-12, y **volvió a dejarlo de ser el 2026-09-13**, cuando entraron las tandas 3 y 4, la regla que faltaba y sus dos pasadas de wiki. **Es la tercera vez seguida que esta tabla caduca en menos de un día**; la lección está al final de [[log.md]] y se resume en una línea: toda pasada de wiki empieza por `git log main..HEAD`, no por lo que dijo la pasada anterior.

**No queda ninguna rama de código fuera de `main`.**

| Rama | Estado | Pruebas |
|---|---|---|
| `main` | Los cinco módulos completos, el modelo de permisos, la sincronización con Keycloak, el traslado dentro del mismo cliente y **órdenes de trabajo de extremo a extremo** | **614** en `b9563a9` |
| ~~`feat/work-order-rest`~~ · ~~`fix/work-order-headquarter-scope`~~ · ~~`docs/wiki-work-order-modulo`~~ | **Mergeadas** el 2026-09-13 por `e69437d`, `1adc2fc` y `b9563a9`, y borradas | — |
| ~~`feat/work-order-schema`~~ · ~~`feat/relocation-same-client`~~ | Mergeadas antes, por `49b6453` y `1ef55cf` | — |

**Esta tabla ya no fija el hash de `main`, a propósito** — ver abajo. Los **614** están **medidos** sobre `b9563a9`, el commit donde se contaron, borrando `target/surefire-reports` antes: 46 clases, cero fallos, cero errores, cero omitidas. El atributo `tests=` da **611** y los `.txt` **433**. Los puntos intermedios que cita [[dominio-orden-trabajo]] van marcados como derivados, porque lo son.

**Por qué ya no se escribe el hash de `main`.** Este archivo lo fijó cuatro veces en dos días y las cuatro caducó, la última **por su propio merge**: la pasada de wiki que anotaba «`main` está en X» dejaba `main` en X+1 al mergearse. No es falta de diligencia, es regresión infinita, y ninguna cantidad de `git log main..HEAD` la arregla.

La distinción que sí sirve, y vale para todo el wiki:

| Un hash que nombra… | Ejemplo | ¿Se escribe? |
|---|---|---|
| **un commit concreto** — una medición, un merge, un arreglo | «614 medidos sobre `b9563a9`», «mergeada por `1ef55cf`» | **Sí.** Es inmutable y verificable |
| **el valor de hoy de un puntero que se mueve** | «`main` está en `b9563a9`» | **No.** Caduca sola, y a veces al escribirla |

`main` se describe **por contenido**; su hash lo da `git`, que para eso está.

**496 es una remedición, no una suma**: 472 + 361 habría sido un número inventado, y el aviso de remedir ya estaba escrito. **Corrección del 2026-09-12**: esta tabla apuntaba a `3c002b2` con **496** y decía que la rama no añadía ninguna prueba. Era cierto el 2026-09-09 y dejó de serlo el 2026-09-10 con `43de295`, que suma 13 pruebas y deja la rama en **509** —507 por el atributo `tests=`, 386 por los `.txt`, 42 clases, cero fallos—, medido borrando `target/surefire-reports` antes.

Las cifras de arriba son el **número de elementos `<testcase>` de los XML de Surefire**, que es el conteo honesto. (Este párrafo daba los equivalentes de `55e0a5d` y `24e7640`; se dejan de citar porque ninguno de los dos describe ya una rama viva.) Los `.txt` dan menos porque **no cuentan las clases `@Nested`** — un `@ParameterizedTest` sí lo cuentan, al contrario de lo que este archivo afirmó hasta el 2026-09-08 —, y el atributo `tests=` se queda corto cuando dos clases `@Nested` tienen un método con el mismo nombre. Y `mvn test` **no borra `target/surefire-reports`**: antes de citar un conteo hay que borrarlo, o se suman informes de corridas y ramas anteriores. Ver [[stack-spring-boot-4-particularidades]].

**La migración del backend está cerrada** salvo esa segunda tanda de `equipment`, y **órdenes de trabajo —el primer módulo construido y no migrado— está terminado** desde el 2026-09-13. **Eso no es lo mismo que el producto terminado**: de los 31 requisitos funcionales de la ERS hay **11** implementados —eran 8—, de los 23 no funcionales **1**, y **no existe una sola línea de frontend**.

**Que el módulo esté completo y solo sume tres requisitos no es un error de cuenta.** Cuatro de sus siete RF describen un **formulario** —elegir áreas, ver equipos por área, seleccionar varios— y eso es frontend. El criterio aplicado: cuenta como implementado lo que el backend satisface por completo; dar por hecho lo demás inflaría la cifra y haría desaparecer de la cuenta trabajo que no se ha hecho. Está escrito en [[hoja-de-ruta-producto]] para poder discutirlo.

**Lo siguiente por dependencias son los reportes de servicio**, que es lo que cuelga directamente de la orden; el módulo ya emite los siete eventos que un reporte querría escuchar, sin consumidor todavía. El orden completo, en [[hoja-de-ruta-producto]].

## Cómo se trabaja aquí

**Micro-commits.** Una preocupación por commit, una rama por preocupación, siempre desde `main` actualizado. Merge con `--no-ff` y borrado de la rama. **El usuario revisa el diff antes de cada commit** — no commitear sin su visto bueno.

**Nunca añadir atribución a los commits.** Ni `Co-Authored-By`, ni `Claude-Session`, ni "Generated with". Todo se atribuye al usuario.

**Mensajes de commit**: asunto en inglés siguiendo Conventional Commits, cuerpo en español explicando *por qué*, no *qué*. Los nombres de funciones y clases, en inglés.

**Cada commit queda en verde.** Si separar dos piezas deja la batería rota —un `@Service` sin adaptador tumba el contexto de Spring—, van juntas. Cortar por agregado antes que por capa.

**El wiki se mantiene solo.** Ante un cambio grande o un hallazgo relevante, actualizar `SecondBrain/` en la misma sesión sin que lo pidan, incluidos `index.md` y `log.md`. Si una afirmación del wiki resulta falsa, **corregirla dejando constancia** de que se corrigió y cuándo.

**Verificar ejecutando, no compilando.** Varios de los defectos encontrados compilaban perfectamente. Las pruebas de esquema y de persistencia corren contra un PostgreSQL real vía Testcontainers.

## Quién hace el trabajo

**Una sola sesión, sin subagentes.** Hubo cuatro —`documentador`, `desarrollador`, `tester` y `wikista`— retirados el 2026-09-12. El ciclo que imponían sigue valiendo como **disciplina de trabajo**, aunque ya no lo reparta nadie:

1. **Escribir producción.** No dar por terminado lo que no compila y no arranca.
2. **Verificar aparte.** Escribir las pruebas mirando el código como si lo hubiera escrito otro, y no ajustar lo esperado hasta que pasen. Una prueba que se dobla para pasar describe el defecto en vez de detectarlo.
3. **Registrar al final**, con la batería en verde, en `SecondBrain/` y en este archivo.

Lo que aquella separación demostró y conviene conservar aun sin ella: **el verde de la batería no prueba que una regla se ejerza** —una prueba con un doble sin estubar pasa comparando `null` contra `null`, y Mockito estricto no lo delata—, y **los defectos aparecen al comparar** dos módulos entre sí, o un documento contra el código, no al leer el módulo donde viven.

**`Documentation/` es territorio aparte**, con sus propias convenciones. Quien escriba ahí debe leer antes **`Documentation/wiki/`**, un wiki de 28 notas con el patrón de `SecondBrain/`: qué dice cada documento oficial, las convenciones de LaTeX y la paleta, el estado real de los requisitos y los defectos conocidos de la ERS. Quedan dos notas por escribir —el glosario del dominio y los defectos conocidos—, marcadas como enlaces sin destino.

## Convenciones de código establecidas

**Agregados de Generación 2** (`client`, `location`; `person` quedó en Generación 1 por decisión explícita):

- Sin setters ni `@Data`. Se entra por `create(...)`, que registra un evento, o por `rehydrate(...)`, que **no** emite: leer de la base no es un hecho del dominio.
- Igualdad por identidad, nunca por datos: `@EqualsAndHashCode(onlyExplicitlyIncluded = true)` sobre el id. **Nunca `callSuper = true`** — `AggregateRoot` no redefine `equals` y la comparación acabaría en la identidad de `Object`.
- Métodos que dicen qué ocurrió (`rename`, `relocateTo`, `deactivate`), no `setX`.
- **Un cambio que no cambia nada no emite evento.** `deactivate()` es idempotente.
- Eventos de baja llamados `Deactivated`, no `Deleted`: aquí nada se borra.
- Colecciones entregadas como copias inmutables.

**Servicios de aplicación**: `@Transactional` en escrituras, `readOnly` en lecturas. Un `record` inmutable por operación de escritura, en `services/<agregado>/commands/`. **Los eventos se publican después de persistir**, y se publican los del agregado recibido, no los del devuelto por el almacén.

**Referencias entre agregados y módulos por identificador**, nunca por objeto. Al crear algo que apunta a otro agregado, comprobarlo en el servicio: así el llamante recibe "esa ciudad no existe" en vez de un conflicto de integridad genérico.

**Persistencia**: los mappers de agregados se escriben **a mano**, no con MapStruct — MapStruct construye por setters o builder, y un agregado no ofrece ninguno a propósito. MapStruct sí sirve del agregado hacia el DTO de respuesta. Los puertos no declaran `delete` ni `update`: retirar es guardar con el estado en falso.

**Esquema**: llaves primarias UUID, con la llave natural como columna única aparte (código ISO, NIT). Prefijos `k_` llaves, `n_` nombres, `t_` texto, `b_` booleanos. Borrado lógico universal con `b_estado_activo`. Las reglas de negocio que el esquema puede expresar van como `CHECK`; las que no —"no abrir un área en una sede cerrada", "una unidad no se traslada al área de otro cliente"— viven en el servicio. Van **trece** construidas: seis de `client` y `equipment` —ver [[regla-traslado-mismo-cliente]]— y las **siete** que el esquema de órdenes de trabajo dejó a su servicio. (Este archivo decía «seis construidas y siete previstas» hasta el **2026-09-13**, y «doce de trece» durante unas horas de ese mismo día, mientras la séptima estuvo detectada y sin cerrar.) **Y una tercera categoría**: una restricción que el esquema *podría* expresar y que **no debe** expresar, porque congelaría el otro lado — ver [[congelar-una-referencia-historica]].

**REST**: `@PreAuthorize` en todas las operaciones, **con la autoridad del recurso y una sola, nombrada literalmente** — `hasAuthority('client.read')`, nunca `admin.full` ni `hasAnyAuthority`. Quien es administrador lo decide `ApiAuthority.expand(...)` en un solo sitio, y una prueba por reflexión impide que un controlador vuelva a nombrarlo. (Hasta el 2026-09-08 aquí decía `hasAuthority('admin.full')` en todas: era cierto y dejó de serlo.) Solo `PATCH`, sin `PUT` — **también en las rutas de sub-recurso**. (La convención se fijó al migrar `location`; `person`, migrado antes, **conserva tres `PUT`** del original y nadie volvió sobre ellos — comprobado el 2026-09-09 sobre los 83 mappings.) `DELETE` responde 204 pero retira sin borrar. El identificador sale de la ruta, nunca del cuerpo. Catálogo de errores propio por módulo, con el advice limitado por `assignableTypes`; **un código de "no existe" nunca se comparte con uno de "datos inválidos"**, y cada referencia hacia otro módulo lleva el suyo.

**OpenAPI**: cada módulo declara su grupo en `OpenApiConfig`, y **cada grupo debe llevar una prueba que consulte `/v3/api-docs/<grupo>` y exija que aparezcan todos sus recursos** — hoy la tienen `client`, `equipment` y `work-order`; siguen sin ella `location`, `person` y `reports`. En `work-order` la prueba se escribió **a la vez** que el grupo, y se comprobó rompiendo el patrón a propósito para verla fallar: una prueba contra un fallo silencioso hay que verla fallar una vez. Un patrón de `pathsToMatch` que no casa con ninguna ruta no falla ni avisa: deja el recurso fuera de Swagger en silencio. Pasó cuatro veces antes de que hubiera pruebas.

## Deuda propia conocida

- La intermitencia de la batería tenía **dos causas y solo una estaba anotada**. La demostrada era el ayudante `unico()` de las pruebas, que recortaba `nanoTime()` con `substring(0, 10)` y lanzaba cuando el resto tenía nueve dígitos (10 % por llamada en `ManagerPersistenceAdapterTest`); corregido el 2026-09-08. La de RabbitMQ —la comprobación de salud apunta a `localhost:5672`— sigue en pie como riesgo, pero sin caso reproducido: ese día dos ejecuciones completas con nada escuchando en 5672 salieron limpias. Conviene desactivarla igualmente en el perfil de pruebas.
- **El filtrado por dueño no existe.** Un usuario del grupo `clients` ve todos los clientes y el catálogo entero. Ninguna clase fuera de `bootstrap/config` toca `Authentication` ni `SecurityContextHolder`. Fue una decisión explícita, no un olvido.
- **Grupos de OpenAPI sin prueba de cobertura**: `location`, `person` —que solo tiene una comprobación de que el documento responde 200, sin mirar qué recursos trae— y `reports`, que apunta a un módulo que no existe y por tanto no casa con ninguna ruta. (Decía «dos»: `work-order` entró **con** la suya el 2026-09-13, así que la lista no creció, pero `person` llevaba fuera de la cuenta desde el principio.) Es el fallo silencioso que persigue la convención de OpenAPI de más arriba, vivo dentro del propio proyecto.
- `correo_persona` y `telefono_persona` admiten dueño nulo, al contrario que los contactos del cliente. Corregirlo exige una migración propia.
- **Ningún correo de una persona está marcado como principal**, y por eso la edición no puede sincronizar el correo con Keycloak: no hay forma de saber cuál es el de la cuenta. Corregirlo exige también una migración.
- **Cambiar `tipoPersona` no mueve al usuario de grupo en Keycloak**: quien deja de ser ingeniero conserva sus permisos. Heredado del original, dejado fuera a propósito de la tanda del 2026-09-09.
- **Retirar el acceso no invalida los tokens ya emitidos**: siguen abriendo el API hasta que caducan, 300 s en el realm de desarrollo.
- ~~⚠️ **Una prueba centinela se pondrá roja a propósito en la tanda REST de órdenes de trabajo.**~~ **Ocurrió el 2026-09-13**, exactamente como estaba anunciado: `lasAutoridadesDeWorkOrderSiguenSinModulo` falló con el primer controlador del módulo y se retiró en ese mismo commit (`ceadba1`), junto con el `filter(a -> !a.startsWith("work-order."))` de `ningunaAutoridadSobra`. La sustituyen dos pruebas, y la segunda —**`assign` es la única operación que exige `work-order.assign`**— es la que impide que la separación entre repartir trabajo y alterarlo desaparezca en silencio. **El aviso escrito por adelantado hizo su trabajo**: el rojo se leyó como lo que era y no como una regresión. Segundo caso del patrón, tras el de `equipo_cliente`.
- ~~**De las siete reglas que el esquema de órdenes de trabajo dejó al servicio, seis se construyeron y una no.**~~ **Resuelta el 2026-09-13** con `0cf56c5`, el mismo día que se detectó: `requireEquipmentInScopeOf` comprueba ya que el área del equipo sea de la sede de la orden. **Lo que conviene recordar no es la regla sino cómo apareció** —contrastando el wiki contra el código, porque la lista de siete se había escrito sin columna de estado— y la decisión que forzó: una guarda nueva que **subsume** a una vieja la mata en silencio si se pone delante, así que el dueño se comprueba antes que la sede y una prueba fija ese orden. Ver [[dominio-orden-trabajo]].
- **Nada prueba la persistencia de `work-order`.** Ni una mención de `WorkOrderPersistenceAdapter`, `WorkOrderPersistenceMapper` ni `WorkOrderRepository` en `src/test`; `WorkOrderServiceTest` usa un doble del puerto. Queda sin ejercer la pieza con más lógica del módulo fuera del dominio —la conciliación que **desactiva filas en vez de borrarlas** y reactiva con el área nueva— y el `@Query` de `findByEquipment`, que filtra por `estadoActivo`. `client`, `location` y `person` sí tienen su `…PersistenceAdapterTest`; `equipment` tampoco. **La ausencia se repite en los dos módulos más recientes.**
- ~~**Una prueba que pasa en vacío**, en `feat/relocation-same-client`~~ — **resuelta el 2026-09-10** con `43de295`; esta línea la daba por abierta hasta el **2026-09-12**. `EquipmentChainServiceTest.Unidad.trasladar` no estubaba `findOwningClient`, Mockito devolvía `null` en las dos consultas y la guarda nunca se ejercía. **Repararla no destapó ningún defecto: la guarda funcionaba**, el riesgo era la ceguera. Lo que queda vivo es lo que lo permitió: **`MockitoExtension` en modo estricto no detecta esto**, porque vigila los estubados que sobran y no las llamadas sin estubar. Ver [[regla-traslado-mismo-cliente]].

