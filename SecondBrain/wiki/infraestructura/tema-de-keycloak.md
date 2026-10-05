---
name: tema-de-keycloak
description: Como MalphasOS pone su marca en Keycloak -Keycloakify para el login, tema clasico para la consola- y las cuatro trampas que costo descubrirlo
tags: [infraestructura, keycloak, frontend, marca, "describe:malphasos"]
source: malphasos-keycloak-theme/, docker-compose.yaml y docker/keycloak/import/malphasos-realm-realm.json
estado: estable
updated: 2026-10-04
---

# El tema de Keycloak

**Qué resuelve**: que quien mira cualquier pantalla de Keycloak —el login que ve el cliente y la consola de administración por la que entra quien desarrolla— vea la marca de MalphasOS y no la de Keycloak. Lo pidió el usuario en esos términos: «no quiero ver los logos de Keycloak, sino los logos, diseño y colores de MalphasOS».

**Estado**: construido el 2026-09-28 y el 2026-10-02, en tres tandas, y **terminado en lo visual el 2026-10-04**: el login con la marca y en español, y la consola rehecha después de que el usuario la viera ilegible. El login lo sirve **Keycloakify**; la consola, un **tema clásico**. Lo único que sigue con la cara de Keycloak es la consola de cuenta del usuario.

(Esta línea decía que faltaba «lo visual del login —hoy tiene el aspecto por defecto de Keycloakify—»: cierto hasta el **2026-10-04**. Y daba la consola por terminada, cosa que **no era**: ver abajo.)

## Dos mecanismos y no uno, y el porqué de cada uno

| Pieza | Cómo | Por qué así |
|---|---|---|
| **Login** | Keycloakify (React 18 + Vite), `malphasos-keycloak-theme/` | Es una interfaz de verdad: tiene estados, errores, varios flujos. Escribir eso en FreeMarker a mano es lo que Keycloakify existe para evitar |
| **Consola de administración** | Tema clásico: `theme.properties` + CSS + dos logos | Cambiar logo y colores son **cuatro archivos**. La vía de Keycloakify para esto cuesta 692 — ver abajo |

**Comparten el nombre de tema, `malphasos`, a propósito.** Keycloak resuelve por **(nombre, tipo)**: el JAR responde por el tipo `login` y la carpeta por el tipo `admin`. Así el realm nombra **un solo tema** en `loginTheme` y en `adminTheme`, y el desplegable de Keycloak muestra una entrada en vez de dos. Comprobado que las dos siguen sirviendo lo suyo después de montarlo.

> **Corregido el 2026-10-04: el realm versionado no lo nombraba en `adminTheme`.** El JSON trae
> `"adminTheme": ""` desde `aa089cbf`, y el servidor de desarrollo tiene `malphasos` porque se le puso
> con `kcadm`. Este párrafo describía el servidor y no el archivo: en una base nueva, la consola del
> realm `malphasos-realm` saldría con la cara de Keycloak. Encontrado comparando el archivo con
> `kcadm get` durante la pasada de wiki. Es la misma clase de fallo que el `IGNORE_EXISTING`: **lo que
> se aplica a mano al entorno en marcha y no se vuelve a escribir en el archivo, solo existe en una
> máquina**.

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

## El login, con la marca (2026-10-04)

**Sin la hoja por defecto de Keycloak y con clases propias**, no sobrescribiéndola. `KcPage` pasa
`doUseDefaultCss={false}` y un mapa `classes` de las clases de Keycloakify a clases `mos-*`, y
`malphasos-login.css` estiliza esas y, para lo que no tiene clase, los identificadores que publican los
formularios de Keycloak (`#kc-form-options`, `#kc-form-buttons`…). Sobrescribir la hoja de PatternFly 4
obliga a ganarle en especificidad regla a regla, y cada versión de Keycloak trae reglas nuevas.

**La plantilla es propia** (`src/login/Template.tsx`, partiendo de la de Keycloakify) por una sola
razón: el encabezado es el **logo apilado del manual**, no el nombre del realm en texto. El nombre queda
como texto alternativo. De paso desaparecen los iconos de PatternFly de los avisos —sin la hoja no hay
fuente de iconos— y el ojo de «mostrar contraseña» es una máscara SVG que toma el color del texto.

**Copia las decisiones del frontend a propósito**: los mismos tokens, el mismo borde de campo, la misma
altura táctil de 44 px, el aviso con borde a la izquierda y el botón en `#ae1800`, porque el acento no
alcanza AA como relleno. Quien entra pasa de esta pantalla a la aplicación en un segundo, y un cambio de
estilo entre las dos se lee como haber salido del producto. Archivo entra como dependencia
(`@fontsource-variable/archivo`), igual que en el frontend.

**La animación que el usuario pidió está hecha como se había anotado**: `clip-path` de `inset(100% 0 0 0)`
a `inset(0)`, `opacity` y un `translateY` de 16 px, en 700 ms. Se queda quieta con
`prefers-reduced-motion` —comprobado emulándolo: `animation-name` pasa a `none`— y no retiene nada: el
formulario se puede usar desde el primer fotograma.

**Y en español, que no es cosa del tema sino del realm.** Con la internacionalización desactivada,
Keycloak sirve sus textos —y sus mensajes de error, que vienen del servidor— en inglés: «Sign in to your
account» sobre la marca. El realm versionado lleva ahora `internationalizationEnabled`, `supportedLocales:
["es"]` y `defaultLocale: "es"`; con un solo idioma, el selector no aparece. **En el entorno en marcha se
aplicó con `kcadm`**, por la trampa del `IGNORE_EXISTING` de arriba.

**El login del realm `master` usa el mismo tema desde ese día**, puesto a mano como su `adminTheme` y
con la misma pega: se pierde con el volumen. Sale en inglés, a propósito: activar el idioma en `master`
cambia también la consola entera.

Verificado con un navegador sin interfaz contra el Keycloak en marcha: escritorio, móvil de 390 px, el
error de credenciales y el sistema en modo oscuro.

## ⚠️ La consola que se daba por terminada era ilegible (2026-10-04)

El usuario entró y la describió así: «el logo no se ve, todos los contenedores son blancos y las letras
también, y no aplicaste los colores que ya se escogieron a todo». **Las tres cosas eran ciertas, y
ninguna se había visto porque la consola nunca se había mirado con el sistema en modo oscuro.**

1. **`keycloak.v2` trae `darkMode=true`.** Si el sistema pide modo oscuro, la consola pone
   `pf-v5-theme-dark` en el `<html>` y PatternFly pasa el texto a claro. Este tema solo define la paleta
   clara, de modo que la mezcla daba **letras claras sobre contenedores claros**. Se fija
   `darkMode=false` en `theme.properties`: la aplicación tampoco tiene modo oscuro. Se reprodujo antes de
   arreglarlo, emulando `prefers-color-scheme: dark` en el navegador sin interfaz.
2. **El logo era la versión en tinta para fondo claro**, sobre la barra negra: solo se leía «OS». Pasa a
   la del manual para fondo oscuro, recortada, y la barra usa su mismo `#2d2b2b` para que no se note el
   recuadro.
3. **La hoja tocaba siete variables.** El menú, los fondos, los bordes, las pestañas y la tipografía
   seguían siendo los de Keycloak. Ahora va la paleta entera y Archivo servida desde el propio tema.

**La barra de la entrada activa del menú costó tres intentos, y el tercero enseña algo.** Primero se tapó
con una sombra y siguió azul: era un `::after` con su propio color. Después se cambió la variable en
`.pf-v5-c-nav` y siguió azul. **Lo que lo resolvió fue medir y no suponer**: leer en la página qué reglas
fijaban la variable. PatternFly la **vuelve a fijar en cada `.pf-v5-c-nav__section`**, y el menú de la
consola agrupa sus entradas en secciones, así que lo puesto en el `nav` no llegaba al enlace. Entre el
segundo y el tercer intento se probó `.pf-m-light` por suposición, y el menú **ni siquiera lleva esa
clase**. Con variables CSS, la regla que gana es la del ancestro más cercano que la fija, no la más
específica, y eso solo se ve preguntándole al navegador.

## Lo que el usuario preguntó y queda contestado

- **Keycloakify es MIT y gratis.** Hay un patrocinador que vende Keycloak alojado; es un servicio alrededor, no un requisito.
- **El logo animado que pidió —el logo apareciendo de abajo hacia arriba, con transparencia— se puede**, y **no como vídeo**: el vídeo con canal alfa es WebM/VP9 en Chrome y Firefox y HEVC en Safari, o sea dos codificaciones del mismo clip. Se hace con el logo en SVG y CSS: `clip-path: inset(100% 0 0 0)` → `inset(0 0 0 0)` para la revelación, `opacity` para la aparición y un `translateY` corto para el movimiento. Dos condiciones del propio proyecto: **respetar `prefers-reduced-motion`** —hay WCAG 2.1 AA declarado y verificado— y **no retener la pantalla** con una duración mínima artificial.
- **La animación del logo está construida** desde el 2026-10-04: ver arriba.
- **Solo hay logos en PNG** (`Documentation/FrontendDesign/BrandManual/.../exports/`). La revelación funciona igual con PNG; al escalar se verá peor que con un vectorial.

## Notas relacionadas

[[keycloak-configuracion]] · [[docker-compose]] · [[integracion-keycloak-frontend]] · [[seguridad-keycloak-backend]] · [[sistema-de-diseno-malphasos]] · [[decisiones-tecnicas-malphasos]] · [[deuda-tecnica-y-riesgos]]
