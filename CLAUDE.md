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

Consulta **`SecondBrain/`**: 41 notas interconectadas que documentan y evalúan `bolivarbioingenieria-app`, con juicio explícito de qué portar tal cual, qué adaptar y qué evitar.

Punto de entrada: `SecondBrain/index.md` (catálogo) y `SecondBrain/CLAUDE.md` (convenciones del wiki). Las notas que más se usan:

- `wiki/malphasos/checklist-reutilizacion.md` — **qué está hecho y qué sigue**. Empieza por aquí.
- `wiki/malphasos/decisiones-tecnicas-malphasos.md` — toda decisión tomada, con su porqué.
- `wiki/patrones-reutilizables/deuda-tecnica-y-riesgos.md` — 57 defectos conocidos del original, más una sección aparte con la deuda que hemos introducido nosotros. **Consultar antes de portar cualquier pieza.**
- `wiki/malphasos/migracion-person-hallazgos.md` y `migracion-location-hallazgos.md` — qué apareció al migrar cada módulo.
- `wiki/arquitectura/evolucion-arquitectonica-crud-a-cqrs.md` — Generación 1 (CRUD anémico, no replicar) vs Generación 2 (agregados + eventos, el patrón a seguir).

El original está en `/home/sean-omarchy/Documents/UDistrital/SeptimoSemestre/IngenieriaDeRequerimientos/bolivarbioingenieria-app` y es **inmutable**: se lee, nunca se escribe.

## Estado (2026-09-02)

| Módulo | Estado |
|---|---|
| `bootstrap` | Configuración transversal, seguridad, OpenAPI, manejo de excepciones |
| `shared/domain/events` | Contrato de eventos de dominio + despachador in-process |
| `person` | Completo, más `PersonCommunicationPort` publicado hacia otros módulos |
| `location` | Completo: esquema, dominio, aplicación, persistencia, REST |
| `client` | Esquema y dominio completos. `Client`, `Headquarter` y `ServiceArea` con aplicación y persistencia. **Falta `Manager` y toda la capa REST** |
| `equipment` | Sin empezar. `equipo_cliente` espera a que exista `modelo` |

Migraciones: `V1__baseline`, `V2__person`, `V3__location`, `V4__client`. Batería en ~251 pruebas.

## Cómo se trabaja aquí

**Micro-commits.** Una preocupación por commit, una rama por preocupación, siempre desde `main` actualizado. Merge con `--no-ff` y borrado de la rama. **El usuario revisa el diff antes de cada commit** — no commitear sin su visto bueno.

**Nunca añadir atribución a los commits.** Ni `Co-Authored-By`, ni `Claude-Session`, ni "Generated with". Todo se atribuye al usuario.

**Mensajes de commit**: asunto en inglés siguiendo Conventional Commits, cuerpo en español explicando *por qué*, no *qué*. Los nombres de funciones y clases, en inglés.

**Cada commit queda en verde.** Si separar dos piezas deja la batería rota —un `@Service` sin adaptador tumba el contexto de Spring—, van juntas. Cortar por agregado antes que por capa.

**El wiki se mantiene solo.** Ante un cambio grande o un hallazgo relevante, actualizar `SecondBrain/` en la misma sesión sin que lo pidan, incluidos `index.md` y `log.md`. Si una afirmación del wiki resulta falsa, **corregirla dejando constancia** de que se corrigió y cuándo.

**Verificar ejecutando, no compilando.** Varios de los defectos encontrados compilaban perfectamente. Las pruebas de esquema y de persistencia corren contra un PostgreSQL real vía Testcontainers.

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

**REST**: `@PreAuthorize("hasAuthority('admin.full')")` en todas las operaciones. Solo `PATCH`, sin `PUT`. `DELETE` responde 204 pero retira sin borrar. El identificador sale de la ruta, nunca del cuerpo. Catálogo de errores propio por módulo, con el advice limitado por `assignableTypes`.

## Deuda propia conocida

- Las pruebas son **intermitentes**: la comprobación de salud de RabbitMQ intenta conectarse a `localhost:5672` y falla si no está levantado. Conviene desactivarla en el perfil de pruebas.
- `correo_persona` y `telefono_persona` admiten dueño nulo, al contrario que los contactos del cliente. Corregirlo exige una migración propia.
