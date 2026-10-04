---
name: esquema-bd-malphasos
description: El esquema de MalphasOS hoy — 26 tablas, 35 foráneas, con diagrama por módulo, generado leyendo la base real
tags: [base-de-datos, diagrama, "describe:malphasos"]
source: malphasos/src/main/resources/db/migration/
estado: estable
updated: 2026-10-04
---

# El esquema de MalphasOS, hoy

**Esta nota existe porque no había ninguna que describiera esta base de datos.** Las dos de este
directorio —[[esquema-bd-v4]] y [[evolucion-esquema-v1-v4]]— describen el esquema del **sistema
original**, generado con Enterprise Architect, y la primera se anuncia como «el esquema PostgreSQL
**actual**»: era cierto cuando se escribió y dejó de serlo al construirse MalphasOS. Quien preguntaba
«cómo tenemos la base de datos» aterrizaba ahí y leía las 27 tablas del sistema viejo.

**Coincidencia que conviene no confundir**: el original tenía **27** tablas y MalphasOS tiene **26**.
No son las mismas menos una; es otro modelo con un número parecido.

## Cómo se hizo esta nota, y cómo se vuelve a hacer

**Leyendo la base de datos en marcha**, no las migraciones ni la memoria. Las migraciones son diez
archivos y el estado final no se ve en ninguno; `information_schema` sí lo ve. Los conteos de abajo
salen de estas consultas, y **se recalculan antes de citarlos**:

```sql
-- tablas (sin flyway_schema_history)
SELECT count(*) FROM information_schema.tables
WHERE table_schema = 'public' AND table_type = 'BASE TABLE'
  AND table_name <> 'flyway_schema_history';

-- foraneas, y el grafo entero
SELECT tc.table_name, kcu.column_name, ccu.table_name AS apunta_a, tc.constraint_name
FROM information_schema.table_constraints tc
         JOIN information_schema.key_column_usage kcu ON kcu.constraint_name = tc.constraint_name
         JOIN information_schema.constraint_column_usage ccu ON ccu.constraint_name = tc.constraint_name
WHERE tc.constraint_type = 'FOREIGN KEY' AND tc.table_schema = 'public';
```

## Las cifras, medidas el 2026-10-04

| | |
|---|---|
| Migraciones aplicadas | **11** (`V1`…`V11`) |
| Tablas de dominio | **26** |
| Llaves foráneas | **35**, de las cuales **4 compuestas** |
| Restricciones `CHECK` propias | **29** |
| Índices únicos **parciales** | **5** |
| Tablas con borrado lógico | **26 de 26** — universal, sin excepción |

**El borrado lógico es universal y eso es una afirmación comprobada, no una convención declarada**: la
consulta que busca tablas sin `b_estado_activo` devuelve cero filas.

## El mapa de módulos

Cada módulo es un hexágono del backend y sus tablas no se mezclan. Las flechas son las referencias que
**cruzan** de un módulo a otro, que son las que importan al leer el código: dentro de un módulo se
navega por objetos, entre módulos siempre por identificador.

```mermaid
graph TD
    location["location<br/>pais · ciudad"]
    person["person<br/>persona + correos y telefonos"]
    client["client<br/>cliente · sede · area · encargado"]
    equipment["equipment<br/>catalogo + catalogo metrologico"]
    workorder["work-order<br/>orden_trabajo + su alcance"]
    report["report<br/>reporte_servicio + lecturas"]

    client -->|pais, ciudad| location
    client -->|representante, encargado| person
    equipment -->|pais del fabricante| location
    equipment -->|area donde se instala| client
    workorder -->|cliente y sede| client
    workorder -->|ingeniero asignado| person
    workorder -->|equipo y area| equipment
    report -->|equipo de la orden| workorder
    report -->|verificacion y punto| equipment
```

## `location` — 2 tablas

Datos de referencia. Los siembra `V7`: 249 países de la ISO 3166-1 y 1.350 ciudades. **No hay pantalla
que los cree**, y es deliberado: son lo primero que una instalación nueva necesita.

```mermaid
erDiagram
    pais ||--o{ ciudad : "tiene"
    pais {
        uuid k_id_pais PK
        varchar n_codigo_iso UK "3 letras"
        varchar n_nombre_pais UK
        boolean b_estado_activo
    }
    ciudad {
        uuid k_id_ciudad PK
        uuid k_id_pais FK
        varchar n_nombre_ciudad "UK por pais"
        boolean b_estado_activo
    }
```

**Defecto conocido**: a `ciudad` le falta un nivel de división administrativa. 64 nombres de municipio
se repiten entre departamentos colombianos y `UQ_ciudad_nombre_por_pais` los hace imposibles. Ver
[[dominio-ubicacion]].

## `person` — 3 tablas

```mermaid
erDiagram
    persona ||--o{ correo_persona : "tiene"
    persona ||--o{ telefono_persona : "tiene"
    persona {
        varchar k_identificador PK "numero de documento"
        varchar n_tipo_identificacion
        varchar n_nombres
        varchar n_apellidos
        varchar n_tipo_persona "ingeniero, admin, representante, encargado"
        boolean b_estado_activo
    }
    correo_persona {
        uuid k_id_correo_persona PK
        varchar k_identificador FK "ANULABLE - deuda"
        varchar n_correo
        boolean b_estado_activo
    }
    telefono_persona {
        uuid k_id_telefono_persona PK
        varchar k_identificador FK "ANULABLE - deuda"
        varchar n_telefono
        boolean b_estado_activo
    }
```

**Dos deudas propias viven aquí**: el dueño es **anulable** en las dos tablas hijas —al contrario que
en los contactos del cliente—, y **ningún correo está marcado como principal**, que es lo que impide
sincronizar el correo con Keycloak al editar. Corregir cualquiera de las dos exige su propia migración.

## `client` — 7 tablas

```mermaid
erDiagram
    cliente ||--o{ sede : "abre"
    cliente ||--o{ representante_legal : "tiene"
    cliente ||--o{ correo_cliente : "tiene"
    cliente ||--o{ telefono_cliente : "tiene"
    sede ||--o{ area_servicio : "divide en"
    sede ||--o{ encargado : "tiene"
    area_servicio ||--o{ encargado : "tiene"
    cliente {
        uuid k_id_cliente PK
        varchar n_nit UK
        varchar n_razon_social
        uuid k_id_pais FK
        boolean b_estado_activo
    }
    sede {
        uuid k_id_sede PK
        uuid k_id_cliente FK
        uuid k_id_ciudad FK
        varchar n_nombre_sede
        boolean b_estado_activo
    }
    area_servicio {
        uuid k_id_area_servicio PK
        uuid k_id_sede FK
        varchar n_nombre_area
        boolean b_estado_activo
    }
    encargado {
        uuid k_id_encargado PK
        varchar k_identificador FK "la persona"
        uuid k_id_sede FK "una de las dos"
        uuid k_id_area_servicio FK "o la otra"
        boolean b_estado_activo
    }
```

**Un encargado lo es de una sede o de un área, nunca de las dos ni de ninguna**, y eso lo impone un
`CHECK` y no una convención.

## `equipment` — 10 tablas

Son dos cosas en un módulo: la **cadena del catálogo** (seis tablas) y el **catálogo metrológico con lo
que se verifica** (cuatro, de `V8` y `V10`).

**«Equipo» significa dos cosas**, y es la trampa de este módulo: `equipo` es una **categoría** —la
combinación de una marca y un tipo— y `equipo_cliente` es la **máquina** instalada en un área.

```mermaid
erDiagram
    marca ||--o{ equipo : "combina"
    tipo_equipo ||--o{ equipo : "combina"
    equipo ||--o{ modelo : "concreta"
    fabricante ||--o{ modelo : "fabrica"
    modelo ||--o{ equipo_cliente : "se instala como"
    tipo_equipo {
        uuid k_id_tipo_equipo PK
        varchar n_nombre_tipo_equipo UK
        varchar t_definicion_tecnica
        integer i_voltage
        numeric d_amperaje
        bigint m_valor_unitario_mantenimiento
        boolean b_estado_activo
    }
    equipo {
        uuid k_id_equipo PK
        uuid k_id_tipo_equipo FK
        uuid k_id_marca FK
        boolean b_estado_activo
    }
    modelo {
        uuid k_id_modelo PK
        uuid k_id_equipo FK
        uuid k_id_fabricante FK
        varchar n_nombre_modelo "IdeaPad 3 - UK por equipo"
        varchar n_invima "anulable: se tramita despues"
        boolean b_estado_activo
    }
    equipo_cliente {
        uuid k_id_equipo_cliente PK
        uuid k_id_modelo FK
        uuid k_id_area_servicio FK "del modulo client"
        varchar k_serie
        date f_fecha_compra
        boolean b_estado_activo
    }
```

**`modelo` tiene nombre desde `V11`**, y hasta entonces no lo tenía: lo único legible que llevaba era
su registro INVIMA, que es un número de trámite **y es anulable**, de modo que cabía un modelo sin nada
que escribir en una fila. El nombre es obligatorio y **único por `equipo`** —«Serie 3» puede ser de dos
marcas distintas, y la marca vive en `equipo`—, con índice parcial como el resto.

**`tipo_equipo` ya no dice cómo se verifica**: hasta el 2026-10-03 tenía `b_verificable`,
`n_tipo_verificacion` e `i_cantidad_datos`, y las tres se fueron en `V10`. El booleano era exactamente
«hay modalidad» —redundante por construcción— y las otras dos no pueden ser del tipo, porque valen
distinto según la magnitud.

```mermaid
erDiagram
    magnitud ||--o{ unidad_medida : "se expresa en"
    tipo_equipo ||--o{ verificacion_tipo_equipo : "se le verifica"
    unidad_medida ||--o{ verificacion_tipo_equipo : "con la unidad"
    verificacion_tipo_equipo ||--o{ punto_verificacion : "en los valores"
    magnitud {
        uuid k_id_magnitud PK
        varchar n_codigo_magnitud UK "llave natural"
        varchar n_nombre_magnitud UK
        boolean b_estado_activo
    }
    unidad_medida {
        uuid k_id_unidad_medida PK
        uuid k_id_magnitud FK
        varchar n_simbolo_unidad "UK POR MAGNITUD, no global"
        varchar n_nombre_unidad
        boolean b_estado_activo
    }
    verificacion_tipo_equipo {
        uuid k_id_verificacion PK
        uuid k_id_tipo_equipo FK
        uuid k_id_magnitud FK "el par va junto"
        uuid k_id_unidad_medida FK "a unidad_medida"
        varchar n_modalidad_verificacion
        integer i_cantidad_datos "nulo si la modalidad es variable"
        boolean b_estado_activo
    }
    punto_verificacion {
        uuid k_id_punto_verificacion PK
        uuid k_id_verificacion FK
        numeric d_valor "sin unidad: la declara su verificacion"
        boolean b_estado_activo
    }
```

**Un tipo se verifica en varias magnitudes**, y es la corrección del 2026-10-03: un termohigrómetro
mide temperatura **y** humedad relativa, con unidades, puntos y modalidades distintas. Con el modelo
anterior había que registrarlo como dos tipos de equipo. Las 20 magnitudes y 46 unidades las siembra
`V10` y **no hay pantalla que las administre**, igual que los países. Ver
[[dominio-equipo-mantenimiento]].

## `work-order` — 2 tablas

```mermaid
erDiagram
    orden_trabajo ||--o{ orden_trabajo_equipo : "incluye"
    orden_trabajo {
        uuid k_id_orden_trabajo PK
        uuid k_id_cliente FK
        uuid k_id_sede FK "el par va junto a sede"
        varchar k_identificador FK "el ingeniero, anulable"
        date f_fecha_programada
        varchar n_periodicidad
        varchar n_tipo_servicio
        varchar n_estado_ejecucion
        boolean b_estado_activo
    }
    orden_trabajo_equipo {
        uuid k_id_orden_trabajo PK, FK
        uuid k_id_equipo_cliente PK, FK
        uuid k_id_area_servicio FK
        boolean b_estado_activo
    }
```

**`orden_trabajo_equipo` se identifica por el par**, sin identificador propio: un equipo está en una
orden o no está, y no hay nada que decir dos veces sobre eso.

## `report` — 2 tablas

```mermaid
erDiagram
    orden_trabajo_equipo ||--o{ reporte_servicio : "se reporta en"
    reporte_servicio ||--o{ dato_verificacion : "registra"
    reporte_servicio {
        uuid k_id_reporte_servicio PK
        uuid k_id_orden_trabajo FK "el par va junto a"
        uuid k_id_equipo_cliente FK "orden_trabajo_equipo"
        varchar t_estado_reporte
        varchar t_falla_reportada
        varchar t_diagnostico
        varchar t_procedimientos
        varchar t_observaciones
        varchar t_resultado
        timestamp t_finalizado
        boolean b_estado_activo
    }
    dato_verificacion {
        uuid k_id_dato_verificacion PK
        uuid k_id_reporte_servicio FK
        uuid k_id_verificacion FK "obligatoria"
        uuid k_id_punto_verificacion FK "anulable"
        integer i_secuencia "1 a 100"
        numeric d_valor_patron
        numeric d_valor_equipo
        varchar n_unidad "copia congelada"
        boolean b_estado_activo
    }
```

**La unidad de una lectura es una copia, no una foránea**, y es deliberado: reconfigurar un tipo no
debe cambiar un reporte ya firmado. Ver [[congelar-una-referencia-historica]].

## Las 4 foráneas compuestas, que son reglas y no adornos

Una foránea compuesta impone en el esquema algo que con dos foráneas sueltas quedaría abierto. Son
cuatro y cada una tapa un estado que de otro modo se podría escribir:

| Tabla | El par | Lo que impide |
|---|---|---|
| `orden_trabajo` | (sede, cliente) → `sede` | Una orden en una sede **de otro cliente** |
| `reporte_servicio` | (orden, equipo) → `orden_trabajo_equipo` | Un reporte de un equipo **que la orden no incluyó** |
| `verificacion_tipo_equipo` | (magnitud, unidad) → `unidad_medida` | Medir temperatura **en %HR** |
| `dato_verificacion` | (verificación, punto) → `punto_verificacion` | Una lectura en un punto **de otra verificación** |

**Y las cuatro admiten el caso nulo sin una regla extra**: una foránea compuesta con alguna columna
nula **no se comprueba** —es `MATCH SIMPLE`, lo que PostgreSQL hace por omisión—, de modo que la
lectura sin punto pasa y la que trae punto queda atada.

## Los 5 índices únicos parciales, y por qué son parciales

`UQ_reporte_servicio_activo`, `UQ_verificacion_magnitud_activa_por_tipo`,
`UQ_punto_verificacion_activo`, `UQ_dato_verificacion_activo` y, desde `V11`,
`UQ_modelo_nombre_activo_por_equipo`. Los cinco llevan `WHERE b_estado_activo`, y la razón es la misma
en todos: **aquí nada se borra**. Una restricción normal impediría volver a dar de alta algo que se
retiró, de modo que reconfigurar hacia atrás sería imposible.

**Y cobran un precio**: Hibernate vacía los `INSERT` antes que los `UPDATE`, así que
sustituir una fila activa por otra equivalente choca contra el índice. Un índice **parcial** no se
puede declarar diferido —`DEFERRABLE` es de las restricciones, y una restricción no admite `WHERE`—,
así que el orden se impone en el adaptador con un `saveAndFlush` intermedio. Lo pagaron dos módulos:
`report` el 2026-09-27 y `equipment` el 2026-10-03, donde llevaba **latente** porque ninguna prueba lo
ejercía. Ver [[stack-spring-boot-4-particularidades]].

## Lo que el esquema **no** dice

Veinte reglas de negocio no están aquí y viven en los servicios, y no por descuido: el esquema no
puede expresarlas. «No abrir un área en una sede cerrada» exige mirar otra fila; «el punto pertenece al
tipo del equipo reportado» son cuatro saltos desde `equipo_cliente`. Y hay una tercera categoría: una
restricción que el esquema **podría** expresar y que **no debe**, porque congelaría el otro lado — ver
[[congelar-una-referencia-historica]].

## Lo que falta

- **El vencimiento de calibración** no tiene columna, y es lo que las alertas necesitan. Es lo único
  que queda de la segunda tanda de `equipment`.
- **El filtrado por dueño no existe**: ninguna tabla lleva columna de pertenencia que el API use para
  recortar lo que un usuario ve. Fue una decisión explícita.
- **La firma digital y la hoja de vida como entidad** no tienen tablas todavía.
