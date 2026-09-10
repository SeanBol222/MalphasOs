---
name: checklist-reutilizacion
description: Orden priorizado sugerido de qué portar primero al construir MalphasOS, basado en el análisis de todo el wiki
tags: [malphasos, checklist, planificacion, "describe:ambos"]
updated: 2026-09-09
---

# Checklist priorizado de reutilización

Orden sugerido para la construcción de MalphasOS. **La construcción ya arrancó** (2026-08-27); las decisiones tomadas se registran en [[decisiones-tecnicas-malphasos]].

> **Estado al 2026-09-08.** Los cuatro módulos de dominio —`person`, `location`, `client` y `equipment`— están completos de esquema a REST. Del alcance del backend queda **la segunda tanda de `equipment`**: verificaciones técnicas y datos metrológicos. Lo demás pendiente es frontend y opcionales.
>
> **Corrección del 2026-09-09: las dos ramas ya están en `main`, y lo que decía este recuadro es falso desde entonces.** `feat/permission-model` (`2de115f`) entró por `e6dda32` y `fix/person-identity-sync` (`5fde17c`) por `258cd81`; las dos se borraron tras el merge, como manda la convención. Se deja la tabla vieja tachada porque el aviso «no sumar, remedir» era correcto y su resultado es el dato útil:
>
> | Rama | En `main` | Pruebas | Qué trae |
> |---|---|---|---|
> | ~~`main` (`82a697b`)~~ | — | ~~339~~ | Los cuatro módulos |
> | ~~`feat/permission-model` (`2de115f`)~~ | **Sí, `e6dda32`** | ~~472~~ | El modelo de permisos |
> | ~~`fix/person-identity-sync` (`5fde17c`)~~ | **Sí, `258cd81`** | ~~361~~ | La sincronización con Keycloak |
> | **`main` (`01c3277`)** | — | **496** | Todo lo anterior, remedido |
>
> **Remedido, no sumado**, tal como se había anotado: `./mvnw test` el 2026-09-09 sobre `01c3277`, borrando `target/surefire-reports` antes — que `mvn test` **no** limpia—, da **496 ejecuciones**, 41 clases, cero fallos y cero errores. Las cifras viejas eran el atributo `tests=` de los XML; **496 es el número de elementos `<testcase>`, que es el conteo honesto** —el atributo da 494 y los `.txt` 380—. Ver [[stack-spring-boot-4-particularidades]].
>
> Fuera de `main` queda hoy **una** rama de código: `feat/relocation-same-client` (`3c002b2`), el traslado que no cruza de cliente. **También 496**, porque no añadió ninguna prueba — ver [[regla-traslado-mismo-cliente]].

## 1. Infraestructura base primero (sin esto no hay nada que construir encima)

- [x] **Hecho.** Clonar el patrón de `docker-compose.yaml` — Postgres + Keycloak compartiendo instancia + RabbitMQ. Corregido el import de realms roto del original, healthcheck en los tres servicios, pgAdmin bajo perfil `tools`. [[docker-compose]]
- [x] **Hecho (2026-08-28).** Realm `malphasos-realm` adaptado del export original e importándose solo al arrancar. Corregido el `admin.full` sin asignar que dejaba la API inutilizable. Seguridad activa y verificada extremo a extremo. [[keycloak-configuracion]], [[issuer-uri-vs-jwk-set-uri]]
- [x] **Hecho.** Estrategia de esquema decidida: **Flyway**, no scripts `initdb`. `V1__baseline.sql` establece el punto de partida con PKs UUID como convención. Cada módulo aportará su migración. [[esquema-bd-v4]], [[evolucion-esquema-v1-v4]], [[decisiones-tecnicas-malphasos]]
- [x] **Hecho.** Backend conectado a PostgreSQL, arrancando en el puerto 8081, con tests sobre Testcontainers en verde.

## 2. Esqueleto de aplicación

- [x] **Hecho.** Dependencias del backend en el `pom.xml`: MapStruct + binding con los annotation processors en orden, springdoc-openapi, keycloak-admin-client. Ojo con las particularidades del stack moderno: [[stack-spring-boot-4-particularidades]]
- [x] **Hecho (2026-08-29).** Portar `shared/domain/events` completo (`AggregateRoot`, `DomainEvent`, `EventMetadata`, `Payload`), sin el `eventTopic` que filtraba el transporte al dominio y sin `Serializable`. [[aggregate-root-pattern]], [[eventos-de-dominio]]
- [x] **Parcial (2026-08-29).** `EventDispatcherPort` + `SpringEventDispatcher` en marcha; **`RabbitMQDispatcher` no se porta todavía**, y cuando se porte hay que corregir antes el mismatch de routing key. Los cuatro módulos despachan hoy en proceso. [[patron-event-dispatcher-dual]], [[deuda-tecnica-y-riesgos]]
- [x] **Hecho.** Portar `SecurityConfig` + `KeycloakRoleConverter` + `KeycloakAdminConfig`, con el client id configurable y sin casts inseguros. **Seguridad ya activa**, con pruebas que verifican 401 sin token y 403 sin permiso. [[seguridad-keycloak-backend]]
- [x] **Hecho (2026-09-08; en `main` desde `e6dda32`).** Modelo de permisos: se heredaba del original que las 83 operaciones exigieran `admin.full` y que los roles del realm fueran decorativos. Ahora cada operación exige la autoridad de su recurso, `ApiAuthority` expande al administrador en un solo sitio, y el realm reparte 19 roles entre tres grupos que por fin se distinguen. 139 pruebas de seguridad, dos de ellas invariantes estructurales por reflexión. **Falta el filtrado por dueño**, dejado fuera a propósito. [[modelo-de-permisos]]
- [x] **Hecho.** Portar `OpenApiConfig` con grupos por módulo; fija la convención de rutas `/v1/api/<recurso>`. [[openapi-swagger]]
- [x] **Parcial.** Catálogo transversal migrado y corregido. Falta la interfaz/clase base común, que se definirá al migrar el primer módulo con excepciones propias. [[manejo-global-excepciones]], [[patron-catalogo-errores-por-contexto]]

## 2.b Primer módulo de dominio migrado: personas

- [x] **Hecho (cerrado el 2026-08-29).** `person_hexagon` migrado completo en seis commits, de adentro hacia afuera, más tres de corrección salidos de pruebas manuales. Se conservó el patrón de Generación 1 por decisión explícita. 22 defectos del original corregidos y dos errores propios documentados: [[migracion-person-hallazgos]]. Verificado extremo a extremo contra la aplicación contenerizada, con 68 pruebas en verde.

## 3. Primer módulo de dominio — usar como plantilla la Generación 2, no la 1

- [x] **Hecho (2026-08-29).** El primer hexágono fue `location`. `Country` y `City` fijan el patrón de Generación 2 que siguieron después `client` y `equipment`: agregado + factoría estática + eventos. [[dominio-ubicacion]], [[evolucion-arquitectonica-crud-a-cqrs]]
- [x] **Decidido y corregido (2026-08-29).** MapStruct **no sirve** para construir un agregado de Generación 2: construye por setters o builder, y un agregado no ofrece ninguno a propósito. Los mappers de persistencia van a mano; MapStruct queda para la dirección agregado→DTO. [[patron-mapper-mapstruct]]

## 4. Módulo de mantenimiento preventivo (núcleo de negocio)

- [x] **Hecho (2026-09-02), primera tanda.** `equipment` completo de esquema a REST en cinco pasos: `V5__equipment_catalog.sql`, seis agregados de Generación 2 (Manufacturer, Brand, EquipmentType, Equipment, Model, ClientEquipment), aplicación, persistencia y capa REST. Se reconstruyó, no se portó. Seis defectos del esquema original corregidos y `b_verificable` eliminado como campo. Ver [[migracion-equipment-hallazgos]].
- [ ] **Segunda tanda de `equipment`: `TechnicalVerification` y `MetrologicalData`.** Es lo único del alcance del backend que queda por construir. [[dominio-equipo-mantenimiento]]
- [x] **Resuelto de hecho (2026-09-02).** La pregunta era si completar el patrón CQRS con puertos read/write separados. Con los cuatro módulos cerrados, **ninguno los separa**: hay un `PersistencePort` por agregado, comprobado sobre el código. No fue una decisión explícita sino una convergencia, y ya no conviene revisarla sin un motivo concreto —lecturas que pesen, o un almacén de lectura aparte—. Lo que sí se adoptó del patrón son los commands inmutables por operación de escritura. [[patron-cqrs-commands]]

## 5. Módulo de clientes — reconstruir, no copiar

- [x] **Hecho (2026-08-29).** `PersonCommunicationPort`, el contrato que `person` publica hacia los demás módulos, con sus DTO en la capa de aplicación en vez de en `infrastructure`. Ver [[traduccion-de-fallos-de-adaptadores]] y [[migracion-person-hallazgos]].
- [x] **Hecho (2026-08-29).** `shared/domain/events`: el contrato de eventos de dominio, sin el `eventTopic` que filtraba el transporte al dominio y sin `Serializable`. Ver [[eventos-de-dominio]].
- [x] **Hecho (2026-08-29).** `location` completo: esquema, dominio, aplicación, persistencia y REST. `Country` y `City` son los primeros agregados de Generación 2. Ocho defectos del original corregidos, uno de ellos de seguridad. No se porta `CountryReportProviderAdapter`, que pertenece al módulo de reportes. Ver [[migracion-location-hallazgos]].
- [x] **Hecho (2026-09-02).** `client` completo: esquema, cuatro agregados de Generación 2, aplicación, persistencia y REST. Se reconstruyó en lugar de portarse. Ocho defectos del original corregidos y `representante_legal` implementada por primera vez. Ver [[migracion-client-hallazgos]].
- [x] **Hecho (2026-09-02).** `equipment`, el núcleo de negocio y el módulo más grande del original (173 archivos). Trajo consigo `equipo_cliente`, que había quedado fuera de `V4__client.sql`: la prueba centinela que fijaba su ausencia se rompió al aparecer la tabla, tal como se le pedía. Ver [[migracion-equipment-hallazgos]].
- [x] **Hecho (2026-09-02).** `representante_legal` implementada dentro del agregado `Client`, como conjunto de identificadores de persona.
- [x] **Decidido (2026-08-29).** La identidad compartida encargado↔persona se expresa con `@MapsId`, y el módulo se reconstruye en Generación 2. Ver [[decisiones-tecnicas-malphasos]] y [[relacion-manager-persona]].
- [x] **Hecho (2026-09-02).** La jerarquía Client→Headquarter→ServiceArea reimplementada como cuatro agregados pequeños que se referencian por identificador, no como un agregado que contenga a los demás. [[dominio-cliente]]

## 6. Identidad y frontend

- [x] **Hecho (2026-09-09; en `main` desde `258cd81`).** Sincronización de la persona con Keycloak al darla de baja y al editarla: `disableUser` y `updateUserProfile` en el puerto, llamados **antes** de persistir. Cierra la brecha de que dar de baja a alguien no le quitaba la entrada. **Queda abierto** el correo —no hay principal que sincronizar—, el cambio de grupo al cambiar de rol, y la ventana del token ya emitido. [[sincronizacion-con-proveedor-de-identidad]]

- [x] **Hecho.** Portar `PersonIdentityPort`/Adapter. `createSuperAdminUser` queda fuera del puerto por estar sin implementar en el original. [[dominio-persona-identidad]], [[migracion-person-hallazgos]]
- [ ] Portar `auth/keycloak.ts` + `AuthProvider` + `PrivateRoute` + `apiFetch` del frontend sin cambios estructurales. [[integracion-keycloak-frontend]]
- [ ] Decidir organización por feature (no por tipo técnico) desde el inicio del frontend de MalphasOS, dado que el original todavía no lo resolvió. [[arquitectura-frontend]]

## 7. Opcional / más adelante

- [ ] Completar y activar `event_persister_hexagon` como audit log si MalphasOS necesita trazabilidad de eventos. [[event-persister-outbox]]
- [ ] Portar el patrón `ReportDataProviderPort<T>` si se separa un módulo de reportes. [[patron-report-data-provider]], [[dominio-reportes]]

## Notas relacionadas

[[sintesis-malphasos]] · [[alcance-malphasos]] · [[deuda-tecnica-y-riesgos]] · [[modelo-de-permisos]] · [[sincronizacion-con-proveedor-de-identidad]]
