---
name: hoja-de-ruta-producto
description: Que falta por construir en MalphasOS -backend y frontend- ordenado por dependencias reales y no por numeracion de requisitos
tags: [malphasos, planificacion, hoja-de-ruta, "describe:malphasos"]
source: Documentation/IEEE830/IEEE830.tex apartado 3.2 y Documentation/wiki/ (28 notas), contrastados contra malphasos/
estado: estable
updated: 2026-09-13
---

# Hoja de ruta del producto

**Qué es esta nota**: lo que falta por construir de MalphasOS, backend y frontend, **ordenado por dependencias**. No es una lista de requisitos —esa la tiene la matriz de trazabilidad— sino el orden en que se pueden abordar sin quedarse a medias.

**Qué no es**: el registro de la migración desde `bolivarbioingenieria-app`. Eso es [[checklist-reutilizacion]], que se queda como está. **Dos listas de tareas se desincronizan**; aquélla mira hacia atrás y ésta hacia adelante.

## El punto de partida, verificado

| | Total | Implementado | Fuente |
|---|---|---|---|
| Requisitos funcionales | 31 | **11** | ERS 3.2, verificada dos veces contra el código |
| Requisitos no funcionales | 23 | **1** (RNF-23, JWT) | Matriz de trazabilidad |
| Frontend | — | **Sesión, autenticación, clientes cerrado entero, y equipos: listado, catálogo y registro** | Comprobado sobre el árbol del repositorio |

> **Corregido el 2026-09-26, dos veces el mismo día.** Esta fila decía «**Nada.** No existe el directorio»: cierto hasta el 2026-09-13. Hoy existen el armazón, el sistema de diseño con su prueba de contraste, la autenticación contra Keycloak, **nueve pantallas** de clientes —ficha, edición, retiro, contactos, sedes, áreas de servicio y encargados— y **seis más** del catálogo de equipos con el registro de un equipo en un área, con **262** pruebas. **Con eso existe ya todo lo que una orden de trabajo necesita tocar**: sedes, áreas y equipos por área. Lo que sigue faltando son las pantallas de órdenes de trabajo, que es lo que cierra los cuatro RF de formulario.

Los 11 implementados son **RF-08** (crear cliente), **RF-22** y **RF-24** (hoja de vida: crear, modificar/eliminar), **RF-49 a RF-53** (login, identificación de rol, alta, edición y baja de usuarios) y **RF-01, RF-02 y RF-05** de órdenes de trabajo. Todo lo demás está `[PREVISTO]`.

> **Actualizado el 2026-09-13**: el marcador estaba en **8** y pasa a **11** con el módulo de órdenes de trabajo, completo en sus cuatro tandas. **No son siete de golpe, y el porqué importa** — ver el desglose justo debajo.

### Por qué el módulo completo no cierra sus siete requisitos

El backend de órdenes de trabajo está terminado, y aun así **solo tres de los siete RF cuentan como implementados**. La razón es que **cuatro de ellos describen un formulario**, no una capacidad del sistema, y no hay frontend.

| RF | Qué pide | Estado | Por qué |
|---|---|---|---|
| RF-01 | Crear orden directa, sin solicitud previa | ✅ | `POST /v1/api/work-orders` |
| RF-02 | Eliminar la dependencia de solicitud | ✅ | Satisfecho **por construcción**: la palabra «solicitud» no existe en el esquema ni en el código, no había nada que retirar |
| RF-03 | Formulario con cliente, sede, áreas, tipo y periodicidad | ⏳ | Los cinco datos existen en el backend; **el formulario no** |
| RF-04 | Selección múltiple de **áreas** de la sede | ⏳ | Es un paso de interfaz, y además **las áreas elegidas no se persisten a propósito**: quedan implícitas en los equipos. Ver [[dominio-orden-trabajo]] |
| RF-05 | Identificador único (UUID) de la orden | ✅ | `k_id_orden_trabajo uuid` |
| RF-06 | Visualización de equipos por área seleccionada | ⏳ | La consulta ya existía antes del módulo; falta la interfaz que agrupe |
| RF-07 | Selección múltiple de equipos | ⏳ | El API añade **de uno en uno**, `POST /{id}/equipments`. La selección múltiple es del formulario |

**El criterio que se ha aplicado, para que se pueda discutir**: cuenta como implementado el requisito que el backend satisface por completo. El que describe una pantalla se queda abierto aunque el dato que necesita ya exista, porque darlo por hecho inflaría el marcador y haría que el trabajo de frontend desapareciera de la cuenta sin haberse hecho.

Es el mismo criterio que ya deja **RF-22 y RF-24 como implementados y RF-26 no**, y conviene aplicarlo igual cuando lleguen los reportes.

Del backend queda además, fuera de la numeración de la ERS, **la segunda tanda de `equipment`**: verificaciones técnicas y datos metrológicos.

Detalle por categoría en `Documentation/wiki/requisitos/estado-de-implementacion.md`.

## Decisiones ya tomadas por el usuario

- **La tanda de seguridad está hecha** (`fix/person-identity-sync`, **en `main` desde `258cd81`**; esta nota la daba por sin mergear, **corregido el 2026-09-09**): dar de baja a una persona ya deshabilita su cuenta de Keycloak. Ver [[sincronizacion-con-proveedor-de-identidad]].
- **El siguiente bloque era órdenes de trabajo**, y **está construido**: las cuatro tandas, del esquema al REST, entre el 2026-09-12 y el 2026-09-13. Ver [[dominio-orden-trabajo]].
- **Lo siguiente decidido es el frontend**, el 2026-09-13, con su declaración de diseño escrita y su manual de marca. Arranca por una **rebanada vertical**: arranque de la aplicación, autenticación y el flujo de órdenes de trabajo. Ver [[arquitectura-frontend-malphasos]].
- **Reportes de servicio** queda como el siguiente bloque de backend. Es lo que cuelga directamente de la orden, y el módulo ya emite los siete eventos que un reporte querría escuchar — sin consumidor todavía.

Lo que sigue no vuelve a decidir eso; explica por qué el orden aguanta y qué arrastra cada pieza.

## El grafo de dependencias, y las dos versiones que existen de él

Hay que distinguir **dos grafos que no coinciden**, y confundirlos es el error fácil:

1. **El declarado**, que la ERS escribe requisito por requisito en su apartado "Dependencias".
2. **El real**, el de qué código no puede escribirse antes que cuál.

Donde discrepan, manda el segundo — pero el primero explica por qué alguien esperaría otra cosa.

### Órdenes de trabajo: no dependía de nada que no estuviera construido — y ya está hecho

RF-01 a RF-07. **Todo lo que necesita existe**: `cliente`, `sede`, `area_servicio` y `equipo_cliente` son tablas reales, y las dos consultas que el dominio pide ya están publicadas —`GET /v1/api/headquarters/{idSede}/service-areas` y `GET /v1/api/service-areas/{idAreaServicio}/equipments`—. Sus dependencias declaradas hacia fuera de la categoría son RF-08, RF-49 y RF-50: **las tres implementadas**.

Lo que sí hay que crear con ella: **tipo de servicio y periodicidad no tienen columna en ningún sitio** (RF-03). No es una tabla más de la orden, es vocabulario nuevo del dominio.

> **Corregido el 2026-09-12**: la frase anterior era cierta hasta esa fecha y **dejó de serlo** con `V6__work_order.sql`, en la rama `feat/work-order-schema`. Las dos columnas existen ya, con `CHECK` propio: `n_periodicidad` y `t_tipo_servicio`. Y **el vocabulario no era tan nuevo como esta nota suponía**: las periodicidades y los estados estaban escritos en un `CHECK` del esquema heredado; solo los tipos de servicio salieron de la ERS. Ver [[dominio-orden-trabajo]].

⚠️ **El grafo declarado contiene un ciclo, y está dentro de esta categoría.** RF-05 depende de RF-07; RF-07 de RF-06; RF-06 de RF-05 —y además de RF-09, que es de reportes y a su vez depende de RF-05 y RF-07—. Leído al pie de la letra, ninguno de los cuatro puede empezar. **Es un defecto de la ERS, no un bloqueo real**: identificar la orden, elegir sus áreas y elegir sus equipos son partes de un mismo agregado y se construyen juntas. Se anota aquí porque quien planifique leyendo solo el documento se detendrá en seco.

> **Confirmado el 2026-09-13**: el módulo se construyó **ignorando ese ciclo** y no hubo ningún bloqueo. Identificar la orden, elegir sus áreas y elegir sus equipos salieron juntas como partes del mismo agregado, exactamente como esta nota predijo. Es la prueba de que el grafo declarado y el real no coinciden, y de cuál de los dos manda.

### De órdenes de trabajo cuelga casi todo lo demás

```
ordenes de trabajo (RF-01..07)
├── reportes de mantenimiento (RF-09, 11, 13, 15, 17)
│   ├── historial de la hoja de vida (RF-26, RF-27)   <- cuelga del REPORTE, no de la orden
│   └── exportar a PDF (RF-17) ── depende ademas de la FIRMA (RF-21)
├── firma digital (RF-18, RF-21) ── y del frontend tactil
├── modulo comercial: generar OT desde cotizacion (RF-47)
└── inventario: entrada/salida durante el mantenimiento (RF-37)
```

Precisiones que el diagrama comprime:

- **El historial de la hoja de vida cuelga del reporte, no de la orden.** RF-26 depende de RF-09 y RF-15, los dos de reportes. La orden de trabajo dice *qué se va a hacer*; el reporte dice *qué se hizo*, y es eso lo que se anota en la hoja de vida. El mecanismo de soporte —el despachador de eventos de dominio— **ya está construido y en uso**; falta el evento del reporte, no el despacho.
- **Exportar un reporte a PDF (RF-17) depende de la firma digital (RF-21).** Y la captura de la firma es **táctil** (RF-18): sin frontend no hay firma, y sin firma no hay PDF según el grafo declarado. Es la cadena que más lejos llega desde el frontend hacia el backend.

### Alertas y calibración: el caso donde los dos grafos discrepan

La ERS declara que **RF-40 depende de RF-11** (autocompletado del reporte). El bloqueo real es otro: **no hay dónde guardar una fecha de calibración**. `tipo_equipo` sabe *si* un tipo se verifica y *con qué modalidad*, y nada más: no hay fechas, no vence, no dispara nada. Las fechas viven en **verificaciones técnicas y datos metrológicos**, que son la **segunda tanda de `equipment`**.

Y falta una pieza que ningún requisito nombra: **no hay tareas programadas en el backend**. `@EnableScheduling` no aparece en el código, comprobado. RNF-19 pide monitorear cada 24 h; alguien tendrá que introducir el mecanismo.

### Dos categorías con la dependencia rota en el propio documento

- **Inventario de existencias** (RF-36, RF-37): ambos dependen de **RF-35, que no existe** en el apartado 3.2.
- **Módulo comercial** (RF-45, RF-47): RF-45 depende de **RF-46, que tampoco existe**.

No es un bloqueo técnico, es uno de especificación: **antes de construirlos hay que escribir el requisito que falta**. Los dos de inventario están además clasificados **Won't Have**.

⚠️ **No confundir el inventario de existencias con `equipo_cliente`.** El código llama "inventario de un área" al censo de unidades físicas de un cliente; RF-36/37 piden el almacén de herramientas y repuestos de BolívarBioingeniería, con cantidades que suben y bajan. Son cosas distintas y la confusión ya está prevista en la propia ERS.

### Lo que se puede construir hoy sin esperar a nada

**RF-14, la configuración de protocolos por tipo de equipo y servicio**, depende únicamente de **RF-22, que está implementado**. Es la única pieza de reportes cuyo camino está libre hoy, y es la que RF-13 necesitará después. Si el bloque de órdenes de trabajo se atasca, es lo que se puede adelantar sin deuda.

## El orden propuesto

| # | Bloque | Desbloquea | Estado de sus dependencias |
|---|---|---|---|
| ~~0~~ | ~~**Mergear las dos ramas pendientes**~~ | Todo lo demás | **Hecho el 2026-09-09**: `e6dda32` y `258cd81`, con `main` en `01c3277` y la batería remedida en **496**. **Actualizado el 2026-09-12**: `main` está hoy en **`55e0a5d`**, con `feat/relocation-same-client` también dentro (`1ef55cf`) y **509** pruebas |
| 1 | **Órdenes de trabajo** (RF-01…07) | Reportes, firma, comercial, inventario | Todas cumplidas. **Empezado el 2026-09-12**: tanda 1 de 4 —el esquema, `V6`— en `feat/work-order-schema`, sin mergear. Faltan dominio, aplicación y REST, y **los siete requisitos siguen contando como no implementados**: un esquema no cumple ninguno |
| 2 | **Reportes de mantenimiento** (RF-09, 11, 13, 15) | Historial de hojas de vida, PDF, alertas | Requiere el bloque 1 (+ RF-14, disponible ya) |
| 3 | **Segunda tanda de `equipment`** (verificaciones técnicas, datos metrológicos) | Alertas y calibración | Ninguna pendiente. Puede ir en paralelo al 1 y al 2 |
| 4 | **Historial de intervenciones** (RF-26, RF-27) | Cierra las hojas de vida | Requiere el bloque 2 |
| 5 | **Frontend** | Firma digital, seis RNF, los manuales de usuario | Ninguna técnica. **Decidido el 2026-09-13: entra ahora.** Arrancado ese mismo día; **la sección de clientes quedó cerrada el 2026-09-26**, y con ella existe ya dónde registrar sedes y áreas, que es lo que el formulario de órdenes necesita para poder construirse |
| 6 | **Firma digital** (RF-18, RF-21) → **PDF** (RF-17) | Cierra reportes | Requiere los bloques 2 y 5 |
| 7 | **Alertas y calibración** (RF-40, RF-41) | — | Requiere el bloque 3 + tareas programadas. **Won't Have** |
| 8 | **Inventario y módulo comercial** | — | **Requisitos ausentes que hay que escribir primero.** Inventario es Won't Have |

**La prioridad MoSCoW de la ERS coincide con este orden en lo que importa**: los bloques 1 y 2 concentran casi todos los *Must Have*; el 4 y el 6 son *Could Have* o sin clasificar; el 7 y la mitad del 8 son *Won't Have*. No hubo que elegir entre dependencias y prioridad.

### Por qué el bloque 0 no es burocracia

**Ya no aplica desde el 2026-09-09, y lo que sigue explica por qué importaba.** `feat/permission-model` incluía una prueba, `RestAuthorizationCoverageTest` —hoy en `main`—, que **falla a propósito el día que aparezca el primer endpoint de órdenes de trabajo**: `ApiAuthority` ya declara `work-order.read`, `work-order.write` y `work-order.assign`, el realm ya se las concede al grupo `engineers`, y la prueba fija que hoy no protegen nada. Construir órdenes de trabajo **antes** de mergear esa rama habría significado escribir los controladores sin ese vocabulario y volver luego. Con la rama ya en `main`, la prueba avisa en el momento justo: **el bloque 1 puede empezar**.

**Nota del 2026-09-12, para quien llegue a la tanda REST**: esa prueba —`lasAutoridadesDeWorkOrderSiguenSinModulo`— **sigue verde**, porque el esquema de `V6` no añade endpoints. Se pondrá roja con el primer controlador del módulo, y entonces hay que **retirarla en el mismo commit**, no investigarla como una regresión. Está comprobado que vive en `main`. Ver [[dominio-orden-trabajo]].

## Lo que esta hoja de ruta no puede decidir: cuándo entra el frontend

**No hay una sola línea de frontend**, y no es una tarea más de la lista: es la que decide si el producto cumple aquello con lo que se justifica.

De él dependen:

- **Seis requisitos no funcionales enteros**, RNF-12 a RNF-17: accesibilidad web, diseño responsivo, **interacción por pantalla táctil**, usabilidad en ≤6 pasos, componentes de entrada, y mensajes de error legibles. Ninguno tiene nada que ver con el backend: el backend ya tiene un catálogo de errores por módulo, lo que falta es quien traduzca esos códigos a algo que una persona lea.
- **RNF-02, RNF-05 y RNF-07**, que piden minimizar la entrada manual, reducir la carga cognitiva e impedir editar lo autocompletado.
- **RF-18**, la captura táctil de la firma, y con ella RF-21 y RF-17.
- **Los manuales de usuario**, que no se pueden escribir sobre un API REST.

Y la ERS **presenta el trabajo móvil en campo como su razón de ser**: el apartado 1.2 dice que el sistema "facilitará el trabajo en campo de los ingenieros mediante el uso de dispositivos móviles"; el 2.1 exige que sea accesible desde el teléfono del ingeniero **sin instalar aplicaciones nativas**; y el 3.1.2 lista la **pantalla táctil** entre las interfaces de hardware del sistema, junto a la cámara.

**Un backend completo con cero frontend cumple 0 de esos requisitos y, leyendo la ERS, no es el producto que se prometió.**

> **Decidido el 2026-09-13.** Esta nota decía que la decisión de cuándo entra el frontend «es del usuario y no de un grafo de dependencias». **Ya se tomó: entra ahora**, antes que los reportes. Hay documento oficial —`Documentation/FrontendDesign/`— con la plataforma, la arquitectura, el sistema visual, el nivel de accesibilidad y la estrategia de pruebas, y el detalle operativo está en [[arquitectura-frontend-malphasos]] y [[sistema-de-diseno-malphasos]].

Seguía siendo cierto lo que esta nota decía de fondo: técnicamente **no estaba bloqueado por nada** y podía empezar contra los módulos que ya publican API.

> **Y aquí esta nota se equivocó, corregido el 2026-09-13.** Decía que «el arranque no parte de cero» porque `Frontend/src/auth/` del original era «un starter completo y portable». **Al elegir Angular dejó de serlo**: esas cuatro piezas son React y hay que reescribirlas contra `keycloak-angular`. Lo que se porta es el **patrón**, no el código. Ver las correcciones en [[integracion-keycloak-frontend]] y [[arquitectura-frontend]].

Lo que sí se hereda sin discusión es el **manual de marca**, que llegó ya hecho y es la autoridad del sistema visual.

## Un efecto lateral que ya venció: la ERS quedó desactualizada

RF-53 está marcado `[IMPLEMENTADO]` con la salvedad escrita de que **su tercer criterio de aceptación —"el acceso se revoca inmediatamente"— no se cumple**. Con `fix/person-identity-sync`, **en `main` desde el 2026-09-09**, **pasa a cumplirse, con una precisión que el documento tendrá que recoger**: se revoca para las autenticaciones nuevas, no para los tokens ya emitidos, que siguen valiendo hasta 300 s. Lo mismo con RF-52, que ahora propaga nombre y apellido a Keycloak.

`Documentation/` no es de este wiki y **no se ha tocado**. Queda anotado aquí para quien vaya a escribir allí.

## Notas relacionadas

[[checklist-reutilizacion]] · [[decisiones-tecnicas-malphasos]] · [[deuda-tecnica-y-riesgos]] · [[dominio-orden-trabajo]] · [[modelo-de-permisos]] · [[sincronizacion-con-proveedor-de-identidad]] · [[dominio-equipo-mantenimiento]] · [[integracion-keycloak-frontend]] · [[arquitectura-frontend]] · [[alcance-malphasos]]
