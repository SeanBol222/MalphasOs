---
name: traduccion-de-fallos-de-adaptadores
description: Un adaptador de salida falla de dos maneras distintas, y traducir solo una deja escapar 500 genéricos fuera del contrato del API
tags: [patron, arquitectura-hexagonal, excepciones, "reusable:alta"]
source: descubierto al probar el registro de personas en MalphasOS
updated: 2026-08-29
---

# Un adaptador de salida falla de dos maneras

Todo adaptador que habla con un sistema externo —Keycloak, un servicio REST, una pasarela de pago— puede fallar de dos formas que no se parecen en nada:

1. **La llamada se completa y devuelve un código de error.** Hay respuesta: 409, 400, 404. El adaptador la interpreta y decide.
2. **La llamada no llega a completarse.** El cliente no consigue autenticarse, no resuelve el host, o la conexión expira. No hay respuesta que interpretar: el cliente **lanza**.

Es fácil escribir solo la primera y creer que está cubierto todo, porque es la que se ejercita al probar el camino feliz con la configuración correcta. La segunda aparece justo cuando algo del entorno está mal, que es el momento en que un mensaje claro más falta hace.

## El síntoma

Un `500` con el cuerpo de error por defecto del framework, **fuera del contrato de errores del API**. El cliente recibe una forma de respuesta que no está documentada en ningún sitio y que no dice nada del problema real.

En MalphasOS ocurrió exactamente así: `createUser` traducía los códigos del `Response` recibido, pero no capturaba nada. Cuando el secreto del client administrativo quedó mal configurado, el cliente de Keycloak lanzó antes de entregar respuesta y la excepción viajó hasta el servlet. `deleteUser`, en el mismo archivo, sí contemplaba el caso: la inconsistencia estaba dentro de una sola clase.

## La causa real viaja envuelta

El detalle que complica la traducción: **el motivo no está en la excepción que se captura, sino en alguna de sus causas**. Los clientes JAX-RS envuelven el fallo en un `ProcessingException`, y el `WebApplicationException` que lleva el código HTTP queda dentro. Mirar solo la excepción de primer nivel no distingue un 401 de un DNS caído.

De ahí que la traducción tenga que **recorrer la cadena de causas** hasta encontrar la que trae el código:

```java
private RuntimeException translateClientFailure(RuntimeException fallo, String operacion) {
    for (Throwable causa = fallo; causa != null; causa = causa.getCause()) {
        if (causa instanceof WebApplicationException web) {
            return switch (web.getResponse().getStatus()) {
                case 401, 403 -> new KeycloakUnauthorizedException(...);
                case 409      -> new KeycloakUserAlreadyExistsException(...);
                case 400      -> new KeycloakInvalidDataException(...);
                default       -> new KeycloakConnectionException(...);
            };
        }
    }
    // Ninguna causa trae código HTTP: no se llegó a hablar con el servidor.
    return new KeycloakConnectionException("No se pudo contactar con ... al " + operacion, fallo);
}
```

El caso terminal importa tanto como el bucle: si ninguna causa lleva código, es que la petición nunca salió, y eso es un fallo de conectividad, no de datos.

## De quién es la culpa determina el código de respuesta

Al traducir hay que responder una pregunta que se confunde con facilidad: **¿quién se equivocó, el que llama o el servicio?**

| Situación | Código | Por qué |
|---|---|---|
| El usuario ya existe | 409 | El llamante mandó datos en conflicto |
| El sistema externo rechaza los datos | 400 | El llamante mandó datos inválidos |
| **El servicio no logra autenticarse contra el externo** | **502** | El llamante no tiene nada que corregir: es el servidor el que está mal configurado |
| No se alcanza el sistema externo | 502 | Igual: dependencia caída |

El error habitual es devolver **401** en el tercer caso, que es lo que hacía `bolivarbioingenieria-app`. Un 401 le pide al llamante que se autentique, cuando quien no consiguió autenticarse fue el propio servicio contra su dependencia. Le manda a arreglar algo que no está en su mano.

## Cómo verificarlo sin romper el entorno

Estos caminos casi nunca se prueban, porque exigen que algo del entorno falle. Con un mock del cliente basta:

- Una prueba que lo haga lanzar un `ProcessingException` envolviendo un `WebApplicationException` con 401 → se espera la excepción de "no autorizado".
- Otra que lo haga lanzar un `ProcessingException` sin causa HTTP → se espera la de conexión.

Son dos pruebas baratas que cubren la mitad del comportamiento del adaptador que nadie mira.

## Reutilizable en MalphasOS

`reusable:alta` — aplica a cualquier adaptador de salida, y hay varios pendientes de migrar. Al portar `client` y `equipment` conviene revisar cada adaptador con esta pregunta: *¿qué pasa si el cliente lanza en vez de devolver?* Ver [[manejo-global-excepciones]] para dónde encaja la traducción dentro del contrato de errores.

## Notas relacionadas

[[manejo-global-excepciones]] · [[arquitectura-hexagonal]] · [[dominio-persona-identidad]] · [[migracion-person-hallazgos]] · [[patron-catalogo-errores-por-contexto]] · [[deuda-tecnica-y-riesgos]]
