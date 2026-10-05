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
aprobó el **2026-10-05**. Es un plan y no una descripción: **nada de lo que sigue está construido
todavía**, salvo lo que la tabla de campos marca como «ya sale». La hoja de vida que existe hoy —el
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

1. **Los datos que ya existen**: ampliar `LifeSheet` con el responsable, los contactos del cliente y
   el estado derivado del historial. Sin migración.
2. **Los campos nuevos del catálogo**: una migración con riesgo, características y **los cinco datos
   eléctricos** en el modelo —moviendo voltaje y amperaje desde el tipo—; uso y limpieza en el tipo;
   código interno y proveedor en la unidad. Sus formularios en el catálogo y en el alta del equipo.
3. **El historial completo**: descripción y responsable congelados en `intervencion`, el oyente que
   los pasa y el relleno desde los reportes.
4. **El número de hoja de vida**: la secuencia, el relleno de los equipos que ya existen y su
   aparición en la respuesta.
5. **Los datos de Bolívar en configuración**, expuestos donde la pantalla pueda leerlos.
6. **La pantalla con el diseño y la impresión**: el componente, la hoja de impresión y el botón.
   **Es la tanda en la que el usuario ve el resultado**, y podría ir antes que la 2 a la 5, con los
   campos que falten mostrando «—».
7. **Lo que espera una decisión de infraestructura**: la foto (almacenamiento de archivos), el QR
   público (enlace firmado) y el PDF del servidor (junto con RF-17).
8. **Lo que espera otro requisito**: el protocolo preventivo, con RF-14.

## Decisiones abiertas, todas juntas

1. ¿«Profesional responsable» es el encargado del área, o del de la sede?
2. ¿Teléfono fijo y móvil se distinguen? Hoy la tabla no lo sabe.
3. ~~¿Los datos eléctricos van en el tipo o en el modelo?~~ **En el modelo, los cinco**, decidido por el usuario el 2026-10-05.
4. ¿El número de hoja de vida es consecutivo global o por cliente?
5. ¿Qué fecha es la «fecha de actualización»?
6. ¿Dónde se guarda la foto?
7. ¿Cuál texto del reporte es la descripción del historial, y el responsable sale con nombre?
8. ¿El valor de compra se imprime? ¿Y el valor unitario de mantenimiento queda fuera?
9. ¿El QR tiene que abrirse sin cuenta?
10. ¿Basta la impresión del navegador, o hace falta el PDF del servidor ya?
11. ¿Los datos de Bolívar de 2019 siguen vigentes?

## Notas relacionadas

[[hoja-de-vida]] · [[congelar-una-referencia-historica]] · [[dominio-equipo-mantenimiento]] · [[dominio-reporte-servicio]] · [[filtrado-por-dueno]] · [[sistema-de-diseno-malphasos]] · [[hoja-de-ruta-producto]] · [[esquema-bd-malphasos]]
