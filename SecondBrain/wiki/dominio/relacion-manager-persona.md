---
name: relacion-manager-persona
description: Un encargado ES una persona, por clave primaria compartida — confirmado en esquema, servicio y DTO; el modelo de dominio es el único que no lo dice
tags: [dominio, backend, deuda-de-diseno, "reusable:media"]
source: Backend/sigma-bb/src/main/java/.../client_hexagon/domain/model/manager_model/, DataBase/v4/initdb/A_Sigma_DB_V4.sql
estado: inconsistente
updated: 2026-08-30
---

# `Manager` y `Person` son el mismo humano, y el dominio no lo dice

> **Corregida el 2026-08-29.** Hasta esa fecha esta nota (llamada entonces `relacion-cliente-persona-ambiguedad`) sostenía que la relación entre `Manager` y `Person` era una **ambigüedad de diseño sin resolver**, y que la existencia del vínculo era "posible pero no confirmada". **Es falso.** La relación está decidida e implementada en tres capas; lo único que falta es que el modelo de dominio la exprese. El error vino de revisar solo los archivos de dominio, donde efectivamente no aparece. La conclusión práctica cambia por completo: no hay una decisión de diseño que tomar antes de migrar `client`, hay una relación que hacer explícita.

## Las tres pruebas

**1. El esquema la declara como identidad compartida.** `encargado.k_identificador` es a la vez la clave primaria de la tabla y una clave foránea a `persona`:

```sql
ALTER TABLE encargado ADD CONSTRAINT "PK_encargado" PRIMARY KEY (k_identificador);
ALTER TABLE encargado ADD CONSTRAINT "FK_encargado_persona"
    FOREIGN KEY (k_identificador) REFERENCES persona (k_identificador);
```

Que la PK *sea* la FK es lo que distingue "un encargado **es** una persona" de "un encargado **tiene** una persona". No puede existir un encargado sin su persona, ni dos encargados sobre la misma.

**2. El servicio la construye así.** `HeadquarterService.addManagerLogicGetUUID()` (y su gemelo en `ServiceAreaService`) crea primero la persona a través de [[dominio-persona-identidad]] y usa el UUID devuelto como identificador del encargado:

```java
personCommunicationPort.save(
    managerServiceMapper.toPersonCommunicationRequest(request).setTipoPersona("MANAGER"))
```

**3. El DTO transporta datos de persona.** `ManagerUseCaseRequest` lleva `cedula`, `primerNombre`, `primerApellido`, `emailPersonList`, `phonePersonList`. Son los campos de una persona, no de un encargado.

## Lo que sí es un defecto

El modelo de dominio y la entidad JPA **no expresan nada de esto**:

- `Manager` tiene tres campos sueltos —`identificadorEncargado`, `tipoEncargado`, `estadoActivo`— sin ninguna referencia a `Person`.
- `ManagerEntity` mapea `k_identificador` como un `@Id UUID` pelado. La FK existe en la base y JPA la ignora: no hay `@OneToOne` ni `@MapsId`.

Consecuencia: la regla vive en el orden en que un método privado hace dos llamadas. Nada impide construir un `Manager` con un UUID que no corresponda a ninguna persona — solo la base lo frenaría, y con un error de integridad, que es el patrón que [[reglas-de-negocio-en-el-esquema]] describe.

Además, `tipoEncargado` es un `String` cuyo javadoc dice `"sede"` y `"area_servicio"`, valores que la restricción `CHK_tipo_encagado` **rechaza**: solo acepta `HEADQUARTER` y `SERVICE_AREA`. Es el mismo defecto que tenía `tipoPersona` antes de convertirlo en enum.

Y un invariante que nadie fuerza: `encargado` tiene `k_id_sede` y `k_id_area_servicio` **ambas anulables**, mientras `t_tipo_encargado` declara cuál de las dos debería estar. Nada impide un `HEADQUARTER` sin sede, con área, con las dos o con ninguna.

## No es el único: `representante_legal`

> **Corregido el 2026-08-30.** Esta sección afirmaba que `representante_legal` vinculaba persona y cliente *"exactamente con el mismo patrón de identidad compartida"* que `encargado`. **Es falso, y son dos patrones distintos.** El error vino de mirar solo los nombres de las columnas —`k_identificador`, FK a persona— sin leer la llave primaria, que es donde se decide la cardinalidad de una relación.

Descubierto el 2026-08-29, explorando el esquema para migrar `client`. Existe una segunda tabla que vincula persona y cliente, pero de otra forma:

```sql
CREATE TABLE representante_legal (
    k_identificador uuid        NOT NULL,   -- FK a persona
    k_id_cliente    varchar(11) NOT NULL,   -- FK a cliente
    b_estado_activo boolean     NOT NULL
);

ALTER TABLE representante_legal ADD CONSTRAINT "PK_representante_legal"
    PRIMARY KEY (k_identificador, k_id_cliente);   -- compuesta
```

**La llave primaria es compuesta**, de modo que la relación es de muchos a muchos: una persona puede representar legalmente a varios clientes, y un cliente tener varios representantes. Es una tabla de unión, no identidad compartida.

| | `encargado` | `representante_legal` |
|---|---|---|
| Llave primaria | `k_identificador` sola | `(k_identificador, k_id_cliente)` |
| Relación con persona | **uno a uno** — el encargado *es* la persona | **muchos a muchos** — la persona *participa* |
| Consecuencia | No puede haber dos encargados sobre la misma persona | La misma persona aparece tantas veces como clientes represente |

La diferencia importa al modelar: `encargado` se expresa con `@MapsId` sobre `Person`, y `representante_legal` no puede, porque su identidad no es la de la persona.

La otra diferencia es que **`representante_legal` no tiene una sola línea de código**. Ni entidad, ni modelo de dominio, ni puerto, ni controlador: se buscó en todo el backend y la única aparición del nombre es un `example` dentro de un javadoc. La tabla está en el esquema y nadie la lee ni la escribe.

Y es justo lo que le falta al tipo de persona `CEO_CLIENT`, que sí existe en el enum, en el catálogo de la base y en los grupos del realm de Keycloak: sin esta relación, un representante legal no puede asociarse a ningún cliente. El modelo de datos promete algo que el sistema no hace.

**Decisión para MalphasOS (2026-08-29): se porta y se implementa**, conservando su llave compuesta. Migrada en `V4__client.sql` el 2026-08-30, con pruebas que fijan que una persona puede representar a varios clientes y que no se repite para el mismo. Ver [[decisiones-tecnicas-malphasos]].

## Qué hacer en MalphasOS

La decisión ya está tomada por el original y es razonable: **un encargado es una persona con un rol dentro de un cliente**. Lo que hay que cambiar es dónde vive esa afirmación — del método privado al modelo.

Queda por decidir un punto de diseño real, distinto del que esta nota planteaba antes: si `Manager` se modela como **entidad propia con identidad compartida** (`@MapsId` sobre la persona, conservando la forma del esquema) o si el rol de encargado se absorbe dentro de `Person` y `Manager` queda como una asignación a una sede o un área. La primera conserva la compatibilidad con el esquema existente; la segunda es más simple pero exige migrar datos.

## Reutilizable en MalphasOS

`reusable:media` — la relación conceptual se conserva tal cual; su expresión en el código hay que rehacerla. La creación en cascada persona→encargado a través de un puerto entre módulos también se conserva, pero ese puerto arrastra un defecto propio: ver `PersonCommunicationPort` en [[migracion-person-hallazgos]].

## Notas relacionadas

[[dominio-cliente]] · [[dominio-persona-identidad]] · [[reglas-de-negocio-en-el-esquema]] · [[migracion-person-hallazgos]] · [[alcance-malphasos]] · [[checklist-reutilizacion]] · [[deuda-tecnica-y-riesgos]]
