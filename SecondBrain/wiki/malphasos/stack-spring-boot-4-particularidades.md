---
name: stack-spring-boot-4-particularidades
description: Diferencias reales de Spring Boot 4 / Flyway 12 / Testcontainers 2 frente a lo que documenta el proyecto original — descubiertas al construir MalphasOS
tags: [malphasos, stack, backend, hallazgo]
source: malphasos/pom.xml (MalphasOS)
updated: 2026-08-27
---

# Particularidades de Spring Boot 4 y el stack moderno

Nota escrita **desde MalphasOS**, no desde `bolivarbioingenieria-app`. Documenta diferencias reales que costaron encontrar y que no se deducen de [[stack-tecnologico]], porque el proyecto original usa Spring Boot 4.0.6 y MalphasOS arrancó en 4.1.1 con librerías más nuevas.

## 1. Las autoconfiguraciones están modularizadas

En Spring Boot 4 cada tecnología tiene su propio módulo de autoconfiguración. Poner la librería suelta en el classpath **ya no basta**:

- `org.flywaydb:flyway-core` por sí solo **no activa nada**. Hay que usar `org.springframework.boot:spring-boot-starter-flyway`, que trae `spring-boot-flyway` (el módulo de autoconfiguración) además de `flyway-core`.
- Esto se nota también en los paquetes de las clases internas: `org.springframework.boot.jdbc.autoconfigure.DataSourceProperties`, `org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration`, `org.springframework.boot.hibernate.autoconfigure.HibernateJpaConfiguration`.

Síntoma cuando falta el starter: Flyway no imprime **ni un log**, no crea `flyway_schema_history`, y la aplicación arranca como si Flyway no existiera.

## 2. Los starters de test también son modulares

El proyecto generado por Spring Initializr no trae `spring-boot-starter-test` monolítico, sino uno por tecnología: `spring-boot-starter-webmvc-test`, `spring-boot-starter-data-jpa-test`, `spring-boot-starter-amqp-test`, `spring-boot-starter-flyway-test`, etc. Al agregar una tecnología nueva conviene agregar su starter de test correspondiente.

## 3. Flyway 12 separa el soporte de cada motor

`flyway-core` 12.x no sabe hablar con PostgreSQL por sí solo. Hace falta además:

```xml
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-database-postgresql</artifactId>
</dependency>
```

Síntoma cuando falta: la aplicación **falla al arrancar** con `Unsupported Database: PostgreSQL 17.10`. Es decir, hacen falta **las dos** dependencias: el starter (para que la autoconfiguración exista) y el módulo del dialecto (para que sepa hablar con el motor).

## 4. Testcontainers 2.x renombró sus artefactos

Spring Boot 4.1.1 fija `testcontainers.version = 2.0.5`. En la versión 2 los módulos llevan prefijo:

| Testcontainers 1.x | Testcontainers 2.x |
|---|---|
| `org.testcontainers:postgresql` | `org.testcontainers:testcontainers-postgresql` |
| `org.testcontainers:junit-jupiter` | `org.testcontainers:testcontainers-junit-jupiter` |

Síntoma con los nombres viejos: Maven ni siquiera lee el proyecto, falla con `'dependencies.dependency.version' ... is missing` (porque el BOM no gestiona esos artefactos, ya no existen con ese nombre).

Las clases Java **no** cambiaron de paquete: `org.testcontainers.containers.PostgreSQLContainer` y `org.testcontainers.utility.DockerImageName` siguen igual.

## 5. Jackson 3 ya viene por defecto

En los logs de arranque aparece `JacksonAutoConfiguration#jsonMapperBuilder` resolviendo `tools.jackson.databind.json.JsonMapper$Builder` — es decir, **Spring Boot 4 ya usa Jackson 3 de serie**. Esto explica retroactivamente la mezcla rara de Jackson 2 y 3 que [[stack-tecnologico]] marcaba como riesgo en el proyecto original: no era un experimento, era la transición del propio framework. En MalphasOS no se declaró ninguna dependencia de Jackson y funciona correctamente.

## Notas relacionadas

[[stack-tecnologico]] · [[decisiones-tecnicas-malphasos]] · [[checklist-reutilizacion]]
