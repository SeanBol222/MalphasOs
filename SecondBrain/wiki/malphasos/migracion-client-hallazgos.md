---
name: migracion-client-hallazgos
description: Migracion de client_hexagon a Generacion 2 — fronteras de agregados, la tabla sin codigo que se implemento, y las reglas que ningun esquema puede expresar
tags: [malphasos, client, hallazgos, migracion, generacion-2]
source: client_hexagon (original) → malphasos/src/.../client (MalphasOS)
updated: 2026-09-02
---

# Migración de `client`: reconstruido, no portado

Tercer módulo migrado y el más grande: 96 archivos en el original. A diferencia de `person` y `location`, aquí **no se portó la implementación**: el original es Generación 1 —CRUD anémico, sin agregados ni eventos— y el wiki lo marca como patrón a no replicar. Lo que se conservó es la **jerarquía conceptual**; todo lo demás se reconstruyó en Generación 2.

```
cliente ─┬─ correo_cliente / telefono_cliente
         ├─ representante_legal ── persona      (N:M)
         └─ sede ── area_servicio
                 └─ encargado ── persona        (1:1, identidad compartida)
```

`equipo_cliente` quedó fuera: su clave foránea apunta a `modelo`, que pertenece al módulo de equipos y arrastra también `equipo` y `fabricante`. Hay una prueba que fija que la tabla todavía no existe, para que no se olvide.

## La decisión que dio forma a todo: fronteras pequeñas

Se eligieron **cuatro agregados independientes** que se referencian por identificador, en lugar de un `Client` que contuviera sus sedes, sus áreas y sus encargados.

| Agregado | Contiene | Referencia por id |
|---|---|---|
| `Client` | correos, teléfonos, representantes | país |
| `Headquarter` | — | cliente, ciudad |
| `ServiceArea` | — | sede |
| `Manager` | — | sede **o** área |

El motivo: cargar un cliente no arrastra su organización entera, y dos personas editando sedes distintas del mismo cliente no compiten por la misma fila. El coste es que ninguna transacción abarca cliente y sede a la vez. Coincide además con lo que el esquema ya modelaba.

`Manager` es el caso interesante: **su identidad es la de la persona**, no una propia. Su `aggregateId` es el identificador de persona y no puede haber dos encargados sobre la misma. Ver [[relacion-manager-persona]].

## La tabla que no tenía código

`representante_legal` existía en el esquema del original **sin una sola línea de código** que la leyera o escribiera: la única aparición del nombre en todo el backend era un `example` dentro de un javadoc. Es lo que le faltaba al tipo de persona `CEO_CLIENT` —que sí existe en el enum, en el catálogo de la base y en los grupos del realm— para poder asociarse a algún cliente. El modelo de datos prometía algo que el sistema no hacía.

Se implementó dentro del agregado `Client`, como un conjunto de identificadores de persona. Retirar a un representante deja su fila inactiva y el agregado solo reconstruye los activos; volver a nombrarlo reactiva la fila en vez de crear otra, que la llave compuesta rechazaría.

## Reglas que ningún esquema puede expresar

Este es el primer módulo donde aparecen invariantes que **no pueden bajar al esquema**, porque una clave foránea comprueba que una fila exista, no que esté activa — y con borrado lógico esas dos cosas dejan de ser la misma:

- **No se abre un área de servicio en una sede cerrada.** Sería registrar actividad en un sitio que ya no opera.
- **No se pone a nadie al frente de una sede o un área cerrada.** Sería nombrar responsable de algo inexistente.

Viven en los servicios de aplicación, con sus pruebas. Es un patrón que se repetirá en `equipment`.

## Lo que el esquema sí pudo expresar, y el original no ataba

La restricción de asignación del encargado. El original dejaba `k_id_sede` y `k_id_area_servicio` **ambas anulables**, con un `t_tipo_encargado` que declaraba de qué se encargaba y nada que atara una cosa a la otra: cabía un `HEADQUARTER` sin sede, con área, con las dos o con ninguna. El tipo decía una cosa y las claves foráneas otra.

```sql
CONSTRAINT "CHK_encargado_asignacion"
    CHECK ((t_tipo_encargado = 'HEADQUARTER'
                AND k_id_sede IS NOT NULL AND k_id_area_servicio IS NULL)
        OR (t_tipo_encargado = 'SERVICE_AREA'
                AND k_id_area_servicio IS NOT NULL AND k_id_sede IS NULL))
```

El agregado tiene la forma que no admite estados imposibles: guarda **una** asignación y el tipo dice a qué apunta. El mapper es el único del módulo donde las dos formas no se corresponden campo a campo.

## Defectos del original corregidos

| Defecto | Consecuencia |
|---|---|
| Las listas de contactos no se inicializaban | Agregar el primer correo lanzaba `NullPointerException`, igual que en `Person` |
| `removeEmail` no encontraba el correo y **devolvía sin avisar** | Retirar algo inexistente parecía funcionar |
| `tipoIdentificacion` era `String` | Los valores válidos vivían solo en el `CHECK` de la tabla |
| La dirección eran tres campos sueltos de la sede | Cabía una sede con calle y sin número |
| `k_id_cliente` era un `varchar(11)`, el NIT, como llave primaria | Destino de las claves foráneas de cuatro tablas |
| Los correos y teléfonos admitían dueño nulo | Contactos huérfanos, sin forma de llegar a ellos |
| Sin unicidad de sede por cliente ni de área por sede | Dos sedes del mismo cliente podían llamarse igual |
| El alta de encargado **siempre creaba una persona nueva** | Un ingeniero de la empresa no podía figurar además como encargado sin duplicarse |

## Los dos caminos de alta del encargado

Consecuencia del último defecto. `register` crea la persona a través de [[traduccion-de-fallos-de-adaptadores|PersonCommunicationPort]] y la asigna; `assign` parte de alguien que ya existe.

`register` **comprueba que el destino exista y esté activo antes de crear la persona**. Al revés, un fallo en la validación dejaría una persona huérfana en la base sin encargado que la justifique. Y el tipo de persona lo fija el servicio, no quien llama.

## El primer módulo que habla con otros dos

```
client ──> person     (PersonCommunicationPort)   representante legal, alta de encargado
       └─> location   (CityServicePort)           ciudad de la sede
```

En ambos casos se comprueba la referencia en el servicio en lugar de dejarla a la clave foránea, para que el llamante reciba "esa ciudad no existe" y no un conflicto de integridad genérico.

Eso obligó además a que el advice del módulo maneje `CityNotFoundException` y `PersonNotFoundException`: llegan por sus controladores y, sin traducirlas, escaparían al manejador transversal como un 500.

## Notas relacionadas

[[dominio-cliente]] · [[relacion-manager-persona]] · [[migracion-location-hallazgos]] · [[migracion-person-hallazgos]] · [[evolucion-arquitectonica-crud-a-cqrs]] · [[patron-cqrs-commands]] · [[deuda-tecnica-y-riesgos]] · [[decisiones-tecnicas-malphasos]]
