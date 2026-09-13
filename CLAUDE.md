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
- `wiki/patrones-reutilizables/deuda-tecnica-y-riesgos.md` — **68** defectos conocidos del original (decía 57; contados fila a fila el 2026-09-08, recontados el 2026-09-09 en 63 y en **68** el 2026-09-12, con las cinco inconsistencias de `orden_trabajo`), más **24** de deuda propia en dos secciones aparte (eran 22 hasta el 2026-09-12 y 23 durante ese mismo día). **Consultar antes de tocar cualquier pieza.**
- `wiki/malphasos/migracion-person-hallazgos.md` y `migracion-location-hallazgos.md` — qué apareció al migrar cada módulo.
- `wiki/arquitectura/evolucion-arquitectonica-crud-a-cqrs.md` — Generación 1 (CRUD anémico, no replicar) vs Generación 2 (agregados + eventos, el patrón a seguir).

El original está en `/home/sean-omarchy/Documents/UDistrital/SeptimoSemestre/IngenieriaDeRequerimientos/bolivarbioingenieria-app` y es **inmutable**: se lee, nunca se escribe.

## Estado (2026-09-09)

| Módulo | Estado |
|---|---|
| `bootstrap` | Configuración transversal, seguridad, OpenAPI, manejo de excepciones, más `ApiAuthority` —el vocabulario de 19 autoridades y la expansión del administrador—. (Este archivo decía que `ApiAuthority` vivía **solo fuera de `main`**: **falso desde el 2026-09-09**, entró por `e6dda32`.) |
| `shared/domain/events` | Contrato de eventos de dominio + despachador in-process |
| `person` | Completo, más `PersonCommunicationPort` publicado hacia otros módulos y la sincronización con Keycloak al dar de baja y al editar |
| `location` | Completo: esquema, dominio, aplicación, persistencia, REST |
| `client` | Completo: esquema, cuatro agregados, aplicación, persistencia y REST |
| `equipment` | Completo en primera tanda: esquema, seis agregados, aplicación, persistencia y REST. **Falta la segunda tanda**: verificaciones técnicas y datos metrológicos. El traslado no cruza de cliente — regla construida el 2026-09-09, **verificada el 2026-09-10** con 13 pruebas y **en `main` desde `1ef55cf`**; esta tabla la dio por «construida y sin verificar» hasta el **2026-09-12** y por «en rama sin mergear» hasta **más tarde ese mismo día**. Ver [[regla-traslado-mismo-cliente]] |
| `work-order` | **Abierto el 2026-09-12, y solo el esquema**: `V6__work_order` en `feat/work-order-schema`, **sin mergear**. Sin agregados, sin servicios y sin controladores; **los siete requisitos RF-01…07 siguen contando como no implementados**. Es la tanda 1 de 4 —esquema, dominio, aplicación, REST— y las **siete reglas** que el esquema no puede expresar van a la tanda 3. Ver [[dominio-orden-trabajo]] |

Migraciones: `V1__baseline`, `V2__person`, `V3__location`, `V4__client`, `V5__equipment_catalog` y, **fuera de `main`**, `V6__work_order`. (Este archivo hablaba de **cinco**: cierto hasta el **2026-09-12**.)

**Corrección del 2026-09-09**: este archivo hablaba de **dos ramas de código fuera de `main`**, `feat/permission-model` y `fix/person-identity-sync`. **Las dos están dentro** desde ese día —`e6dda32` y `258cd81`— y se borraron tras el merge.

**Corrección del 2026-09-12, posterior a la de más abajo del mismo día**: la tabla de abajo situaba `main` en `01c3277` con **496** y listaba `feat/relocation-same-client` como rama pendiente con 509. **Las dos cosas dejaron de ser ciertas**: `main` está en **`55e0a5d`** y trae dentro la rama del traslado (`1ef55cf`) y su pasada de wiki (`55e0a5d`). Queda **una** rama de código fuera:

| Rama | Qué trae | Pruebas |
|---|---|---|
| `main` (`55e0a5d`) | Los cuatro módulos, el modelo de permisos, la sincronización con Keycloak y **el traslado dentro del mismo cliente**, con sus 13 pruebas | **509** |
| ~~`feat/relocation-same-client` (`43de295`)~~ | **Mergeada en `main`** por `1ef55cf`. Las dos pruebas frágiles que aportó —otra sede del mismo cliente se permite; un área inactiva y de otro cliente responde 400 por cerrada, no 409— están ahora en la línea principal. Ver [[regla-traslado-mismo-cliente]] | — |
| `feat/work-order-schema` (`24e7640`) | El esquema del quinto módulo: `V6__work_order`, más `WorkOrderSchemaTest` —23 métodos, 32 ejecuciones, **cero defectos encontrados**—. Desciende de `43de295`. Ver [[dominio-orden-trabajo]] | **541** |

Las **509** de `main` **no se remidieron al corregir esto**: `git diff 43de295 main -- malphasos/` sale vacío, así que el código es el mismo que ya se midió el 2026-09-12 y el conteo no puede haber cambiado. **Añadido el 2026-09-12**: `feat/work-order-schema` da **541** elementos `<testcase>` —539 por el atributo `tests=`, 418 por los `.txt`, 43 clases, cero fallos, cero errores, cero omitidas—, medido borrando `target/surefire-reports` antes. 509 + 32 = 541 y la suma es legítima porque la rama desciende de `43de295`.

**496 es una remedición, no una suma**: 472 + 361 habría sido un número inventado, y el aviso de remedir ya estaba escrito. **Corrección del 2026-09-12**: esta tabla apuntaba a `3c002b2` con **496** y decía que la rama no añadía ninguna prueba. Era cierto el 2026-09-09 y dejó de serlo el 2026-09-10 con `43de295`, que suma 13 pruebas y deja la rama en **509** —507 por el atributo `tests=`, 386 por los `.txt`, 42 clases, cero fallos—, medido borrando `target/surefire-reports` antes.

Las cifras de arriba son el **número de elementos `<testcase>` de los XML de Surefire**, que es el conteo honesto; en `main` (`55e0a5d`, mismo código que `43de295`) el atributo `tests=` da 507 y los `.txt` 386, y en `24e7640` dan **539 y 418**. (Los 494 y 380 que este archivo citaba para `main` eran los de `01c3277`, antes del merge del traslado.) Los `.txt` dan menos porque **no cuentan las clases `@Nested`** — un `@ParameterizedTest` sí lo cuentan, al contrario de lo que este archivo afirmó hasta el 2026-09-08 —, y el atributo `tests=` se queda corto cuando dos clases `@Nested` tienen un método con el mismo nombre. Y `mvn test` **no borra `target/surefire-reports`**: antes de citar un conteo hay que borrarlo, o se suman informes de corridas y ramas anteriores. Ver [[stack-spring-boot-4-particularidades]].

**La migración del backend está cerrada** salvo esa segunda tanda de `equipment`. **Eso no es lo mismo que el producto terminado**: de los 31 requisitos funcionales de la ERS hay **8** implementados, de los 23 no funcionales **1**, y **no existe una sola línea de frontend**. El siguiente bloque decidido es **órdenes de trabajo**, y **está empezado desde el 2026-09-12**: va la primera de sus cuatro tandas, el esquema, en rama sin mergear. Un esquema no cumple ningún requisito, así que el marcador sigue en 8. El orden completo, con sus dependencias, en [[hoja-de-ruta-producto]].

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

**Esquema**: llaves primarias UUID, con la llave natural como columna única aparte (código ISO, NIT). Prefijos `k_` llaves, `n_` nombres, `t_` texto, `b_` booleanos. Borrado lógico universal con `b_estado_activo`. Las reglas de negocio que el esquema puede expresar van como `CHECK`; las que no —"no abrir un área en una sede cerrada", "una unidad no se traslada al área de otro cliente"— viven en el servicio. Van **seis** construidas; la lista y su porqué, en [[regla-traslado-mismo-cliente]]. El esquema de órdenes de trabajo deja **siete más previstas** para el servicio de su tanda 3 —el total llegaría a trece—, listadas en [[dominio-orden-trabajo]]. **Y una tercera categoría**: una restricción que el esquema *podría* expresar y que **no debe** expresar, porque congelaría el otro lado — ver [[congelar-una-referencia-historica]].

**REST**: `@PreAuthorize` en todas las operaciones, **con la autoridad del recurso y una sola, nombrada literalmente** — `hasAuthority('client.read')`, nunca `admin.full` ni `hasAnyAuthority`. Quien es administrador lo decide `ApiAuthority.expand(...)` en un solo sitio, y una prueba por reflexión impide que un controlador vuelva a nombrarlo. (Hasta el 2026-09-08 aquí decía `hasAuthority('admin.full')` en todas: era cierto y dejó de serlo.) Solo `PATCH`, sin `PUT` — **también en las rutas de sub-recurso**. (La convención se fijó al migrar `location`; `person`, migrado antes, **conserva tres `PUT`** del original y nadie volvió sobre ellos — comprobado el 2026-09-09 sobre los 83 mappings.) `DELETE` responde 204 pero retira sin borrar. El identificador sale de la ruta, nunca del cuerpo. Catálogo de errores propio por módulo, con el advice limitado por `assignableTypes`; **un código de "no existe" nunca se comparte con uno de "datos inválidos"**, y cada referencia hacia otro módulo lleva el suyo.

**OpenAPI**: cada módulo declara su grupo en `OpenApiConfig`, y **cada grupo debe llevar una prueba que consulte `/v3/api-docs/<grupo>` y exija que aparezcan todos sus recursos** — hoy solo la tienen `client` y `equipment`. Un patrón de `pathsToMatch` que no casa con ninguna ruta no falla ni avisa: deja el recurso fuera de Swagger en silencio. Pasó cuatro veces antes de que hubiera pruebas.

## Deuda propia conocida

- La intermitencia de la batería tenía **dos causas y solo una estaba anotada**. La demostrada era el ayudante `unico()` de las pruebas, que recortaba `nanoTime()` con `substring(0, 10)` y lanzaba cuando el resto tenía nueve dígitos (10 % por llamada en `ManagerPersistenceAdapterTest`); corregido el 2026-09-08. La de RabbitMQ —la comprobación de salud apunta a `localhost:5672`— sigue en pie como riesgo, pero sin caso reproducido: ese día dos ejecuciones completas con nada escuchando en 5672 salieron limpias. Conviene desactivarla igualmente en el perfil de pruebas.
- **El filtrado por dueño no existe.** Un usuario del grupo `clients` ve todos los clientes y el catálogo entero. Ninguna clase fuera de `bootstrap/config` toca `Authentication` ni `SecurityContextHolder`. Fue una decisión explícita, no un olvido.
- **Dos grupos de OpenAPI sin prueba de cobertura**, `location` y `reports`; el segundo apunta a un módulo que no existe, así que no casa con ninguna ruta. Es el fallo silencioso que persigue la convención de OpenAPI de más arriba, vivo dentro del propio proyecto.
- `correo_persona` y `telefono_persona` admiten dueño nulo, al contrario que los contactos del cliente. Corregirlo exige una migración propia.
- **Ningún correo de una persona está marcado como principal**, y por eso la edición no puede sincronizar el correo con Keycloak: no hay forma de saber cuál es el de la cuenta. Corregirlo exige también una migración.
- **Cambiar `tipoPersona` no mueve al usuario de grupo en Keycloak**: quien deja de ser ingeniero conserva sus permisos. Heredado del original, dejado fuera a propósito de la tanda del 2026-09-09.
- **Retirar el acceso no invalida los tokens ya emitidos**: siguen abriendo el API hasta que caducan, 300 s en el realm de desarrollo.
- ⚠️ **Una prueba centinela se pondrá roja a propósito en la tanda REST de órdenes de trabajo.** `RestAuthorizationCoverageTest.lasAutoridadesDeWorkOrderSiguenSinModulo` fija que hoy ninguna autoridad `work-order.*` protege ningún endpoint; **falla con el primer controlador del módulo**, y hay que retirarla **en ese mismo commit**, junto con el `filter(a -> !a.startsWith("work-order."))` de `ningunaAutoridadSobra`. Es el patrón de omisión consciente funcionando; sin este aviso parece una regresión. Vive en `main` desde `e6dda32` — un informe del 2026-09-12 afirmó lo contrario y **es falso**, comprobado ese día. Ver [[dominio-orden-trabajo]].
- **El esquema de órdenes de trabajo admite siete situaciones que ninguna regla defiende todavía** — el área del puente fuera de la sede de la orden, el equipo que no estaba ahí, el equipo de otro cliente, un ingeniero que no lo es, referencias inactivas, transiciones de estado hacia atrás y modificar una orden ya `EJECUTADA`. Pendientes **por construcción**: van al servicio en la tanda 3. Hoy no son explotables porque no hay API que cree órdenes. Ver [[dominio-orden-trabajo]].
- ~~**Una prueba que pasa en vacío**, en `feat/relocation-same-client`~~ — **resuelta el 2026-09-10** con `43de295`; esta línea la daba por abierta hasta el **2026-09-12**. `EquipmentChainServiceTest.Unidad.trasladar` no estubaba `findOwningClient`, Mockito devolvía `null` en las dos consultas y la guarda nunca se ejercía. **Repararla no destapó ningún defecto: la guarda funcionaba**, el riesgo era la ceguera. Lo que queda vivo es lo que lo permitió: **`MockitoExtension` en modo estricto no detecta esto**, porque vigila los estubados que sobran y no las llamadas sin estubar. Ver [[regla-traslado-mismo-cliente]].

