---
name: hoja-de-vida
description: Que es la hoja de vida de un equipo en MalphasOS -un compilado de solo lectura con cuatro secciones-, por que su historial si tiene tabla, y el oyente que lo escribe
tags: [dominio, equipos, reportes, hoja-de-vida, "describe:malphasos"]
source: malphasos/src/main/java/com/malphasos/malphasos/equipment/application/services/lifeSheet/LifeSheetService.java
estado: estable
updated: 2026-10-05
---

# La hoja de vida de un equipo

**Construida el 2026-10-04, en cuatro tandas, y cierra RF-22, RF-24, RF-26 y RF-27.** Antes de empezar
se construyó lo que no era: un historial de reportes presentado como hoja de vida. La aclaración del
usuario, que ordenó todo lo demás:

> La hoja de vida no sale de los reportes. Es un compilado de los datos del cliente, del tipo de
> equipo, la marca, el modelo, la serie, el fabricante. Lo único que sale del reporte es el historial
> de mantenimiento. Tú registras el equipo y se genera la hoja de vida, con su historia en cero desde
> el comienzo; cuando sale un mantenimiento, aparece ahí.

## Qué es: un documento compilado, no una entidad

**No tiene tabla.** Es un modelo de lectura —`LifeSheet`, en `equipment/application/model`— que se
arma leyendo datos que ya vivían repartidos, y **existe desde que el equipo se registra**. Sus
secciones son las cuatro que enumera el criterio de aceptación de RF-22, con esos nombres y en ese
orden, para que el documento y el requisito se lean uno contra el otro:

| Sección | De dónde sale | ¿Tabla propia? |
|---|---|---|
| **Identificación** | `equipo_cliente` · `area_servicio` · `sede` · `cliente` · `ciudad` | no |
| **Técnica** | `tipo_equipo` · `marca` · `modelo` | no |
| **Fabricante** | `fabricante` · `pais` | no |
| **Servicio técnico** | `intervencion`, escrita al cerrar cada reporte | **sí** |

Se sirve como **un solo recurso**, `GET /client-equipments/{id}/life-sheet`. Antes pintarla exigía
nueve peticiones cruzadas en el cliente, porque **ninguna respuesta del módulo trae nombres** —anotado
desde el 2026-09-26—; para este documento deja de pagarse.

### Es de solo lectura, y eso es la respuesta a RF-24

RF-24 pide «modificar/eliminar hoja de vida». **Decisión del usuario el 2026-10-04**: editarla no
significa nada, porque cada dato vive en su propio agregado y **se corrige donde vive** —la sede en la
sede, el modelo en el modelo— con las operaciones que ya existían. Un `PATCH` sobre la hoja de vida
tendría que repartir cambios entre cinco agregados de tres módulos, y cambiar la marca desde la hoja de
un equipo la cambiaría para **todos** los que la comparten sin que eso se vea desde ahí. La pantalla no
tiene un solo control editable, y una prueba lo exige.

### Lo que cuesta, a sabiendas

**Once consultas por documento.** La alternativa era una unión de nueve tablas, seis de ellas de
`client` y `location`: el mismo atajo que se rechazó ese mismo día al acotar el inventario por dueño,
porque cruzar tablas ajenas en SQL propio es como dos módulos acaban siendo uno. Una hoja de vida se
abre para leerla o imprimirla, no en un bucle. Si algún día duele, la salida es un modelo de lectura
con su tabla, no una consulta que atraviese las fronteras.

## El historial sí tiene tabla, y es una decisión de dominio

Los tres datos que RF-27 pide —fecha de servicio, tipo y resultado— **ya existían**: la fecha de
cierre y el resultado en `reporte_servicio`, el tipo de servicio en `orden_trabajo`. El historial se
podía derivar con una consulta. Lo que lo hace tabla es la segunda decisión del usuario:

> **Si un reporte finalizado se retira después, la intervención se queda: el mantenimiento ocurrió.**

La hoja de vida de un equipo médico es un documento con valor legal. Una consulta sobre los reportes
activos perdería esa línea; una tabla la conserva.

### Las tres columnas son una copia congelada

`intervencion` guarda lo que el servicio **fue**, no lo que su reporte diga hoy: es el mismo patrón que
el área en `orden_trabajo_equipo`, ver [[congelar-una-referencia-historica]]. Y trae la misma
consecuencia: **no hay foránea compuesta** contra el reporte, porque bloquearía corregirlo. Mutar
`V12` destapó un segundo motivo más fuerte: esa foránea **no se puede ni crear** sin añadir a
`reporte_servicio` un `UNIQUE` redundante por definición, cuyo único propósito sería sostener una
restricción que no debe existir.

**Una línea por reporte, y la restricción no es parcial**, al contrario que los cinco índices únicos
parciales del resto del esquema: retirar una intervención no debe dejar hueco para otra del mismo
reporte. Es además lo que hace al oyente idempotente sin que el oyente lo sepa.

### `V13` rellena el pasado

`V12` solo anota lo que se cierra a partir de ella. Un reporte que ya estaba finalizado antes no tenía
su línea, y nada lo avisaba: la hoja de vida diría «sin intervenciones» con mantenimientos hechos. **Se
encontró al arrancar `V12` sobre la base de desarrollo, no leyendo el código.** No se pudo arreglar
en `V12` porque ya estaba aplicada y cambiarla rompe la suma de comprobación de Flyway; `V13` escribe
exactamente lo que el oyente habría escrito, retirados incluidos, y es idempotente. Su limitación, que
lleva escrita: copia el tipo de servicio que la orden tiene **hoy**, porque el del momento del cierre
no se guardó en ninguna parte.

### `V14`: corregir un reporte reemplaza su línea, no la duplica

Un reporte cerrado no se edita: se retira y se abre otro sobre la misma orden y el mismo equipo. Y la
intervención sobrevive al reporte retirado, porque un mantenimiento hecho no se borra. **Las dos
decisiones eran buenas por separado y juntas dejaban dos líneas para un solo mantenimiento.** Se
detectó el mismo día que se construyó el historial, y la salida la eligió el usuario: **el sustituto
reemplaza al anterior**.

- La línea vieja **no se borra**: queda retirada y apuntando a la que la sustituyó
  (`k_id_reemplazada_por`), que es el rastro de que hubo una corrección.
- El reemplazo ocurre **al cerrar el sustituto, no al abrirlo**. Si se abriera y nunca se cerrara,
  reemplazar al abrir dejaría la hoja de vida sin un mantenimiento que sí ocurrió.
- Retirar un reporte sin abrir sustituto **no toca la línea**, y un mantenimiento en otra orden del
  mismo equipo **no reemplaza a nadie**: la regla es por par (orden, equipo).
- **Quién corrige a quién lo decide `report`**, que es quien conoce los reportes: el oyente busca los
  otros reportes cerrados del mismo par y se los pasa a `equipment` en el comando, y `equipment` sigue
  sin leer tablas ajenas. Solo se reemplazan líneas vigentes, de modo que en una cadena de correcciones
  cada una apunta a la que la sustituyó primero.

**El esquema lo sostiene con dos `CHECK`**: reemplazada implica retirada —con `CASE` y no con `OR`, por
la trampa del `NULL` que ya costó una vez— y ninguna línea se reemplaza a sí misma. **Y `V14` reconcilia
lo que `V13` ya rellenó** con la misma regla que el oyente —queda vigente la del reporte activo, o la
del cierre más reciente si todos están retirados—, de modo que el resultado no depende de si la
corrección fue antes o después de la migración. El contrato HTTP no cambió: la pantalla siempre mostró
solo las líneas vigentes.

Verificado recorriendo el flujo entero contra PostgreSQL real —cerrar, retirar, abrir otro, cerrar— y
encontrando una línea; seis mutaciones, las seis caen.

## El oyente: el primer consumidor de un evento de dominio

`ServiceReportFinishedListener` anota la intervención al cerrarse un reporte. **Es el primero**: hay
**51** eventos de dominio declarados en cinco módulos y, hasta él, ninguno escuchado.

**No vive donde la ERS lo reservó.** El documento dice que irá en
`equipment/infrastructure/input/listeners/`, y ahí habría creado un ciclo: consumir el evento obliga a
conocer `report`, y `report` ya importa `equipment`. Vive en `report` y entra por
`InterventionRecordingPort`, que `equipment` publica —el patrón de `PersonCommunicationPort` y
`ClientOwnershipPort`—. **El directorio reservado quedó vacío y conviene retirarlo**: uno reservado para
algo que acabó en otro sitio es indistinguible de uno olvidado.

**Síncrono y en la misma transacción, a propósito.** Cerrar el reporte y anotar la intervención son
atómicos. La alternativa, `AFTER_COMMIT`, fallaba peor: el reporte quedaría cerrado y su historial
vacío sin que nada lo notara, y ese hueco no se repara sin una operación manual que RF-26 prohíbe tener.
**No hay `POST` de intervenciones**: la única forma de que aparezca una línea es que un reporte se cierre.

**`Intervention` es un `record`, no un agregado de Generación 2.** Un agregado protege invariantes a
lo largo de los cambios que sufre, y una intervención no cambia: describe algo que pasó.

## Lo que queda abierto

- ~~**Corregir un reporte deja dos líneas en la hoja de vida.**~~ **Resuelto el 2026-10-04 con `V14`**,
  el mismo día: el sustituto reemplaza al anterior. Ver arriba.
- **El vocabulario del resultado no sale de la ERS**, y ahora lo arrastran dos tablas: cambiarlo cuesta
  dos migraciones. Ver [[deuda-tecnica-y-riesgos]].
- **La hoja de vida no se ha visto en un navegador con datos**: la base de desarrollo no tiene ningún
  equipo registrado.

## Cómo se verificó

Contra PostgreSQL real, cerrando un reporte **por la puerta del servicio** y mirando después la hoja de
vida: es la única forma de demostrar «no se requiere acción manual», porque con un doble del oyente no
se prueba que el oyente se entere solo. **Diecisiete mutaciones en las cuatro tandas y `V13`**, dos
supervivientes, y un cambio que pasó sin que nada lo notara; los tres se cerraron:

- una prueba del `CHECK` del tipo de servicio usaba `MANTENIMIENTO`, de trece caracteres, que rechazaba
  el `varchar(11)` y no el vocabulario;
- la hoja de vida de un equipo ajeno se rechazaba igual con el filtro quitado del principio, porque la
  cuarta sección también acota: por el camino se leían el cliente, la sede y el área de **otro
  cliente**. Lo fija ahora `verifyNoInteractions` sobre los once puertos restantes;
- y no una mutación sino un cambio real: sustituir «Ver» por dos enlaces en la lista de equipos dejó
  las 410 pruebas del frontend en verde. Nada probaba ese enlace.

## Lo que viene: el formato impreso

El **2026-10-05** el usuario aprobó un diseño nuevo para la hoja de vida, pensado para imprimir y con la
marca de Bolívar Bioingeniería. Pide datos que hoy no existen —riesgo, uso, datos eléctricos completos,
número de hoja, foto— y una descripción y un responsable en cada línea del historial. El plan entero
está en [[hoja-de-vida-formato-impreso]].

## Notas relacionadas

[[hoja-de-vida-formato-impreso]] · [[dominio-equipo-mantenimiento]] · [[dominio-reporte-servicio]] · [[congelar-una-referencia-historica]] · [[filtrado-por-dueno]] · [[esquema-bd-malphasos]] · [[deuda-tecnica-y-riesgos]] · [[hoja-de-ruta-producto]]
