---
name: estado-de-implementacion
description: La cifra agregada de implementacion de MalphasOS -17 de 31 RF, 1 de 23 RNF- con como se verifico, cuando caduco y donde esta el detalle
tags: [requisitos, estado, trazabilidad]
fuente: "Documentation/IEEE830/IEEE830.tex, apartado 3.2; Documentation/TraceabilityMatrix/MatrizDeTrazabilidad.tex"
estado: vigente
updated: 2026-10-04
---

# Estado de implementación agregado

## La cifra

> ⚠️ **Corregido el 2026-09-27, y la corrección es grande: esta nota decía 8 y llevaba caducada tres semanas.** La cifra era correcta al medirla el 2026-09-05 y dejó de serlo el **2026-09-13** con el módulo de órdenes de trabajo, otra vez el **2026-09-27** con su formulario, y una tercera ese mismo día con los reportes de servicio. **Nadie la recontó**, exactamente el riesgo que el apartado «antes de citar una cifra de estado» advierte al final de esta misma nota: el aviso estaba escrito y no impidió nada, porque quien construye no lee la advertencia de la nota que no está abriendo.
>
> Lo que sí funcionó fue el contador paralelo de `SecondBrain/wiki/malphasos/hoja-de-ruta-producto.md`, que se actualizó en cada tanda. **Dos contadores de lo mismo en dos wikis es la causa de fondo**, y queda anotado: si se quiere una sola cifra, esta nota debería citar a la otra en vez de repetirla.

| Tipo de requisito | Total | Implementado | Previsto | Fuente de la evaluación |
|---|---|---|---|---|
| Funcionales (RF) | 31 | **17** | 13 + 1 desviación | ERS, apartado 3.2, recontado el 2026-09-28 contra el código |
| No funcionales (RNF) | 23 | **1** (RNF-23, JWT) | 22 | Matriz de trazabilidad (primera evaluación; la ERS no marca RNF) |
| De dominio (RD) | 6 | 2 inferidos (RD-03, RD-04) | 4 no verificables | Matriz de trazabilidad, con salvedad — ver [[requisitos-de-dominio]] |

## Los 17 requisitos funcionales implementados

| Código | Categoría | Requisito |
|---|---|---|
| RF-01 | Órdenes de Trabajo | Crear orden directa, sin solicitud previa |
| RF-02 | Órdenes de Trabajo | Eliminar la dependencia de solicitud — satisfecho **por construcción** |
| RF-03 | Órdenes de Trabajo | Formulario de la orden (2026-09-27) |
| RF-05 | Órdenes de Trabajo | Identificador único de la orden |
| RF-06 | Órdenes de Trabajo | Visualización de equipos por área (2026-09-27) |
| RF-07 | Órdenes de Trabajo | Selección múltiple de equipos (2026-09-27) |
| RF-09 | Reportes de Mantenimiento | Reporte individual por equipo de la orden (2026-09-27) |
| RF-11 | Reportes de Mantenimiento | Autocompletado del reporte desde la orden (2026-09-28) |
| RF-15 | Reportes de Mantenimiento | Registro de información técnica: los cinco campos (2026-09-27) |
| RF-08 | Gestión de Clientes | Crear cliente |
| RF-22 | Hojas de Vida | Crear hoja de vida |
| RF-24 | Hojas de Vida | Modificar/eliminar hoja de vida |
| RF-49 | Usuarios y seguridad | Login de usuario |
| RF-50 | Usuarios y seguridad | Identificación de rol |
| RF-51 | Usuarios y seguridad | Creación de usuarios |
| RF-52 | Usuarios y seguridad | Modificación de usuarios |
| RF-53 | Usuarios y seguridad | Eliminación de usuarios |

**⚠️ RF-04 no está en esa lista a propósito**: la pantalla no pide elegir áreas antes de mostrar sus equipos, sino que muestra todas las áreas abiertas de la sede a la vez. Se llega al mismo sitio con un paso menos, pero **no es lo que el requisito describe**, así que queda registrado como **desviación consciente** y no como implementado. (**RF-11 estuvo en esa misma situación un día** y ya no: la ficha del reporte los muestra desde el 2026-09-28, leídos de la orden y sin control que editar.)

> **Corregido el 2026-09-27.** El párrafo que seguía decía que «las seis categorías del flujo operativo —órdenes de trabajo, reportes, firma digital, inventario, alertas, módulo comercial— siguen `[PREVISTO]` en su totalidad». **Dos de esas seis ya no lo están**: órdenes de trabajo cierra 6 de 7 y reportes 2 de 6. Lo que sigue siendo cierto es el orden de dependencias que explica el porqué.

Tres categorías fundacionales concentraron todo lo construido al principio: gestión de clientes, catálogo de equipos (parte de hojas de vida) y usuarios/seguridad. El [[plan-gestion-alcance]] explica el porqué del orden: "casi todos los requisitos dependen de RF-49 y RF-50, y las categorías operativas dependen de los datos maestros: no hay orden de trabajo sin cliente, sin sus sedes y áreas ni sin los equipos de la hoja de vida".

## Por categoría (apartado 3.2 de la ERS)

| Categoría | Rango | Total | Implementados | Nota de esta wiki |
|---|---|---|---|---|
| 3.2.1 Órdenes de Trabajo | RF-01 a RF-07 | 7 | **6** + 1 desviación (RF-04) | [[rf-ordenes-trabajo]] |
| 3.2.2 Clientes | RF-08 | 1 | 1 | [[rf-clientes]] |
| 3.2.3 Reportes de Mantenimiento | RF-09 a RF-17 | 6 | **3** | [[rf-reportes-mantenimiento]] |
| 3.2.4 Firma Digital | RF-18, RF-21 | 2 | 0 | [[rf-firma-digital]] |
| 3.2.5 Hojas de Vida | RF-22 a RF-27 | 4 | 2 | [[rf-hojas-vida]] |
| 3.2.6 Inventario | RF-36, RF-37 | 2 | 0 | [[rf-inventario]] |
| 3.2.7 Alertas y calibración | RF-40, RF-41 | 2 | 0 | [[rf-alertas-calibracion]] |
| 3.2.8 Módulo Comercial | RF-45, RF-47 | 2 | 0 | [[rf-modulo-comercial]] |
| 3.2.9 Usuarios y seguridad | RF-49 a RF-53 | 5 | 5 | [[rf-usuarios-seguridad]] |
| **Total** | | **31** | **17** | |

## ⚠️ Sexta verificación, 4 de octubre de 2026: dos de los 17 están en duda

**Primera vez que esta cifra se contrasta contra la ERS en la dirección contraria**: hasta ahora se
comprobaba que no se documentara como existente algo que no estaba, y esta vez se buscó lo inverso —y
lo que apareció fue un posible **sobreconteo**, no un olvido.

**RF-22 y RF-24 figuran como implementados contra el texto de la propia ERS.** El bloque de estado del
`.tex` dice que de las cuatro secciones que RF-22 exige, la de servicio técnico «falta, y falta
entera»; y que «no existe una entidad llamada hoja de vida», de modo que el formulario que el primer
criterio pide tampoco existe —hay cinco recursos, uno por eslabón del catálogo—. RF-24 depende de RF-22.

Es **la misma situación que RF-04**, que está marcado como desviación. Aplicando el criterio escrito más
abajo —«cuenta como implementado lo que el backend satisface por completo»— el marcador sería **15 de 31
con tres desviaciones**. **No se ha cambiado la cifra**: la decisión es la misma que quedó abierta con
RF-04 y le corresponde a quien lleva el proyecto, pero conviene tomarla una vez y que valga para los
tres. El detalle, con las citas del `.tex`, en [[estado-de-la-ers-caducado]].

**Y el denominador sí resultó exacto**, comprobado por dos vías independientes que coinciden: 31
declaraciones `\textbf{RF-xx ...}` y 31 filas de tabla, con la misma lista. Los otros 18 códigos que la
ERS menciona no son requisitos: se citan sin declararse, y eso ya estaba en [[priorizacion-moscow]].

## Cómo se verificó, y cuándo

**Quinta verificación, 3 de octubre de 2026**: el marcador **no se mueve** y conviene decir por qué. El
catálogo de equipos se remodeló —un tipo se verifica ahora en varias magnitudes, porque un
termohigrómetro mide temperatura y humedad— y **ningún requisito de la ERS describe eso**: el documento
no menciona magnitudes ni unidades. Lo que el cambio hace es que **RF-15 deje de ser una verdad a
medias**, porque un termohigrómetro no se podía reportar sin inventarse dos tipos de equipo, y el
requisito se daba por implementado igualmente. Se anota aquí porque es el primer caso de esta wiki en
que **un requisito marcado como implementado mejora sin cambiar de estado**, y la distinción importa:
el marcador cuenta requisitos satisfechos, no calidad de la satisfacción.

Fuentes: `V10__verification_magnitudes.sql`, las **diez** migraciones `V1`–`V10` y el bloque de
verificación de `malphasos-frontend/src/app/features/equipment/catalogo/`.

**Cuarta verificación, 28 de septiembre de 2026**: entra RF-11 con el frontend de los reportes, contra `malphasos-frontend/src/app/features/report/`. Es el primer requisito de esta wiki que **se cierra el día siguiente de anotarse como pendiente**, y sirve de contraste: la cifra caduca rápido cuando el trabajo va rápido, no solo cuando nadie mira.

**Tercera verificación, 27 de septiembre de 2026**: recuento contra el código y contra los commits de `main`, no contra lo que esta nota decía. Fuentes: los controladores REST de `workorder` y `report`, las nueve migraciones `V1`–`V9` (hoy **diez**, hasta `V10`), y las pantallas de `malphasos-frontend/src/app/features/`. De ahí salen las ocho filas nuevas y las dos desviaciones registradas.

Las dos primeras, que dieron la cifra de 8:

1. **2 de septiembre de 2026** — la ERS se revisó requisito por requisito contra el código y se añadió el punto "Estado en la implementación actual" a cada uno de los 31 RF.
2. **5 de septiembre de 2026** — la matriz de trazabilidad **repitió** la verificación en vez de copiar el estado ya declarado, contra los controladores REST, los agregados de dominio y las cinco migraciones (`V1` a `V5`). Coincidió con la primera. Es también la primera vez que se evalúa el estado de los 23 RNF y los 6 RD, que la ERS nunca marca.

Un efecto lateral que ambos documentos registran: **contrastar la especificación contra el código resultó ser también una técnica de detección de defectos**. Salieron seis fallos del código —dos de seguridad— que ninguna revisión del sistema por separado había revelado (ver `SecondBrain/wiki/malphasos/deuda-tecnica-y-riesgos.md` para el detalle de esos seis).

## Antes de citar una cifra de estado

Verificar contra `malphasos/` directamente si ha pasado tiempo desde la fecha de este documento — esta wiki registra el estado a una fecha concreta, no lo recalcula. Si el código cambió, esta nota queda desactualizada hasta que se corrija explícitamente (ver la regla en `wiki/CONVENCIONES.md`).

**Este aviso ya falló una vez**, y conviene saberlo antes de confiar en él: estuvo escrito aquí desde el 2026-09-05 y la cifra pasó tres semanas caducada de todos modos, porque quien construye no abre la nota que no está buscando. Un aviso dentro del documento que envejece no protege al documento. Lo que sí funcionó fue **actualizar el contador en la misma sesión en que se construye**, que es lo que hace la hoja de ruta del otro wiki.

## Notas relacionadas

[[regla-implementado-vs-previsto]] ⭐ · [[matriz-de-trazabilidad]] · [[plan-gestion-alcance]] · [[requisitos-no-funcionales]] · [[requisitos-de-dominio]]
