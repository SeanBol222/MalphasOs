---
name: sincronizacion-con-proveedor-de-identidad
description: Dos sistemas de registro sin transaccion compartida - en que orden llamarlos, que se propaga, que no se puede propagar todavia y que ventana no cierra ninguna de las dos cosas
tags: [patron, identidad, keycloak, seguridad, "describe:malphasos"]
source: malphasos/src/main/java/com/malphasos/malphasos/person/ (MalphasOS)
estado: estable
updated: 2026-09-09
---

# Sincronizar una entidad con el proveedor de identidad

Una persona de MalphasOS vive en dos sitios a la vez: como fila en `persona` y como usuario en Keycloak. No hay transacción que abarque a los dos. Cada operación que toca a una persona tiene que decidir, por tanto, **en qué dirección puede quedar la inconsistencia** cuando algo falle a mitad.

Hasta el 2026-09-09 esa decisión no estaba tomada: `delete` solo tocaba la base y `update` tampoco propagaba nada. El resultado era que **dar de baja a alguien no le quitaba la entrada** —el dato decía "inactiva" y la identidad seguía diciendo "pase"— y que los dos registros divergían desde la primera edición.

## El acoplamiento que manda sobre todo lo demás: el id es el mismo

`register` hace `UUID.fromString(...)` sobre lo que devuelve `createUser`. **El identificador de la persona *es* el identificador del usuario de Keycloak**, y no existe tabla de correspondencia entre ambos.

De ahí sale la primera decisión, y su motivo real no es el que parece:

> Dar de baja **deshabilita** el usuario, no lo borra.

La razón tentadora —"por simetría con el borrado lógico del resto del sistema"— es la débil. La dura es que **borrar rompería esa correspondencia para siempre**: recrear la cuenta daría un UUID distinto del que la persona ya tiene almacenado, sin nada que los reconcilie. `deleteUser` se conserva en el puerto, pero solo para su caso original: deshacer un alta cuya persistencia falló, cuando el usuario acaba de crearse y no tiene historial que valga la pena conservar.

⚠️ El javadoc de `PersonService.delete` sigue justificando la elección **por la simetría**, mientras el de `PersonIdentityPort.disableUser` y el cuerpo del commit `9588c6d` dan el motivo correcto. Quien lea solo el servicio se lleva la razón floja.

## El orden falla cerrado: primero la identidad, después el dato

```
delete(id):  disableUser(id)  →  estadoActivo = false  →  save
update(id):  validar  →  updateUserProfile(id, perfil)  →  save
```

Las dos escrituras pueden fallar por separado, así que hay que elegir cuál de las dos inconsistencias posibles se prefiere:

| Orden | Si falla la segunda escritura | Veredicto |
|---|---|---|
| Keycloak → base | Persona **activa que no puede entrar** | Molesto, visible, y se repara repitiendo el `DELETE` |
| Base → Keycloak | Persona **de baja que sí puede entrar** | Es exactamente el defecto que se estaba corrigiendo |

**El coste aceptado**: la transacción de base de datos queda abierta durante una llamada de red a otro servicio. Se asume a sabiendas; la alternativa —confirmar primero y sincronizar después— es la que reintroduce el agujero.

Es una invariante que se rompe moviendo una línea, así que **está fijada con una prueba de orden** (`InOrder` de Mockito) y no con un comentario. Una prueba que solo comprobara "se llamó a Keycloak" pasaría igual con el orden invertido.

## Idempotencia y tolerancia al "no existe"

- **No se comprueba si la persona ya estaba inactiva** antes de llamar a Keycloak. Repetir la baja es barato, deshabilitar un usuario ya deshabilitado no cambia nada, y **repara la desincronización** si alguien reactivó la cuenta a mano desde la consola de administración.
- **Un usuario que no existe en Keycloak no impide la baja.** Quien se dio de alta con `save` —el camino que crea la persona sin cuenta— nunca tuvo usuario. El objetivo, que nadie entre con esa identidad, ya está cumplido. Cualquier **otro** fallo sí aborta la operación: no se puede dar por retirada a una persona cuyo acceso no se ha comprobado que quedó cerrado.
- Ese caso se distingue con una excepción propia, `KeycloakUserNotFoundException`, que **no tiene entrada en el catálogo de errores**: se atiende en la capa de aplicación y no llega nunca al cliente del API.

## Lo que no se propaga, y por qué no es pereza

`update` lleva a Keycloak **solo nombre y apellido**.

| Campo | Por qué no viaja |
|---|---|
| Nombre de usuario | Identifica la cuenta y es el sujeto de las sesiones abiertas; la petición de actualización ni siquiera lo recibe |
| Contraseña | Es otra operación, con otras garantías —conocer la anterior, o exigir cambio al siguiente inicio de sesión—; no debe cambiarse de rebote al editar un apellido |
| **Correo** | **No se puede saber cuál es.** Una persona tiene varios correos en su propio recurso y **ninguno está marcado como principal**: no hay forma de deducir cuál es el de la cuenta |

El del correo es el que importa: **no es una omisión, es un límite del modelo de datos**. Mientras `correo_persona` no distinga un correo principal, sincronizarlo obliga a elegir uno arbitrariamente. Por eso la deuda "`update` no propaga nada a Keycloak" queda **parcialmente abierta**, no cerrada.

Tampoco viaja el **tipo de persona**, y ese hueco es de otra familia: ver más abajo.

Que el conjunto propagado siga siendo el que es está fijado por una prueba que **recorre por reflexión los componentes del `record` `PersonIdentityProfile`**: si alguien le añade usuario, correo o contraseña, la prueba se rompe a propósito. Convierte "update no propaga la contraseña" en algo que se defiende solo en vez de en una promesa del javadoc.

## La ventana que esto no cierra, y que conviene no disimular

Verificado contra un **Keycloak 26.6.1 real**, no contra dobles:

- Deshabilitar impide autenticarse de nuevo y renovar el token: el intento responde `invalid_grant`.
- **Un token de acceso ya emitido sigue abriendo el API hasta que caduca.** El resource server lo valida solo con la firma, sin preguntarle al emisor si esa cuenta sigue viva. En el realm de desarrollo `accessTokenLifespan` son **300 segundos**.

**La brecha queda cerrada para las autenticaciones nuevas, no para las ya emitidas.** Cerrarla del todo exigiría introspección del token en cada petición (o una lista de revocación consultada por el resource server), que es un coste por petición que hoy no se paga. Queda dicho, no resuelto.

## Un agujero de la misma familia, cerrado el 2026-10-04

**Este apartado se llamaba «un agujero de la misma familia que sigue abierto».** Decía que cambiar `tipoPersona` no mueve al usuario de grupo, que la pertenencia se fija una sola vez al crear el usuario, y que eso pertenecía «a la línea de trabajo del [[modelo-de-permisos]], no a la de sincronización de datos». **Cerrado**, con `syncGroup`.

Y la frase que conviene corregir no es la del hecho, es la de la clasificación: **sí era sincronización de datos**, y de la peor clase. El alcance de lectura se decide por el tipo de la persona —ver [[filtrado-por-dueno]]—, de modo que la fila y la identidad podían decir cosas distintas y **el sistema creía las dos a la vez**: el filtro miraba el tipo y las autoridades venían del grupo. Clasificarlo como «cosa de permisos, no de sincronización» es lo que lo dejó un mes abierto en la nota que precisamente trata de los dos sistemas que no comparten transacción.

**Lo que `syncGroup` decide, y no es evidente.** `PersonType` tiene cinco valores y los grupos del realm son tres, así que el mapeo no es total. Un encargado no queda en **ningún** grupo, porque no accede al sistema: su cuenta autentica y toda llamada suya responde 403. Y un `SUPER_ADMIN` queda en el grupo de administradores, que es lo más que un grupo puede dar —ninguno concede `super.admin.full`, a propósito—, porque dejarlo sin grupo dejaría sin acceso a quien se acaba de promover. El `switch` es una expresión, de modo que un sexto tipo no compilará sin decidir con qué entra.

**Y es idempotente, recorriendo los tres grupos en vez de preguntar por el actual.** Cuesta lo mismo y además **corrige** una cuenta que acabó en dos grupos a mano, en lugar de dejarla así. Los grupos que este sistema no administra no se tocan.

Por el mismo criterio **no se añadió `enableUser`**: hoy no existe ningún camino de reactivación que lo llamaría, y un puerto con operaciones que nadie invoca es código muerto.

## Un efecto secundario que nadie había señalado

Añadir `case 404` a `translateClientFailure` cambió la conducta de `deleteUser`, que ya existía. `deleteUser` tiene **dos caminos distintos para un 404**:

1. Keycloak responde y el adaptador comprueba `response.getStatus() >= 400` → `KeycloakConnectionException`. **Sin cambios.**
2. El cliente **lanza** un `WebApplicationException` con 404 → pasa por `translateClientFailure` → antes daba `KeycloakConnectionException`, ahora da `KeycloakUserNotFoundException`.

Es inocuo: su único llamante, `rollbackUser`, captura `RuntimeException` y lo único que cambia es el texto del log. Pero **era un cambio real de conducta en una operación que ya existía**, y el criterio que vale la pena retener es el del hallazgo: *un `switch` de traducción compartido tiene tantos llamantes como métodos lo usen, y añadirle un caso los modifica a todos*. Los dos caminos quedaron fijados con pruebas.

## Cómo generalizarlo

Cualquier adaptador de salida hacia un sistema que también guarda estado —una pasarela de pagos, un CRM, un directorio— hereda las mismas cuatro preguntas:

1. ¿Qué identificador manda, y qué operación lo destruiría?
2. Si solo una de las dos escrituras se completa, ¿cuál de las dos inconsistencias es la tolerable? Ordenar en consecuencia, y **fijar el orden con una prueba**.
3. ¿Qué respuestas del sistema externo son "no pasa nada" y cuáles abortan?
4. ¿Qué queda sin cerrar aun haciéndolo bien, por cómo funciona el sistema externo? Escribirlo.

## Notas relacionadas

[[dominio-persona-identidad]] · [[seguridad-keycloak-backend]] · [[traduccion-de-fallos-de-adaptadores]] · [[modelo-de-permisos]] · [[keycloak-configuracion]] · [[deuda-tecnica-y-riesgos]] · [[decisiones-tecnicas-malphasos]]
