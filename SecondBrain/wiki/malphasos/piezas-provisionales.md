---
name: piezas-provisionales
description: El registro de lo que MalphasOS lleva de forma provisional, a la espera de otra aplicación o de una decisión, y cómo se retira cada pieza sin dejar rastro
tags: [malphasos, provisional, mantenimiento, "describe:malphasos"]
estado: estable
updated: 2026-11-22
---

# Piezas provisionales

Lo que está en MalphasOS **sabiendo que se va a ir**: lo pidió un cliente para ya, y su sitio definitivo es
otro —una aplicación externa, otro módulo, una decisión pendiente—. Cada pieza se construye **aparte**, para
que retirarla sea borrar y no reescribir, y se anota aquí con la receta exacta.

**Antes de retirar una**: seguir su receta, correr las dos baterías y anotar la retirada en esta nota
—tachada con su fecha, sin borrar la fila—, en [[decisiones-tecnicas-malphasos]] y en `log.md`. **Antes de
añadir una**: construirla detrás de una interfaz que el resto del sistema use solo si alguien la implementa, y
escribir aquí su receta el mismo día.

| Pieza | Desde | Por qué es provisional | Se va cuando |
|---|---|---|---|
| [El resumen de la orden en .xlsx](#el-resumen-de-la-orden-en-xlsx) | 2026-11-22, v1.2.1 | Un cliente lo necesita ya; lo hará una aplicación externa | Exista esa aplicación |

## El resumen de la orden en .xlsx

**Qué hace**: el ZIP de los informes de una orden —«Descargar informes (ZIP)» en su ficha,
`GET /v1/api/reports/archive`— lleva, además del resumen en PDF, **`orden-XXXXXX.xlsx`**: el mismo resumen como hoja
de cálculo, una fila por equipo atendido, con la marca y el modelo separados y el cierre como fecha, cabecera
fija y filtros. Formato Office Open XML (ISO/IEC 29500), que abren Excel, LibreOffice y Google Sheets.

**Por qué es provisional**: lo pidió el usuario el 2026-11-22 porque un cliente lo necesita, y dijo que en el
futuro lo hará **una aplicación externa**. Por eso vive en un módulo propio y no dentro de `report`.

**Cómo está enganchado**:

- El módulo **`orderworkbook`** (`malphasos/src/main/java/com/malphasos/malphasos/orderworkbook/`): una sola
  clase, `OrderSummaryWorkbook`, que implementa la interfaz de abajo y escribe el .xlsx con **fastexcel**. Su
  `package-info.java` repite esta receta.
- La interfaz **`WorkOrderArchiveExtension`**, en `report/application/ports/output/`: «un archivo más para el
  ZIP, hecho del mismo resumen que su PDF». `ServiceReportService` la recibe como **lista opcional por campo**
  (`@Autowired(required = false)`, vacía por defecto): sin implementaciones, el ZIP es el de siempre.
- Para separar la marca, el modelo y la fecha, `WorkOrderSummarySheet.Fila` ganó tres campos —`marca`,
  `modelo`, `cerradoEl`— que el PDF no usa.

**Cómo se retira**:

1. Borrar `malphasos/src/main/java/com/malphasos/malphasos/orderworkbook/` y
   `malphasos/src/test/java/com/malphasos/malphasos/orderworkbook/`.
2. En `malphasos/pom.xml`, quitar la propiedad `fastexcel.version` y las dos dependencias `org.dhatim`
   —`fastexcel` y `fastexcel-reader`, esta de pruebas—, marcadas **PROVISIONAL**.
3. Correr la batería del backend. Debe pasar sin tocar nada más: la prueba del servicio que comprueba el
   enganche, `ServiceReportServiceTest.conUnaExtension`, pone su propia extensión y no depende del módulo.
4. **Opcional**: si ninguna otra pieza usa `WorkOrderArchiveExtension`, se puede quitar también —la interfaz,
   el campo `extensionesDelZip` del servicio, la prueba `conUnaExtension`— y los tres campos de `Fila` con
   su prueba `lasPiezasSueltas`. No hace falta: sin implementaciones no hacen nada.
5. Si el CHANGELOG de esa versión lo menciona, decir que el ZIP deja de llevar el .xlsx.

**El frontend no cambia** ni al ponerlo ni al quitarlo: el botón descarga lo que el servidor meta en el ZIP.

Relacionadas: [[decisiones-tecnicas-malphasos]] · [[dominio-reporte-servicio]] · [[hoja-de-ruta-producto]]
