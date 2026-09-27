# Índice — SecondBrain de MalphasOS

Catálogo del wiki, **ordenado por la pregunta con la que se llega**. Ver [[CLAUDE.md]] para las convenciones.

Reorganizado el 2026-09-09: hasta entonces estaba ordenado por las categorías técnicas de `bolivarbioingenieria-app` y clasificado por la etiqueta `reusable:*`, porque el wiki servía para decidir **qué portar**. Esa migración terminó. Hoy la pregunta es **cómo funciona MalphasOS y qué falta por construir**, y el índice sigue ese orden. Ninguna nota se borró; las que describen solo el sistema original están al final, que es el lugar que les corresponde ahora.

> **Los tres atajos**: [[hoja-de-ruta-producto]] para qué sigue · [[decisiones-tecnicas-malphasos]] para por qué el código es así · [[deuda-tecnica-y-riesgos]] para qué está roto o a medias.

---

## 1. Qué falta por construir

- [[hoja-de-ruta-producto]] ⭐ — **Empieza aquí si vas a construir algo nuevo.** Lo que queda del producto, backend y frontend, ordenado por dependencias reales y no por numeración de requisitos.
- [[deuda-tecnica-y-riesgos]] ⭐ — Todo lo detectado como defecto, en dos secciones que no se mezclan: lo heredado del original y lo que hemos introducido nosotros. Consultar antes de tocar cualquier pieza.
- [[checklist-reutilizacion]] — **Registro cerrado** de la migración desde `bolivarbioingenieria-app`, con el progreso real marcado. No es una lista de tareas pendientes.
- [[alcance-malphasos]] — Qué entró y qué se quedó fuera, módulo por módulo. Escrita antes de empezar.

## 2. Cómo funciona MalphasOS hoy

### Antes de decidir nada

- [[decisiones-tecnicas-malphasos]] ⭐ — Toda decisión ya tomada, con su justificación y su coste. Consultar antes de re-decidir algo.
- [[stack-spring-boot-4-particularidades]] — Spring Boot 4, Flyway 12, Testcontainers 2 y las trampas de conteo de Surefire. Nada de esto se deduce del proyecto original.

### Arquitectura y convenciones transversales

- [[arquitectura-hexagonal]] — Ports & adapters, flujo real de una petición capa por capa.
- [[evolucion-arquitectonica-crud-a-cqrs]] ⭐ — Generación 1 (CRUD anémico) vs Generación 2 (agregados + eventos). La lectura que explica por qué el código está escrito así.
- [[aggregate-root-pattern]] — La clase base que acumula eventos de dominio.
- [[eventos-de-dominio]] — Contrato `DomainEvent` y despacho. El transporte por RabbitMQ sigue sin portarse.
- [[patron-cqrs-commands]] — Commands inmutables por operación de escritura. Los puertos read/write **no** se separaron en ningún módulo.
- [[patron-mapper-mapstruct]] — Y por qué MapStruct **no** sirve para construir un agregado de Generación 2.
- [[manejo-global-excepciones]] — Catálogo + advice + DTO, repetido por módulo a propósito.
- [[patron-catalogo-errores-por-contexto]] — El mismo patrón visto como pieza atómica.
- [[traduccion-de-fallos-de-adaptadores]] — Un adaptador de salida falla de dos maneras; traducir solo una deja escapar 500 fuera del contrato.
- [[openapi-swagger]] — Un grupo por módulo, y el fallo silencioso de un `pathsToMatch` que no casa con ninguna ruta.
- [[antipatron-open-in-view]] — Por qué está apagado y qué hacer en su lugar.

### Los módulos de dominio

- [[dominio-cliente]] — Client / Headquarter / ServiceArea / Manager. Reconstruido en cuatro agregados pequeños.
- [[dominio-persona-identidad]] — Person + Keycloak Admin API. El único módulo que se quedó en Generación 1, por decisión explícita.
- [[dominio-ubicacion]] — Country / City. El primer módulo de Generación 2 y la plantilla de los siguientes.
- [[dominio-equipo-mantenimiento]] ⭐ — El núcleo del negocio. Catálogo e inventario construidos; **faltan verificaciones técnicas y datos metrológicos**. Desde el 2026-09-26 incluye **la cadena del catálogo vista desde el frontend**: por qué crear un equipo exige cinco piezas y por qué «equipo» significa dos cosas.
- [[regla-traslado-mismo-cliente]] — Una unidad solo se traslada a áreas de su propio cliente. Construida el 2026-09-09 y **verificada el 2026-09-10** con 13 pruebas; esta línea la marcaba con ⚠️ como «sin verificar» hasta el **2026-09-12**. Leer antes de tocar `ClientEquipmentService`: recoge además por qué una prueba pasaba en vacío sin que Mockito estricto lo delatara.
- [[relacion-manager-persona]] — Un encargado ES una persona por clave primaria compartida.
- [[dominio-orden-trabajo]] — **El quinto módulo, completo en sus cuatro tandas** entre el 2026-09-12 y el 2026-09-13; el REST, en rama sin mergear. El núcleo del negocio de mantenimiento, del que cuelgan reportes, firma e historial. Trae el estado **verificado** de las siete reglas que el esquema dejó al servicio —**seis hechas y una no**— y el porqué de que un módulo terminado cierre solo tres de sus siete requisitos.

### Datos y esquema

- [[esquema-bd-v4]] — Las 27 tablas del original y las convenciones que MalphasOS heredó: prefijos por tipo, PK UUID, borrado lógico.
- [[patron-soft-delete]] — `b_estado_activo` universal: aquí nada se borra.
- [[reglas-de-negocio-en-el-esquema]] — Los seis sitios donde un esquema SQL esconde reglas de negocio. Revisar antes de dar por migrado un módulo.
- [[congelar-una-referencia-historica]] — Cuando una columna guarda *dónde estaba* algo, la clave foránea compuesta que parece faltar bloquearía el cambio legítimo. Leer antes de «arreglar» `orden_trabajo_equipo`.

### Seguridad e identidad

- [[seguridad-keycloak-backend]] — Resource server + admin client, dos piezas separadas. Incluye la ventana del token ya emitido.
- [[modelo-de-permisos]] ⭐ — Las **20** autoridades, la expansión en **dos escalones**, la **escalera de usuarios** —quién crea a quién— y la única excepción acotada a la autoridad literal. (Decía «19 autoridades» y «en dos capas»: cierto hasta el **2026-09-13**. Y antes «en rama sin mergear»: **falso desde el 2026-09-09**, está en `main` por `e6dda32`.)
- [[sincronizacion-con-proveedor-de-identidad]] — Dos sistemas de registro sin transacción compartida: en qué orden llamarlos y qué queda sin cerrar. (Decía «en rama sin mergear»: **falso desde el 2026-09-09**, está en `main` por `258cd81`.)
- [[issuer-uri-vs-jwk-set-uri]] — Por qué Keycloak en Docker devuelve 401 con tokens válidos.
- [[keycloak-configuracion]] — El realm, sus clients y sus grupos.

### Infraestructura local

- [[docker-compose]] — Postgres, Keycloak, RabbitMQ, pgAdmin y el backend.
- [[dockerfile-y-contenedores]] — Build en dos etapas, usuario sin privilegios, healthcheck real.

## 3. Cómo se llegó hasta aquí

Los hallazgos de cada migración. Se leen por lo que enseñan sobre **cómo apareció** cada defecto, que es lo más reutilizable que ha producido este proyecto.

- [[migracion-person-hallazgos]] ⭐ — Los 22 defectos que destapó el primer módulo, y qué los encontró.
- [[migracion-location-hallazgos]] — Lo que apareció en el módulo que este wiki daba por ejemplar: igualdad rota, setters públicos y un evento que mentía.
- [[migracion-client-hallazgos]] — El módulo más grande, reconstruido y no portado: fronteras de agregados y la tabla que no tenía código.
- [[migracion-equipment-hallazgos]] ⭐ — El núcleo de negocio en cinco pasos, con la prueba centinela que se rompió a propósito.
- [[sintesis-malphasos]] — La tesis de qué portar, **cerrada** el 2026-09-09 con el registro de en qué acertó y en qué no.

## 4. Lo que todavía no existe en MalphasOS

Notas que describen piezas del sistema original **no construidas aquí**. Son el punto de partida cuando les llegue el turno; ver [[hoja-de-ruta-producto]] para cuándo.

- [[arquitectura-frontend]] — React 19 + TS + Vite **del original**. Su conclusión de reutilización caducó el 2026-09-13 al elegirse Angular.
- [[arquitectura-frontend-malphasos]] ⭐ — **Cómo se escribe frontend aquí**: Angular, por módulo de negocio con los nombres del backend, cliente generado desde OpenAPI. Y por qué cada pieza es el espejo de una del backend. Desde el 2026-09-26, también: **un servicio por agregado**, claves de caché jerárquicas, y por qué `whenStable()` no sirve en zoneless con una petición en vuelo.
- [[sistema-de-diseno-malphasos]] ⭐ — El manual de marca traducido a interfaz: tokens, contrastes **medidos**, radio cero, escala de 8, y los estados que se distinguen por peso y no por color. Desde el 2026-09-26, el componente que el manual no nombra: **el campo que predice** sobre un catálogo de 1.350 filas.
- [[integracion-keycloak-frontend]] — `keycloak-js` + `AuthProvider` + `PrivateRoute` + `apiFetch`: un starter de autenticación completo y portable. Y, desde el 2026-09-26, **lo que costó el primer arranque real contra `keycloak-angular`**: dos providers que la librería no declara y una página en blanco sin mensaje.
- [[dominio-reportes]] — El agregador cross-dominio del original. En MalphasOS el grupo de OpenAPI existe y el módulo no.
- [[patron-report-data-provider]] — El puerto genérico que ese módulo usaba.
- [[event-persister-outbox]] — Auditoría de eventos. En el original está construida y desconectada.

## 5. De dónde viene todo esto: el sistema original

Valor histórico. Explican por qué una decisión es como es, no qué hace MalphasOS.

- [[sistema-bolivarbioingenieria]] — Qué es `bolivarbioingenieria-app`, sus módulos y cómo encajan.
- [[stack-tecnologico]] — Su stack completo con versiones exactas. El de MalphasOS es otro: ver [[stack-spring-boot-4-particularidades]].
- [[evolucion-esquema-v1-v4]] — Cuatro versiones del esquema que endurecen un mismo modelo, no un rediseño.
- [[patron-event-dispatcher-dual]] — Un puerto, dos transportes intercambiables. En MalphasOS solo se usa el de proceso.

---

## Notas que faltan por escribir

Enlaces sin destino, a propósito: marcan lo que merece una nota y todavía no la tiene.

- `[[esquema-malphasos]]` — **el hueco más notorio**: no hay ninguna nota que describa el esquema real de MalphasOS. [[esquema-bd-v4]] describe el del original, y las **siete** migraciones `V1`–`V7` solo están contadas de refilón en las notas de migración. (Decía «cinco» y `V1`–`V5`: **cierto hasta el 2026-09-12**; «seis», hasta el **2026-09-26**, cuando entró `V7__seed_location_reference_data.sql`, la primera de datos y no de esquema — está descrita en [[dominio-ubicacion]].)

---

**51 notas** · reorganizado el 2026-09-09 · última corrección el 2026-09-26 · ver [[log.md]] para el historial.
