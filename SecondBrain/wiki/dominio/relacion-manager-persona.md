---
name: relacion-manager-persona
description: Un encargado ES una persona, por clave primaria compartida — confirmado en esquema, servicio y DTO; el modelo de dominio es el único que no lo dice
tags: [dominio, backend, deuda-de-diseno, "reusable:media"]
source: Backend/sigma-bb/src/main/java/.../client_hexagon/domain/model/manager_model/, DataBase/v4/initdb/A_Sigma_DB_V4.sql
estado: inconsistente
updated: 2026-08-29
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

## Qué hacer en MalphasOS

La decisión ya está tomada por el original y es razonable: **un encargado es una persona con un rol dentro de un cliente**. Lo que hay que cambiar es dónde vive esa afirmación — del método privado al modelo.

Queda por decidir un punto de diseño real, distinto del que esta nota planteaba antes: si `Manager` se modela como **entidad propia con identidad compartida** (`@MapsId` sobre la persona, conservando la forma del esquema) o si el rol de encargado se absorbe dentro de `Person` y `Manager` queda como una asignación a una sede o un área. La primera conserva la compatibilidad con el esquema existente; la segunda es más simple pero exige migrar datos.

## Reutilizable en MalphasOS

`reusable:media` — la relación conceptual se conserva tal cual; su expresión en el código hay que rehacerla. La creación en cascada persona→encargado a través de un puerto entre módulos también se conserva, pero ese puerto arrastra un defecto propio: ver `PersonCommunicationPort` en [[migracion-person-hallazgos]].

## Notas relacionadas

[[dominio-cliente]] · [[dominio-persona-identidad]] · [[reglas-de-negocio-en-el-esquema]] · [[migracion-person-hallazgos]] · [[alcance-malphasos]] · [[checklist-reutilizacion]] · [[deuda-tecnica-y-riesgos]]
