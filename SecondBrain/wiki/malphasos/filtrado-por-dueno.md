---
name: filtrado-por-dueno
description: Como MalphasOS acota lo que un representante legal puede leer, el alcance como argumento del caso de uso, y el patron de delegar la comprobacion a quien es dueno del recurso
tags: [malphasos, seguridad, permisos, arquitectura, "describe:malphasos"]
source: malphasos/src/main/java/com/malphasos/malphasos/shared/application/model/ReadScope.java
estado: estable
updated: 2026-10-04
---

# El filtrado por dueño

**Construido el 2026-10-04, en cuatro tandas, y era la mayor deuda abierta del proyecto.** Hasta ese
día un representante legal con `client.read` leía **todos** los clientes del sistema, y con
`equipment.read`, `work-order.read` y `report.read` leía los equipos, las órdenes y los reportes de
todos. El modelo de permisos decía *qué* se puede hacer y nada decía *sobre qué filas*.

## Lo primero: la pregunta que se daba por abierta ya estaba contestada

Tres notas de este wiki repetían que implementarlo «**exige decidir cómo se ata una cuenta de Keycloak
a un cliente del dominio**» —[[modelo-de-permisos]], [[decisiones-tecnicas-malphasos]] y
[[deuda-tecnica-y-riesgos]]—. Esa decisión estaba tomada desde la migración de `person`, escrita en el
javadoc de `PersonService.register`:

> El identificador de la persona es el que asigna el proveedor de identidad, de modo que ambos
> sistemas comparten la misma clave y **no hace falta una tabla de correspondencia**.

El `sub` del token **es** `persona.k_identificador`. De ahí a los clientes hay una tabla que existe
desde `V4`, `representante_legal`, cuya llave primaria es compuesta a propósito: una persona puede
representar a **varios** clientes. **No hizo falta ninguna migración.**

La lección no es sobre este filtro: una nota que dice «esto exige decidir X» hay que releerla cuando X
se haya decidido en otra parte, porque el bloqueo sobrevive a su causa.

## A quién se le acota, y por qué solo a ellos

El grupo `clients` del realm tiene **cinco autoridades y las cinco son de lectura** —`client.read`,
`equipment.read`, `report.read`, `service-area.read`, `work-order.read`—. De modo que esto es un
problema de lectura y no de escritura, y toda escritura pasa el alcance libre con el motivo escrito al
lado. Y `clients` lo forman **solo los representantes legales**: `groupFor` manda `CEO_CLIENT` ahí, el
encargado no tiene cuenta y los ingenieros y administradores van a sus propios grupos.

## El diseño: el alcance es un argumento, no un dato ambiental

`ReadScope` vive en `shared/application/model` y viaja como **parámetro** de cada consulta.

```java
List<Client> findAll(ReadScope alcance);
Client findById(UUID id, ReadScope alcance);
```

La alternativa era leer `SecurityContextHolder` en el adaptador de persistencia. Filtraba igual, no
cambiaba ninguna firma y **dejaba el filtro invisible desde fuera**: una consulta nueva lo habría
olvidado sin que nada avisara. Con el alcance en la firma, el compilador obligó a **trece** llamantes
a declarar el suyo, y de los trece, cuatro eran lecturas que no acotaban y quedaron marcadas en el
código hasta cerrarse.

Sigue en pie lo que la arquitectura exige: **ninguna clase de `application` ni de `domain` toca
`Authentication`**. Quien la toca es `ReadScopeResolver`, en `client/infrastructure/input/security/`.

### `ReadScope` existe para hacer imposible una confusión concreta

«Ve todo» y «no ve nada» se parecen demasiado en un `Set`: el conjunto vacío leído como «sin filtro»
abre el sistema entero, y el «sin filtro» leído como conjunto vacío lo cierra. Son ramas distintas, y
**`visibleClients()` lanza** si el alcance es libre, así que nadie puede construir un `WHERE IN` con la
lista de nadie y creer que ha filtrado algo. Un alcance restringido al conjunto vacío **sí** es
legítimo: quien no representa a ningún cliente no ve ninguno.

### Dónde vive el resolutor, y por qué ahí

En `client`, por la misma razón que `PersonWriteGuard` vive en `person`: necesita los puertos de
clientes y de personas, y `bootstrap` no importa ningún módulo de negocio. Y en **`client`** y no en los
otros tres que lo usan porque el grafo de dependencias ya iba en ese sentido —`report` → `equipment` y
`workorder` → `client` → `person`—, de modo que **no hubo que invertir nada ni duplicar el resolutor**.

## El patrón que apareció solo: delegar en el dueño del recurso

De las 18 lecturas acotadas, **ocho no comprueban nada por su cuenta**. Cuando una lectura filtra por un
recurso ajeno, pasarle el alcance a quien es dueño de ese recurso es *toda* la comprobación: la de
existencia y la de pertenencia se vuelven la misma llamada.

```java
// El inventario de un area, en equipment. No hay ninguna guarda propia.
serviceAreaServicePort.findById(idAreaServicio, alcance);   // lanza si es de otro cliente
return clientEquipmentPersistencePort.findByServiceArea(idAreaServicio);
```

**Y su riesgo, que es el mismo las ocho veces y es invisible en una revisión de código**: pasar
`ReadScope.unrestricted()` en lugar del alcance de quien llama **compila igual y no filtra nada**. Por
eso cada una de las ocho lleva una verificación de que el alcance viaja, y las mutaciones que lo
sustituyen caen.

**El único sitio donde delegar sería incorrecto** es `findById` de un reporte. Un reporte guarda su
orden, no su cliente; pedir la orden con el alcance habría sido lo breve, y el error que sale es «esa
orden no existe» cuando lo que se pidió fue **un reporte**: la respuesta contaría de qué es el
identificador que no se puede ver. Se resuelve el dueño con alcance libre y se lanza el error del
recurso pedido. Su prueba comprueba el **tipo** de la excepción, no solo que falle.

## Lo que cuesta saber de quién es cada cosa

| Recurso | Dónde está su dueño | Coste |
|---|---|---|
| Cliente | es él mismo | ninguno |
| Orden de trabajo | la fila guarda `k_id_cliente` | **ninguno** |
| Sede | la fila guarda `k_id_cliente` | leer la fila, que ya se lee |
| Área de servicio | área → sede → cliente | una consulta más |
| Equipo instalado | equipo → área → sede → cliente | una llamada a `findOwningClient` |
| Reporte | reporte → orden → cliente | una consulta más |

**Ese coste solo se paga cuando el alcance restringe.** A la gente de la casa no se le cobra el filtro
que no se le aplica, y hay una prueba por caso que falla si el atajo desaparece — sin ellas, cada
lectura de un área o de un reporte pasaría a costar una consulta extra para todo el mundo.

## Las cuatro decisiones que esto tomó, y que se pueden discutir

1. **Fuera del alcance se responde 404, no 403.** Un 403 confirma que ese identificador existe. Con
   UUID ajenos eso es poco, pero es información que no hace falta dar, y reutiliza el código de «no
   existe» de cada módulo sin inventar catálogo de errores nuevo.
2. **Una cuenta de Keycloak sin fila en `persona` ve todo.** El SuperUsuario se crea a mano y por
   definición no la tiene: cerrarle la vista rompería el único arranque que el sistema documenta. El
   riesgo residual —una cuenta hecha a mano metida en el grupo `clients`— no amplía nada, porque quien
   puede crear usuarios en Keycloak puede ponerse en `admins` igual. Queda como deuda.
3. **El alcance mira el nombramiento, no el estado del dato.** Retirar a un representante le quita el
   acceso en el acto; un cliente retirado, o un área cerrada, **siguen** en el alcance de su
   representante, porque los listados devuelven lo retirado a todo el mundo y esconderlo solo a su
   dueño le daría una vista distinta de la del ingeniero que lo cerró.
4. **El catálogo compartido no se acota**, y no es un olvido: marcas, tipos de equipo, fabricantes,
   modelos, magnitudes y unidades son el catálogo de la empresa y **no tienen dueño**. Filtrar donde no
   hay dueño no significa nada, y esconderlo dejaría la ficha del equipo de un cliente sin marca ni
   modelo que mostrar. Son 8 de las 11 lecturas de [[dominio-equipo-mantenimiento]].

## Lo que sigue sin acotar, a propósito

- **Los ingenieros lo ven todo.** Acotarlos a sus órdenes asignadas es una decisión de producto, ningún
  requisito la pide y afectaría a pantallas que hoy funcionan. Anotado y no construido.
- **Las escrituras no se acotan** porque ningún grupo que pueda escribir es de un cliente. Si algún día
  un representante pudiera escribir algo, esto deja de ser cierto y hay que volver aquí.
- **`findAll()` de sedes y de áreas** siguen en sus puertos de entrada **sin acotar y sin llamante**:
  ninguna ruta los expone y nadie los invoca. Exponerlos sin acotarlos sería un agujero, así que
  conviene borrarlos o acotarlos antes de que alguien los use.

## Cómo se verificó

**22 mutaciones sobre las 18 lecturas, y dos supervivientes al primer intento**, los dos en `client` y
los dos por el mismo motivo, que da una regla reutilizable:

> **Para probar que una guarda es la que rechaza, hay que estubar el camino que rechazaría si la guarda
> no estuviera.** Una prueba pedía las sedes de un cliente ajeno sin estubar su existencia: al
> desactivar el filtro caía en la comprobación de existencia, Mockito devolvía `Optional.empty()` para
> un mock sin estubar, y **rechazaba igual, por el camino equivocado**. Y en modo estricto ese estubado
> va con `lenient()`, que es exactamente la declaración «esto está aquí para que no sea esto lo que
> falle».

Las pruebas de cada regla van en **una clase por módulo** —`OwnershipFilteringTest` y sus tres
hermanas— y no repartidas entre las pruebas de servicio: no son dieciocho detalles, son una regla, y
repartida nadie habría notado que falta un caso. Las pruebas de servicio existentes pasan el alcance
libre, que es cobertura **alrededor** del filtro y no sobre él.

## Notas relacionadas

[[modelo-de-permisos]] · [[deuda-tecnica-y-riesgos]] · [[decisiones-tecnicas-malphasos]] · [[hoja-de-ruta-producto]] · [[seguridad-keycloak-backend]] · [[esquema-bd-malphasos]] · [[dominio-equipo-mantenimiento]] · [[regla-traslado-mismo-cliente]]
