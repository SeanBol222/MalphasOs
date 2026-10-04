#!/usr/bin/env python3
"""
Comprueba que cada columna dibujada en `wiki/base-de-datos/esquema-bd-malphasos.md` existe de verdad.

POR QUE EXISTE. Esa nota se escribio el 2026-10-03 diciendo de si misma que estaba «generada leyendo la
base en marcha». Era verdad a medias: la lista de tablas y el grafo de foraneas si se leyeron de
`information_schema`, y los NOMBRES DE COLUMNA se escribieron de memoria. Al revisarla el 2026-10-04
aparecieron **doce columnas que no existen** en seis tablas, mas una llave primaria inventada en
`encargado`. Un diagrama con nombres plausibles y falsos es peor que no tener diagrama: se cita.

Uso, con los contenedores arriba y desde la raiz del repositorio:

    docker compose exec -T postgres psql -U malphasos -d malphasos_db -t -A -F'|' \
      -c "SELECT table_name, column_name FROM information_schema.columns \
          WHERE table_schema='public' AND table_name <> 'flyway_schema_history';" \
      | python3 SecondBrain/herramientas/verificar-esquema.py

Devuelve 0 si todo cuadra y 1 nombrando lo que sobra. No comprueba tipos ni marcas PK/FK: para eso
hace falta leer el diagrama, y lo que este guion caza es justamente el error que se cometio.
"""

import collections
import pathlib
import re
import sys

NOTA = pathlib.Path(__file__).resolve().parents[1] / "wiki/base-de-datos/esquema-bd-malphasos.md"


def columnas_reales(entrada):
    reales = collections.defaultdict(set)

    for linea in entrada:
        linea = linea.strip()
        if "|" in linea:
            tabla, columna = linea.split("|", 1)
            reales[tabla].add(columna)

    return reales


def columnas_dibujadas(texto):
    dibujadas = []

    for bloque in (m[1] for m in re.finditer(r"```mermaid\n(erDiagram[\s\S]*?)```", texto)):
        tabla = None

        for linea in bloque.splitlines():
            limpia = linea.strip()
            inicio = re.match(r"^(\w+)\s*\{$", limpia)

            if inicio:
                tabla = inicio.group(1)
            elif limpia == "}":
                tabla = None
            elif tabla:
                partes = limpia.split()
                # «tipo nombre [PK|FK|UK] ["comentario"]»; se ignoran las lineas de comentario sueltas
                if len(partes) >= 2 and not limpia.startswith('"'):
                    dibujadas.append((tabla, partes[1]))

    return dibujadas


def main():
    reales = columnas_reales(sys.stdin)

    if not reales:
        print("No llego nada por la entrada estandar: ¿estan los contenedores arriba?")
        return 2

    dibujadas = columnas_dibujadas(NOTA.read_text(encoding="utf-8"))
    sobran = [(t, c) for t, c in dibujadas if c not in reales.get(t, set())]

    print(f"columnas dibujadas: {len(dibujadas)}  |  tablas reales: {len(reales)}")

    if sobran:
        print(f"\nLa nota nombra {len(sobran)} columna(s) que no existen:")
        for tabla, columna in sobran:
            print(f"  {tabla}.{columna}")

        return 1

    print("todas existen")

    return 0


if __name__ == "__main__":
    sys.exit(main())
