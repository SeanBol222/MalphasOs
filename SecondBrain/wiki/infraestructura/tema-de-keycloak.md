---
name: tema-de-keycloak
description: Como MalphasOS pone su marca en Keycloak -Keycloakify para el login, tema clasico para la consola- y las cuatro trampas que costo descubrirlo
tags: [infraestructura, keycloak, frontend, marca, "describe:malphasos"]
source: malphasos-keycloak-theme/, docker-compose.yaml y docker/keycloak/import/malphasos-realm-realm.json
estado: estable
updated: 2026-10-02
---

# El tema de Keycloak

**Qué resuelve**: que quien mira cualquier pantalla de Keycloak —el login que ve el cliente y la consola de administración por la que entra quien desarrolla— vea la marca de MalphasOS y no la de Keycloak. Lo pidió el usuario en esos términos: «no quiero ver los logos de Keycloak, sino los logos, diseño y colores de MalphasOS».

**Estado**: construido el 2026-09-28 y el 2026-10-02, en tres tandas. El login lo sirve **Keycloakify**; la consola, un **tema clásico**. Lo que falta es lo visual del login —hoy tiene el aspecto por defecto de Keycloakify— y la consola de cuenta del usuario, que sigue con la de Keycloak.

## Dos mecanismos y no uno, y el porqué de cada uno

| Pieza | Cómo | Por qué así |
|---|---|---|
| **Login** | Keycloakify (React 18 + Vite), `malphasos-keycloak-theme/` | Es una interfaz de verdad: tiene estados, errores, varios flujos. Escribir eso en FreeMarker a mano es lo que Keycloakify existe para evitar |
| **Consola de administración** | Tema clásico: `theme.properties` + CSS + dos logos | Cambiar logo y colores son **cuatro archivos**. La vía de Keycloakify para esto cuesta 692 — ver abajo |

**Comparten el nombre de tema, `malphasos`, a propósito.** Keycloak resuelve por **(nombre, tipo)**: el JAR responde por el tipo `login` y la carpeta por el tipo `admin`. Así el realm nombra **un solo tema** en `loginTheme` y en `adminTheme`, y el desplegable de Keycloak muestra una entrada en vez de dos. Comprobado que las dos siguen sirviendo lo suyo después de montarlo.

## La decisión que se tomó dos veces, y la primera estaba mal

**Lo primero que se recomendó fue Angular, y era un error de razonamiento que conviene conservar** porque es un patrón de error, no un despiste:

1. Se dijo «Angular, porque meter React repetiría el error del `Frontend/src/auth/` del original». **Falso**: aquello era código React sin equivalente y esto tiene librería propia de Angular, mantenida justo en Angular 22.
2. Lo que de verdad decide **no es el framework sino qué cubre cada uno**: la documentación de Keycloakify dice *«only React supports custom Admin UIs»* y *«Admin themes are unsupported in Angular»*. Como el encargo **era** la consola de administración, Angular dejaba fuera la mitad.
3. Y hay un dato que desarma el argumento del «segundo framework»: **Keycloakify no se puede instalar dentro de un proyecto Angular existente**. El tema es un proyecto aparte en cualquiera de los dos casos, así que lo que lleve dentro no contamina la aplicación.

La lección: **el argumento por analogía con una herida vieja sonaba bien y no aplicaba**. Lo que decidió fue leer qué cubre cada opción.

## ⚠️ `initialize-admin-theme` copia la consola entera

`npx keycloakify initialize-admin-theme` no configura un tema: **copia el código fuente de la consola de administración de Keycloak dentro del proyecto** para que se pueda editar.

| Lo que trae | |
|---|---|
| Archivos | **692** |
| Líneas | **88.845** |
| Peso | **12 MB** |
| Dependencias nuevas | ~25 (PatternFly, react-router, reactflow, i18next, dagre, jszip…) |
| Anclaje | `@keycloakify/keycloak-admin-ui@~260700.0.2` → Keycloak 26.7 |

Eso es **tener un fork de la consola de Keycloak** —la palabra se usó mal en la conversación: no es un fork de git, es una copia que hay que **reconciliar con cada versión nueva de Keycloak**—. Se ejecutó, se midió y **se revirtió**.

**Y no se arregla ignorándolo en git**, que fue la primera idea: el código tiene que estar ahí para que el tema se construya —y el build corre en Docker, que parte de lo que git tiene—; los cambios de marca viven **dentro** de esos archivos, así que ignorarlos es dejar sin versionar justo lo propio; y el coste de reconciliación no desaparece. Si no se van a editar, no hacen falta para nada.

**La alternativa, verificada leyendo la plantilla y no de memoria**: `theme/keycloak.v2/admin/index.ftl`, dentro de `org.keycloak.keycloak-admin-ui-26.6.1.jar`, expone `logo`, `logoUrl`, `favIcon`, `favIconType`, `title`, `styles` y `scripts`. Y el paquete de la consola hace `src: r.resourceUrl + l`, de donde sale que `logo` lleva barra inicial y `styles` no —la plantilla ya pone la suya—.

## Las tres trampas de despliegue

**1. `--import-realm` usa `IGNORE_EXISTING`.** Si el realm ya está en la base, el JSON versionado **no se aplica**, y no falla: lo dice en una línea de INFO entre cien —«Realm already exists. Import skipped»—. Quien edita el archivo se queda creyendo que surtió efecto. Se aplica con `kcadm` sobre el realm en marcha, que es la única vía que **no se lleva por delante los usuarios de desarrollo**.

**2. El tema se aplica por realm, y la consola que usa quien desarrolla es la del realm `master`**, que Keycloak crea solo y **no se importa de ninguna parte**. Sin ponérselo a mano se inicia sesión y se sigue viendo el logo de Keycloak. No hay archivo que pueda llevarlo: es un `kcadm` más, y se pierde al borrar el volumen de Postgres.

**3. Montar un JAR ya construido como volumen falla de la peor manera.** Si el archivo no existe, **Docker crea un directorio** con ese nombre y Keycloak arranca sin tema y sin decir nada. Por eso el JAR se construye **dentro de la imagen**, en una etapa con Node y Maven —Keycloakify necesita Maven para empaquetarlo, y en la máquina de desarrollo no estaba: para la primera prueba hubo que sacarlo del caché del wrapper del backend, que es justo el tipo de atajo que no se puede documentar como paso—.

Las tres recetas están escritas como comentario en `docker-compose.yaml`, junto al volumen de importación, que es donde alguien las va a buscar.

## Lo que el usuario preguntó y queda contestado

- **Keycloakify es MIT y gratis.** Hay un patrocinador que vende Keycloak alojado; es un servicio alrededor, no un requisito.
- **El logo animado que pidió —el logo apareciendo de abajo hacia arriba, con transparencia— se puede**, y **no como vídeo**: el vídeo con canal alfa es WebM/VP9 en Chrome y Firefox y HEVC en Safari, o sea dos codificaciones del mismo clip. Se hace con el logo en SVG y CSS: `clip-path: inset(100% 0 0 0)` → `inset(0 0 0 0)` para la revelación, `opacity` para la aparición y un `translateY` corto para el movimiento. Dos condiciones del propio proyecto: **respetar `prefers-reduced-motion`** —hay WCAG 2.1 AA declarado y verificado— y **no retener la pantalla** con una duración mínima artificial.
- **Solo hay logos en PNG** (`Documentation/FrontendDesign/BrandManual/.../exports/`). La revelación funciona igual con PNG; al escalar se verá peor que con un vectorial.

## Notas relacionadas

[[keycloak-configuracion]] · [[docker-compose]] · [[integracion-keycloak-frontend]] · [[seguridad-keycloak-backend]] · [[sistema-de-diseno-malphasos]] · [[decisiones-tecnicas-malphasos]] · [[deuda-tecnica-y-riesgos]]
