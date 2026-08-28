---
name: issuer-uri-vs-jwk-set-uri
description: Por qué Keycloak en Docker devuelve 401 con tokens válidos, y cómo separar la URL pública del emisor de la interna de las claves
tags: [patron, seguridad, keycloak, docker, "reusable:alta"]
source: descubierto al activar la seguridad en MalphasOS
updated: 2026-08-28
---

# El 401 con tokens perfectamente válidos

Al contenerizar Keycloak aparece un problema que no existe cuando todo corre en la misma máquina: **el navegador y el backend llegan al servidor de identidad por direcciones distintas**.

- El navegador se autentica contra `http://localhost:8080` (el puerto publicado).
- El backend, dentro de la red de Docker, alcanza a Keycloak como `http://keycloak:8080`.

Keycloak escribe en cada token el claim `iss` con la URL por la que se emitió. Si el backend valida contra otra, **rechaza el token con 401 aunque la firma sea correcta y no haya expirado**. El síntoma engaña: parece un problema de credenciales cuando en realidad es de nombres.

## Cómo distinguirlo de una falta de permisos

Vale la pena tener presente la diferencia, porque orienta el diagnóstico:

- **401** — el token no se aceptó: emisor distinto, firma inválida o expirado.
- **403** — el token se validó correctamente, pero no lleva el permiso exigido.

Si al añadir un rol el 401 no cambia a 403, el problema no está en la autorización sino en la validación.

## Las dos soluciones habituales

**Un hostname resoluble desde ambos lados.** Es lo que hacía `bolivarbioingenieria-app` con `keycloak.test`, que exige añadirlo al `/etc/hosts` del desarrollador. Funciona y mantiene una sola URL, a costa de un paso manual en cada máquina.

**Separar las dos URL**, que es lo que hace MalphasOS:

```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri:  http://localhost:8080/realms/malphasos-realm            # publica: la del claim iss
          jwk-set-uri: http://keycloak:8080/realms/.../openid-connect/certs    # interna: de donde bajan las claves
```

Spring Boot, cuando encuentra `jwk-set-uri`, construye el decodificador con esas claves y **omite el descubrimiento del emisor**, pero sigue validando el claim `iss` contra `issuer-uri`. Las claves viajan por la red interna y el emisor se comprueba contra la URL pública, sin renunciar a nada.

Declarar solo `issuer-uri` obliga a que ambas coincidan, porque Spring lo usa a la vez para descubrir la configuración y para validar. De ahí salen la mayoría de estos 401.

## Un detalle que lo enmascara

Un realm exportado puede traer `attributes.frontendUrl` fijado. Ese valor **anula `KC_HOSTNAME`** y decide por su cuenta la URL pública del realm. El export de `bolivarbioingenieria-app` lo traía apuntando a `http://keycloak:8080`, que ningún navegador puede resolver. Conviene revisarlo siempre antes de dar por buena la configuración de hostname: se puede pasar horas ajustando variables de entorno que el realm está ignorando.

## Reutilizable en MalphasOS

`reusable:alta` — ya aplicado. La misma separación hará falta en cualquier entorno donde el emisor tenga una URL pública distinta de la interna, que es prácticamente todo despliegue real detrás de un proxy o un balanceador.

## Notas relacionadas

[[keycloak-configuracion]] · [[seguridad-keycloak-backend]] · [[docker-compose]] · [[decisiones-tecnicas-malphasos]]
