---
name: diseno-base-de-datos
description: Los diagramas PlantUML del esquema de la base de datos en DatabaseDesign/, empezando por el plan de V41 —un encargado con varias sedes y áreas—, que es PREVISTO y no implementado
tags: [documentos, base-de-datos, plantuml, diagramas, previsto]
fuente: "Documentation/DatabaseDesign/V41-encargados/"
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

## Cómo se regeneran

**No se dibujan a mano.** `fuente/generar.py` lee dos volcados de la base de desarrollo —
`information_schema.columns` y `pg_constraint`, sacados en `V40`— y escribe los tres `.puml`; las
instrucciones están en su cabecera. Dibujar 36 tablas a mano es la manera segura de equivocarse en una
columna, que es lo que le pasó a `esquema-bd-malphasos` el día que se escribió.

**Necesita `!pragma layout smetana`**, porque la máquina de desarrollo no tiene Graphviz y PlantUML no
puede maquetar un diagrama de entidades sin él. Y en una leyenda **no se escribe `--`**: PlantUML lo lee
como tachado. Para el PDF vale el rodeo por SVG de [[herramientas-y-compilacion]].

## Notas relacionadas

[[regla-implementado-vs-previsto]] · [[herramientas-y-compilacion]] · [[ers-ieee830]]
