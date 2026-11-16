# Carga de datos — formato `malphasos-carga/1`

Cómo se le entregan a MalphasOS los datos de otro sistema: el catálogo de la empresa, cada cliente con
sus equipos y el historial de cada cliente, en archivos JSON con un mismo formato. Un conversor —escrito
en cualquier lenguaje, junto al sistema de origen— produce los archivos, y MalphasOS los valida y los
carga.

**La regla de todo el diseño: el detalle va en los archivos.** MalphasOS no deduce, no completa y no
recuerda nada de una carga: crea lo que no existe, ignora lo que ya existe y rechaza lo que no cuadra.
Todo lo que haya que limpiar, unificar o completar lo hace el conversor. Y la carga **no añade nada al
esquema de la base de datos**: usa lo que MalphasOS ya tiene.

| Archivo | Qué es |
|---|---|
| `malphasos-carga-1.schema.json` | El contrato: JSON Schema 2020-12. Lo que no cumple el esquema no se carga |
| `ejemplo-1-catalogo.json` · `ejemplo-2-cliente.json` · `ejemplo-3-historial.json` | Una carga completa con datos inventados. Recorre todas las secciones |
| `GUIA.md` | Esto: lo que el esquema no puede decir |

> **Estado, 2026-11-16.** Existen **el formato, la validación, la carga y su pantalla**, solo para el
> SuperUsuario: Administración › **Importar datos** sube los archivos —o el ZIP que los lleva todos—,
> enseña el informe agrupado por archivo y solo ofrece aplicar tras una validación limpia. Por debajo, `POST /v1/api/data-loads/validation` devuelve el informe
> sin escribir nada, y `POST /v1/api/data-loads` valida otra vez y escribe todo en una transacción —200
> con el informe, o 422 con él y nada escrito—. (Decía el 2026-11-07 que faltaba la pantalla; el
> 2026-11-03, que solo existía el formato, y unas horas antes de esa nota, que no existía la carga. Hasta
> el 2026-11-16 una carga era de uno a tres archivos, y la de un sistema entero llevaba un paso por
> cliente y otro, a mano, para las firmas: ver «Cómo se usa».)

## Los archivos

Cada archivo dice qué es en `contenido`, y el esquema rechaza lo que no le corresponde.

| `contenido` | Qué lleva | Cuántos |
|---|---|---|
| `catalogo` | Lo de la empresa, que sirve a todos los clientes: marcas, fabricantes, protocolos, tipos de equipo, modelos, patrones, y sus **ingenieros y administradores** | Uno |
| `cliente` | **Un** cliente con sus supervisores, sedes, áreas y equipos | Uno por cliente |
| `historial` | Las órdenes ejecutadas de **un** cliente, con sus reportes. Dice de qué cliente es en `cliente` | Uno por cliente |

**Una carga lleva el catálogo y cualquier número de clientes, cada uno con su historial**: como mucho
un catálogo, y cada cliente y cada historial una sola vez. **Los archivos que se nombran entre sí van en
la misma carga**: un equipo nombra su modelo, una orden su ingeniero, un reporte su equipo, y MalphasOS
no recuerda las refs de una carga a otra. Lo más sencillo es **subirlo todo junto**; si un cliente se
carga después, se sube con el catálogo, y lo que del catálogo ya está se ignora.

**El paquete.** Los archivos se suben sueltos o dentro de **un ZIP**, que es lo que conviene que escriba
el conversor: un solo archivo, de una sola conversión, que no se puede subir a medias. Dentro, la
carpeta no importa —solo los `.json`, y lo demás se ignora con un aviso—, pero el informe nombra cada
archivo por su ruta dentro del ZIP, así que vale la pena que diga de quién es:

```
carga.zip
├── catalogo.json
└── clientes/
    ├── clinica-los-almendros/cliente.json
    ├── clinica-los-almendros/historial.json
    └── …
```

Hasta 500 archivos y 200 MB descomprimidos. El tamaño de la subida lo fija el servidor (20 MB por
archivo).

## Cómo se usa

1. **Convertir.** El conversor escribe el paquete: el catálogo, **con la firma de cada ingeniero**, y por
   cliente su archivo y su historial.
2. **Validar** el paquete en Administración › Importar datos. No escribe nada, y devuelve un informe:
   cuántos registros de cada sección entrarían, cuántos se ignoran porque ya existen, y cada error con su
   archivo y su ruta —`clientes/fana/historial.json · /ordenes/3/reportes/0/equipo: la ref «eq-2789» no
   está declarada`—.
3. **Corregir el conversor y repetir** hasta que el informe salga limpio. No se corrige el archivo a mano:
   así la próxima conversión sale bien sola.
4. **Aplicar.** Todo o nada, en una sola transacción: una carga que falla no deja nada, y se puede
   repetir. Antes, un respaldo (`respaldo.sh ahora`, ver `DESPLIEGUE.md`).

**La firma del ingeniero.** Los reportes del historial se cierran al cargar con la firma del ingeniero
de la orden, como cualquier cierre, y **un historial cuyo ingeniero no tenga firma se rechaza**: el
reporte se congela, y uno congelado no la recibiría después. La firma puede estar **guardada en
MalphasOS** —el ingeniero ya tenía cuenta y la dibujó— o **venir en la carga**, en el campo `firma` de su
persona en el catálogo: un PNG de hasta 256 KB, en base64. Si la persona ya tiene una, se queda con la
suya. Se rechaza también si la persona de la orden no es ingeniero.

(Hasta el 2026-11-16 la firma solo podía estar guardada, y por eso la carga de un sistema eran tres
pasos por lo menos: el catálogo solo, que cada ingeniero recibiera cuenta y dibujara su firma, y después
cada cliente con el catálogo otra vez. Con dieciséis clientes eran dieciocho pasos.)

7. **Completar las firmas de quien recibe**, si se quiere: un reporte cerrado admite la firma de quien
   recibe después, desde su pantalla, como cualquier reporte —con la opción de firmar todos los de la
   orden o del área a la vez—. Y el supervisor puede aprobar las órdenes.

Solo el SuperUsuario podrá cargar. Se monta y se depura todo en una instalación de prueba, y en la
definitiva se repiten los mismos pasos con los mismos archivos.

## Las reglas generales

**Todo se nombra por `ref`, nunca por identificador de MalphasOS.** Cada registro lleva una `ref` que
pone el conversor, y los demás lo nombran por ella. Una `ref` es **única entre los archivos de una
carga**, y MalphasOS no la guarda: solo sirve para enlazar. Lo más cómodo es derivarla del identificador
del sistema de origen —`eq-2789`, `ot-3511`, `rep-11118`—, que además hace que dos conversiones del
mismo sistema den las mismas refs.

**Lo que no se sabe, se omite.** No se escribe `"NT"`, `"NA"`, `"N/A"` ni `""`: un campo opcional
ausente es «no se sabe», y un texto como `"NT"` se guardaría tal cual y se imprimiría en los reportes.

**Lo que no tiene campo va en las observaciones.** El número que un reporte o una orden tenían en el
sistema de origen, por ejemplo, lo escribe el conversor en las `observaciones` del reporte —«N.º
anterior: reporte 11118, orden 3511.»—: se ve en pantalla y en el PDF.

**Los largos son los de MalphasOS.** Cada texto del esquema lleva el largo máximo de su columna —50
el nombre de un tipo de equipo, 120 el de un protocolo, 500 los procedimientos de un reporte—, y
cédula, documento, teléfono, sigla y números, el formato y el tamaño que MalphasOS admite. Un texto en
blanco no vale. Así **lo que el esquema aprueba, la carga lo acepta**: un archivo que pasa la
validación no puede fallar después por un largo. (Hasta el 2026-11-05 el esquema dejaba 255 y 4000
caracteres en todos los textos, y un nombre de protocolo de 140 salía válido y la carga lo rechazaba.)
Lo que se pase de largo **se arregla en el conversor**, no recortándolo a ciegas: un texto cortado se
imprimiría a medias en el reporte.

**Quien decide es el validador de MalphasOS**, no el de cada lenguaje. El esquema es el mismo, pero
cada librería de JSON Schema interpreta a su manera los `format`: un correo `pend-001@x.invalid` pasa
en Python y MalphasOS lo rechaza. Un conversor puede validar contra el esquema mientras se escribe, pero
el archivo está bien cuando lo dice `data-loads/validation`. Para un dato provisional, un dominio que
exista para eso: `example.com`.

**Las fechas van en ISO**: `2025-03-10`. Las fechas con hora llevan su zona: `2025-03-10T15:30:00-05:00`.

## Lo que ya existe se ignora

Si un registro de la carga ya está en MalphasOS, **se ignora**: no se modifica ni se duplica, y lo que lo
nombra en la carga apunta al que ya existe. El informe de la validación lo cuenta. Se reconoce por su
llave natural, comparando los nombres sin tildes, sin mayúsculas y sin espacios de sobra.

| Sección | Llave natural |
|---|---|
| `marcas`, `fabricantes`, `protocolos`, `tiposEquipo` | el nombre |
| `modelos` | tipo, marca y nombre |
| `patrones` | la serie |
| `personas` | la cédula |
| `clientes` | el documento |
| sedes · áreas | el nombre, dentro de su cliente · de su sede |
| `equipos` | modelo y serie |
| `ordenes` · reportes | **ninguna** |

**Dos casos no se pueden reconocer, y se rechazan para no duplicar:**

- **Un historial de un cliente que ya tiene órdenes** en las fechas del archivo. Como la carga es todo
  o nada, una carga fallida se repite sin problema; lo que se impide es cargar dos veces una que salió
  bien.
- **Un equipo sin serie para un cliente que ya existía**, porque no hay con qué saber si ya está. Un
  cliente nuevo se carga entero, con sus equipos sin serie.

**Y un modelo y una serie son de un solo equipo en toda la base**, no por cliente. Se rechazan dos
equipos de la carga con los mismos —aunque sean de dos clientes— y uno que ya es de **otro** cliente:
la carga lo tomaría por ese, y su historial quedaría en el cliente equivocado. Si de verdad son dos
aparatos, a uno se le quita la serie en el archivo y recibe la provisional.

## Lo que MalphasOS hace con cada sección

- **Personas**: entran como **fichas sin cuenta**. La cuenta se da después, persona por persona, con
  «Dar acceso». **Una carga nunca lleva contraseñas.** El primer correo será el de la cuenta. La
  **firma** de un ingeniero o un administrador se guarda como si la hubiera dibujado en su cuenta, si no
  tiene ya una; un supervisor no lleva firma.
- **Clientes**: si no traen `sigla`, se genera de la razón social, como en la pantalla.
- **Equipos**: reciben su número de hoja de vida, `HV-<sigla>-0001`, como en un alta normal. **Uno sin
  serie recibe una provisional**, `P:` y los seis primeros caracteres de su identificador —`P:3f9a1c`—,
  y es **la regla de todo equipo**, no solo de los cargados: el alta desde la pantalla hace lo mismo. Se
  cambia por la real cuando alguien lea la placa. Uno `retirado` entra dado de baja, con su historial.
- **Patrones**: el catálogo se queda con el certificado **más reciente**. Cada reporte usa el que estaba
  **vigente el día del servicio**, que es lo que imprime.
- **Órdenes**: entran **ejecutadas**. No se puede seguir trabajando en ellas, como en cualquier orden
  terminada.
- **Reportes**: entran **cerrados**, y MalphasOS los cierra como cualquier otro: compone y congela su
  documento con **el formato actual** y la firma guardada del ingeniero. El documento **no dice que es
  importado**: sale como cualquier reporte, con los recuadros de las firmas que falten vacíos.
- **Lecturas**: se califican contra la tolerancia de la verificación del tipo, igual que al cerrar un
  reporte. Sin tolerancia, no se califican.
- **Puntos anteriores**: una verificación con puntos fijos declara en `puntos` los que se usan hoy y en
  `puntosAnteriores` los que se usaron antes. Los anteriores entran **retirados**, como cuando alguien
  cambia los puntos de un tipo desde la pantalla: ningún reporte nuevo los pide, y las lecturas viejas
  siguen apuntándolos. Una lectura histórica se toma en uno u otro, y su reporte entra cerrado aunque
  no cubra los puntos de hoy: **se carga lo que se midió**, no lo que el tipo pediría ahora.
- **Pasos de un reporte histórico**: entran los que trae, hechos o no aplica, aunque el tipo tenga hoy
  otros. Un reporte nuevo copiará los del tipo; uno cargado no los recibe.
- **Lecturas de un reporte histórico**: un reporte puede entrar **sin lecturas** aunque su tipo se
  verifique. Pasa con los informes llenados en una plantilla sin metrología. Al cerrar un reporte nuevo
  se exigen; uno cargado entra con las que tiene.
- **Un tipo que ya existe no cambia**: la carga no le añade verificaciones ni protocolos. Si el archivo
  le declara una magnitud que el tipo de MalphasOS no verifica, la carga se rechaza; se añade desde el
  catálogo antes de cargar.

**Lo que solo se ve al escribir.** La validación no lo puede ver todo, y la carga rechaza entera —422,
nada escrito— con el sitio del archivo: una sigla que ya es de otro cliente, una lectura en un punto que
no está ni en `puntos` ni en `puntosAnteriores`, una regla de un agregado —una calibración posterior al
vencimiento de su certificado— o una restricción de la base, que llega con su mensaje.

## Magnitudes y unidades

Las verificaciones nombran la magnitud por su **código** y la unidad por su **símbolo**, tal como los
trae MalphasOS. Las magnitudes no se cargan, porque ya están sembradas:

| Código | Magnitud | Unidades |
|---|---|---|
| `acidez` | Acidez | `pH` |
| `concentracion` | Concentración | `mg/L` `%` `ppm` |
| `conductividad` | Conductividad eléctrica | `µS/cm` |
| `corriente_electrica` | Corriente eléctrica | `mA` `A` `µA` |
| `energia` | Energía | `J` |
| `flujo` | Flujo | `mL/min` `L/min` `mL/h` |
| `frecuencia` | Frecuencia | `lpm` `rpm_resp` `Hz` |
| `humedad_relativa` | Humedad relativa | `%` `%HR` |
| `iluminancia` | Iluminancia | `lx` |
| `longitud` | Longitud | `mm` `cm` `m` |
| `masa` | Masa | `kg` `mg` `g` |
| `potencia` | Potencia | `W` `kW` |
| `presion` | Presión | `cmH2O` `bar` `mmHg` `psi` `kPa` |
| `resistencia_electrica` | Resistencia eléctrica | `kΩ` `Ω` |
| `saturacion_oxigeno` | Saturación de oxígeno | `%SpO2` |
| `temperatura` | Temperatura | `K` `°C` `°F` |
| `tension_electrica` | Tensión eléctrica | `mV` `V` |
| `tiempo` | Tiempo | `s` `h` `min` |
| `velocidad_rotacion` | Velocidad de rotación | `rpm` |
| `volumen` | Volumen | `µL` `L` `mL` |

## Lo que enseñaron los informes de la aplicación anterior

El formato se probó convirtiendo **129 informes reales** de un cliente, exportados en Excel, a sus tres
archivos: los tres cumplen el esquema y sus 1134 referencias tienen destino. Los datos no están en el
repositorio. Lo que sigue es lo que cualquier conversor desde ese sistema tendrá que resolver, y que el
esquema no puede resolver por él.

- **Cuatro plantillas**, con las filas en sitios distintos y la página 2 a veces ausente —los de 2022
  son de una sola página—. **Leer por etiqueta, no por celda.** La plantilla trae además columnas
  ocultas con una copia de las lecturas para la gráfica, y en una hay basura de otra: solo valen las
  columnas visibles.
- **El equipo se describe de nuevo en cada informe, y no siempre igual**: el mismo equipo aparece con
  dos nombres de tipo o dos series. En la carga se describe **una vez**, con lo del informe más reciente.
  El identificador estable es el **consecutivo del sistema**, no la serie.
- **La mitad de los equipos no tiene serie** (`NT`): se omite.
- **Un área llamada «baja por mal funcionamiento»** no es un área: es un equipo `retirado`, que se queda
  en el área en que estaba.
- **Una orden reúne servicios de varios tipos**, y en MalphasOS el tipo es de la orden. El conversor la
  **parte en una por tipo**, con refs distintas. Diagnóstico cuenta como correctivo y la verificación
  metrológica sola, como calibración.
- **La periodicidad no viene**: se deduce de la fecha del próximo servicio.
- **Las verificaciones al ingreso tienen conceptos que MalphasOS no tiene** —temperatura, amperaje—:
  van a las `observaciones` del reporte. Y hay **«no conforme» sin observación**, que MalphasOS no
  admite: el conversor tiene que poner una. **También en los accesorios**: uno no conforme lleva su
  `observacion`. El esquema lo exige desde el 2026-11-08; hasta entonces lo pedía solo para el estado
  físico y el operativo, y la primera carga real pasó la validación y se rechazó al aplicarla.
- **Un informe preventivo marcado también «garantía»**: en MalphasOS solo un correctivo va en garantía,
  y el esquema lo exige desde el 2026-11-08. El conversor quita la garantía de una orden que no es
  correctiva y lo avisa.
- **Los pasos del protocolo son texto libre** en cada informe, con erratas que los multiplican, y
  **cambian con la versión de la plantilla**: el mismo paso se llamó «Limpieza técnica final» hasta 2024
  y «Limpieza general» después, y el sistema neumático de un tensiómetro tuvo dos redacciones. Los 129
  informes traían 52 textos distintos, que quedaron en 35 protocolos: cada redacción vieja de un paso que
  sigue existiendo apunta al de hoy, y la que no tiene equivalente se queda con la suya. **Cada tipo lleva
  los pasos de su informe más reciente**, que son los que se copiarán a un reporte nuevo; un reporte
  histórico trae en `pasos` los que se hicieron entonces, aunque no sean los de hoy. Y **hubo una plantilla
  equivocada**: un termómetro con los pasos de un autoclave y de un otoscopio, que se quitaron.
- **Las series de relleno**: «N/R», «NR», «SN», «NT-LILA». No son series —dicen que no la hay, a veces
  con el color de la etiqueta— y se omiten. Y **la misma serie en dos equipos del mismo modelo**, catorce
  veces en dieciséis clientes, una de ellas en dos clientes distintos: el conversor deja la serie en el de
  consecutivo más bajo y los demás entran con la provisional, avisados. Unirlos sería adivinar que son el
  mismo aparato.
- **El mismo equipo dos veces en una orden**: dos visitas, un mes aparte, con el mismo número. En
  MalphasOS una orden tiene un reporte por equipo, y el conversor la parte en una por visita. La validación
  no lo veía hasta el 2026-11-16, y la base lo rechazó al aplicar.
- **La unidad escrita no casa con los valores**: básculas pesa bebé marcadas «Kg» con lecturas de 100 a
  1800, y «g» con lecturas de 0,1 a 10. El conversor las lleva todas a gramos por la escala de los valores,
  porque un tipo se verifica una sola vez en cada magnitud.
- **Un patrón es su código interno**: la serie se escribe distinta de un informe a otro, o «NT», y el
  mismo instrumento aparece con varios certificados a lo largo de los años. Y **dos patrones distintos
  comparten serie**, que MalphasOS no admite.
- **Los informes no dicen cuándo vence un certificado**, y MalphasOS lo exige.
- **La ciudad no se escribe como en el catálogo** —con «D.C.», sin tilde—.
- **Las personas aparecen solo por su nombre**, con variantes —con y sin segundo nombre—, y **sin
  cédula**, que MalphasOS exige. Hay que conseguirlas aparte.
- **Casi ningún informe dice quién recibió**: el campo es opcional.
- **Los puntos de verificación cambian con los años**: un tensiómetro se verificó en 60–300 mmHg hasta
  2023 y en 40–200 desde 2025, y una gramera tiene un informe con 1800 g donde los demás dicen 1500.
  Por eso existe `puntosAnteriores`. Y **hay informes con las columnas invertidas** —el patrón en la del
  equipo—, que se reconocen porque el patrón de un punto fijo no marca valores redondos: el conversor
  los voltea, pero tiene que saber cuáles son.

## Decisiones tomadas (2026-11-03)

Todas las decidió el usuario.

1. **Ningún cambio en el esquema de la base de datos.** La carga solo pasa el historial a lo que
   MalphasOS ya tiene.
2. **Los archivos que se nombran se suben juntos, y lo repetido se ignora.** MalphasOS hace el menor
   esfuerzo posible: el detalle está en los archivos.
3. **Un historial se rechaza si el cliente ya tiene órdenes** en esas fechas.
4. **El número anterior va en las observaciones** del reporte.
5. **El ingeniero firma con su firma guardada, al cargar**: por eso el catálogo entra primero y los
   ingenieros guardan su firma antes del historial. **La de quien recibe se pone después**, como en
   cualquier reporte cerrado. **Corregida el 2026-11-16**: la firma del ingeniero puede venir también en
   la carga, y el catálogo ya no entra solo antes (ver la 11).
6. **El documento no dice que es importado**: sale como cualquier reporte.
7. **Un certificado sin vencimiento conocido**: el conversor pone la fecha de calibración **más un año**.
8. **Dos patrones con la misma serie**: la validación **los rechaza**, y quien carga corrige la serie en
   el conversor. No se inventan series que se imprimirían como la de la placa.
9. **Una ciudad que no casa con el catálogo**: la validación **la rechaza y sugiere** la más parecida.
   El conversor lleva una tabla de equivalencias. Nunca se elige una ciudad sola.
10. **Todo equipo sin serie recibe una provisional**, `P:` y el comienzo de su identificador, en la
    carga y en el alta desde la pantalla. La `P` dice que no es la de la placa.

### Y del 2026-11-16

11. **Una sola carga para todo**, pedida por el usuario tras cargar el primer catálogo de dieciséis
    clientes: el catálogo y todos los clientes juntos, en un ZIP, con la firma de cada ingeniero dentro.
    Validar y aplicar, y nada más. Los archivos sueltos siguen valiendo.
12. **La firma de la carga no sustituye a una guardada**: la que alguien dibujó en MalphasOS manda.
13. **Un modelo y una serie, o un equipo en una orden, que se repiten se rechazan al validar**, como todo
    lo que la base rechazaría al aplicar: lo que el esquema y la validación aprueban, la carga lo acepta.
