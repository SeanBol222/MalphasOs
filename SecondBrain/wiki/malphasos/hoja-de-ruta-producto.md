---
name: hoja-de-ruta-producto
description: Que falta por construir en MalphasOS -backend y frontend- ordenado por dependencias reales y no por numeracion de requisitos
tags: [malphasos, planificacion, hoja-de-ruta, "describe:malphasos"]
source: Documentation/IEEE830/IEEE830.tex apartado 3.2 y Documentation/wiki/ (28 notas), contrastados contra malphasos/
estado: estable
updated: 2026-09-12
---

# Hoja de ruta del producto

**Qué es esta nota**: lo que falta por construir de MalphasOS, backend y frontend, **ordenado por dependencias**. No es una lista de requisitos —esa la tiene la matriz de trazabilidad— sino el orden en que se pueden abordar sin quedarse a medias.

**Qué no es**: el registro de la migración desde `bolivarbioingenieria-app`. Eso es [[checklist-reutilizacion]], que se queda como está. **Dos listas de tareas se desincronizan**; aquélla mira hacia atrás y ésta hacia adelante.

## El punto de partida, verificado

| | Total | Implementado | Fuente |
|---|---|---|---|
| Requisitos funcionales | 31 | **8** | ERS 3.2, verificada dos veces contra el código |
| Requisitos no funcionales | 23 | **1** (RNF-23, JWT) | Matriz de trazabilidad |
| Frontend | — | **Nada.** No existe el directorio | Comprobado sobre el árbol del repositorio |

Los 8 implementados son **RF-08** (crear cliente), **RF-22** y **RF-24** (hoja de vida: crear, modificar/eliminar) y **RF-49 a RF-53** (login, identificación de rol, alta, edición y baja de usuarios). Todo lo demás está `[PREVISTO]`.

Del backend queda además, fuera de la numeración de la ERS, **la segunda tanda de `equipment`**: verificaciones técnicas y datos metrológicos.

Detalle por categoría en `Documentation/wiki/requisitos/estado-de-implementacion.md`.

## Decisiones ya tomadas por el usuario

- **La tanda de seguridad está hecha** (`fix/person-identity-sync`, **en `main` desde `258cd81`**; esta nota la daba por sin mergear, **corregido el 2026-09-09**): dar de baja a una persona ya deshabilita su cuenta de Keycloak. Ver [[sincronizacion-con-proveedor-de-identidad]].
- **El siguiente bloque es órdenes de trabajo.**

Lo que sigue no vuelve a decidir eso; explica por qué el orden aguanta y qué arrastra cada pieza.

## El grafo de dependencias, y las dos versiones que existen de él

Hay que distinguir **dos grafos que no coinciden**, y confundirlos es el error fácil:

1. **El declarado**, que la ERS escribe requisito por requisito en su apartado "Dependencias".
2. **El real**, el de qué código no puede escribirse antes que cuál.

Donde discrepan, manda el segundo — pero el primero explica por qué alguien esperaría otra cosa.

### Órdenes de trabajo: no depende de nada que no esté construido

RF-01 a RF-07. **Todo lo que necesita existe**: `cliente`, `sede`, `area_servicio` y `equipo_cliente` son tablas reales, y las dos consultas que el dominio pide ya están publicadas —`GET /v1/api/headquarters/{idSede}/service-areas` y `GET /v1/api/service-areas/{idAreaServicio}/equipments`—. Sus dependencias declaradas hacia fuera de la categoría son RF-08, RF-49 y RF-50: **las tres implementadas**.

Lo que sí hay que crear con ella: **tipo de servicio y periodicidad no tienen columna en ningún sitio** (RF-03). No es una tabla más de la orden, es vocabulario nuevo del dominio.

> **Corregido el 2026-09-12**: la frase anterior era cierta hasta esa fecha y **dejó de serlo** con `V6__work_order.sql`, en la rama `feat/work-order-schema`. Las dos columnas existen ya, con `CHECK` propio: `n_periodicidad` y `t_tipo_servicio`. Y **el vocabulario no era tan nuevo como esta nota suponía**: las periodicidades y los estados estaban escritos en un `CHECK` del esquema heredado; solo los tipos de servicio salieron de la ERS. Ver [[dominio-orden-trabajo]].

⚠️ **El grafo declarado contiene un ciclo, y está dentro de esta categoría.** RF-05 depende de RF-07; RF-07 de RF-06; RF-06 de RF-05 —y además de RF-09, que es de reportes y a su vez depende de RF-05 y RF-07—. Leído al pie de la letra, ninguno de los cuatro puede empezar. **Es un defecto de la ERS, no un bloqueo real**: identificar la orden, elegir sus áreas y elegir sus equipos son partes de un mismo agregado y se construyen juntas. Se anota aquí porque quien planifique leyendo solo el documento se detendrá en seco.

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
| 5 | **Frontend** | Firma digital, seis RNF, los manuales de usuario | Ninguna técnica. **No es una decisión de esta nota**, ver abajo |
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

**Un backend completo con cero frontend cumple 0 de esos requisitos y, leyendo la ERS, no es el producto que se prometió.** La decisión de cuándo entra —después del bloque 2, en paralelo desde ya, o al final— es del usuario y no de un grafo de dependencias, porque técnicamente **no está bloqueado por nada**: puede empezar hoy contra los cuatro módulos que ya publican API.

Lo que sí conviene tener presente al decidirlo: el arranque no parte de cero. `Frontend/src/auth/` del proyecto original —`keycloak.ts`, `AuthProvider`, `PrivateRoute`, `apiFetch`— es un starter completo y portable. Ver [[integracion-keycloak-frontend]] y [[arquitectura-frontend]].

## Un efecto lateral que ya venció: la ERS quedó desactualizada

RF-53 está marcado `[IMPLEMENTADO]` con la salvedad escrita de que **su tercer criterio de aceptación —"el acceso se revoca inmediatamente"— no se cumple**. Con `fix/person-identity-sync`, **en `main` desde el 2026-09-09**, **pasa a cumplirse, con una precisión que el documento tendrá que recoger**: se revoca para las autenticaciones nuevas, no para los tokens ya emitidos, que siguen valiendo hasta 300 s. Lo mismo con RF-52, que ahora propaga nombre y apellido a Keycloak.

`Documentation/` no es de este wiki y **no se ha tocado**. Queda anotado aquí para quien vaya a escribir allí.

## Notas relacionadas

[[checklist-reutilizacion]] · [[decisiones-tecnicas-malphasos]] · [[deuda-tecnica-y-riesgos]] · [[dominio-orden-trabajo]] · [[modelo-de-permisos]] · [[sincronizacion-con-proveedor-de-identidad]] · [[dominio-equipo-mantenimiento]] · [[integracion-keycloak-frontend]] · [[arquitectura-frontend]] · [[alcance-malphasos]]
