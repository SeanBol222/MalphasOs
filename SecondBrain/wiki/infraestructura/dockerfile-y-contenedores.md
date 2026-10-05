---
name: dockerfile-y-contenedores
description: Cómo se contenedorizan el backend y, desde el 2026-10-04, el frontend — build en dos etapas, usuario sin privilegios, healthcheck real vía Actuator y nginx sin proxy
tags: [infraestructura, docker, "reusable:media", "describe:ambos"]
source: Backend/sigma-bb/Dockerfile (original), malphasos/Dockerfile y malphasos-frontend/Dockerfile (MalphasOS)
updated: 2026-10-04
---

# Dockerfile y contenedores

## El Dockerfile del proyecto original

`Backend/sigma-bb/Dockerfile` es un build multietapa correcto en lo esencial (compila con Maven en una etapa, copia el jar a otra), pero arrastra cuatro problemas que no conviene heredar:

1. **Ejecuta como root.** No declara ningún `USER`, así que el proceso Java corre con todos los privilegios dentro del contenedor.
2. **Usa el JDK completo en runtime.** La etapa final parte de `eclipse-temurin:21`, no de una imagen JRE. Ejecutar no requiere compilador: es peso e instrumental de más, incluida la superficie de ataque que aporta.
3. **No hay `.dockerignore`.** Todo el directorio entra al contexto de build: `target/`, `.git/` y cualquier `.env` que exista. Es un riesgo real de acabar con secretos dentro de una imagen.
4. **Usa `CMD` en vez de `ENTRYPOINT`.** Con `CMD`, cualquier argumento pasado a `docker run` reemplaza el comando entero en lugar de llegar a la aplicación.

Tampoco tiene healthcheck, aunque eso es más una carencia del compose que del Dockerfile.

## Lo que hace MalphasOS

Mismo esqueleto de dos etapas, con las cuatro correcciones aplicadas: JRE sobre Alpine en runtime, usuario `malphasos` sin privilegios, `.dockerignore` explícito y `ENTRYPOINT`. Las dependencias se resuelven en una capa separada del código fuente (`COPY pom.xml` + `dependency:go-offline` antes de `COPY src`), de modo que Docker no vuelve a descargar el árbol de Maven cada vez que cambia una línea de código.

La imagen final ronda los **440 MB**. Es bastante menos que arrastrando el JDK, pero no es minúscula: el grueso son el JRE y las dependencias de Spring. Reducirla más pasaría por `jlink` con un runtime recortado o por *layered jars* de Spring Boot; ninguna de las dos se aplicó todavía.

## Healthcheck: por qué hace falta Actuator

Un healthcheck que solo comprueba que el puerto acepta conexiones no distingue una aplicación sana de una que arrancó pero perdió la base de datos. Por eso MalphasOS incorpora Spring Boot Actuator y el contenedor consulta `/actuator/health`.

Se expone **únicamente** `health`: el resto de endpoints de Actuator revelan beans, configuración y variables de entorno. Con `show-details: when-authorized`, un cliente anónimo ve solo `{"status":"UP"}` y el desglose por componente exige autenticación. La ruta se añade a las públicas de [[seguridad-keycloak-backend]] para que el healthcheck siga funcionando cuando la seguridad esté activa.

⚠️ **Trampa**: el health indicator de RabbitMQ que trae Actuator intenta conectarse al broker, y por defecto apunta a `localhost:5672`. Dentro del contenedor eso no existe, así que el healthcheck reporta DOWN con la aplicación perfectamente sana. Hay que configurar `spring.rabbitmq.host` con el nombre del servicio de Docker. Lo mismo aplica a cualquier otro health indicator que Actuator active automáticamente al detectar una dependencia en el classpath.

## El frontend, en su propio contenedor (2026-10-04)

`malphasos-frontend/Dockerfile`, servicio `frontend` del compose. **Las mismas dos decisiones que el
backend**: dos etapas —Node compila, y la imagen final solo lleva nginx con los ficheros estáticos, sin
Node: **76 MB**— y **sin privilegios de root** (`nginx-unprivileged`, escuchando en el 8080 del
contenedor).

**Se publica en el 5173, a propósito**: es el origen que el realm de Keycloak tiene como URI de
redirección y el que el backend admite en CORS, de modo que **no hubo que tocar ni el realm ni el
backend**. La contrapartida es que no puede correr a la vez que `ng serve`, que usa el mismo puerto.

**nginx no hace de proxy.** El navegador llama al API y a Keycloak en las mismas URL que en desarrollo,
y el contenedor no necesita conocer a ninguno de los dos. Tampoco necesita `../contracts`, porque el
cliente del API generado desde OpenAPI está versionado dentro del frontend.

Tres detalles **comprobados contra el contenedor en marcha**, no solo escritos en `nginx.conf`:

- una ruta profunda como `/equipos/:id/hoja-de-vida` responde con la aplicación y no con un 404 al
  recargar —el `try_files` de toda SPA—;
- los trozos con hash se cachean un año, porque cambian de nombre si cambian de contenido;
- e `index.html` va **sin caché**, porque es el que apunta a los trozos nuevos: cachearlo dejaría un
  despliegue viejo sirviéndose.

**Y la lección que dejó el mismo día, al otro lado del compose**: el contenedor del backend estaba en
`V13` horas después de mergear `V14`, porque nadie lo había reconstruido. Con todo en contenedores, **lo
que hay en `main` y lo que corre son dos cosas**, y se igualan con `docker compose up -d --build`, no
con un merge.

## Reutilizable en MalphasOS

`reusable:media` — el esqueleto de dos etapas del original sirve como punto de partida, pero las cuatro correcciones de arriba deben aplicarse siempre. Ya están aplicadas; esta nota existe para que no se reintroduzcan al copiar del original.

## Notas relacionadas

[[docker-compose]] · [[seguridad-keycloak-backend]] · [[decisiones-tecnicas-malphasos]] · [[deuda-tecnica-y-riesgos]]
