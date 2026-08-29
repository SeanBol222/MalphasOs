---
name: reglas-de-negocio-en-el-esquema
description: Dónde se esconden las reglas de negocio dentro de un esquema SQL, y por qué es fácil dejarse alguna al migrarlas al dominio
tags: [patron, base-de-datos, migracion, "reusable:alta"]
source: aprendido al migrar person_hexagon
updated: 2026-08-28
---

# Las reglas de negocio no están solo en el código

Al migrar un módulo, la tentación es leer las clases y dar por hecho que ahí está todo el comportamiento. No es así: **un esquema SQL guarda reglas de negocio en al menos seis sitios distintos**, y cada uno se lee de una forma diferente.

Esto no es teoría. Al migrar el módulo de personas se trasladaron al dominio las reglas de la función `validar_roles_persona()` pero se pasó por alto una tercera, que vivía en una restricción `CHECK` de la propia tabla. El resultado: una combinación de datos inválida atravesaba el dominio sin que nadie la frenara, llegaba hasta la base y volvía convertida en un error 500. Lo encontró una prueba manual, no la batería automática, porque **las pruebas cubrían las reglas que se sabía que existían**.

## Dónde mirar antes de dar por migrado un módulo

| Dónde | Qué buscar | Cómo se detecta que falta |
|---|---|---|
| **`CHECK` en la tabla** | Valores permitidos, relaciones entre columnas de la misma fila | El dato inválido llega a la base y vuelve como error de infraestructura |
| **`CHECK` añadido con `ALTER TABLE`** | Lo mismo, pero lejos del `CREATE TABLE` y fácil de no ver al leer | Igual que el anterior, y más difícil de encontrar leyendo |
| **Funciones y triggers** | Reglas que involucran varias filas o tablas | ⚠️ Verificar que exista el `CREATE TRIGGER`: la función puede ser código muerto |
| **`UNIQUE`** | Identidad del negocio: qué no puede repetirse | Clave duplicada devuelta como error de servidor |
| **`NOT NULL`** | Qué es obligatorio de verdad | La entidad JPA puede contradecir el esquema en ambos sentidos |
| **`DEFAULT`** | Estado inicial esperado de una entidad | El código escribe un valor distinto del que asume el resto del sistema |
| **Claves foráneas y su `ON DELETE`** | Qué se puede borrar y qué arrastra | Un borrado permitido en el código que la base rechaza |

## Comprobaciones concretas

Al migrar un módulo conviene ejecutar, sobre el esquema del proyecto original:

```bash
# Restricciones de la tabla, incluidas las agregadas por separado
grep -iE "CHECK|UNIQUE|NOT NULL" esquema.sql | grep -i "<tabla>"
grep -iE "ALTER TABLE <tabla>" esquema.sql

# Funciones de validacion... y si alguien las invoca de verdad
grep -iE "CREATE (OR REPLACE )?FUNCTION" esquema.sql
grep -icE "CREATE +TRIGGER" esquema.sql   # si da 0, todas las funciones son codigo muerto
```

Ese último punto ya apareció una vez: `bolivarbioingenieria-app` define `validar_roles_persona()` y **no crea ningún trigger en todo el esquema**, de modo que sus reglas nunca se ejecutaron. Ver [[migracion-person-hallazgos]].

## Qué hacer con cada regla encontrada

Trasladarla al dominio, donde puede producir un mensaje que explique qué se incumplió y probarse sin base de datos, **y dejar la restricción equivalente en el esquema** como última defensa ante escrituras por SQL directo. No son alternativas: cumplen funciones distintas.

Una señal útil: **si una petición llega a violar una restricción de la base, falta una validación en el dominio**. Por eso conviene que ese caso se registre en el log con detalle, aunque al cliente se le responda un escueto 409.

## Una prueba que cierra la categoría, no el caso

Cuando aparece una regla sobre un conjunto de valores, conviene probarla recorriendo el enum entero en lugar del ejemplo concreto que falló:

```java
for (PersonType type : PersonType.values()) {
    if (type == PersonType.MANAGER) continue;
    assertThatThrownBy(() -> person.setSegundoTipoPersona(type).validateRoles())
            .isInstanceOf(IllegalArgumentException.class);
}
```

Así, agregar un valor nuevo al enum obliga a decidir explícitamente si es válido, en lugar de dejar un hueco silencioso.

## Notas relacionadas

[[migracion-person-hallazgos]] · [[esquema-bd-v4]] · [[deuda-tecnica-y-riesgos]] · [[manejo-global-excepciones]]
