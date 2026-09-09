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

Consulta **`SecondBrain/`**: 46 notas interconectadas. **Reenfocado el 2026-09-09**: nació para decidir qué portar de `bolivarbioingenieria-app` —pregunta ya contestada, la migración terminó— y hoy responde cómo funciona MalphasOS y qué falta por construir. Cada nota declara con la etiqueta `describe:*` si habla de MalphasOS, del sistema original, o del camino de uno al otro.

Punto de entrada: `SecondBrain/index.md` (catálogo) y `SecondBrain/CLAUDE.md` (convenciones del wiki). Las notas que más se usan:

- `wiki/malphasos/hoja-de-ruta-producto.md` — **qué falta por construir**, backend y frontend, ordenado por dependencias. Empieza por aquí si vas a abrir un módulo nuevo.
- `wiki/malphasos/checklist-reutilizacion.md` — el registro **cerrado** de la migración. Dice qué se hizo y cuándo; no es una lista de pendientes.
- `wiki/malphasos/decisiones-tecnicas-malphasos.md` — toda decisión tomada, con su porqué.
- `wiki/malphasos/modelo-de-permisos.md` — quién puede hacer qué, y por qué se decide en un solo sitio.
- `wiki/patrones-reutilizables/deuda-tecnica-y-riesgos.md` — **63** defectos conocidos del original (decía 57; contados fila a fila el 2026-09-08 y recontados el 2026-09-09), más **17** de deuda propia en dos secciones aparte. **Consultar antes de tocar cualquier pieza.**
- `wiki/malphasos/migracion-person-hallazgos.md` y `migracion-location-hallazgos.md` — qué apareció al migrar cada módulo.
- `wiki/arquitectura/evolucion-arquitectonica-crud-a-cqrs.md` — Generación 1 (CRUD anémico, no replicar) vs Generación 2 (agregados + eventos, el patrón a seguir).

El original está en `/home/sean-omarchy/Documents/UDistrital/SeptimoSemestre/IngenieriaDeRequerimientos/bolivarbioingenieria-app` y es **inmutable**: se lee, nunca se escribe.

## Estado (2026-09-09)

| Módulo | Estado |
|---|---|
| `bootstrap` | Configuración transversal, seguridad, OpenAPI, manejo de excepciones. `ApiAuthority` —el vocabulario de 19 autoridades y la expansión del administrador— vive solo en `feat/permission-model`, **no en `main`** |
| `shared/domain/events` | Contrato de eventos de dominio + despachador in-process |
| `person` | Completo, más `PersonCommunicationPort` publicado hacia otros módulos y la sincronización con Keycloak al dar de baja y al editar |
| `location` | Completo: esquema, dominio, aplicación, persistencia, REST |
| `client` | Completo: esquema, cuatro agregados, aplicación, persistencia y REST |
| `equipment` | Completo en primera tanda: esquema, seis agregados, aplicación, persistencia y REST. **Falta la segunda tanda**: verificaciones técnicas y datos metrológicos |

Migraciones: `V1__baseline`, `V2__person`, `V3__location`, `V4__client`, `V5__equipment_catalog`.

**Dos ramas de código están fuera de `main` y no descienden una de otra.** Antes de dar por cierta cualquier afirmación sobre el código, comprobar en cuál se está:

| Rama | Qué trae | Pruebas |
|---|---|---|
| `main` (`82a697b`) | Los cuatro módulos | 339 |
| `feat/permission-model` (`2de115f`) | Cada una de las 83 operaciones REST exige la autoridad de su recurso, no `admin.full`; `ApiAuthority` y 19 autoridades. Falta el filtrado por dueño. Ver [[modelo-de-permisos]] | 472 |
| `fix/person-identity-sync` (`5fde17c`) | Dar de baja a una persona **deshabilita su cuenta de Keycloak** antes de persistir, y editarla propaga nombre y apellido. Ver [[sincronizacion-con-proveedor-de-identidad]] | 361 |

`ApiAuthority` **no existe en `main`**. Las dos ramas duplican por su cuenta el arreglo de `unico()`; el día que se mergeen hay que remedir la batería, no sumar.

Las cifras de arriba son el atributo `tests=` de los XML de Surefire. Los `.txt` dan menos porque **no cuentan las clases `@Nested`** — un `@ParameterizedTest` sí lo cuentan, al contrario de lo que este archivo afirmó hasta el 2026-09-08 —, y el propio atributo `tests=` se queda **por debajo del número real de ejecuciones** cuando dos clases `@Nested` tienen un método con el mismo nombre: en `fix/person-identity-sync` son 363 ejecuciones frente a 361 por atributo y 329 por `.txt`. Y `mvn test` **no borra `target/surefire-reports`**: antes de citar un conteo hay que borrarlo, o se suman informes de corridas y ramas anteriores. Ver [[stack-spring-boot-4-particularidades]].

**La migración del backend está cerrada** salvo esa segunda tanda de `equipment`. **Eso no es lo mismo que el producto terminado**: de los 31 requisitos funcionales de la ERS hay **8** implementados, de los 23 no funcionales **1**, y **no existe una sola línea de frontend**. El siguiente bloque decidido es **órdenes de trabajo**. El orden completo, con sus dependencias, en [[hoja-de-ruta-producto]].

## Cómo se trabaja aquí

**Micro-commits.** Una preocupación por commit, una rama por preocupación, siempre desde `main` actualizado. Merge con `--no-ff` y borrado de la rama. **El usuario revisa el diff antes de cada commit** — no commitear sin su visto bueno.

**Nunca añadir atribución a los commits.** Ni `Co-Authored-By`, ni `Claude-Session`, ni "Generated with". Todo se atribuye al usuario.

**Mensajes de commit**: asunto en inglés siguiendo Conventional Commits, cuerpo en español explicando *por qué*, no *qué*. Los nombres de funciones y clases, en inglés.

**Cada commit queda en verde.** Si separar dos piezas deja la batería rota —un `@Service` sin adaptador tumba el contexto de Spring—, van juntas. Cortar por agregado antes que por capa.

**El wiki se mantiene solo.** Ante un cambio grande o un hallazgo relevante, actualizar `SecondBrain/` en la misma sesión sin que lo pidan, incluidos `index.md` y `log.md`. Si una afirmación del wiki resulta falsa, **corregirla dejando constancia** de que se corrigió y cuándo.

**Verificar ejecutando, no compilando.** Varios de los defectos encontrados compilaban perfectamente. Las pruebas de esquema y de persistencia corren contra un PostgreSQL real vía Testcontainers.

## Reparto del trabajo

Tres subagentes con dominios que no se solapan, invocados con la herramienta Agent y en este orden:

1. **`desarrollador`** — `malphasos/src/main/`, las migraciones y `docker/`. No escribe pruebas.
2. **`tester`** — `malphasos/src/test/`. No toca producción: si encuentra un defecto, lo reporta y el ciclo vuelve al desarrollador.
3. **`wikista`** — `SecondBrain/` y este archivo. Trabaja el último, con la batería en verde.

Los tres trabajan en ramas propias y **no mergean a `main`**: dejan la rama y reportan, para que el usuario revise.

**`Documentation/` no tiene dueño asignado.** Existió un subagente `documentador`, retirado el 2026-09-05 tras entregar la ERS marcada, el plan de gestión del alcance, las matrices de trazabilidad e interesados y la wiki de `Documentation/`. Todo ello está ya en `main`.

Quien vaya a escribir ahí debe leer antes **`Documentation/wiki/`**, un wiki de 28 notas con el patrón de `SecondBrain/`: qué dice cada documento oficial, las convenciones de LaTeX y la paleta, el estado real de los requisitos y los defectos conocidos de la ERS. Quedan dos notas por escribir —el glosario del dominio y los defectos conocidos—, marcadas como enlaces sin destino.

Se trabaja en el worktree `MalphasOS-Documentation`, sobre la rama del mismo nombre.

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

**Esquema**: llaves primarias UUID, con la llave natural como columna única aparte (código ISO, NIT). Prefijos `k_` llaves, `n_` nombres, `t_` texto, `b_` booleanos. Borrado lógico universal con `b_estado_activo`. Las reglas de negocio que el esquema puede expresar van como `CHECK`; las que no —"no abrir un área en una sede cerrada"— viven en el servicio.

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

