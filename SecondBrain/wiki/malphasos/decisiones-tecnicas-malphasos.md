---
name: decisiones-tecnicas-malphasos
description: Registro cronológico de decisiones técnicas tomadas al construir MalphasOS, con su justificación y en qué se apartan del proyecto original
tags: [malphasos, decisiones, adr]
updated: 2026-08-27
---

# Decisiones técnicas de MalphasOS

Registro de decisiones tomadas al construir MalphasOS, en el espíritu de un ADR ligero. Se añade una entrada cada vez que se decide algo que condiciona el resto del proyecto. Complementa [[checklist-reutilizacion]]: el checklist dice *qué falta*, esto dice *qué ya se decidió y por qué*.

## Metodología de trabajo

**Micro-commits con revisión previa.** Un cambio lógico por commit, cada preocupación en su propia rama partiendo de `main` actualizado, merge con `--no-ff` para que el historial muestre de dónde vino cada cosa. El usuario revisa el diff antes de cada commit. Nada se commitea sin haber sido verificado de verdad (tests corriendo, servicios levantados).

## Stack y build

| Decisión | Elegido | Por qué |
|---|---|---|
| Spring Boot | **4.1.1** (original: 4.0.6) | Es lo que generó Spring Initializr; no había razón para bajar de versión. Trae diferencias reales, ver [[stack-spring-boot-4-particularidades]] |
| Java | **21** (LTS) | Coincide con el proyecto original. El `JAVA_HOME` de la máquina apunta a Java 26 vía mise, así que el build se corre con `JAVA_HOME=/usr/lib/jvm/java-21-openjdk` |
| groupId | `com.malphasos` | MalphasOS es producto propio, separado del namespace `com.bolivar.bioingenieria` del cliente |
| MapStruct | **1.6.3** (original: 1.5.5.Final) | Mejor soporte para Java 21+; el patrón de [[patron-mapper-mapstruct]] no depende de la versión |
| Jackson | **ninguna dependencia explícita** | Spring Boot 4 ya trae Jackson 3 de serie; declararlo a mano fue lo que generó la mezcla rara en el original |

## Base de datos

| Decisión | Elegido | Por qué |
|---|---|---|
| Esquema | **Flyway** (original: scripts `initdb`) | Los scripts de `/docker-entrypoint-initdb.d` solo corren al crear el volumen: cambiar el esquema obliga a borrar la base. Flyway versiona y es reproducible en cualquier entorno. Ver [[esquema-bd-v4]] para el modelo destino |
| Propiedad del esquema | **Flyway, exclusivamente** | `ddl-auto: validate` — Hibernate nunca modifica el esquema, solo verifica que las entidades coincidan con lo que crearon las migraciones |
| Primera migración | **baseline sin tablas** | `V1__baseline.sql` solo habilita `pgcrypto` y establece el punto de partida del versionado. Cada módulo de dominio traerá su propia migración, en vez de congelar de golpe decisiones de modelado que siguen abiertas (ver [[relacion-cliente-persona-ambiguedad]]) |
| PKs | **UUID desde el día uno** | El original tardó cuatro iteraciones en estandarizarlas, ver [[evolucion-esquema-v1-v4]] |
| `open-in-view` | **false** | Evita resolver lazy-loading durante el renderizado de la respuesta; obliga a decidir la carga de datos en la capa de aplicación |

## Testing

| Decisión | Elegido | Por qué |
|---|---|---|
| Base de datos en tests | **Testcontainers** | Levanta un PostgreSQL 17 real, el mismo motor que producción, así las migraciones de Flyway se validan de verdad. H2 habría obligado a escribir SQL al mínimo común denominador, renunciando a `jsonb`, extensiones y tipos propios de Postgres que el dominio sí necesita |
| Qué se testea | **comportamiento, no solo arranque** | Además de `contextLoads`, hay tests que verifican que Flyway efectivamente corrió y que el baseline habilitó la extensión |

## Infraestructura

| Decisión | Elegido | Por qué |
|---|---|---|
| Puerto del backend | **8081** | El 8080 lo ocupa Keycloak, que es su puerto convencional y el que referencian el frontend y la configuración del original |
| Keycloak hostname | **`localhost`** (original: `keycloak.test`) | No obliga a editar `/etc/hosts` |
| Import de realms | **`--import-realm`** | La variable `KEYCLOAK_IMPORT` del original es de la era WildFly y Keycloak 26 la ignora, ver [[docker-compose]] |
| Servicio de la app en compose | **no incluido todavía** | `malphasos/` aún no tiene `Dockerfile`; en desarrollo el backend corre desde el IDE contra los contenedores |
| Secretos | **`.env` ignorado, `.env.example` versionado** | El repo nunca contiene credenciales reales |
| Theme de Keycloak | **el de por defecto** | `sigma-theme` lleva branding de Bolívar Bioingeniería; MalphasOS tendrá el suyo cuando tenga identidad visual |

## Pendientes de decidir

- La relación entre "encargado" y "persona/usuario del sistema": ver [[relacion-cliente-persona-ambiguedad]]. **Debe resolverse antes de modelar el módulo de clientes.**
- Si el manejo de excepciones usa una base común entre módulos o se repite por bounded context: ver [[manejo-global-excepciones]].
- Organización del frontend por feature vs por tipo técnico: ver [[arquitectura-frontend]].

## Notas relacionadas

[[stack-spring-boot-4-particularidades]] · [[checklist-reutilizacion]] · [[alcance-malphasos]] · [[sintesis-malphasos]] · [[docker-compose]]
