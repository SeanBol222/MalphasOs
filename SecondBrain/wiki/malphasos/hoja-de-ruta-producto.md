---
name: hoja-de-ruta-producto
description: Que falta por construir en MalphasOS -backend y frontend- ordenado por dependencias reales y no por numeracion de requisitos
tags: [malphasos, planificacion, hoja-de-ruta, "describe:malphasos"]
source: Documentation/IEEE830/IEEE830.tex apartado 3.2 y Documentation/wiki/ (28 notas), contrastados contra malphasos/
estado: estable
updated: 2026-10-31
---

# Hoja de ruta del producto

**Qué es esta nota**: lo que falta por construir de MalphasOS, backend y frontend, **ordenado por dependencias**. No es una lista de requisitos —esa la tiene la matriz de trazabilidad— sino el orden en que se pueden abordar sin quedarse a medias.

**Qué no es**: el registro de la migración desde `bolivarbioingenieria-app`. Eso es [[checklist-reutilizacion]], que se queda como está. **Dos listas de tareas se desincronizan**; aquélla mira hacia atrás y ésta hacia adelante.

## El punto de partida, verificado

| | Total | Implementado | Fuente |
|---|---|---|---|
| Requisitos funcionales | 31 | **21** | ERS 3.2, verificada dos veces contra el código |
| Requisitos no funcionales | 23 | **1** (RNF-23, JWT) | Matriz de trazabilidad |
| Frontend | — | **Sesión, autenticación, clientes, equipos con su catálogo, órdenes de trabajo, reportes de servicio por tipo de servicio y personas**, todo de extremo a extremo, más **el reporte como documento imprimible**, con **689** pruebas | Medido el 2026-10-16 sobre `3ddfc3a1` (decía **630** sobre `da7d7ded`, unas horas antes) |

> **Corregido el 2026-10-16.** Esta fila decía **409** pruebas «medidas el 2026-10-03»: llevaba trece
> días caducada mientras `CONVENCIONES.md` subía de 409 a 572. Es la misma lección de siempre —un conteo
> se recalcula al citarlo— y la misma forma de fallar: el número vivía en dos sitios y solo se cuidaba
> uno.

> **Corregido el 2026-09-26, dos veces el mismo día.** Esta fila decía «**Nada.** No existe el directorio»: cierto hasta el 2026-09-13. Hoy existen el armazón, el sistema de diseño con su prueba de contraste, la autenticación contra Keycloak, **nueve pantallas** de clientes —ficha, edición, retiro, contactos, sedes, áreas de servicio y encargados— y **seis más** del catálogo de equipos con el registro de un equipo en un área, con **262** pruebas. **Con eso existe ya todo lo que una orden de trabajo necesita tocar**: sedes, áreas y equipos por área. Lo que sigue faltando son las pantallas de órdenes de trabajo, que es lo que cierra los cuatro RF de formulario.

> **Actualizado el 2026-10-10: son 21.** Entran **RF-13** y **RF-14** con los protocolos —el catálogo en `V21` y la copia en cada reporte preventivo en `V22`—. RF-14 cuenta con una interpretación escrita en `Documentation/wiki`: solo el preventivo lleva protocolo, por decisión del usuario.

> **Actualizado el 2026-10-04: son 19.** Entran **RF-26** y **RF-27** con la hoja de vida, y **RF-22** y **RF-24** dejan de estar en duda: la revisión de ese mismo día los había marcado «en revisión» porque la sección de servicio técnico faltaba entera y no había *un* documento sino cinco recursos. Las dos cosas se construyeron. Ver [[hoja-de-vida]].

Los 19 implementados son **RF-08** (crear cliente), **RF-22, RF-24, RF-26 y RF-27** (hoja de vida: el documento, su lectura, el registro automático y el historial), **RF-49 a RF-53** (login, identificación de rol, alta, edición y baja de usuarios), **RF-01 a RF-03, RF-05, RF-06 y RF-07** de órdenes de trabajo, y **RF-09, RF-11 y RF-15** de reportes de mantenimiento. **RF-04 queda marcado como desviación**, no como implementado. Todo lo demás está `[PREVISTO]`.

> **Actualizado el 2026-09-28**: eran **16**. Entra **RF-11** con el frontend de los reportes — la ficha muestra ya cliente, sede, responsables y tipo de servicio **leídos de la orden**, sin un solo control que editar, que es lo que el requisito pide y lo que RNF-07 exige de lo autocompletado. Esta nota lo daba por pendiente el día anterior, con razón: el dato existía y la pantalla no.

> **Actualizado el 2026-09-27, segunda vez ese día**: eran **14**. Entran RF-09 —un reporte por equipo de la orden, asociado a los dos y accesible desde ella— y RF-15 —los cinco campos de información técnica, guardados por separado y consultables después—, con el módulo de reportes de servicio completo en sus cuatro tandas. **RF-11 no entra**, y merece decirse por qué: el backend garantiza que cliente, sede, responsables y tipo de servicio **no se pueden teclear** —no existe columna ni campo para ellos— y que son consistentes, porque hay una sola fuente; pero el requisito dice que «los campos del reporte **muestran** automáticamente» esos datos, y mostrar es una pantalla. Mismo criterio que dejó cuatro requisitos de la orden de trabajo esperando su formulario.

> **Actualizado el 2026-09-27**: eran **11**. Los tres que entran —RF-03, RF-06 y RF-07— son de los cuatro que describían un formulario, construido ese día. El cuarto, RF-04, **se desvía a propósito** y por eso no cuenta: el criterio de esta nota es estricto, y darlo por hecho sería justo lo que esta tabla existe para evitar.

> **Actualizado el 2026-09-13**: el marcador estaba en **8** y pasa a **11** con el módulo de órdenes de trabajo, completo en sus cuatro tandas. **No son siete de golpe, y el porqué importa** — ver el desglose justo debajo.

### Por qué el módulo completo no cierra sus siete requisitos

El backend de órdenes de trabajo está terminado, y aun así **solo tres de los siete RF cuentan como implementados**. La razón es que **cuatro de ellos describen un formulario**, no una capacidad del sistema, y no hay frontend.

| RF | Qué pide | Estado | Por qué |
|---|---|---|---|
| RF-01 | Crear orden directa, sin solicitud previa | ✅ | `POST /v1/api/work-orders` |
| RF-02 | Eliminar la dependencia de solicitud | ✅ | Satisfecho **por construcción**: la palabra «solicitud» no existe en el esquema ni en el código, no había nada que retirar |
| RF-03 | Formulario con cliente, sede, áreas, tipo y periodicidad | ✅ | **Construido el 2026-09-27**, en dos pasos: se programa la visita —cliente, sede, fecha, tipo y periodicidad— y los equipos se añaden después. Es lo que el API ofrece y lo que ocurre de verdad: se acuerda la visita y luego se decide sobre qué se trabaja |
| RF-04 | Selección múltiple de **áreas** de la sede | ⚠️ | **Desviación consciente, 2026-09-27.** La pantalla no pide elegir áreas y después mostrar sus equipos: muestra **todas las áreas abiertas de la sede a la vez**, agrupadas, y se marcan equipos dentro —con «marcar toda el área» como equivalente de elegir una—. Se llega al mismo sitio con un paso menos, pero **el paso intermedio que el requisito describe no existe**. Queda así para poder discutirlo; el dato no cambia, porque las áreas elegidas nunca se persistieron a propósito. Ver [[dominio-orden-trabajo]] |
| RF-05 | Identificador único (UUID) de la orden | ✅ | `k_id_orden_trabajo uuid` |
| RF-06 | Visualización de equipos por área seleccionada | ✅ | **2026-09-27**: la pantalla de alcance agrupa por área y ofrece **solo** equipos de áreas de la sede de la orden, que es la regla que el backend construyó el 2026-09-13 y que hasta ahora no se ejercía desde un navegador |
| RF-07 | Selección múltiple de equipos | ✅ | **2026-09-27**: casillas por equipo y «marcar toda el área». El API suma **de uno en uno**, así que la pantalla manda las llamadas en serie y **dice cuántas entraron** si alguna falla: no hay transacción que las envuelva |

**El criterio que se ha aplicado, para que se pueda discutir**: cuenta como implementado el requisito que el backend satisface por completo. El que describe una pantalla se queda abierto aunque el dato que necesita ya exista, porque darlo por hecho inflaría el marcador y haría que el trabajo de frontend desapareciera de la cuenta sin haberse hecho.

Es el mismo criterio que dejaba **RF-22 y RF-24 como implementados y RF-26 no**. (Desde el 2026-10-04 los cuatro de la hoja de vida cuentan, y con el criterio aplicado de verdad: el documento existe con sus cuatro secciones, el historial se escribe solo y se lee ordenado.)

Del backend queda además, fuera de la numeración de la ERS, **la segunda tanda de `equipment`**: verificaciones técnicas y datos metrológicos.

Detalle por categoría en `Documentation/wiki/requisitos/estado-de-implementacion.md`.

## Decisiones ya tomadas por el usuario

- **La tanda de seguridad está hecha** (`fix/person-identity-sync`, **en `main` desde `258cd81`**; esta nota la daba por sin mergear, **corregido el 2026-09-09**): dar de baja a una persona ya deshabilita su cuenta de Keycloak. Ver [[sincronizacion-con-proveedor-de-identidad]].
- **El siguiente bloque era órdenes de trabajo**, y **está construido**: las cuatro tandas, del esquema al REST, entre el 2026-09-12 y el 2026-09-13. Ver [[dominio-orden-trabajo]].
- **Lo siguiente decidido es el frontend**, el 2026-09-13, con su declaración de diseño escrita y su manual de marca. Arranca por una **rebanada vertical**: arranque de la aplicación, autenticación y el flujo de órdenes de trabajo. Ver [[arquitectura-frontend-malphasos]].
- ~~**Reportes de servicio** queda como el siguiente bloque de backend.~~ **Construido el 2026-09-27**, en cuatro tandas y cuatro merges. Y la frase con que se justificaba —«el módulo de órdenes ya emite los siete eventos que un reporte querría escuchar»— **resultó no ser el motivo**: el reporte no escucha ninguno de esos eventos, los consulta por el identificador de la orden. Los siete siguen sin consumidor. Ver [[dominio-reporte-servicio]].
- ~~**Lo siguiente decidido, y es de frontend**: las pantallas de reportes.~~ **Construidas el 2026-09-28**, en cuatro tandas, y con ellas cerró RF-11. El hueco entre backend y pantalla duró **un día**, contra las dos semanas de las órdenes de trabajo.
- **El módulo de personas tiene pantallas desde el 2026-10-02**, y conviene decir qué cambia y qué no: **el marcador no sube** —RF-51 a RF-53 ya contaban por el backend— y lo que cierra es que **no había forma de dar de alta a nadie sin `curl`**. Es el caso inverso al de las órdenes de trabajo: allí el frontend subió la cuenta, aquí solo hizo usable lo que ya contaba. El criterio estricto de esta nota da los dos resultados sin contradecirse.
- **Keycloak lleva la marca desde el 2026-10-02**: el login lo sirve Keycloakify y la consola un tema clásico. No cierra ningún requisito —RF-49 ya estaba— y era un encargo explícito del usuario. **Lo visual se terminó el 2026-10-04**: el login con el diseño del manual y en español, y la consola rehecha porque con el sistema en modo oscuro no se leía. (Esta línea daba la marca por puesta desde el 2-10; el login tenía todavía el aspecto de Keycloakify.) Ver [[tema-de-keycloak]].
- **El alta de un equipo elige tipo, marca y modelo**, en ese orden, desde el 2026-10-04, y **el frontend corre en su propio contenedor**. Ninguno de los dos mueve el marcador. Ver [[dominio-equipo-mantenimiento]] y [[dockerfile-y-contenedores]].
- ~~**La hoja de vida**~~ **construida el 2026-10-04**, en cuatro tandas y dos migraciones, y **no como entidad**: es un compilado de solo lectura con las cuatro secciones de RF-22, y solo su historial tiene tabla. Esta línea decía «la hoja de vida como entidad»; la aclaración del usuario fue justo que no lo es. Ver [[hoja-de-vida]].
- **La hoja de vida impresa**, decidida el **2026-10-05**: el usuario aprobó el diseño —el «tablero con escudo», con la marca de Bolívar— y pidió el plan para producirlo. Está en [[hoja-de-vida-formato-impreso]], en siete tandas, y **cruza con los dos caminos de abajo**: su sección de protocolo espera a RF-14, y si hace falta un PDF generado en el servidor, es la misma decisión que RF-17. No mueve el marcador: RF-22 ya cuenta.
- **Lo siguiente sin decidir todavía**: o los **protocolos** (RF-14, camino libre, cierra RF-13 detrás) o la **firma digital** (RF-18 y RF-21, que abre el PDF de RF-17). Las dos están desbloqueadas y ninguna depende de la otra.

Lo que sigue no vuelve a decidir eso; explica por qué el orden aguanta y qué arrastra cada pieza.

## ⚠️ Lo pedido para la sesión siguiente (2026-10-02)

**Una revisión completa y exhaustiva de todo lo desarrollado hasta el momento.** Lo pidió el usuario al cerrar la sesión del 2026-10-02, y no es «seguir construyendo»: es contrastar lo construido contra lo escrito. Por dónde empezar, con lo que este proyecto ya sabe de sí mismo:

1. **Los números se recalculan, no se citan de memoria.** Cada cifra de este wiki que no se haya medido ese día es sospechosa: ya caducaron el hash de `main` cuatro veces, el conteo de deuda dos y la cifra de requisitos de `Documentation/` estuvo **tres semanas** en 8 cuando eran 14.
2. **Los defectos aparecen al comparar**, no al leer. Las parejas que más han dado: el wiki contra el código, la ERS contra el código, un módulo contra otro.
3. **Verde no es verificado.** Las mutaciones de esta sesión destaparon **cuatro** pruebas que pasaban sin ejercer nada; conviene asumir que quedan más.
4. **Lo que falta mirar con lupa**: `person` conserva tres `PUT` y las credenciales del realm están publicadas. (Aquí figuraba también «el filtrado por dueño no existe»: **construido el 2026-10-04**, ver [[filtrado-por-dueno]]. Era el único de los tres con implicación de seguridad real.)

> **Corregido el 2026-10-03.** Este punto decía además que «`equipment` sigue sin pruebas de
> persistencia», y **era falso**: `EquipmentCatalogPersistenceTest` existe desde el 2026-09-26 con
> nueve casos —hoy doce— y `ClientEquipmentRelocationPersistenceTest` cubre el traslado desde el
> 2026-09-10. La afirmación venía de la fila de deuda de `work-order`, que al cerrarse dijo «`equipment`
> sigue sin las suyas» y nadie volvió a comprobarlo. **Es el ejemplo exacto del punto 2 de esta misma
> lista**: el defecto apareció al comparar el wiki con el código, y lo encontró la propia revisión que
> este apartado pedía.

### Lo que la revisión ha encontrado hasta ahora (2026-10-03)

La primera corrección no fue de código sino **de modelo**, y la trajo el usuario con un contraejemplo:
un termohigrómetro mide dos cosas y el catálogo solo sabía expresar una. De ahí salió `V10` y, con
ella, seis defectos que ninguna prueba estaba viendo —dos de ellos **latentes desde septiembre**—. Están
en [[deuda-tecnica-y-riesgos]] y el resumen honesto es que **el punto 3 de arriba se confirmó**: el
verde no probaba lo que parecía probar.

Y un dato sobre el punto 1, porque es peor de lo que decía: el conteo de deuda propia **ya estaba mal
el día que se escribió esta lista**. Declaraba 46 filas «contadas una a una el 2026-10-02» y el archivo
tenía 51.

### Lo que la pasada de mutaciones encontró (2026-10-04)

**Tres hallazgos, y los tres son del tipo que el punto 3 de arriba anticipaba.** Se desactivaron reglas
una a una para ver si alguna prueba se enteraba:

**Once mutaciones, los seis módulos.** Dos de cada seis pasaron sin que nada se enterara:

| Módulo | Mutación | Resultado |
|---|---|---|
| `equipment` | La unidad deja de tener que ser de su magnitud | **842 en verde** — `EquipmentTypeService` no tenía ninguna prueba |
| `report` | El cierre deja de separar las lecturas por verificación | **en verde** — ninguna prueba de cierre tenía dos verificaciones |
| `client` | Encargar a alguien de un **área** cerrada | **en verde** — la guarda gemela, la de la sede, sí estaba probada |
| `person` | La cuarta alta admite cualquier tipo, no solo `MANAGER` | **en verde** — `PersonServiceTest` no llamaba a `save` |
| `report` | La unidad de una lectura se inventa | cazada |
| `report` | El punto de otra verificación se acepta | cazada |
| `report` | Una verificación retirada pasa a valer | cazada |
| `work-order` | Un equipo de **otra sede** del mismo cliente entra en la orden | cazada |
| `work-order` | Un equipo de **otro cliente** entra en la orden | cazada |
| `work-order` | Una orden en la sede de otro cliente | cazada |
| `client` | Abrir un área en una sede cerrada | cazada |
| `location` | Una ciudad en un país que no existe | cazada |

**Segunda vuelta, 2026-10-04: los agregados y el frontend**, que la primera no había tocado. Diez
mutaciones más, **una superviviente**:

| Dónde | Mutación | Resultado |
|---|---|---|
| agregado | Quitar `onlyExplicitlyIncluded`: la igualdad pasa a ser por datos | **856 en verde** — solo 2 de 17 agregados lo tenían comprobado |
| agregado | `deactivate()` deja de ser idempotente | cazada |
| agregado | La colección deja de ser copia inmutable | cazada |
| agregado | Retirar una verificación deja sus puntos activos | cazada |
| frontend | Una magnitud ya usada se vuelve a ofrecer | cazada |
| frontend | La tabla de un reporte cerrado vuelve a teclearse | cazada |
| frontend | Una casilla a medias se manda al servidor | cazada |
| frontend | Las rutas pierden su guard de autoridad | cazada |
| frontend | El botón de cerrar se habilita sin lo que el servidor exige | cazada |
| frontend | El buscador acepta un valor inventado | cazada |

**El frontend aguantó las seis**, que es el resultado más limpio del barrido. Y la superviviente de los
agregados tenía un cómplice: la prueba que parecía cubrirla **se llamaba** «los seis agregados comparan
por identidad» y solo lo afirmaba de uno. **El nombre de una prueba es una afirmación sin verificar.**

**Las cuatro de la primera vuelta tienen la misma forma, y conviene nombrarla**: en los cuatro casos había
cobertura **alrededor** de la regla y ninguna **sobre** ella. Un servicio con tres clases vecinas que
parecían cubrirlo; una regla de cierre con siete pruebas de cierre que no usaban dos verificaciones;
una guarda con su gemela probada; y un método con veinte pruebas en su clase y ninguna que lo llamara.
**La cobertura vecina es lo que hace invisible el hueco.**

Y el método tiene una trampa que conviene registrar: **la primera prueba escrita para la segunda
mutación no la cazó**. Usaba dos magnitudes constantes, donde el identificador del punto ya discrimina,
de modo que el filtro no cambiaba el resultado. **Una prueba del caso correcto por el camino equivocado
se lee igual que una buena**; lo único que las distingue es ver fallar la mutación.

**Y un hallazgo que no vino de mutar sino de comparar el wiki contra la base**: la nota del esquema
escrita el día anterior tenía **doce nombres de columna inventados** y una llave primaria que no existe.
Está en [[deuda-tecnica-y-riesgos]], y lo que deja es un guion en `SecondBrain/herramientas/`.

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
│   ├── historial de la hoja de vida (RF-26, RF-27)   <- HECHO el 2026-10-04
│   └── exportar a PDF (RF-17) ── depende ademas de la FIRMA (RF-21)
├── firma digital (RF-18, RF-21) ── y del frontend tactil
├── modulo comercial: generar OT desde cotizacion (RF-47)
└── inventario: entrada/salida durante el mantenimiento (RF-37)
```

Precisiones que el diagrama comprime:

- **El historial de la hoja de vida cuelga del reporte, no de la orden.** RF-26 depende de RF-09 y RF-15, los dos de reportes. La orden de trabajo dice *qué se va a hacer*; el reporte dice *qué se hizo*, y es eso lo que se anota en la hoja de vida. El mecanismo de soporte —el despachador de eventos de dominio— **ya está construido y en uso**; falta el evento del reporte, no el despacho.

  > **Actualizado el 2026-09-27**: ese evento **ya existe**. `service-report.finished` viaja con el equipo, el resultado y la fecha de cierre, que es exactamente lo que la hoja de vida anota. Lo que falta ahora es el **consumidor**, no el evento.
  >
  > **Y el 2026-10-04 el consumidor existe**, y es el primero del sistema: de 51 eventos declarados ninguno tenía quien lo escuchara. Va en `report` y no en el directorio que la ERS reservó en `equipment`, porque ahí habría creado un ciclo entre los dos módulos. Y el evento **no trae el tipo de servicio**, que es de la orden: el oyente lo consulta.
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

### Protocolos (RF-13 y RF-14): lo investigado el 2026-10-05, antes de diseñar

El usuario eligió los protocolos como lo siguiente tras la hoja de vida, y la sesión se detuvo **antes
de diseñar nada**, porque pidió cambiar de tema —rehacer el estilo visual del frontend—. Queda aquí lo
que se reunió, para no tener que buscarlo otra vez:

- **Lo que dice la ERS** (`IEEE830.tex`, §3.2.3). **RF-14**: «la configuración previa de protocolos
  asociados a cada tipo de equipo y tipo de servicio»; un módulo para crearlos y editarlos, **cada
  protocolo se asocia a uno o más tipos de equipo y a un tipo de servicio**, y quedan disponibles para la
  asignación automática. Depende de RF-22. **RF-13**: al generar el reporte, el protocolo correcto se
  carga solo, según el tipo de equipo **y** el tipo de servicio, sin selección manual. Depende de RF-09
  y RF-14. El texto de estado de RF-13 dice que el tipo de servicio no está modelado: **falso desde el
  2026-09-12**, existe desde `V6` en `orden_trabajo.t_tipo_servicio`.
- **Lo que tenía el original** (`DataBase/v4/initdb/A_Sigma_DB_V4.sql`): una tabla
  `protocolo_mantenimiento` con un booleano `b_ejecucion_protocolo` —«un check si el protocolo fue
  ejecutado sobre el equipo»— y una foránea al reporte. **Sin pasos y sin relación con el tipo de
  equipo**: era una casilla por reporte, no un protocolo.
- **Lo que usa la empresa en papel** (la hoja de vida BB-ING-HV-30 de 2019): el protocolo de
  mantenimiento preventivo de una balanza es **una lista ordenada de pasos** —«Limpieza y desinfección
  inicial», «Verificación del estado físico y operativo», «Verificación, ajuste y limpieza del sistema
  electrónico»…—. Es el contenido que la sección «Protocolo preventivo» de la hoja de vida impresa
  espera, y que decía «Sin protocolo registrado» hasta el **2026-10-11**: desde `83eec61a` lleva los
  protocolos del tipo, en su prioridad.

**Las preguntas que hay que hacerle al usuario antes de construir**, porque cambian el esquema:

1. ¿Un protocolo es solo una **lista ordenada de pasos** en texto, o cada paso lleva algo más —un
   resultado, un valor medido—?
2. ¿En el reporte, el técnico **marca cada paso como hecho**, o el protocolo solo se muestra como guía?
3. ¿Puede haber **dos protocolos para el mismo par** (tipo de equipo, tipo de servicio)? RF-13 exige
   elegir uno solo y automáticamente, lo que pide que el par sea único entre los vigentes.
4. ¿El reporte **congela** el protocolo con el que se hizo —como la intervención congela la fecha y el
   resultado— para que editar el protocolo después no cambie reportes viejos? Es lo que el proyecto ha
   hecho siempre con lo histórico ([[congelar-una-referencia-historica]]).
5. ¿Vive en `equipment`, junto al tipo de equipo, o en un módulo propio? El tipo de servicio es de
   `work-order`, pero `equipment` ya tiene su propia copia del vocabulario (`InterventionType`).

**Dos respuestas llegaron el 2026-10-06, por el diseño y no por la pregunta.** Al ver la maqueta del
llenado del reporte, el usuario pidió que «lo que se hizo» fuera **una lista de verificación** en vez de
un texto, y aprobó esta forma: el protocolo llega cargado según el tipo de equipo y el de servicio, cada
paso tiene título y una línea de qué hacer, y en el reporte **cada paso se marca como hecho o como «no
aplica»**, con una nota opcional; lo hecho fuera del protocolo va en un campo de «trabajo adicional», y
el reporte **no se cierra con pasos sin marcar**. Eso contesta la 1 —una lista ordenada de pasos de
texto— y la 2 —se marcan—. **Siguen abiertas la 3, la 4 y la 5.** Y una corrección de vocabulario que
conviene no perder: el usuario habló de que «el procedimiento se guarda como protocolo», y **no es así
hoy**: `reporte_servicio.t_procedimientos` es un texto libre y los protocolos no existen. La lista de
verificación es el diseño de RF-14 y RF-13, no un cambio de pantalla.

Con RF-14 construido, la hoja de vida impresa llenaría su sección de protocolo preventivo con el del
tipo del equipo, y RF-13 cargaría el protocolo al abrir el reporte.

#### Decidido el 2026-10-10: un protocolo es una actividad reutilizable, no la lista de un tipo

Al preguntarle por la unicidad, el usuario **cambió el modelo**, y para mejor: «Limpieza y desinfección»
se repite en muchos tipos de equipo, así que **un protocolo es cada actividad**, vive una vez en un
catálogo y **se asigna** a los tipos que la necesitan. La pregunta 3 —«¿dos protocolos para el mismo
par?»— deja de tener sentido tal como estaba escrita: un tipo tiene **varios** protocolos asignados, en
orden, y esa lista es la que se carga sola.

| Pregunta | Respuesta |
|---|---|
| Qué es un protocolo | Una actividad del catálogo: **nombre** y **una línea de qué hacer** |
| Cómo llega a un tipo | **Se asigna con una prioridad** —1, 2, 3…— que guarda la tabla de unión y que **decide el usuario, nunca el programa**. Las prioridades de un tipo van **seguidas, sin huecos ni repetidas**: al mover o quitar uno se renumeran. Al asignarlo se le puede poner **una indicación propia** («alcohol isopropílico, no al 70 %»); la descripción general es la misma para todos |
| Cómo se asigna en pantalla | Al **crear o editar el tipo de equipo**: una lista de los protocolos existentes para elegir, y los elegidos en una **lista que se arrastra** para cambiar su prioridad —con botones de subir y bajar además, porque arrastrar no se puede con el teclado—. Si el protocolo no existe, se crea en un **panel aparte dentro del tipo que avisa de los parecidos** mientras se escribe el nombre —sin tildes ni mayúsculas— y ofrece usar uno de ellos; el servidor rechaza además dos nombres iguales. Pedido así para que **no sea fácil repetir protocolos** |
| Para qué servicio | **Solo preventivo**. Un correctivo es una reparación distinta cada vez y se describe con texto libre |
| Qué hace el reporte | **Copia** los pasos al abrirse —nombre, descripción, indicación, orden— y en la copia se marca cada uno **hecho** o **no aplica**, con nota. Editar el protocolo después no cambia reportes viejos ([[congelar-una-referencia-historica]]) |
| Qué pasa con «Procedimientos» | Pasa a llamarse **«Trabajo adicional»**. Con pasos, cerrar exige **todos marcados** y el texto es opcional; sin pasos —un correctivo, o un tipo sin protocolos— el texto sigue siendo obligatorio, como hoy |
| Dónde vive | En `equipment`, junto al tipo de equipo, con `equipment.write` y sin autoridad nueva. Lo decidió la sesión; el usuario no lo objetó |

**El diseño que sale de ahí, para construir en cuatro tandas:**

1. **Hecha el 2026-10-10**, `371d0908`. **Catálogo** (`V21`): `protocolo` —nombre único entre los vigentes sin distinguir mayúsculas ni
   tildes, descripción, borrado lógico— y `protocolo_tipo_equipo` —tipo, protocolo, `i_prioridad` única
   por tipo, indicación—. Dominio, servicio, persistencia y REST en `equipment`. **La asignación se manda
   entera, como lista ordenada** —un `PATCH` con los protocolos y sus indicaciones—, y la prioridad la
   deriva el servidor de la posición: así no puede llegar un hueco ni una repetida, que es la regla.
2. **Hecha el 2026-10-10**, `ef2e121c`. **El reporte copia los pasos** (`V22`): `paso_reporte` con lo copiado y el estado de cada paso. Se
   copian al abrir un reporte de una orden **preventiva**; la regla de cierre cambia como dice la tabla.
3. **Hecha el 2026-10-11**, `71d2277e`. **Pantallas del catálogo**: Catálogo › Protocolos, y en el alta y la edición del tipo de equipo la
   lista que se arrastra, con el panel que crea y avisa de parecidos.
4. **Hecha el 2026-10-11**, `c94b0f77` y `83eec61a`. **La lista de verificación del reporte** —la de la
   maqueta—: cada paso se marca hecho o no aplica, con nota obligatoria en el segundo, y el panel de cierre
   cuenta los pasos. Y **la hoja de vida** llenando su sección «Protocolo preventivo» —decía «Sin protocolo
   registrado» para todos— con los protocolos del tipo, numerados por su prioridad y con su indicación.
   **Es el protocolo de hoy**, no el de cada servicio: lo que se hizo lo copió el reporte y llega por el
   historial.

> **Sigue siendo cierto el 2026-09-27, y ahora es lo único que le falta a la categoría.** El módulo de reportes se construyó sin protocolos a propósito: un protocolo es *qué hay que revisar*, y el reporte funciona sin él —se escriben los procedimientos a mano—. Con RF-14 construido, RF-13 sería cargarlos solos al abrir el reporte. Su otra mitad, **el tipo de servicio, sí existe ya** desde `V6`: la nota de requisitos que decía que no estaba modelado caducó el 2026-09-12.

## El rediseño visual: la «sala de control» (2026-10-06 y 2026-10-10)

El 2026-10-06 el usuario pidió rehacer el aspecto del frontend —«no me gusta como se ve»—. Se le
propusieron **tres direcciones** en un lienzo de diseño, las tres dentro del manual de marca: **A, sala de
control** —barra lateral en tinta, datos densos—, **B, editorial** —retícula suiza, titulares grandes,
reglas de tinta— y **C, instrumental** —paneles blancos, etiquetas condensadas, búsqueda global y una
agenda semanal—. **Eligió la A**, y pidió dos piezas más sobre ella: el llenado del reporte de servicio y
un Inicio **con agenda** como la de la C, que prefirió al Inicio sin ella.

El lienzo es un artefacto privado de claude.ai, `MalphasOS — rediseño visual`
(`https://claude.ai/artifact/1TYREfJ9XHVeL8biAgKVie`); las pantallas de la A son `Main`, `A-Equipos`,
`A-Orden`, `A-Movil`, `A-Reporte` y `A-Agenda`.

**El plan, en tandas, una rama cada una:**

| # | Tanda | Necesita backend | Estado |
|---|---|---|---|
| 1 | **Armazón**: barra lateral en tinta, menú en dos bloques, sesión y cerrar sesión al pie, menú plegable en un teléfono | No | **Hecha el 2026-10-10**, `a5161573` |
| 2 | **Estilo común**: encabezados de página, tablas, botones y superficies blancas, aplicado a listados y fichas | No | **Hecha el 2026-10-10**, `1227feaf` |
| 3 | **Inicio con agenda**, por semana y por mes, con «solo mis órdenes» | Sí: la consulta por periodo, `2abeebfa` | **Hecha el 2026-10-10**, `4cee773b` |
| 4 | **El llenado del reporte** con secciones numeradas y el panel de lo que falta para cerrar | No | **Hecha el 2026-10-10**, `62b3663b` |
| 5 | **Protocolos (RF-14) y la lista de verificación del reporte (RF-13)** | Sí: `V21`, `V22` y `V23`, ya construidos | **Hecha el 2026-10-11**: catálogo, copia en el reporte, pantallas del catálogo, lista de verificación y sección de la hoja de vida (`371d0908`, `ef2e121c`, `71d2277e`, `c94b0f77`, `83eec61a`). **El rediseño queda completo en sus cinco tandas** |

**Una desviación de la maqueta, consciente**: en un teléfono la A dibujaba una barra de pestañas abajo, y
se construyó un **menú plegable** —la misma barra lateral abierta a pantalla completa—. Así hay una sola
navegación, y no dos que puedan desincronizarse. Queda anotado para discutirlo.

### Segunda fase: las demás pantallas (2026-10-11)

Con las cinco tandas hechas, todas las pantallas llevaban ya las clases comunes, pero solo Inicio y el
reporte tenían la forma de su maqueta. El usuario pidió rediseñar el resto, y decidió dos cosas: **lo
que no tiene maqueta se dibuja primero** y se aprueba antes de construirlo, y **el listado de equipos
amplía el backend** en vez de filtrar en el navegador.

| Pantalla | Maqueta | Necesita backend | Estado |
|---|---|---|---|
| **Equipos** | `A-Equipos`, aprobada el 2026-10-06 | Sí: `GET /client-equipments/overview`, `b253345f` | **Hecha el 2026-10-11**, `6394cf4b` |
| **Orden de trabajo** | `A-Orden`, aprobada el 2026-10-06 | Sí, poco: el listado de equipos admite `ids`, `a53540ea` | **Hecha el 2026-10-11**, `3fecdcab`. Sin las fechas de cada paso del estado, que la maqueta dibujaba y no se guardan en ninguna parte |
| **Clientes, ficha de cliente y sede** | `A-Clientes`, `A-Cliente` y `A-Sede`, dibujadas y aprobadas el 2026-10-11 | Sí: `GET /headquarters/summary` en `client` y `GET /client-equipments/summary` en `equipment`, `45ccb1a8`. Se suman en el frontend, porque `client` no conoce a `equipment` | **Hechas el 2026-10-11**, `6ead9ccc`. «Ciudad principal» pasó a «Ciudades», porque ningún dato dice cuál es la principal; sin el rango de hojas de vida ni el teléfono de los encargados |
| **Personas** | `A-Personas`, aprobada el 2026-10-11 | No: el cliente de cada encargado sale del resumen de sedes | **Hecha el 2026-10-11**, `62da8e9c`. La gente de la casa dice «De la casa» y no el nombre de la empresa, que no se escribe a mano en el frontend |
| **Catálogo** | `A-Catalogo`, aprobada el 2026-10-11 | Sí: `GET /protocols/by-type`, `c4247c8a` | **Hecha el 2026-10-11**, `811225b1`. **Con ella la segunda fase está completa** |


## La primera versión funcional: órdenes y reportes por tipo de servicio (decidida el 2026-10-14)

Con el rediseño completo, el usuario pidió cerrar el ciclo de la orden y **distinguir los reportes por
tipo de servicio**, para sacar la primera versión funcional. Hasta ahora el reporte era el mismo
formulario para los tres tipos: el preventivo añadía los pasos del protocolo y nada distinguía un
correctivo de una calibración. **Ni la ERS ni el original describen esos formatos**, así que todo lo que
sigue son decisiones del usuario del 2026-10-14.

**Decisiones del usuario:**

| Pregunta | Decisión |
|---|---|
| Qué registra un **correctivo** | Falla reportada, diagnóstico, causa, trabajo realizado y repuestos cambiados (texto) |
| Qué registra una **calibración** | El **equipo patrón** usado, cada lectura con su **error** contra el patrón y una **tolerancia** por verificación; el sistema dice conforme o no conforme |
| Cómo se registra el patrón | **Un catálogo de patrones** de la empresa —nombre, marca, serie, certificado y vencimiento—; el reporte elige uno y **copia lo que tenía ese día** |
| Cómo se expresa la tolerancia | **En la unidad** de la verificación: ±0,5 °C. Conforme si `|equipo − patrón| ≤ tolerancia` |
| A qué reportes aplica el patrón | **A todo reporte con lecturas**, no solo a la calibración: la ERS habla de «preventivo con calibración» |
| Patrón con el certificado vencido el día del servicio | **No deja cerrar** el reporte |
| Ciclo de la orden | **Ejecutar exige todos los reportes cerrados**, y **el listado de órdenes se rehace** |
| Fuera de esta versión | Reprogramar o corregir una orden, y la firma con su PDF (RF-18, RF-21, RF-17) |

**Decisiones de la sesión, para que el usuario pueda discutirlas:**

- En un correctivo, **la falla y el diagnóstico son obligatorios para cerrar**; causa y repuestos no.
- La tolerancia es **opcional en el catálogo**: sin ella la lectura se registra igual y la conformidad
  dice «sin tolerancia», porque los tipos ya creados no la tienen.
- La conformidad **no decide el resultado del servicio**: lo sigue eligiendo el ingeniero, y la pantalla
  avisa si marca «Operativo» con lecturas no conformes.
- Que la orden no se ejecute sin reportes cerrados la impone `work-order`, que **no puede depender de
  `report`** —es al revés—: el módulo de órdenes declara un puerto y `report` lo implementa.

**Las tandas — las seis construidas entre el 2026-10-14 y el 2026-10-16:**

| # | Tanda | Commits | Merge |
|---|---|---|---|
| 1 | **Ejecutar exige reportes cerrados** — backend y botón de la ficha | `dff120af` · `32673020` | `31fada11` |
| 2 | **El correctivo** — `V24`, su regla de cierre y su bloque en la pantalla | `bab66abe` · `becf1011` | `ab42378d` |
| 3 | **El catálogo de patrones** — `V25`, API y Catálogo › Patrones | `a15d7811` · `060d840c` | `b8ae22e3` |
| 4 | **La tolerancia** — `V26`, en cada verificación del tipo y en su panel | `608fdcb7` · `1446250d` | `f73563e7` |
| 5 | **Patrón, error y conformidad en el reporte** — `V27` | `b9bc6517` · `ed3c8c9d` | `d2d69f95` |
| 6 | **El listado de órdenes rediseñado** — sobre una maqueta aprobada el 2026-10-16 | `c005aca7` · `da7d7ded` | `a29beeea` |

**Con esto la primera versión funcional está construida.** Lo que cada tanda decidió está en
[[dominio-orden-trabajo]], [[dominio-reporte-servicio]] y [[dominio-equipo-mantenimiento]]; el esquema,
en [[esquema-bd-malphasos]].

**Lo que salió por el camino y cambia planes**:

- **La regla de conformidad se movió de sitio** entre la tanda 4 y la 5: el tipo de equipo la tenía, y
  el reporte la necesitaba con la tolerancia **congelada** en cada lectura. Se retiró del tipo.
- **El vencimiento de calibración ya tiene columna**, y esta nota decía en la tabla de abajo que era
  «lo único que sigue sin tener dónde guardarse». RF-40 y RF-41 hablan de los **equipos patrón**, y
  `equipo_patron.f_vencimiento_certificado` es esa fecha. **RF-40 no pasa a implementado**: de sus
  tres criterios de aceptación se cumple entero uno —el indicador en el listado—, el aviso al asociar un
  patrón al reporte se cumple a medias —el vencido no se ofrece y bloquea el cierre, el próximo a vencer
  no avisa— y la comparación diaria no existe, porque el backend no tiene tareas programadas.
- **El marcador no se mueve: siguen siendo 21.** Ningún RF describe el correctivo, el patrón ni la
  conformidad —la ERS habla de «preventivo con calibración» y no dice qué se registra—, y ejecutar con
  reportes cerrados tampoco está escrito como requisito. Es trabajo que la especificación no reclama,
  igual que el filtrado por dueño.

## El formato que la empresa ya entrega (decidido y construido el 2026-10-16)

El usuario aportó **cuatro informes reales** de la empresa —en `Documentation/raw/`, **sin versionar**,
porque llevan datos personales de un cliente real— y pidió que el reporte se pudiera **llenar, ver en la
aplicación y exportar a PDF** con ese formato. Entre tres diseños eligió el **C**: el informe de siempre,
con una gráfica de error y su banda de tolerancia.

**Decisiones del usuario:**

| Pregunta | Decisión |
|---|---|
| Número del reporte | **Los seis primeros caracteres del identificador**, como un commit de git |
| Patrones | **Varios por verificación**, con la ficha que el informe imprime en su trazabilidad |
| Tipos de servicio | **Los tres, más la verificación metrológica derivada**: la casilla se marca sola si hay lecturas |
| Formato `MP0`/`MP1`/`MP2` | **Lo elige el tipo de equipo** — **revocada ese mismo día**: los formatos se retiran, ver «El diseño único del informe» |
| Supervisor | **El encargado del área** |
| Quién recibe | **Texto libre en el reporte**: nombre, cédula y cargo |
| Al ingreso | **Las dos verificaciones fijas en todo reporte**, más una lista libre de accesorios |

**Las tandas, las seis en `main` el mismo día:**

| # | Tanda | Commits | Merge |
|---|---|---|---|
| 1 | **El número** — `V28` | `0e4012ae` · `42e7c793` | `d9253ac3` |
| 2 | **La ficha del patrón** — `V29` y su formulario | `2487351f` · `19db813c` | `e12dfb0d` |
| 3 | **Patrones por verificación** — `V30` | `9f769654` · `cfa6b24f` | `e913c2e3` |
| 4a | **Ingreso y entrega** — `V31` | `6a588034` · `521ebab2` | `d3644bc6` |
| 4b | **El formato en el tipo** — `V32` | `deabd70b` · `042970e4` | `9af749da` |
| 5 | **El documento** — `/reportes/:id/documento`, impreso desde el navegador | `85bc3303` · `ac44e406` · `3ddfc3a1` | `b873c538` |

**Lo que queda abierto, como preguntas al usuario**: qué distingue `MP1` de `MP2` —se imprimen igual—,
si el ingreso y la entrega deben exigirse para cerrar —hoy no—, y si el código interno de un patrón
puede repetirse —hoy sí—. Y **lo que el documento no congela**: cliente y equipo salen de la hoja de vida
de hoy. Todo en [[deuda-tecnica-y-riesgos]]; el detalle, en [[dominio-reporte-servicio]].

**El marcador no se mueve: siguen siendo 21.** RF-17 pide el PDF **firmado**, y el documento se imprime
sin firma; sigue esperando a RF-18 y RF-21, que es exactamente donde el orden de abajo lo pone.

## El diseño único del informe (decidido el 2026-10-16)

Construido el formato de los informes reales, el usuario lo revisó con todas las preguntas abiertas
delante y decidió que **los formatos `MP0`, `MP1` y `MP2` eran del sistema antiguo** y no se conservan:
**un solo «Informe de mantenimiento»**. Y una segunda decisión que va más allá del informe: **MalphasOS
es un producto aparte de Bolívar Bioingeniería y va a ser de código abierto**, así que ningún dato de la
empresa puede vivir en el código.

**El informe:**

| Pregunta | Decisión |
|---|---|
| Formatos | **Ninguno.** Se retira el formato del tipo de equipo (`V32`) y su selector |
| Título | **Fijo**, «Informe de mantenimiento», con las casillas Preventivo · Correctivo · Calibración · V. Metrológica · Garantía |
| Secciones que no aplican | **No se imprimen.** Sin lecturas no hay trazabilidad ni gráfica, y el informe cabe en una hoja |
| Garantía | **Una marca del correctivo** en la orden; solo un correctivo puede llevarla |
| Resultado | **Tres valores guardados, dos impresos**: Operativo y Con restricciones son «Apto» —la restricción va en las observaciones—, Fuera de servicio es «No apto» |
| Falla y diagnóstico | **Opcionales en todo reporte** —ya lo eran—, obligatorios en el correctivo hasta el **2026-10-27**, cuando el usuario los dejó opcionales también ahí; impresos solo con texto |
| Para cerrar | **Exige además las dos verificaciones al ingreso**. Quién recibe, no |
| Protocolo | **Con ✓ o N/A por paso**, y la nota del N/A |
| Placa de inventario | **No se añade**: basta el código interno |
| Firmas | **Tres líneas para firmar a mano**, con el nombre debajo, mientras no haya firma digital. **Superado el 2026-10-17**: la 1.0.0 trae la firma digital —ver la sección siguiente—, y a mano queda solo la del supervisor |
| La orden | **Lleva número**, los seis primeros caracteres de su identificador como el reporte, y el informe lo imprime |
| Al cerrar | **El informe se congela**: el reporte guarda copia de lo que imprime, incluidos los datos de la empresa *(esto último, de la sesión)* |
| Código interno del patrón | **Único entre los vigentes**, como la serie |
| La gráfica | **Sin cambios**: la de error con su banda de tolerancia, el diseño C |

**Los datos de la empresa:**

| Pregunta | Decisión |
|---|---|
| Qué sale del código | **Todo lo de Bolívar Bioingeniería**: nombre, NIT, dirección, contacto, garantía, aclaración del proceso y su escudo. **El logo, los colores y el diseño de MalphasOS se quedan** |
| Dónde viven | **En un archivo aparte, ignorado por git**, que lee el backend; el frontend los pide al API. Una sola fuente para la hoja de vida, el informe y el inicio |
| Qué se versiona | **Un ejemplo con una empresa inventada**, sin valores por defecto en el código; las pruebas usan esa empresa |
| El historial de git | **No se reescribe**: se limpia el árbol desde ahora |
| Sin logo configurado | El documento imprime solo el nombre *(de la sesión)* |

**Las tandas, en este orden:**

| # | Tanda | Por qué aquí |
|---|---|---|
| 1 | **Este registro**, y la corrección de lo que se dijo de `MP1` | Antes de construir |
| 2 | **Los datos de la empresa fuera del código** | El informe nuevo ya los lee del API |
| 3 | **El informe único**: sin `MPx`, título, casillas, secciones por datos, Apto/No apto y líneas de firma | |
| 4 | **La garantía y el número de la orden** | Tocan `work-order` |
| 5 | **Cerrar exige el ingreso**, y el código interno del patrón único | Dos reglas pequeñas |
| 6 | **El informe congelado al cerrar** | La más grande: antes de empezarla se enseña al usuario qué se copia |
| 7 | **Pasada de wiki** | |

**Hecho**, del 2026-10-16 al 17: las tandas 1 a 4 y la primera mitad de la 5 —cerrar exige el ingreso,
sin `CHECK` porque impediría retirar los reportes cerrados antes de `V31`—. **Lo que queda** —el código
interno único, el informe congelado y la pasada de wiki— **se absorbe en el plan de la 1.0.0**, abajo.

> **Corrección de esta misma nota.** La sección anterior, y [[dominio-reporte-servicio]], decían que los
> cuatro informes reales eran `MP0` y `MP2`. **La balanza es `MP1`**: se leyó mal el primer día. Ya no
> cambia nada, porque los formatos se retiran, pero quedó escrito y se corrige.

## La versión 1.0.0 (decidida el 2026-10-17)

El usuario pidió **cerrar todo lo necesario para la 1.0.0 y empezar producción**, y decidió en dos
rondas. Lo que salió de revisar el despliegue antes de preguntar: **el código funciona y el despliegue
no está listo**. El frontend lleva `localhost` compilado en `environment.ts`, Keycloak corre en
`start-dev` por HTTP con el realm de desarrollo —`dev.admin` y un secreto fijo—, no hay HTTPS, Postgres
y Keycloak publican sus puertos, no hay respaldo, la versión es `0.0.1-SNAPSHOT` y **RabbitMQ no lo usa
ninguna clase de producción**.

**Despliegue:**

| Pregunta | Decisión |
|---|---|
| Dónde corre | **Aún no se sabe**: se deja listo para cualquier servidor con Docker y el dominio va en el `.env` |
| HTTPS | **Caddy dentro del compose**, con certificados automáticos; es lo único que publica puertos |
| Respaldo | **`pg_dump` diario** de la base de la aplicación y la de Keycloak, con retención y una restauración probada |
| Alcance | **Lo decidido más producción, y también la firma digital** (RF-17, RF-18 y RF-21) |

**La firma.** La ERS no pide una firma criptográfica sino **una firma dibujada en la pantalla**:
RF-18 un área de trazo con «Limpiar» que no acepta la firma vacía, RF-21 replicarla a los reportes de
la misma orden tras confirmarlo, y RF-17 un botón que exporta el PDF —firmado—. **Contradice la decisión
del día anterior** —«tres líneas a mano»—, y el usuario la cambió a sabiendas:

| Pregunta | Decisión |
|---|---|
| Quién firma en pantalla | **Quien recibe el servicio**, el cliente o su encargado |
| El ingeniero | **Su firma vive en su cuenta**: la dibuja una vez y no firma cada servicio. Solo la cambia su dueño *(de la sesión)* |
| Cuándo firma quien recibe | **Antes o después de cerrar**: la firma es lo único que se añade a un informe cerrado, porque muchas veces se entrega después. Firmada, no se cambia *(de la sesión)* |
| Replicar (RF-21) | **Como la ERS**: una casilla «aplicar a todos los reportes de esta orden», solo a los de esa orden que aún no tengan firma, abiertos o cerrados *(el alcance, de la sesión)*. **Ampliado el 2026-10-30** a tres alcances —el reporte, su área en la orden o la orden entera—, pedido por el usuario con el análisis del esquema |
| La firma del ingeniero en el informe | **Se copia al cerrar**, con el resto de lo congelado: cambiarla después no altera lo entregado. Cerrar no la exige; sin ella la línea sale para firmar a mano *(de la sesión)* |
| El supervisor | **A mano**: es el encargado del área, del cliente y sin cuenta |
| El PDF | **En el servidor** |

**Cómo se genera el PDF** *(de la sesión)*: el backend compone el informe congelado en una plantilla
HTML y lo convierte con **Gotenberg** —Chromium sin pantalla— en un contenedor interno sin puertos, y la
pantalla del documento muestra ese PDF con su botón. **El diseño vive en un solo sitio**: con una copia
en Angular y otra en el servidor las dos se desviarían, que es lo que ya pasó con las reglas de cierre
antes de reflejarlas. El precio es dibujar la gráfica de error en SVG desde Java. La hoja de vida se
queda en el frontend: RF-17 no la pide.

**Las tandas:**

| # | Tanda | |
|---|---|---|
| 1 | **Este registro** | |
| 2 | **Código interno del patrón único** entre los vigentes | Lo que quedaba de la tanda 5. **Hecho** el 2026-10-17 (`V35`) |
| 2b | **La empresa editable por un administrador** | Pedida el mismo día, después del plan: ver abajo |
| 3 | **El informe congelado al cerrar** | Antes de empezar se enseña al usuario qué se copia. **Hecho** el 2026-10-17 (`V37`, merge `417e90b2`); los cerrados antes se congelan al arrancar, marcados como tardíos |
| 4 | **La firma del ingeniero en su cuenta** | Tabla nueva y «Mi firma». **Hecha** el 2026-10-17 (`V38`): `/persons/me/signature`, la persona sale del token. Una cuenta sin fila en `persona` —el SuperUsuario— **no puede tener firma** |
| 5 | **La firma de quien recibe y su réplica** | RF-18 y RF-21. **Hecha** el 2026-10-17 (`V39`): `PATCH /reports/{id}/receipt-signature`, antes o después de cerrar; firmar registra también quién firma, y después ya no se cambia ni la firma ni el nombre. La casilla firma los reportes **activos** de la orden sin firma; la imagen se guarda una vez en `imagen_documento` |
| 6 | **El PDF en el servidor** | RF-17. **Hecho** el 2026-10-17: `GET /reports/{id}/pdf`, con la plantilla del informe en Thymeleaf y Gotenberg en el compose. La pantalla del documento muestra ese PDF y lo exporta; la plantilla de Angular, su hoja de estilos y la gráfica **se retiraron**, y sus pruebas pasaron al backend. La hoja de vida se sigue imprimiendo desde el navegador |
| 7 | **Producción**: configuración del frontend al arrancar, compose de producción con Caddy, Keycloak en `start` con un realm sin usuarios ni secretos, fuera RabbitMQ, respaldo y guía de despliegue | **Hecha** el 2026-10-17, en un solo dominio. Probada levantándola entera con `localhost`: HTTPS, el primer SuperUsuario con su guion, un alta que habla con Keycloak, el PDF, y un respaldo restaurado de ida y vuelta con las 37 tablas idénticas. Ver `DESPLIEGUE.md` y [[docker-compose]] |
| 8 | **1.0.0**: versiones, `CHANGELOG`, pasada de los dos wikis y de la ERS, y el tag cuando el usuario lo diga | **Hecha** el 2026-10-17, salvo el tag. **El marcador sube a 24**, y la ERS se puso al día: marcaba implementados solo los ocho de su primera revisión. El repaso de la deuda destapó una regresión de la tanda 6 —el PDF recortaba lo que no cabía en la hoja— y se corrigió antes del tag |

**La empresa, editable (decidido el 2026-10-17, después del plan).** El usuario pidió que datos como la
política de garantía los pueda cambiar un administrador, sin tocar el servidor. Tres decisiones:

| Pregunta | Decisión |
|---|---|
| Qué se edita | **Todo, logo incluido**: nombre, NIT, dirección, contacto, garantía, aclaración, códigos de los formatos y el logo |
| El archivo `config/empresa.yaml` | **Solo siembra el primer arranque**: si la base no tiene empresa, la crea con él; después manda la base y el archivo no se lee. Deja de ser obligatorio |
| Quién | **Administradores**, con `company.read` y `company.write`, que el escalón de administrador ya concede |

Se construye **antes de congelar el informe**, porque lo congelado copia la empresa. Entra un módulo
nuevo, `company`, con `V36`, y la pantalla «Empresa». **Anula** «sin nombre la aplicación no arranca» del
2026-10-16.

## La refactorización del esquema, `V42` a `V51` (decidida el 2026-10-27)

Pedida por el usuario antes de construir nada más sobre la base: «veo demasiados ciclos, redundancias y de
todo». El análisis, sus decisiones y el plan están en `Documentation/DatabaseDesign/analisis-2026-10-27.md`,
con el esquema de hoy y el final dibujados en `Documentation/DatabaseDesign/refactor-3fn/`. **No mueve el
marcador de requisitos**: cambia cómo se guardan los datos, no lo que el sistema hace, salvo cuatro reglas
de la orden nuevas —el borrador nace al añadir el equipo, ejecutar exige un reporte cerrado por equipo— y
el bloqueo del traslado de un equipo que está en una orden viva.

| Migración | Qué | Estado |
|---|---|---|
| `V42` | Índices de las foráneas, dueños obligatorios y tres nombres que engañaban | **Construida el 2026-10-27** |
| `V43` | Los teléfonos de la empresa en su tabla | **Construida el 2026-10-27** |
| `V44` | La ciudad de la empresa, del catálogo | **Construida el 2026-10-27** |
| `V45` | El logo de la empresa y las firmas, por huella en `imagen_documento` | **Construida el 2026-10-27** |
| `V46` | Sin segundo tipo de persona | **Construida el 2026-10-27** |
| `V47` | La orden llega a su cliente por la sede | **Construida el 2026-10-28** |
| `V48` | La verificación llega a su magnitud por la unidad | **Construida el 2026-10-28** |
| `V49` | La orden se congela al ejecutarse | **Construida el 2026-10-28** |
| `V50` | Sin tabla puente entre la orden y el equipo, con «Sustituir» y el traslado bloqueado | **Construida el 2026-10-28** |
| `V51` | El reporte cerrado vive en su documento, sin copias del catálogo | **Construida el 2026-10-28** |
| `V52` | Sin `intervencion`: la hoja de vida lee el historial de los reportes | **Construida el 2026-10-28**: la refactorización a 3FN está completa |

**Renumerada el 2026-10-27**: el plan tenía siete, `V42` a `V48`, y el `V43` de entonces juntaba los
teléfonos, la ciudad y las imágenes de la empresa. Se partió en tres al construirlo porque tocan tres
sitios distintos —la persistencia, la pantalla de la empresa, y `company`, `person` y `report` a la
vez— y en un solo commit no se revisaban. **Y otra vez el 2026-10-28**: el `V47` que juntaba la orden por
la sede y la verificación por la unidad se partió en dos, porque son dos módulos, y el plan llega a
`V51`. **Y una tercera ese mismo día**: el `V49` que juntaba el congelado de la orden y la tabla puente se
partió en `V49` y `V50`, y el plan llega a `V52`. Con él quedaron decididas dos cosas que el puente no
dejaba ver: sustituir un reporte cerrado es un botón «Sustituir», que lo retira y abre su borrador en un
paso, y se puede sustituir con la orden ya ejecutada sin que su documento cambie.

**Y el recibido** —firmar un reporte, un área o la orden entera— no pide migración: se queda en el reporte,
como desde `V39`, y es trabajo de servicio y de pantalla después de `V49`. **Construido el 2026-10-30**,
tras `V52`: la casilla «aplicar a la orden» se volvió un alcance de tres valores —solo este reporte, los
del área de este equipo en la orden, o todos los de la orden—, en el API como `alcance` y en la pantalla
como tres opciones. Ver [[dominio-reporte-servicio]].

**Y los informes de una orden en un ZIP**, pedido el 2026-10-31 y construido ese día: un botón en la ficha
de la orden, cuando todos sus equipos tienen el reporte cerrado. Sin migración. Ver
[[dominio-reporte-servicio]].

## El orden propuesto

| # | Bloque | Desbloquea | Estado de sus dependencias |
|---|---|---|---|
| ~~0~~ | ~~**Mergear las dos ramas pendientes**~~ | Todo lo demás | **Hecho el 2026-09-09**: `e6dda32` y `258cd81`, con `main` en `01c3277` y la batería remedida en **496**. **Actualizado el 2026-09-12**: `main` está hoy en **`55e0a5d`**, con `feat/relocation-same-client` también dentro (`1ef55cf`) y **509** pruebas |
| 1 | **Órdenes de trabajo** (RF-01…07) | Reportes, firma, comercial, inventario | Todas cumplidas. **Empezado el 2026-09-12**: tanda 1 de 4 —el esquema, `V6`— en `feat/work-order-schema`, sin mergear. Faltan dominio, aplicación y REST, y **los siete requisitos siguen contando como no implementados**: un esquema no cumple ninguno |
| 2 | **Reportes de mantenimiento** (RF-09, 11, 13, 15) | Historial de hojas de vida, PDF, alertas | **Completo de extremo a extremo**: backend el 2026-09-27 —`V9`, agregado con cinco eventos, seis reglas cruzadas con su persistencia probada y seis operaciones REST— y **frontend el 2026-09-28**, en cuatro tandas. Cierra **RF-09, RF-11 y RF-15**. Queda **RF-13, que espera los protocolos de RF-14**, y RF-17, que espera la firma. Ver [[dominio-reporte-servicio]] |
| 3 | **Segunda tanda de `equipment`** (verificaciones técnicas, datos metrológicos) | Alertas y calibración | **Completa desde el 2026-10-14.** El *con qué* se verifica entró con `V8`; el **resultado** de verificar entró con `V9` y **vive con el reporte**; y el **vencimiento**, que era lo que faltaba, entró con `V25` en el catálogo de equipos patrón. (Decía «queda solo el vencimiento de calibración… lo único que sigue sin tener dónde guardarse»: cierto hasta el **2026-10-14**.) Lo que las alertas necesitan ahora es **quién vigile la fecha** |
| 4 | **Historial de intervenciones** (RF-26, RF-27) | Cierra las hojas de vida | **Desbloqueado el 2026-09-27** y **con pantalla desde el 2026-09-28**: `/equipos/:id/historial` lista los reportes de un equipo, con su fecha, su resultado y sus lecturas. **No cuenta como RF-26**: estos dos requisitos piden el historial **dentro de** la hoja de vida, que no existe como entidad. Lo que falta es exactamente eso, no la consulta |
| 5 | **Frontend** | Firma digital, seis RNF, los manuales de usuario | Ninguna técnica. **Decidido el 2026-09-13: entra ahora.** Clientes cerrado el 2026-09-26, equipos y catálogo ese mismo día, y **las órdenes de trabajo el 2026-09-27**: con eso el primer bloque está completo y el marcador de requisitos sube por primera vez gracias al frontend |
| 6 | **Firma digital** (RF-18, RF-21) → **PDF** (RF-17) | Cierra reportes | Requiere los bloques 2 y 5. **Decidido el 2026-10-17** y en construcción para la 1.0.0: ver la sección anterior |
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

[[checklist-reutilizacion]] · [[decisiones-tecnicas-malphasos]] · [[deuda-tecnica-y-riesgos]] · [[dominio-orden-trabajo]] · [[dominio-reporte-servicio]] · [[modelo-de-permisos]] · [[sincronizacion-con-proveedor-de-identidad]] · [[dominio-equipo-mantenimiento]] · [[integracion-keycloak-frontend]] · [[arquitectura-frontend]] · [[alcance-malphasos]]
