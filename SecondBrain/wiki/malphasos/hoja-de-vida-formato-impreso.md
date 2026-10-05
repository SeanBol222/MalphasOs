---
name: hoja-de-vida-formato-impreso
description: Todo lo que hace falta para producir en la aplicación la hoja de vida con el diseño aprobado el 2026-10-05 —campo por campo de dónde sale, qué migraciones pide, cómo se imprime, en qué tandas y qué queda por decidir—
tags: [malphasos, hoja-de-vida, frontend, impresion, diseno, "describe:malphasos"]
source: el lienzo de diseño «Hoja de vida de equipos — propuestas», propuesta C2; malphasos/.../equipment/application/services/lifeSheet/LifeSheetService.java
estado: incompleto
updated: 2026-10-05
---

# La hoja de vida impresa: qué hace falta para producirla

**Qué es esta nota**: el plan para llevar a la aplicación el formato de hoja de vida que el usuario
aprobó el **2026-10-05**. Es un plan y no una descripción: **están construidas las seis tandas que no esperan a nadie —6, 1, 5, 2, 3 y 4—** —la
pantalla con el diseño y su impresión, mergeada por `ac9c2592`; los datos sin migración, por
`9516327e`; los campos nuevos del catálogo, por `86b39bbc`; el historial completo, por
`26e64149`; y la numeración, por `d2793614`, todas el mismo día—. **Lo que queda es lo que espera
a otra cosa**: la foto, a que se decida dónde guardar archivos, y el protocolo, a RF-14 y lo demás sigue pendiente,
salvo lo que la tabla de campos marca como «ya sale». (Decía «nada de lo que sigue está construido»:
cierto hasta esa tarde.) La hoja de vida que existe hoy —el
compilado de solo lectura, su historial y el oyente que lo escribe— está en [[hoja-de-vida]].

**Lo que pidió el usuario, en sus palabras**: no replicar el formato de 2019 «tal cual», sino «algo más
moderno, algo mejor», con **toda la información** de aquel y con el **logo nuevo de Bolívar**.

## El diseño aprobado: la propuesta C2, «Tablero con escudo»

Se hizo en Claude Design, con tres direcciones —A «Ficha técnica», B «Expediente», C «Tablero de
estado»— y el usuario eligió la **C**, con un añadido suyo: el escudo del logo en grande y
transparente detrás. La versión final es la **C2**, y vive en el lienzo
`https://claude.ai/artifact/33ucW5fKnAPSJkMmmNf9Mu`. **El lienzo es privado**: solo lo abre su dueño,
de modo que esta nota tiene que bastar para construir sin él.

### La marca: la de Bolívar Bioingeniería, no la de MalphasOS

La hoja de vida la entrega la **empresa de servicio a su cliente**: es un documento de Bolívar, y lleva
su marca. Los activos están en `landingPage/brand-assets/` (fuera de este repositorio, en
`~/Documents/BolivarBioIngenieria/landingPage/`), exportados de Claude Design:

- **El isotipo** —la «B» de trazos y nodos— es un SVG de 100×100 con cinco líneas de trazo 7, punta
  cuadrada, y cinco círculos de radio 5, todo en `#EC3013`. Está en `symbol/color/`.
- **El logotipo** es el isotipo más «BOLÍVAR» en Archivo 800 y «BIOINGENIERÍA» en Archivo 500, con
  `letter-spacing` de 1. En el diseño se compuso con HTML y no como imagen, para que salga nítido al
  imprimir.
- **La paleta es la misma que la de MalphasOS**: tinta `#201e1d`, papel `#f3f2f2`, rojo `#ec3013`,
  rojo para texto `#ae1800`, neutros `#605d5d` y `#d7d3d3`. La aplicación ya tiene estos tokens.

### La página, medida

Tamaño **carta** (816×1056 px a 96 ppp), fondo blanco, **Archivo** como única familia, cifras
tabulares. El texto más pequeño es de 12 px (9 pt), que es el mínimo legible en papel.

**Página 1 — el equipo**

1. **Banda oscura** (`#201e1d`, a todo el ancho): el logotipo en pequeño; «Hoja de vida · HV-0001» en
   versalitas grises; el **equipo en grande** —«Balanza Beurer GS14», 36 px, peso 900—; debajo, serie,
   área y cliente; y a la derecha un **código QR** de 84 px con «Versión digital».
2. **Fila de estado**, cuatro celdas: **estado actual**, **último servicio**, **intervenciones** y
   **riesgo** (en rojo de texto). Es lo que da el «en tres segundos se sabe si el equipo está bien».
3. Dos **tarjetas** grises a media opacidad: **identificación** —tipo, marca y modelo, serie, placa y
   código interno, INVIMA, área— y **cliente** —razón social y NIT, sede y ciudad, dirección,
   responsable, teléfono y móvil, correo—.
4. **Foto** del equipo (132 px) junto a «**Qué es**»: la definición técnica y, en una línea, fabricante,
   país, proveedor, fecha y valor de compra.
5. **Características técnicas**, cuadrícula de 4×2: tecnología, uso, alimentación, frecuencia,
   voltaje, potencia, corriente y específicas.
6. **Protocolo preventivo** (lista numerada) y **cuidado y limpieza** (lista, con la limpieza diaria
   en negrita), lado a lado.
7. Pie: código del documento, fecha de actualización y «Página 1 de 2».

**Página 2 — la vida del equipo**

1. **Banda oscura** estrecha: logotipo, número de hoja, equipo y serie.
2. **Historial de servicio técnico**, tabla con fecha, tipo, **descripción y responsable** y
   **resultado**, y filas alternas grises. El resultado se marca con un cuadrado que se distingue
   **por la forma y no solo por el color** —relleno en tinta: operativo; hueco en rojo: con
   restricciones; relleno en rojo: fuera de servicio—, de modo que se lee en una impresora en blanco y
   negro. Debajo, **filas en blanco** para anotar a mano un servicio hecho fuera del sistema.
3. **Servicio técnico** —los datos de Bolívar— y dos **líneas de firma**: «Elaboró · Bolívar
   Bioingeniería» y «Recibió · Profesional responsable».

**El escudo**: la «B» del isotipo a **620 px**, **centrada** a lo ancho y en la zona clara, por
debajo de la banda, en el **naranja del logo `#EC3013` al 6 % de opacidad**, detrás del contenido. Las
tarjetas y las filas grises son **semitransparentes** (`rgba(234, 231, 231, 0.55)`) para que el escudo
no quede cortado en recuadros. En el diseño, el color y la opacidad son ajustables; **en la aplicación
van fijos**.

## Campo por campo: de dónde sale cada dato

La columna «Dónde vivirá» es una **propuesta**, y se pone a discusión porque decide qué se repite entre
unidades: lo que va en el **tipo** lo comparten todas las balanzas; lo que va en el **modelo**, todas
las GS14; lo que va en la **unidad**, solo esa máquina.

### Ya sale de la hoja de vida de hoy (`GET /client-equipments/{id}/life-sheet`)

Tipo, marca, modelo, serie, placa de inventario, área, registro INVIMA, cliente (razón social), NIT,
sede, dirección, ciudad, fabricante, país, fecha y valor de compra, tecnología, voltaje, amperaje,
definición técnica, recomendaciones de cuidado, y del historial la fecha, el tipo y el resultado.

### Existe en el sistema pero la hoja de vida no lo trae

| Dato del diseño | De dónde se saca | Coste |
|---|---|---|
| Profesional responsable | El **encargado del área** (`Manager` de tipo `SERVICE_AREA`), y si no hay, el de la **sede** (`HEADQUARTER`). Su nombre está en `persona` | Ampliar `LifeSheetService`. **Decisión abierta**: confirmar que «profesional responsable» es el encargado |
| Teléfono, móvil y correo del cliente | Los contactos del cliente, `EmailClient` y `PhoneClient` | Ampliar. ⚠️ **Un teléfono no dice si es fijo o móvil**: la tabla no tiene tipo. O se añade, o se imprimen los dos primeros sin etiqueta distinta |
| Estado actual | El **resultado de la última intervención vigente** | Se deriva, sin migración. Sin intervenciones: «Sin servicios» |
| Último servicio | La fecha de la última intervención vigente | Se deriva |
| Intervenciones | Cuántas intervenciones vigentes hay | Se deriva |
| Cuidado y limpieza como **lista** | `recomendacionesCuidado` es un solo texto | Se parte por saltos de línea al pintar, sin migración |

### No existe todavía: pide migración

| Dato del diseño | Dónde vivirá (propuesta) | Por qué ahí |
|---|---|---|
| Clasificación por riesgo (I, IIa, IIb, III) | **Modelo**, junto al registro INVIMA | El riesgo lo fija el registro sanitario del dispositivo, y el registro ya vive en el modelo. Con `CHECK` sobre los cuatro valores |
| Uso | **Tipo** | Describe para qué sirve esa clase de aparato |
| Limpieza cotidiana | **Tipo** | Igual que las recomendaciones de cuidado, que ya están ahí |
| Alimentación eléctrica, potencia (W), frecuencia (Hz) | **Modelo** — **decidido por el usuario el 2026-10-05** | Los datos eléctricos son de cada modelo y no del tipo: dos balanzas de marcas distintas no consumen lo mismo. **Y voltaje y amperaje se mueven también**, del tipo al modelo: estaban en el tipo por herencia del original, y partir los cinco entre dos tablas habría sido lo peor. Ver abajo |
| Características específicas | **Modelo** | Lo que distingue a ese modelo de otros del mismo tipo |
| Código interno | **Unidad** (`equipo_cliente`) | Es el código que el cliente le pone a su máquina, como la placa |
| Proveedor | **Unidad** | Quién le vendió **esa** máquina. Texto libre: un catálogo de proveedores no lo pide nadie todavía |
| Número de hoja de vida («HV-0001») | **Unidad**, con una secuencia | **Decisión abierta**: consecutivo global o por cliente. Una secuencia de PostgreSQL da el global sin carreras; por cliente exige un contador por cliente con bloqueo |
| Fecha de actualización | — | **Decisión abierta**. Ninguna tabla guarda cuándo cambió por última vez: no hay columnas de auditoría en todo el esquema. Las opciones son la fecha de **generación** —la de impresión, que no exige nada—, la del **último servicio**, o añadir auditoría, que es un cambio transversal |
| Foto del equipo | **Modelo** | Una foto por modelo sirve a todas sus unidades. ⚠️ **El backend no guarda archivos**: no hay subida, ni almacenamiento, ni columnas binarias. Es una decisión de infraestructura por sí sola —en la base, en disco o en un almacén de objetos— |
| Protocolo preventivo | Los **protocolos** de RF-14 | **No se inventa una tabla aquí**: RF-14 es justo esto, está en la hoja de ruta y detrás espera RF-13. Mientras no exista, la sección sale vacía o no sale |

### Voltaje y amperaje dejan el tipo (decidido el 2026-10-05)

**Es la única parte del plan que quita columnas**, y por eso va escrita aparte. `tipo_equipo` tiene hoy
`i_voltage` y el amperaje desde `V5`, heredados del original; pasan a `modelo` junto con los tres datos
eléctricos nuevos. La migración tiene que:

1. añadir las cinco columnas a `modelo`;
2. **copiar** voltaje y amperaje de cada tipo a todos sus modelos —un modelo cuelga de un tipo a través
   de `equipo`—, porque es el valor que esos modelos tenían hasta hoy por herencia;
3. y **quitar** las dos columnas del tipo, con su `CHECK` de voltaje positivo, que se rehace en el
   modelo.

Arrastra el cuerpo de edición del tipo, su respuesta, la pantalla del catálogo, el panel que crea un
modelo en línea y `LifeSheet`, que hoy lee los dos datos del tipo. **Es la misma forma que `V10`**, que
bajó la modalidad de verificación del tipo a cada verificación: un dato que estaba un nivel por encima
del que le correspondía.

### El historial: descripción y responsable

Hoy cada línea guarda **fecha, tipo y resultado**, y el diseño pide además **qué se hizo y quién lo
hizo**. El reporte lo tiene —diagnóstico, procedimientos, observaciones— y la orden, al ingeniero.

**Propuesta: copiarlo congelado en `intervencion` al cerrarse el reporte**, como ya se copian la fecha,
el tipo y el resultado ([[congelar-una-referencia-historica]]). Hay dos razones, y la primera manda:

1. **`equipment` no puede leer `report`**: `report` ya importa `equipment`, y leer al revés crearía el
   ciclo que obligó a poner el oyente en `report` ([[hoja-de-vida]]). El oyente ya tiene el reporte en
   la mano: pasar dos campos más en el comando no cuesta nada.
2. Una hoja de vida es un documento con valor legal: **lo que dice de un servicio no debe cambiar
   porque alguien edite después a la persona** que lo hizo.

Pide una migración con dos columnas —`t_descripcion` y `n_responsable`— y **un relleno** de las
intervenciones existentes desde sus reportes, en la línea de `V13`. **Decisión abierta**: cuál de los
tres textos del reporte es la «descripción» —procedimientos parece el natural— y si el responsable se
imprime con nombre o solo como la empresa.

### Los datos de Bolívar

Nombre, dirección, teléfonos y correo de la empresa de servicio **no son datos de ningún cliente**, y
no van en la base: van en **configuración** (`application.yml`, con variables de entorno), igual que
el origen permitido por CORS. Los del diseño salen de la hoja de vida de **2019**, y **hay que
confirmarlos**: la landing page de Bolívar tiene esos campos todavía en blanco.

### Lo que el diseño no muestra, a propósito

- El **valor unitario de mantenimiento** del tipo: RF-22 lo pide, pero es un precio interno de
  Bolívar, y no debe salir en el papel del cliente. **Decisión abierta**: confirmarlo.
- El **valor de compra** sí sale, en la línea de «Qué es». **Decisión abierta**: si al cliente le
  sirve verlo, o es de los datos que se quedan en la pantalla.

## Cómo se imprime

**Recomendación: imprimir desde el navegador, con la pantalla y una hoja de estilos de impresión**, no
con un PDF generado en el servidor.

- **La pantalla de la hoja de vida pasa a tener este mismo diseño**, y el botón «Imprimir» llama a
  `window.print()`. Con `@page { size: letter; margin: 0 }` y la banda a sangre, el navegador produce
  el PDF —«Guardar como PDF»— sin una sola dependencia nueva.
- **El backend no tiene ninguna librería de PDF** hoy. Generarlo en el servidor —OpenPDF, Flying
  Saucer o un Chromium sin interfaz— es la vía si hace falta **archivar o enviar** el documento sin que
  nadie lo abra, y **es la misma decisión que RF-17**, el PDF del reporte de servicio. Conviene
  tomarlas juntas.

**Lo que el diseño fijo no resuelve y la versión real sí tiene que resolver**: el diseño son dos páginas
cerradas, y un equipo real puede tener **cuarenta servicios**.

- La **página 1** tiene altura fija: lo que no cabe se recorta, así que los textos largos —definición,
  características específicas— necesitan un **límite de caracteres** o una regla de corte.
- El **historial fluye** por tantas páginas como haga falta: la cabecera de la tabla se repite en cada
  hoja (`thead` con `display: table-header-group`), una fila **no se parte** entre páginas
  (`break-inside: avoid`), y las **filas en blanco** van solo al final del historial.
- El **escudo**, la **banda estrecha** y el **pie** tienen que salir en **cada** página. Con impresión
  del navegador eso se hace con elementos `position: fixed`, que se repiten por página; el número de
  página («2 de 3») **no** se puede calcular así, y o se omite o exige el PDF del servidor.
- **Los fondos**: el navegador no imprime colores de fondo por defecto. La banda y las tarjetas
  necesitan `print-color-adjust: exact`.

## El QR

Apunta a la hoja de vida en la aplicación (`/equipos/{id}/hoja-de-vida`). **Esa ruta pide iniciar
sesión**, y solo la abre quien tiene `equipment.read` y, si es de un cliente, sobre sus propios
equipos ([[filtrado-por-dueno]]). Para el técnico y para el representante del cliente eso basta.

**Decisión abierta**: si alguien **sin cuenta** —un auditor de la Secretaría de Salud, por ejemplo—
tiene que poder escanearlo. Eso exige un **enlace público firmado**, una propiedad de seguridad nueva,
y no es un detalle de diseño.

## El plan, por tandas

Cada tanda deja la batería en verde y algo usable.

1. ~~**Los datos que ya existen**~~ — **construida el 2026-10-05** junto con la 5: el responsable y los
   contactos del cliente. El estado derivado del historial no hizo falta aquí: lo calcula la pantalla.
2. ~~**Los campos nuevos del catálogo**~~ — **construida el 2026-10-05**, con `V15` y `V16`. Ver «Los
   campos del catálogo, construidos» abajo.
3. ~~**El historial completo**~~ — **construida el 2026-10-05** con `V17` y `V18`. Ver «El historial
   completo, construido» abajo.
4. ~~**El número de hoja de vida**~~ — **construida el 2026-10-05** con `V19` y `V20`, junto con la sigla.
   Ver «La numeración, construida» abajo.
5. ~~**Los datos de Bolívar en configuración**~~ — **construida el 2026-10-05** con la 1. Ver abajo.
6. ~~**La pantalla con el diseño y la impresión**~~ — **construida el 2026-10-05, la primera**, por
   decisión del usuario, con «—» en lo que falta. Ver «La pantalla, construida» abajo.
7. **Lo que espera una decisión de infraestructura**: la foto (almacenamiento de archivos), el QR
   público (enlace firmado) y el PDF del servidor (junto con RF-17).
8. **Lo que espera otro requisito**: el protocolo preventivo, con RF-14.

## La pantalla, construida (2026-10-05)

`features/equipment/hoja-de-vida/`, mergeada por `ac9c2592`. Se comprobó **generando el PDF real**
desde un navegador sin interfaz contra el contenedor del frontend: **dos páginas carta**, con la banda,
el escudo, el QR y los pies donde el diseño los pone. En un teléfono las columnas se apilan. El equipo
de la base de desarrollo no tiene intervenciones, así que **el historial lleno solo lo ven las
pruebas**.

Lo que se aprendió y no estaba en el plan:

- **Lo que no espera a nadie, ya sale**: el estado actual, el último servicio y el número de
  intervenciones **se derivan del historial**, que el servidor manda de la más reciente a la más
  antigua. Sin intervenciones, «Sin servicios».
- **La fecha de impresión se toma en la zona local.** `toISOString()` da la de Greenwich, y en Bogotá a
  partir de las siete de la noche ya es mañana. Lo mismo para la fecha de un servicio: se corta la
  parte de la fecha del texto que manda el servidor en vez de pasarla por `Date`, o un cierre de las
  21:30 salía al día siguiente. Las dos las caza una mutación.
- **El QR se dibuja como SVG desde la matriz de módulos** de la librería `qrcode`, no con `canvas`: el
  corredor de pruebas no tiene `canvas`, y así la prueba compara contra la matriz real que la librería
  genera para esa dirección.
- **«Página 2», no «Página 2 de 2»**: el navegador no puede contar las hojas si el historial sigue en
  una tercera. Lo había anunciado la sección de impresión, y se cumplió.
- **Es la única pantalla con hoja de estilos propia** (`hoja-de-vida.css`), y fue a propósito: un
  documento con medidas de papel, marca de agua y reglas de impresión es ilegible en clases de
  utilidad. **Subió el presupuesto de estilos por componente de 4 a 8 kB** —la hoja pesa 6— y `qrcode`
  quedó declarada como dependencia CommonJS permitida.
- **El marco de la aplicación se oculta al imprimir** (`print:hidden` en la cabecera) y `@page` fija
  carta sin márgenes en `styles.css`: hoy solo imprime esta pantalla.
- **El cuadro de servicio técnico con los datos de Bolívar no está**: esos datos llegan del backend
  (tanda 5), y meterlos a mano en el frontend habría sido la copia que se decidió no tener.

## Los datos sin migración, construidos (2026-10-05)

Mergeados por `9516327e`: el responsable, los contactos del cliente y el membrete de la empresa.

- **Quién responde lo decide `client`, no `equipment`**: es `ServiceAreaResponsiblePort`, el tercer puerto
  que `client` publica hacia fuera —tras `PersonCommunicationPort`, que es de `person`, y
  `ClientOwnershipPort`—. `equipment` solo recibe nombres. Habría sido posible que `equipment` leyera
  los encargados y las personas por su cuenta —`person` no depende de nadie, así que no habría ciclo—,
  pero la regla «área, y si no, sede» es de quien entiende de encargados.
- **Devuelve una lista**, en orden alfabético: nada impide dos encargados en un área, y escoger uno
  sería decidir algo que nadie decidió. La pantalla los une con «·». Los encargados retirados y las
  personas dadas de baja **no cuentan**; el repositorio devuelve unos y otros, y el filtro está en el
  servicio.
- **Solo los contactos vigentes**: un teléfono retirado no se imprime.
- **El membrete lo pone el adaptador REST**, con `ServiceCompanyProperties` (`shared/config`,
  prefijo `app.empresa`), no la capa de aplicación: no es un dato del equipo sino del documento.
  Cada valor se sobrescribe con una variable de entorno, `EMPRESA_MOVIL` y las demás.
- Verificado con cinco pruebas unitarias de la regla, una de integración contra PostgreSQL que cruza
  `equipment`, `client` y `person`, y siete mutaciones, todas caídas. En la base de desarrollo el
  cliente no tiene encargados ni contactos, así que el PDF real sale con «—» en esas tres casillas y
  con el cuadro de la empresa lleno.

## Los campos del catálogo, construidos (2026-10-05)

Mergeados por `86b39bbc`. Con ellos, **las únicas rayas que le quedan a la hoja de vida son las de lo que
nadie ha llenado**, más el número de hoja, la descripción y el responsable de cada servicio, el
protocolo y la foto, que tienen su propia tanda.

- **`V15` mueve voltaje y amperaje del tipo al modelo** y le da al modelo su `TechnicalSheet`: clase de
  riesgo (I, IIa, IIb, III), características, alimentación, voltaje, potencia, corriente y frecuencia.
  **Cada modelo hereda el valor que su tipo tenía**, que es el que tenía hasta hoy; se comprobó sobre la
  base de desarrollo, con datos —las pruebas arrancan con la base vacía y no pueden verlo—. Es la
  misma forma que `V10`: un dato que estaba un nivel por encima del que le corresponde. El tipo gana
  `t_uso` y `t_limpieza_cotidiana`.
- **`V16` da a cada equipo instalado su código interno y su proveedor**, que son de esa máquina y no
  de su modelo. Texto libre: un catálogo de proveedores no lo pidió nadie.
- **La ficha es un valor del modelo, no siete campos sueltos**, y se corrige **entera** por ruta
  propia, `PATCH /models/{id}/technical-sheet`, como el INVIMA. Lo que no viaja queda vacío.
- **El amperaje se normaliza a dos decimales y rechaza un tercero** en vez de redondearlo. No es
  cosmético: `BigDecimal.equals` distingue 2.5 de 2.50, y sin normalizar, reenviar la misma ficha
  contaba como cambio y emitía un evento.
- **Pantalla nueva**: Catálogo › Modelos › «Ficha técnica», con página propia. La edición del tipo
  manda el uso y la limpieza **siempre, vacíos incluidos**, porque un blanco es la única forma de
  quitarlos.
- **Una escritura del catálogo caduca la hoja de vida en caché**: imprime el uso del tipo y la ficha
  del modelo.
- **Dos mutaciones sobrevivían y pidieron su prueba**: la hoja de vida devolviendo una ficha vacía en
  lugar de la del modelo, y el catálogo sin caducar la hoja de vida. Las dos tenían cobertura
  alrededor y ninguna encima.
- ⚠️ **Un equipo instalado no se puede editar desde la aplicación.** El API lo permite
  (`PATCH /client-equipments/{id}`) y el frontend tiene la mutación escrita, pero **ninguna pantalla la
  usa**: el código interno y el proveedor solo se escriben en el alta. Anotado en
  [[deuda-tecnica-y-riesgos]].

## El historial completo, construido (2026-10-05)

Mergeado por `26e64149`. Cada línea del historial dice **qué se hizo** —los procedimientos del
reporte— y **quién lo hizo** —el nombre del ingeniero de la orden—, congelados al cerrarse, y la hoja
de vida los imprime como «Grace Hopper · Bolívar Bioingeniería Ltda.». Sin ingeniero, solo la
empresa; sin procedimientos, la raya.

- **`V17` añade las columnas y `V18` rellena las líneas anteriores**, aparte, como `V13` fue aparte
  de `V12`. **El primer intento las juntaba**, y la prueba que vuelve a ejecutar el relleno sobre datos
  chocó con las columnas ya creadas: un relleno que se quiere probar tiene que poder ejecutarse dos
  veces, y un `ALTER TABLE` no puede. La limitación es la misma que la de `V13`: copia el ingeniero
  que la orden tiene **hoy**.
- **`report` pasa a leer de `person`**, por el nombre del ingeniero. No hay ciclo: `person` no depende
  de nadie. El oyente del cierre ya leía la orden y los reportes; ahora también el reporte que se
  cierra y la persona.
- **El nombre completo se compone en un solo sitio**: `PersonCommunicationResponse.nombreCompleto()`,
  en el modelo que `person` publica. Hasta esta tanda estaba copiado en el servicio del responsable de
  un área, y con el ingeniero habría sido la segunda copia.
- ⚠️ **El arnés de mutación tenía un error y dejó el código mutado.** Tras correr las pruebas copiaba
  el archivo mutado encima de la copia de seguridad, de modo que las cuatro mutaciones del backend
  quedaron aplicadas. Se vio revisando el estado del árbol; se deshicieron una a una y la batería
  entera volvió a pasar. **Los resultados eran válidos** —la copia se tomaba antes de mutar—; lo que
  fallaba era la restauración. Es la cuarta forma del mismo problema que el proyecto ya conoce: **un
  arnés que no comprueba que dejó las cosas como estaban** se lee como si lo hubiera hecho.

## La numeración, construida (2026-10-05)

Mergeada por `d2793614`. Cada cliente tiene su sigla (`V19`) y cada equipo su número (`V20`), y la hoja
de vida lo lleva en las dos bandas: `HV-CLI-0001` en la base de desarrollo.

- **La sigla se genera en Java y en SQL, y una prueba exige que coincidan.** La de SQL —`sigla_base`,
  que se queda en la base— dio sigla a los clientes que ya existían; la de Java, a los que se crean.
  Una mutación que cambiaba la regla solo en Java la cazó esa prueba, que es para lo que está.
- **El número lo asigna la base, con un trigger.** Es un contador por cliente, y el `UPDATE ...
  RETURNING` sobre la fila del cliente lo bloquea hasta el final de la transacción: dos altas a la vez
  esperan una a la otra, y si el alta falla el contador vuelve atrás con ella. Hibernate lo relee con
  `@Generated(event = INSERT)`, y **el adaptador hace `saveAndFlush`**: con `save` el `INSERT` ocurría
  al confirmar y la unidad volvía sin número —una mutación lo comprobó—.
- **Son los primeros triggers del esquema**, y no rompen la regla de que lo demás vive en el servicio:
  uno es la mitad de un contador y el otro cubre lo que entra por fuera del dominio. Ver
  [[esquema-bd-malphasos]].
- **Los equipos que ya existían se numeraron por serie** dentro de cada cliente: **ninguna tabla guarda
  la fecha de alta**, y es la primera vez que eso importa. Anotado en [[deuda-tecnica-y-riesgos]].
- **La sigla tiene ruta propia**, `PATCH /clients/{id}/acronym`, con `ERR_CLIENT_008` y 409 si la tiene
  otro cliente. La edición del cliente la manda solo si cambió y después de los demás datos.

## Decisiones, todas tomadas el 2026-10-05

Las once que esta nota dejaba abiertas se contestaron el mismo día que se escribió. **Mandan sobre lo
que dicen las secciones de arriba** donde no coincidan; las secciones conservan el razonamiento.

| # | Pregunta | Decisión |
|---|---|---|
| 1 | ¿Quién es el profesional responsable? | **El encargado del área**, y si el área no tiene, **el de la sede** |
| 2 | ¿Fijo y móvil se distinguen? | **No.** Una sola casilla, «Teléfonos», con los que haya. Sin migración |
| 3 | ¿Datos eléctricos en el tipo o en el modelo? | **En el modelo, los cinco**, moviendo voltaje y amperaje desde el tipo |
| 4 | ¿Cómo se numera la hoja? | **`HV-<sigla>-0001`**: una **sigla nueva por cliente** y el consecutivo **por cliente**. Ver abajo |
| 5 | ¿Qué es la fecha de actualización? | **La fecha de impresión.** Sin migración |
| 6 | ¿Dónde se guarda la foto? | **Aplazada.** El recuadro sale vacío y no frena nada |
| 7 | ¿Descripción y responsable del historial? | **Procedimientos** del reporte, y el **nombre del ingeniero** de la orden, los dos congelados al cerrar |
| 8 | ¿Qué precios se imprimen? | **Solo el valor de compra.** El valor unitario de mantenimiento no sale: es precio interno |
| 9 | ¿El QR se abre sin cuenta? | **No.** Abre la hoja en la aplicación y pide iniciar sesión. Nada nuevo de seguridad |
| 10 | ¿Navegador o PDF del servidor? | **Navegador.** El PDF del servidor, si llega, llega con RF-17 |
| 11 | ¿Siguen vigentes los datos de Bolívar? | **Casi**: ya **no hay teléfonos fijos**. Ver abajo |

Y tres que no estaban en la lista y salieron al contestarla:

- **Uso y limpieza cotidiana van en el tipo**, como proponía la tabla de campos.
- **El orden de las tandas cambia**: **la pantalla y la impresión van primero**, con «—» en lo que aún no
  existe, para que el usuario vea el diseño con datos reales cuanto antes. Las tandas de datos la van
  llenando.
- **Dónde viven los datos de Bolívar**: en `application.yml` y no en el `.env`. Ver abajo.

### La sigla del cliente y el número de hoja

- **Una columna nueva en `cliente`**: la sigla, de **3 a 6 caracteres**, mayúsculas y dígitos,
  empezando por letra, y **única** entre clientes.
- **Se genera sola al crear el cliente, a partir de la razón social** —nadie la escribe—, y es
  **editable** después desde su ficha, por si sale fea. **No se regenera al corregir la razón social**:
  nace con el cliente y no sigue a su nombre, porque corregir una tilde no debe cambiar cómo se numeran
  sus equipos. Las tres cosas, decididas por el usuario el 2026-10-05.
- **La regla**, que es la misma para el alta y para la migración de los clientes que ya existen —de
  modo que el código y la migración generan lo mismo, y una prueba debe exigirlo—:
  1. mayúsculas, sin tildes ni puntuación;
  2. fuera la forma jurídica (S.A.S., S.A., LTDA., E.U., S. EN C.) y las palabras vacías (DE, DEL, LA,
     LAS, LOS, Y, E, EN);
  3. la inicial de cada palabra que queda, hasta 6;
  4. si salen menos de 3, se completa con las letras siguientes de la última palabra;
  5. si ya existe, se le añade un número: `CDN`, `CDN2`, `CDN3`.

  | Razón social | Sigla |
  |---|---|
  | Clínica Dermatológica del Norte S.A.S. | `CDN` |
  | Hospital Universitario San Ignacio | `HUSI` |
  | Bolívar Bioingeniería Ltda. | `BBI` |
  | Dermacenter S.A.S. | `DER` |
  | Clínica del Dolor y Neurología S.A., con `CDN` ya usada | `CDN2` |

  ⚠️ **El paso 5 tiene una carrera**: dos altas simultáneas con la misma sigla base pueden elegir el
  mismo número. ~~Lo para el índice único y el servicio reintenta con el siguiente.~~ **Corregido al
  construirlo, el 2026-10-05: no se reintenta, se serializa.** En PostgreSQL una transacción que falla
  no se puede reintentar desde dentro, de modo que el reintento habría tenido que ser de otra capa. Un
  candado de transacción (`pg_advisory_xact_lock`) antes de buscar la sigla libre hace que la segunda
  alta espere y la encuentre ocupada.
- **El consecutivo cuenta por cliente**: `HV-CLD-0001`, `HV-CLD-0002`. Exige un contador por cliente que
  no se pise con dos altas a la vez —un `SELECT ... FOR UPDATE` sobre la fila del cliente, o un contador
  propio en ella—; una secuencia de PostgreSQL no sirve, porque es global. **Construido el 2026-10-05
  como contador en la fila y trigger**: ver «La numeración, construida» abajo.
- **El número se asigna al registrar el equipo y no cambia**. Los equipos que ya existen reciben el suyo
  en la migración, por orden de alta dentro de cada cliente.
- **Cambiar la sigla de un cliente no renumera sus hojas**: las ya numeradas **conservan el número
  viejo para siempre** y solo los equipos nuevos salen con la sigla nueva —decidido por el usuario el
  mismo día—. Es la opción que no deja ningún papel impreso con un número que ya no está en el sistema,
  y obliga a **guardar el número entero en la unidad**, sigla incluida, no a componerlo al leer.
- **Un equipo trasladado no cambia de cliente** —es una regla del sistema, ver
  [[regla-traslado-mismo-cliente]]—, de modo que la sigla de su hoja nunca deja de ser la de su dueño.

### Los datos de Bolívar, vigentes al 2026-10-05

| | |
|---|---|
| Nombre | Bolívar Bioingeniería Ltda. |
| Dirección | Calle 77 C N.º 100 B 46, Villas del Madrigal |
| Ciudad | Bogotá |
| Teléfono fijo | **Ninguno** — los dos de 2019 ya no existen |
| Móvil | 312 305 5157 |
| Correo | bolivarbioingenieria@gmail.com |

**Viven en `application.yml`, bajo `app.empresa.*`**, con estos valores versionados y cada uno
sobrescribible por una variable de entorno, como el origen de CORS. **No en el `.env`**: ese archivo es
para secretos y para lo que cambia de una máquina a otra, y está fuera de git, de modo que al clonar el
proyecto los datos no estarían. Estos ni son secretos ni cambian por máquina. El backend los entrega
**dentro de la respuesta de la hoja de vida**, para que la pantalla y la impresión los tengan en la
misma petición y el frontend no guarde una copia. Una tabla editable desde la aplicación solo valdría
la pena si cambiaran a menudo o si el sistema sirviera algún día a más de una empresa.

## Notas relacionadas

[[hoja-de-vida]] · [[congelar-una-referencia-historica]] · [[dominio-equipo-mantenimiento]] · [[dominio-reporte-servicio]] · [[filtrado-por-dueno]] · [[sistema-de-diseno-malphasos]] · [[hoja-de-ruta-producto]] · [[esquema-bd-malphasos]]
