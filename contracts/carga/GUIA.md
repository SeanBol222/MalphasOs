# Carga de datos — formato `malphasos-carga/1`

Cómo se le entregan a MalphasOS los datos de otro sistema: tres clases de archivo JSON —el catálogo de
la empresa, cada cliente con sus equipos y el historial de cada cliente—, con un mismo formato. Está pensado para que un
conversor —escrito en cualquier lenguaje, junto al sistema de origen— produzca el archivo, y MalphasOS
lo valide y lo cargue.

| Archivo | Qué es |
|---|---|
| `malphasos-carga-1.schema.json` | El contrato: JSON Schema 2020-12. Lo que no cumple el esquema no se carga |
| `ejemplo-1-catalogo.json` · `ejemplo-2-cliente.json` · `ejemplo-3-historial.json` | Una carga completa con datos inventados, en sus tres archivos. Recorre todas las secciones |
| `GUIA.md` | Esto: lo que el esquema no puede decir |

> **Estado, 2026-11-03.** Lo que existe es **el formato**. La validación, la carga y su pantalla están
> **previstas para la versión 1.1.0** y no construidas: lo que esta guía dice que MalphasOS *hará* al
> cargar es el diseño, no una descripción del código.

## Los tres archivos

Cada archivo dice qué es en `contenido`, y el esquema rechaza lo que no le corresponde.

| `contenido` | Qué lleva | Cuántos |
|---|---|---|
| `catalogo` | Lo de la empresa, que sirve a todos los clientes: marcas, fabricantes, protocolos, tipos de equipo, modelos, patrones, y sus **ingenieros y administradores** | Uno |
| `cliente` | **Un** cliente con sus supervisores, sedes, áreas y equipos | Uno por cliente |
| `historial` | Las órdenes ejecutadas de **un** cliente, con sus reportes. Dice de qué cliente es en `cliente` | Uno por cliente |

**Se cargan en ese orden**, porque cada uno nombra cosas del anterior: un equipo, su modelo; una orden,
su ingeniero; un reporte, su equipo. Un archivo puede nombrar por su ref lo que entró en una carga
anterior **del mismo origen**, y la validación lo busca allí.

La división es para trabajar **cliente por cliente**: se monta y se depura todo en una instalación de
prueba, y en la definitiva se cargan los mismos archivos en el mismo orden. Las refs se recuerdan en cada
instalación, de modo que la definitiva no necesita nada de la de prueba.

## Cómo se usará

1. **Convertir.** El conversor lee el sistema de origen y escribe el archivo.
2. **Validar.** MalphasOS lo revisa sin escribir nada y devuelve un informe: cuántos registros de cada
   sección entrarían, cuántos ya existen y cada error con su ruta —`ordenes[3].reportes[0].equipo: la
   ref «eq-2789» no existe en el archivo ni en MalphasOS`—.
3. **Corregir y repetir** hasta que el informe salga limpio. Se corrige el conversor, no el archivo a
   mano: así la próxima carga sale bien sola.
4. **Aplicar.** Todo o nada, en una sola transacción. Antes, un respaldo
   (`respaldo.sh ahora`, ver `DESPLIEGUE.md`).

Solo el SuperUsuario podrá cargar.

## Las reglas generales

**Todo se nombra por `ref`, nunca por identificador de MalphasOS.** Cada registro lleva una `ref` que
pone el conversor, y los demás lo nombran por ella: un equipo dice en qué `area` está, un reporte de qué
`equipo` es. Una `ref` es **única en todo el archivo** —no por sección—, y **estable**: la misma cosa
lleva siempre la misma ref de una carga a otra. Lo más sencillo es derivarla del identificador que el
registro tiene en el sistema de origen: `eq-2789`, `ot-3511`, `rep-11118`.

**`origen` dice de qué sistema vienen las refs.** MalphasOS recuerda qué registro creó cada ref bajo su
origen. Es lo que hace que **cargar dos veces no duplique nada**, y es imprescindible: una orden o un
reporte no tienen ninguna llave natural por la que reconocerlos, y un equipo sin serie tampoco.

**Lo que no se sabe, se omite.** No se escribe `"NT"`, `"NA"`, `"N/A"` ni `""`: un campo opcional
ausente es «no se sabe», y un texto como `"NT"` se guardaría tal cual y se imprimiría en los reportes.

**Las fechas van en ISO**: `2025-03-10`. Las fechas con hora llevan su zona: `2025-03-10T15:30:00-05:00`.

**Un archivo se puede volver a cargar.** Si el catálogo crece —un modelo nuevo que aparece al convertir
otro cliente—, se carga otra vez entero: lo que ya estaba se reconoce y solo entra lo nuevo.

## Lo que ya existe no se toca

Si un registro del archivo ya está en MalphasOS, **no se modifica**: se reutiliza, y el informe de la
validación lista las diferencias para que quien carga decida si corregirlas a mano. Lo que alguien
editó en MalphasOS nunca lo pisa una carga.

Un registro «ya está» si su ref se cargó antes bajo el mismo origen, o si no, si coincide su llave
natural. Los nombres se comparan sin tildes, sin mayúsculas y sin espacios de sobra.

| Sección | Llave natural |
|---|---|
| `marcas`, `fabricantes`, `protocolos`, `tiposEquipo` | el nombre |
| `modelos` | tipo, marca y nombre |
| `patrones` | la serie |
| `personas` | la cédula |
| `clientes` | el documento |
| sedes · áreas | el nombre, dentro de su cliente · de su sede |
| `equipos` | modelo y serie, si tiene serie |
| `ordenes` · reportes | **ninguna**: solo la ref |

## Lo que MalphasOS hará con cada sección

- **Personas**: entran como **fichas sin cuenta**. La cuenta se da después, persona por persona, con
  «Dar acceso». **Una carga nunca lleva contraseñas.** El primer correo será el de la cuenta.
- **Clientes**: si no traen `sigla`, se genera de la razón social, como en la pantalla.
- **Equipos**: reciben su número de hoja de vida, `HV-<sigla>-0001`, como en un alta normal. Uno
  `retirado` entra dado de baja y con su historial.
- **Patrones**: el catálogo se queda con el certificado **más reciente**. Cada reporte usa el que estaba
  **vigente el día del servicio**, que es lo que imprime.
- **Órdenes**: entran **ejecutadas**. No se puede seguir trabajando en ellas, como en cualquier orden
  terminada.
- **Reportes**: entran **cerrados**, y MalphasOS compone y congela su documento con **el formato
  actual**. **Sin firmas**: el documento dice que es un reporte importado, en vez de dejar en blanco los
  recuadros de firma. El `numeroAnterior` se imprime junto al número de MalphasOS para poder buscarlo.
- **Lecturas**: se califican contra la tolerancia de la verificación del tipo, igual que al cerrar un
  reporte. Sin tolerancia, no se califican.

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
archivos: los tres cumplen el esquema y sus 1134 referencias tienen destino. Los datos no están en el repositorio. Lo que sigue es lo que cualquier conversor
desde ese sistema tendrá que resolver, y que el esquema no puede resolver por él.

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
  **parte en una por tipo**, con refs distintas y el mismo `numeroAnterior`. Diagnóstico cuenta como
  correctivo y la verificación metrológica sola, como calibración.
- **La periodicidad no viene**: se deduce de la fecha del próximo servicio.
- **Las verificaciones al ingreso tienen conceptos que MalphasOS no tiene** —temperatura, amperaje—:
  van a las `observaciones` del reporte. Y hay **«no conforme» sin observación**, que MalphasOS no
  admite: el conversor tiene que poner una.
- **Los pasos del protocolo son texto libre** en cada informe, con erratas que los multiplican —40
  textos distintos—. Unificarlos en el catálogo antes de cargar ahorra protocolos repetidos.
- **Un patrón aparece con varios certificados a lo largo de los años**, y a veces con dos códigos
  internos. Y **dos patrones distintos comparten serie**, que MalphasOS no admite.
- **Los informes no dicen cuándo vence un certificado**, y MalphasOS lo exige.
- **La ciudad no se escribe como en el catálogo** —con «D.C.», sin tilde—.
- **Las personas aparecen solo por su nombre**, con variantes —con y sin segundo nombre—, y **sin
  cédula**, que MalphasOS exige. Hay que conseguirlas aparte.
- **Casi ningún informe dice quién recibió**: el campo es opcional.

## Decisiones abiertas

Ninguna cambia la forma del archivo, pero sí lo que MalphasOS hace con él.

1. **El vencimiento de un certificado que no se conoce.** Propuesta: que el conversor ponga la fecha de
   calibración más un año y la validación lo avise.
2. **Dos patrones con la misma serie.** Propuesta: que la validación lo rechace y el conversor
   distinga las series —por ejemplo, añadiendo el código interno—.
3. **Una ciudad que no casa.** Propuesta: que la validación la rechace sugiriendo la más parecida, y el
   conversor lleve una tabla de equivalencias.
4. **Un equipo sin serie**, que MalphasOS hoy exige. Propuesta: permitir la serie vacía solo en lo
   importado, y que la hoja de vida diga «sin serie».
