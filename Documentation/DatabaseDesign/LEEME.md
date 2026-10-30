# Diseño de la base de datos

Diagramas del esquema de MalphasOS para revisar un cambio **antes** de escribir la migración. Cada carpeta
es una foto fechada de un plan; la descripción viva del esquema está en `SecondBrain/`, en
`esquema-bd-malphasos`.

| Qué | Dónde | Estado |
|---|---|---|
| **Análisis del esquema** (2026-10-27): ciclos, formas normales, copias y problemas, con las decisiones del usuario y el orden de las migraciones | `analisis-2026-10-27.md` | Decidido |
| **La refactorización a 3FN y la propuesta final** | `refactor-3fn/` | `V42` a `V51` construidas; previsto hasta `V52` |
| Un encargado con varias sedes y áreas | `V41-encargados/` | Implementado el 2026-10-27 |
| El recibido firmado por sede o por área, en una tabla de la orden | `V42-recibido-por-orden/` | **Descartado** el 2026-10-27: el recibido se queda en el reporte, y firmar varios a la vez no pide migración. Se conserva como foto del plan |

## `refactor-3fn/`, la más grande

```
refactor-3fn/
├── 1-hoy/              la base en V41
├── 2-final/            la propuesta final, tras la refactorización a 3FN (V42 a V52)
│   ├── vista-general   solo los nombres de las tablas y sus relaciones: empezar por aquí
│   ├── completo        todas las tablas con todas sus columnas
│   └── modulos/        una por módulo: client, company, equipment, location, person, report, work-order
└── fuente/             el generador, los volcados de la base y los .puml
```

Las tablas en **rojo** cambian o se retiran; las **verdes** son nuevas. En los diagramas por módulo, las
tablas de otros módulos salen en **gris**, solo con su nombre.

Cada diagrama está en `.svg` —se amplía en el navegador sin perder nitidez— y en `.pdf`.

## Cómo se regeneran

No se dibujan a mano: cada carpeta lleva en `fuente/` su generador y los volcados de la base de los que
sale. En `refactor-3fn/`, `bash fuente/dibujar.sh` lo hace todo; en las otras dos, las instrucciones están
en la cabecera de `fuente/generar.py`.
