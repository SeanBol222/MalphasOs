---
name: migracion-location-hallazgos
description: Lo que aparecio al migrar el modulo que el wiki daba por ejemplar — agregados con la igualdad rota, setters publicos y un evento que mentia sobre si mismo
tags: [malphasos, location, hallazgos, migracion, generacion-2]
source: location_hexagon (original) → malphasos/src/.../location (MalphasOS)
updated: 2026-08-29
---

# Migración de `location`: el módulo ejemplar visto de cerca

Segundo módulo migrado, y el primero de **Generación 2**. Se eligió por delante de `client` porque `sede.k_id_ciudad` es `NOT NULL` y apunta a `ciudad`: sin las tablas de ubicación, la migración de clientes no se puede ni escribir.

> **Corrección a este wiki.** [[dominio-ubicacion]] describía este módulo como *"la referencia de implementación de la Generación 2"* y *"el mejor ejemplo pedagógico de cómo se ve el patrón completo"*, y [[aggregate-root-pattern]] calificaba la pieza compartida como `reusable:alta` **"tal cual, sin cambios"**. Ambas afirmaciones eran generosas. La **forma** es efectivamente ejemplar —factorías estáticas, eventos por hecho, un puerto de despacho— pero la implementación traía cuatro defectos, uno de ellos capaz de romper cualquier prueba o colección que dependiera de comparar dos agregados.

## Los cuatro defectos de los agregados

| Defecto | Consecuencia |
|---|---|
| **`@Data` sobre el agregado** | Genera un setter público por campo. Cualquiera podía renombrar un país sin emitir evento y sin pasar por validación alguna: el agregado guardaba las reglas por la puerta principal y dejaba abierta la de atrás. Es lo contrario de lo que un agregado existe para hacer |
| **`@EqualsAndHashCode(callSuper = true)`** | La superclase `AggregateRoot` no redefine `equals`, así que la llamada terminaba en la comparación por referencia de `Object`. **Dos países con exactamente los mismos datos nunca resultaban iguales**, y su `hashCode` incluía el de identidad, de modo que tampoco coincidían en un `HashSet` |
| **`updateCountryPatch` mentía sobre sí mismo** | Escribía `"country.patch"` en la metadata mientras construía un `CountryUpdatedEvent`. Un consumidor que filtrara por el tipo del evento y otro que filtrara por la clase habrían visto cosas distintas |
| **`deleteCountry()` no cambiaba nada** | Solo registraba el evento. El estado activo ni siquiera existía en el modelo de dominio: vivía únicamente en la columna `b_estado_activo`, que alguien más tenía que acordarse de poner en falso |

## La llave primaria derivada del nombre

Aparte, y de otra naturaleza: `City.createIdFromName` construía la llave primaria de una ciudad tomando **las dos primeras letras de su nombre**.

```java
return name.substring(0, 2).toUpperCase();
```

Bogotá y Boyacá producen ambas `"BO"`. Medellín y Melgar, `"ME"`. La segunda ciudad que empezara por las mismas dos letras chocaba contra la llave primaria de la primera. Además falla con un nombre de una sola letra.

## Lo que el esquema no protegía

Ninguna de las dos tablas tenía **una sola restricción de unicidad**: admitía dos países llamados igual y dos ciudades idénticas dentro del mismo país. El código del país era la llave primaria, un `varchar(3)` sin restricción de formato donde cabía cualquier cosa, minúsculas y espacios incluidos — y era el destino de las claves foráneas de `ciudad`, `cliente` y `fabricante`.

## Cómo quedó en MalphasOS

- **Identidad por UUID** en ambas tablas, según la convención de `V1__baseline`. El código ISO se conserva como llave natural, única y con formato validado (`^[A-Z]{3}$`), pero deja de ser aquello a lo que apunta medio esquema.
- **El nombre de una ciudad es único dentro de su país**, no globalmente: hay un Córdoba en España y otro en Argentina.
- **Igualdad por identidad.** Dos objetos que representan el mismo país lo son aunque difieran sus datos, porque uno puede ser una versión más vieja del otro.
- **Sin setters.** Solo `create`, `rehydrate`, `rename`, `relocateTo` y `deactivate`.
- **`rehydrate` no emite.** Recuperar algo de la base no es un hecho del dominio; si emitiera, cada lectura publicaría un evento de creación.
- **Los cambios que no cambian nada no emiten.** Renombrar con el mismo nombre no registra evento: anunciar un cambio que no ocurrió obliga a cada consumidor a defenderse de duplicados.

## Dos decisiones de nombres

**`Deactivated` en lugar de `Deleted`.** Aquí no se borra nada, el registro permanece inactivo. Un consumidor que lea "deleted" concluye razonablemente que la fila ya no existe, y eso es falso. Ver [[patron-soft-delete]].

**Renombrar y trasladar son hechos distintos** (`city.renamed`, `city.relocated`), y no un único `CityUpdatedEvent`. Mover una ciudad de país cambia la cobertura geográfica de todas las sedes que hay en ella; renombrarla no afecta a nadie. Con un solo evento, quien se interesara solo por lo segundo tenía que comparar el payload contra el estado anterior para averiguar qué había cambiado.

## Lo que enseña

**Un módulo puede tener la forma correcta y la implementación equivocada.** La arquitectura de `location_hexagon` es la buena —es la que se está replicando— y aun así los agregados no protegían su estado ni sabían si estaban activos. El wiki lo había leído por su estructura, que es lo que salta a la vista, y no por su comportamiento.

De ahí que la etiqueta `reusable:alta` merezca un matiz que antes no tenía: **alta para el patrón, no necesariamente para el código**. Ver [[checklist-reutilizacion]].

## Notas relacionadas

[[dominio-ubicacion]] · [[aggregate-root-pattern]] · [[eventos-de-dominio]] · [[evolucion-arquitectonica-crud-a-cqrs]] · [[migracion-person-hallazgos]] · [[deuda-tecnica-y-riesgos]] · [[decisiones-tecnicas-malphasos]]
