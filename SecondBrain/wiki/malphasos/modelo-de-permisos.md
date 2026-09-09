---
name: modelo-de-permisos
description: Las 19 autoridades del API de MalphasOS, la regla de expansión del administrador aplicada en dos capas, y qué recibe cada grupo del realm
tags: [malphasos, seguridad, keycloak, autorizacion, "reusable:media", "describe:malphasos"]
estado: estable
updated: 2026-09-08
---

# Modelo de permisos de MalphasOS

Escrito el 2026-09-08, al cerrar la tanda que lo construyó (nueve commits, `e68f83e`…`2de115f`, rama `feat/permission-model`). Antes de esa tanda **el modelo de permisos no existía**: las 83 operaciones REST exigían `admin.full` y el realm solo se lo daba al grupo `admins`. Ver [[seguridad-keycloak-backend]] para las piezas de seguridad y [[keycloak-configuracion]] para el realm.

## El defecto que corrige

Las 83 operaciones publicadas declaraban `@PreAuthorize("hasAuthority('admin.full')")` —83 mappings, 83 anotaciones idénticas—. Los grupos `engineers` y `clients` recibían cuatro roles de lectura (`client.read`, `equipment.read`, `service-area.read`, `work-order.read`) que **ninguna operación comprobaba**: autenticaban correctamente y recibían 403 en toda llamada. Y los dos grupos tenían **exactamente los mismos cuatro roles**, de modo que un técnico de campo y un cliente externo eran indistinguibles para el sistema.

Es el mismo defecto que [[keycloak-configuracion]] ya registraba del original —«los 15 roles granulares no se usan»— heredado intacto al migrar.

**Por qué nadie lo vio durante meses**: todas las pruebas de seguridad construían el token con `admin.full`, el único rol que sí funcionaba. Una batería que solo recorre el camino del administrador no dice absolutamente nada sobre los demás perfiles. El hallazgo salió de contrastar la ERS contra el código (2026-09-02), no de leer los controladores.

## El vocabulario: 19 autoridades

`bootstrap/config/security/ApiAuthority.java` reúne el vocabulario completo. Los nombres **no los inventa la clase: reflejan los roles que el realm define** sobre el client `malphasos-api`.

Dos autoridades de mando, que no protegen ningún endpoint:

| Autoridad | Qué es |
|---|---|
| `admin.full` | Concede las 17 de recurso al expandirse. Es el permiso del grupo `admins` |
| `super.admin.full` | Implica `admin.full`. Hoy conceden lo mismo, y se conservan separados porque el realm los distingue y un realm futuro podría darle al segundo algo que el primero no tenga. **Ningún grupo lo recibe** |

Las 17 de recurso, con las operaciones que protegen a fecha de hoy (contado sobre los controladores, no sobre el catálogo):

| Autoridad | Operaciones | Notas |
|---|---|---|
| `person.read` | 2 | |
| `person.write` | 12 | Los sub-recursos (correos, teléfonos) no llevan autoridad propia: un correo no se gestiona sin gestionar a su dueño |
| `location.read` | 4 | |
| `location.write` | 6 | |
| `client.read` | 4 | |
| `client.write` | 11 | Incluye sedes y sub-recursos del cliente: una sede no existe sin su cliente |
| `client.delete` | 1 | Solo la baja del cliente entero. Retirar un correo no es cerrar la cuenta |
| `service-area.read` | 2 | Vocabulario propio, para que un ingeniero trabaje sobre áreas sin tocar la ficha del cliente |
| `service-area.write` | 3 | |
| `engineer.read` | 2 | El realm llama `engineer` a lo que el código llama `Manager` y la ERS «profesional responsable». Se conserva el vocabulario del realm porque es el que la documentación recoge |
| `engineer.assign` | 4 | **No existe `engineer.write`**: al encargado se le asigna, no se le escribe. Una prueba lo fija, porque quien la escriba dejaría el endpoint sin nadie que pudiera llamarlo |
| `equipment.read` | 13 | |
| `equipment.write` | 17 | |
| `equipment.assign` | 2 | Vincular una unidad a un área y trasladarla a otra. Asignar cambia quién responde por un equipo, y es lo que un ingeniero de campo hace y un administrativo no |
| `work-order.read` | 0 | |
| `work-order.write` | 0 | |
| `work-order.assign` | 0 | |

**Total: 83 operaciones** — 27 lecturas (`GET`) y 56 escrituras. Verificado el 2026-09-08 contando las anotaciones sobre `feat/permission-model`.

Las tres de `work-order` están en el catálogo **sin módulo detrás**: ya estaban en el realm y asignadas al grupo `engineers`. Incluirlas ahora evita que el día que aparezcan sus endpoints el administrador se quede fuera por olvido. Una prueba fija que hoy no protegen nada, y fallará el día que lo hagan — que es cuando toca revisar esta nota.

## La decisión central: expandir en un sitio, no repetir en 83

Cada operación declara **únicamente la autoridad de su recurso**: `hasAuthority('client.read')`.

La alternativa era que cada una nombrara además al administrador, `hasAnyAuthority('admin.full','client.read')`. Se descartó porque deja el modelo de permisos **repetido 83 veces**, y basta olvidarse de una para abrir un agujero que no rompe nada visible. Quién es administrador se decide en un solo sitio: `ApiAuthority.expand(...)`.

Una prueba estructural impide deshacer la decisión sin enterarse: **ningún controlador puede nombrar al administrador**, y cada operación debe exigir exactamente una autoridad nombrada literalmente.

## Por qué la regla se aplica en dos capas

Existen dos implementaciones de la misma jerarquía, y no es duplicación por descuido:

- `KeycloakRoleConverter` llama a `ApiAuthority.expand(...)` al convertir el token en autoridades.
- `SecurityConfig` declara un bean `RoleHierarchy` con la misma jerarquía (`super.admin.full > admin.full`, y `admin.full > cada autoridad de recurso`).

El converter **solo interviene cuando la autenticación nace de un JWT que pasa por esta cadena de filtros**. Cualquier autenticación construida por otra vía llegaría a `@PreAuthorize` con las autoridades crudas, y un administrador se vería rechazado. La jerarquía cierra ese hueco.

El coste de tener dos caminos —que digan cosas distintas— se paga de dos formas: **ambas se derivan de la misma constante `RESOURCE_AUTHORITIES`**, y cada una tiene su prueba propia, porque derivar de la misma constante no garantiza que ambas la traduzcan bien.

Detalle que ahorra un rato de desconcierto: los nombres de la jerarquía van **sin el prefijo `ROLE_`**. Esa convención de Spring Security no se usa aquí; los permisos llegan de Keycloak con su propio nombre y se comprueban con `hasAuthority`, nunca con `hasRole`.

## `expand()` añade, nunca quita

Un rol que no pertenece al vocabulario **se conserva tal cual**. La alternativa —filtrar lo desconocido— parece más limpia y es peor: alguien crea un rol en el realm, lo asigna, y el permiso desaparece en silencio dentro de un converter, sin log ni error. Un fallo así se diagnostica mirando el sitio equivocado durante horas.

La pertenencia se decide por **igualdad exacta** sobre el conjunto, no por prefijo: `admin.fullish` y `super.admin` no conceden nada. Hay una prueba para eso, porque es justo lo que un refactor a `startsWith` rompe sin avisar.

## Qué recibe cada grupo del realm

Verificado el 2026-09-08 sobre `docker/keycloak/import/malphasos-realm-realm.json`:

| Grupo | Roles | Perfil |
|---|---|---|
| `admins` | 18 de 19 | Todo menos `super.admin.full`, que no tiene grupo |
| `engineers` | 11 (antes 4) | Lectura completa (`person`, `location`, `client`, `service-area`, `equipment`, `engineer`) + `equipment.write` + `equipment.assign` + los tres de `work-order` |
| `clients` | 4 (sin cambios) | Solo lectura: `client.read`, `equipment.read`, `service-area.read`, `work-order.read` |

`admins` recibe también los cuatro roles nuevos, **pese a que la expansión de `admin.full` se los concedería igualmente**. El realm debe poder leerse sin conocer el código: un `admins` sin `person` ni `location` afirmaría por escrito que un administrador no puede tocar personas ni ciudades.

Los cuatro roles nuevos —`person.read`, `person.write`, `location.read`, `location.write`— **llevan descripción, al contrario que los quince anteriores**, y sin tildes porque el archivo es ASCII puro y viaja entre entornos.

## Cómo se verifica: dos invariantes estructurales

139 pruebas de seguridad (conteo XML de Surefire del 2026-09-08: 82 en `SecurityIntegrationTest`, 19 en `ApiAuthorityTest`, 14 en `RestAuthorizationCoverageTest`, 11 en `KeycloakRoleConverterTest`, 7 en `RealmAuthorityContractTest`, 6 en `SecurityConfigRoleHierarchyTest`).

Lo que más pesa no son los casos, sino dos invariantes, y la razón importa: **las 83 anotaciones se pusieron con un script por número de línea**. Un desfase de una línea deja un endpoint con la autoridad del vecino, y ninguna prueba de HTTP por casos lo ve.

**`RestAuthorizationCoverageTest`** recorre los controladores por reflexión —escanea el classpath, no arranca Spring— y exige:

- toda operación publicada declara `@PreAuthorize`, y ninguno a nivel de clase;
- la autoridad citada existe en `ApiAuthority` (`hasAuthority('equipmentt.read')` compila, arranca y responde 403 a todo el mundo para siempre);
- ningún controlador nombra al administrador;
- toda consulta `GET` se protege con una autoridad `.read`, y ninguna escritura se conforma con una;
- toda autoridad de un módulo ya construido protege algún endpoint, y las de `work-order` todavía no.

Los umbrales van como **mínimos** (`>= 83`, `>= 27`, `>= 56`), no como igualdades: sirven de guarda de no vacuidad —que la comprobación siguiente no pase por lista vacía— sin romperse cada vez que se añade un endpoint legítimo.

**`RealmAuthorityContractTest`** confronta el vocabulario del código con el JSON del realm: ni un rol de más ni de menos, y qué recibe cada grupo. Si divergen, hoy el administrador se quedaría sin algo **sin que nada fallara**.

## Lo que este modelo todavía no hace

- **No hay filtrado por dueño.** Un usuario del grupo `clients` con `client.read` ve **todos** los clientes y el catálogo entero, no solo el suyo. Fue una decisión explícita de dejarlo fuera de esta tanda, no un olvido. Verificado el 2026-09-08: **ninguna clase fuera de `bootstrap/config` toca `Authentication`, `SecurityContextHolder` ni `@AuthenticationPrincipal`**, de modo que ningún servicio sabe quién llama. Implementarlo no es añadir un `WHERE`: exige decidir cómo se ata una cuenta de Keycloak a un cliente del dominio.
- **No es verificable que el realm que Keycloak importa coincida con el JSON del repositorio.** Las pruebas leen el archivo versionado subiendo directorios desde el módulo; una edición hecha a mano en la consola de administración no la ve nadie. El contrato es con el archivo, no con el servidor.
- `work-order` tiene tres autoridades y ningún módulo.

## Reutilizable

`reusable:media` — el vocabulario concreto es de este dominio, pero **el patrón sí se traslada**: expandir el rol de mando en un solo punto en vez de repetirlo en cada anotación, aplicarlo en dos capas derivadas de la misma constante, y sostenerlo con invariantes estructurales por reflexión en vez de con casos uno a uno.

## Notas relacionadas

[[seguridad-keycloak-backend]] · [[keycloak-configuracion]] · [[decisiones-tecnicas-malphasos]] · [[deuda-tecnica-y-riesgos]] · [[stack-spring-boot-4-particularidades]] · [[dominio-cliente]] · [[relacion-manager-persona]]
