---
name: diseno-base-de-datos
description: Los diagramas PlantUML del esquema de la base de datos en DatabaseDesign/ — el plan de V41, un encargado con varias sedes y áreas, implementado, y el de V42, el recibido firmado por sede o por área, PREVISTO
tags: [documentos, base-de-datos, plantuml, diagramas, previsto]
fuente: "Documentation/DatabaseDesign/"
estado: vigente
updated: 2026-10-27
---

# Diseño de la base de datos

**Qué es.** `Documentation/DatabaseDesign/` guarda diagramas entidad-relación en PlantUML del esquema
de MalphasOS, para revisar un cambio **antes** de escribir la migración. El primero, del 2026-10-27,
es `V41-encargados/`, pedido por el usuario para ver el cambio antes de aprobarlo.

**Qué no es.** No es la descripción viva del esquema: esa es `esquema-bd-malphasos` en `SecondBrain/`,
que se mantiene con el código. Cada carpeta de aquí es **una foto fechada** de un plan.

## La regla que manda aquí

Lo dibujado como cambio es **previsto** mientras no se implemente —ver [[regla-implementado-vs-previsto]]—,
y los diagramas lo dicen en el título: «PROPUESTO». Cuando la migración entra, esta nota lo dice con su
fecha, y los diagramas **no se regeneran**: son la foto del plan que se aprobó.

**`V41` está implementada desde el 2026-10-27**, tal como la dibujan los diagramas: las dos tablas, la
llave compuesta, el paso de los datos y la regla del mismo cliente en el servicio. Lo que el plan dejaba
para después —que una pantalla ponga a un encargado ya registrado al frente de otra sede— entró ese
mismo día, en la ficha de la sede.

## `V41-encargados/` — un encargado con varias sedes y áreas

| Archivo | Qué muestra |
|---|---|
| `esquema-actual` | **La base de hoy, completa**: 36 tablas de `V40` por módulo, con columnas, llaves y foráneas. `encargado`, la tabla que se retira, en rojo |
| `cambio-v41` | **Solo el cambio**: `encargado` y sus vecinas antes, las dos tablas puente después, con las reglas que el esquema no comprueba y cómo se mueven los datos |
| `esquema-con-v41` | **La base como quedaría**: 37 tablas, con las dos nuevas en verde |

Cada uno está en `.puml` —la fuente—, `.svg` —para ampliar sin perder nitidez— y `.pdf`.

**Las decisiones que dibuja**, tomadas por el usuario el 2026-10-26 y el 2026-10-27:

- Dos tablas puente, `encargado_sede` y `encargado_area`, con llave compuesta (persona, sede) y
  (persona, área), en lugar de una fila por asignación con identificador propio.
- Todas las asignaciones activas de una persona son **de un mismo cliente**. El esquema no lo puede
  comprobar entre dos tablas: es una regla del servicio.
- Se puede estar a cargo de una sede y de un área de esa misma sede a la vez.
- Sin fecha de inicio: nada la pide ni la muestra.

## `analisis-2026-10-27.md` y `refactor-3fn/` — el esquema a tercera forma normal (PREVISTO)

Pedido por el usuario antes de construir el recibido por orden: «veo demasiados ciclos, redundancias y de
todo». El análisis se hizo sobre la base en `V41` y con un guion sobre el grafo de foráneas: **un solo
ciclo dirigido** —`intervencion`, que se apunta a sí misma—, cuatro foráneas que repiten un camino, dos
violaciones de 1FN, dos de 3FN, seis copias deliberadas para la historia y cinco grupos de problemas
menores. `refactor-3fn/` dibuja el esquema actual con lo que cambia en rojo y el resultante.

**Y en vistas que se leen**, pedidas por el usuario —«todo está apretado y no se entiende nada»—: para
cada esquema, una **vista general** con solo los nombres de las tablas por módulo y sus relaciones, y
**una por módulo** con todas sus columnas, donde las tablas de otros módulos a las que apuntan salen solo
con su nombre, en gris. El diagrama entero con todas las columnas se conserva.

**Y ordenado en carpetas**, también a pedido del usuario —«hay demasiado archivo y no entiendo»—: en
`refactor-3fn/`, una por esquema —`1-hoy/`, `2-refactorizado/` y `3-final/`, la propuesta final con el
recibido por orden—, cada una con `vista-general`, `completo` y `modulos/`; los `.puml` y el generador, en
`fuente/`. `bash fuente/dibujar.sh` lo regenera todo en su sitio. `DatabaseDesign/LEEME.md` dice qué hay
en cada carpeta.

**El usuario decidió** quitar `intervencion` y la magnitud de la verificación, y hacer el resto como se
propuso. ~~Mantener las copias para la historia~~: **corregido el mismo día**, a propuesta del usuario, las
copias del reporte (B1, B2 y B3) se sustituyen por su **documento congelado al cerrar**, que ya existía
desde `V37`; el área de cada equipo en la orden (B6) se quita **bloqueando el traslado** de un equipo que
está en una orden sin ejecutar; y **la orden se congela al ejecutarse**, en su propio documento. La hoja
de vida sigue leyendo en vivo todo menos el historial, que sale del documento de cada reporte cerrado.
Son **siete** migraciones, `V42` a `V48` —eran seis, `V42` a `V47`, y fueron ocho unas horas, mientras el
recibido por orden iba a ser `V49`—. **El recibido se queda en el reporte**, ver abajo.

**Una corrección dentro del propio análisis**: un borrador decía que siete de los trece ciclos sin
dirección desaparecían. Recalculado, el número apenas baja —de 13 a 12 con B6 fuera; antes de quitarla
no bajaba—, porque ese número cuenta también dos tablas que apuntan a un catálogo compartido, que no es
redundancia. Lo que mide redundancia, las foráneas que repiten un camino, baja de cuatro a **una**
—decía dos mientras B6 se quedaba—, y la que queda es la identidad de una lectura sin punto. Las
carpetas de `refactor-3fn/` son ahora dos: `1-hoy/` y `2-final/`; la intermedia sobraba al dejar de haber
recibido por orden.

**Y sin tabla puente entre la orden y el equipo**, también a propuesta del usuario: el reporte ya tenía
un solo reporte activo por (orden, equipo), que es la llave de `orden_trabajo_equipo`, así que un equipo
está en una orden si y solo si tiene en ella un reporte activo. Con cuatro reglas aceptadas por el
usuario —el borrador nace al añadir el equipo, quitarlo es retirar su reporte, ejecutar exige un reporte
cerrado por equipo y la sustitución sigue igual—, y dentro de `V46`. La propuesta final queda en **36**
tablas.

## `V42-recibido-por-orden/` — se firma el recibido por sede o por área (DESCARTADO el 2026-10-27)

> **Descartado el 2026-10-27.** El usuario decidió que quien recibe no tiene que estar registrado ni
> tener que ver con el área: su nombre, documento y firma **se quedan en el reporte**, como desde `V39`, y
> lo que quiere son tres formas de firmar —un reporte, todos los de un área o todos los de la orden—, que
> no cambian el esquema porque la imagen se guarda una vez por su huella. La carpeta se conserva como foto
> del plan aprobado y luego retirado; el porqué completo está en el análisis.
>
> Antes de eso fue **renumerado**, dos veces: se planeó como `V42`, pasó a `V47` con la refactorización y
> a **`V49`** con el documento congelado de la orden, donde se guardarán sus recibidos. Va detrás de la
> refactorización. Sus diagramas están sobre `V41` y se regenerarán sobre el esquema nuevo antes de
> construirlo; la carpeta conserva el nombre con el que se aprobó.

Planeado el 2026-10-27, a pedido del usuario: firmar el recibido reporte por reporte «está muy mal», y
la firma pasa a la **orden**, para toda la sede o para un área. Mismos tres diagramas —`esquema-actual`
sobre `V41`, `cambio-v42` y `esquema-con-v42`—, con `reporte_servicio` en rojo porque pierde columnas,
no porque se retire.

**Las decisiones del usuario**, preguntadas antes de dibujar:

- La firma es **de la orden o de un área**, y cada reporte la lee de ahí, incluidos los que se abran
  después de firmar. No se copia: por eso hace falta una tabla, `recibido_orden`.
- Se puede firmar la sede entera **y además** un área: **el área manda sobre la sede**.
- **Ya no se firma un reporte suelto**: el reporte solo muestra el recibido que le toca.

**Las de la sesión**, dibujadas para que se revisen: se sigue pudiendo registrar quién recibe sin firmar
—firma en papel—; una firma no se cambia ni se retira; dos foráneas compuestas atan el área a la sede de
la orden; y la migración lleva el recibido de cada reporte a su orden, o se detiene diciendo cuáles si dos
reportes de una misma área no coinciden.

## Cómo se regeneran

**No se dibujan a mano.** Cada carpeta lleva su `fuente/generar.py`, que lee dos volcados de la base de desarrollo —
`information_schema.columns` y `pg_constraint`, sacados en `V40`— y escribe los tres `.puml`; las
instrucciones están en su cabecera. Dibujar 36 tablas a mano es la manera segura de equivocarse en una
columna, que es lo que le pasó a `esquema-bd-malphasos` el día que se escribió.

**Necesita `!pragma layout smetana`**, porque la máquina de desarrollo no tiene Graphviz y PlantUML no
puede maquetar un diagrama de entidades sin él. Y en una leyenda **no se escribe `--`**: PlantUML lo lee
como tachado. Para el PDF vale el rodeo por SVG de [[herramientas-y-compilacion]].

## Notas relacionadas

[[regla-implementado-vs-previsto]] · [[herramientas-y-compilacion]] · [[ers-ieee830]]
