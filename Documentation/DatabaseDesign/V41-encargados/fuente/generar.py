"""Genera los tres diagramas PlantUML del plan de encargados a partir del esquema vivo (V40).

Uso, desde Documentation/DatabaseDesign/V41-encargados:
    python3 fuente/generar.py .
    java -jar <plantuml.jar> -tsvg *.puml
    for f in *.svg; do rsvg-convert -f pdf -o "${f%.svg}.pdf" "$f"; done

Los volcados se sacaron de la base de desarrollo en V40, el 2026-10-27, con:
    columnas.txt      information_schema.columns de las tablas de public, sin flyway_schema_history
    restricciones.txt pg_constraint de public: llaves primarias, foraneas, unicas y de exclusion

Lee columnas.txt y restricciones.txt, volcados de information_schema y pg_constraint, y escribe:
  esquema-actual.puml   la base de hoy, completa
  cambio-v41.puml       solo lo que toca la migracion, antes y despues
  esquema-con-v41.puml  la base completa como quedaria
"""
import re
import sys
from collections import defaultdict
from pathlib import Path

AQUI = Path(__file__).parent
SALIDA = Path(sys.argv[1])

MODULOS = {
    'person — personas': ['persona', 'correo_persona', 'telefono_persona', 'firma_persona'],
    'location — ubicación': ['pais', 'ciudad'],
    'client — clientes': ['cliente', 'correo_cliente', 'telefono_cliente', 'representante_legal',
                          'sede', 'area_servicio', 'encargado', 'encargado_sede', 'encargado_area'],
    'equipment — equipos y catálogo': ['tipo_equipo', 'marca', 'fabricante', 'equipo', 'modelo',
                                       'equipo_cliente', 'magnitud', 'unidad_medida',
                                       'verificacion_tipo_equipo', 'punto_verificacion',
                                       'protocolo', 'protocolo_tipo_equipo', 'equipo_patron',
                                       'intervencion'],
    'work-order — órdenes de trabajo': ['orden_trabajo', 'orden_trabajo_equipo'],
    'report — reportes de servicio': ['reporte_servicio', 'dato_verificacion', 'paso_reporte',
                                      'accesorio_reporte', 'patron_verificacion_reporte'],
    'company — empresa y documentos': ['empresa', 'imagen_documento'],
}

ESTILO = """\
!pragma layout smetana
skinparam backgroundColor #F3F2F2
skinparam defaultFontName Archivo
skinparam shadowing false
skinparam linetype ortho
skinparam roundCorner 0
skinparam class {
  BackgroundColor #FFFFFF
  BorderColor #6B6866
  ArrowColor #6B6866
  HeaderBackgroundColor #FFFFFF
}
skinparam package {
  BackgroundColor #FBFAFA
  BorderColor #9A9795
  FontColor #2B2928
}
skinparam class<<retirada>> {
  BackgroundColor #F6D9D3
  BorderColor #AE1800
  HeaderBackgroundColor #F6D9D3
  FontColor #AE1800
}
skinparam class<<nueva>> {
  BackgroundColor #E3F0E6
  BorderColor #1F6B35
  HeaderBackgroundColor #E3F0E6
}
hide circle
hide methods
"""


def leer():
    columnas = defaultdict(list)
    for linea in (AQUI / 'columnas.txt').read_text().splitlines():
        tabla, columna, tipo, largo, nula, _ = linea.split('|')
        tipo = {'character varying': 'varchar', 'timestamp without time zone': 'timestamp',
                'integer': 'int', 'boolean': 'boolean', 'numeric': 'numeric',
                'smallint': 'smallint', 'text': 'text', 'uuid': 'uuid', 'date': 'date',
                'bytea': 'bytea', 'bigint': 'bigint'}.get(tipo, tipo)
        if largo:
            tipo += f'({largo})'
        columnas[tabla].append((columna, tipo, nula == 'YES'))

    pk, fks, unicas = {}, [], defaultdict(list)
    for linea in (AQUI / 'restricciones.txt').read_text().splitlines():
        tabla, nombre, tipo, definicion, destino = linea.split('|')
        cols = re.search(r'\(([^)]*)\)', definicion).group(1).split(', ')
        if tipo == 'p':
            pk[tabla] = cols
        elif tipo == 'f':
            fks.append((tabla, nombre, cols, destino))
        elif tipo in ('u', 'x'):
            unicas[tabla].append(nombre)
    return columnas, pk, fks, unicas


def cambiar(columnas, pk, fks, unicas):
    """Aplica V41: dos tablas puente en lugar de encargado."""
    columnas = {t: list(c) for t, c in columnas.items()}
    pk, fks, unicas = dict(pk), list(fks), {t: list(u) for t, u in unicas.items()}

    del columnas['encargado'], pk['encargado']
    unicas.pop('encargado', None)
    fks = [f for f in fks if f[0] != 'encargado']

    columnas['encargado_sede'] = [('k_identificador', 'uuid', False), ('k_id_sede', 'uuid', False),
                                  ('b_estado_activo', 'boolean', False)]
    pk['encargado_sede'] = ['k_identificador', 'k_id_sede']
    fks += [('encargado_sede', 'FK_encargado_sede_persona', ['k_identificador'], 'persona'),
            ('encargado_sede', 'FK_encargado_sede_sede', ['k_id_sede'], 'sede')]

    columnas['encargado_area'] = [('k_identificador', 'uuid', False),
                                  ('k_id_area_servicio', 'uuid', False),
                                  ('b_estado_activo', 'boolean', False)]
    pk['encargado_area'] = ['k_identificador', 'k_id_area_servicio']
    fks += [('encargado_area', 'FK_encargado_area_persona', ['k_identificador'], 'persona'),
            ('encargado_area', 'FK_encargado_area_area_servicio', ['k_id_area_servicio'],
             'area_servicio')]
    return columnas, pk, fks, unicas


def entidad(tabla, columnas, pk, fks, unicas, estereotipo=''):
    claves_foraneas = {c for t, _, cols, _ in fks if t == tabla for c in cols}
    marca = f' <<{estereotipo}>>' if estereotipo else ''
    lineas = [f'entity "{tabla}" as {tabla}{marca} {{']
    llave = pk.get(tabla, [])
    for columna, tipo, nula in columnas[tabla]:
        if columna in llave:
            sufijo = ' <<PK, FK>>' if columna in claves_foraneas else ' <<PK>>'
            lineas.append(f'  * {columna} : {tipo}{sufijo}')
    lineas.append('  --')
    for columna, tipo, nula in columnas[tabla]:
        if columna in llave:
            continue
        sufijo = ' <<FK>>' if columna in claves_foraneas else ''
        obligatoria = '' if nula else '* '
        lineas.append(f'  {obligatoria}{columna} : {tipo}{sufijo}')
    for nombre in unicas.get(tabla, []):
        lineas.append(f'  .. {nombre} ..')
    lineas.append('}')
    return '\n'.join(lineas)


def relaciones(fks, columnas, tablas):
    salida = []
    for tabla, nombre, cols, destino in fks:
        if tabla not in tablas or destino not in tablas:
            continue
        nulas = {c for c, _, n in columnas[tabla] if n}
        lado = 'o|' if any(c in nulas for c in cols) else '||'
        etiqueta = f' : {", ".join(cols)}' if len(cols) > 1 else ''
        salida.append(f'{tabla} }}o--{lado} {destino}{etiqueta}')
    return salida


def completo(titulo, nombre, columnas, pk, fks, unicas, estereotipos, nota):
    partes = [f'@startuml {nombre}', ESTILO, f'title {titulo}', '']
    tablas = set(columnas)
    for modulo, miembros in MODULOS.items():
        presentes = [t for t in miembros if t in tablas]
        if not presentes:
            continue
        partes.append(f'package "{modulo}" {{')
        for t in presentes:
            partes.append(entidad(t, columnas, pk, fks, unicas, estereotipos.get(t, '')))
        partes.append('}\n')
    sueltas = tablas - {t for m in MODULOS.values() for t in m}
    if sueltas:
        raise SystemExit(f'Tablas sin modulo asignado: {sorted(sueltas)}')
    partes += relaciones(fks, columnas, tablas)
    partes += ['', nota, '@enduml', '']
    return '\n'.join(partes)


def main():
    columnas, pk, fks, unicas = leer()
    if 'encargado' not in columnas:
        raise SystemExit('El volcado no tiene la tabla encargado: no es V40')

    leyenda = """legend right
  **Cómo leerlo**
  Punto negro: columna obligatoria (NOT NULL)
  «PK» llave primaria · «FK» foránea
  Línea punteada con nombre: restricción única o de exclusión
  Pata de gallo hacia la tabla que apunta; doble raya: foránea
  obligatoria; círculo: anulable. Las compuestas llevan sus columnas
  Índices únicos parciales: ver esquema-bd-malphasos en SecondBrain
endlegend"""

    actual = completo('MalphasOS — esquema actual (V40, 36 tablas)', 'esquema-actual',
                      columnas, pk, fks, unicas, {'encargado': 'retirada'},
                      leyenda.replace('endlegend',
                                      '  <color:#AE1800>rojo</color>: tabla que V41 retira\nendlegend'))
    (SALIDA / 'esquema-actual.puml').write_text(actual)

    c2, p2, f2, u2 = cambiar(columnas, pk, fks, unicas)
    despues = completo('MalphasOS — esquema con V41 (PROPUESTO, 37 tablas)', 'esquema-con-v41',
                       c2, p2, f2, u2, {'encargado_sede': 'nueva', 'encargado_area': 'nueva'},
                       leyenda.replace('endlegend',
                                       '  <color:#1F6B35>verde</color>: tabla nueva de V41\nendlegend'))
    (SALIDA / 'esquema-con-v41.puml').write_text(despues)

    # El cambio, solo con las tablas que toca y sus vecinas.
    vecinas = ['persona', 'cliente', 'sede', 'area_servicio']
    antes_t = vecinas + ['encargado']
    despues_t = vecinas + ['encargado_sede', 'encargado_area']

    def bloque(titulo, alias, tablas, cols, llaves, foraneas, uq, estereo):
        lineas = [f'package "{titulo}" as {alias} {{']
        renombre = {t: f'{alias}_{t}' for t in tablas}
        for t in tablas:
            texto = entidad(t, cols, llaves, foraneas, uq, estereo.get(t, ''))
            texto = texto.replace(f'as {t}', f'as {renombre[t]}', 1)
            lineas.append(texto)
        lineas.append('}')
        for linea in relaciones(foraneas, cols, set(tablas)):
            origen, resto = linea.split(' ', 1)
            flecha, destino = resto.split(' ', 1)
            destino, _, etiqueta = destino.partition(' : ')
            lineas.append(f'{renombre[origen]} {flecha} {renombre[destino]}'
                          + (f' : {etiqueta}' if etiqueta else ''))
        return '\n'.join(lineas)

    cambio = [
        '@startuml cambio-v41', ESTILO,
        'title V41 — un encargado puede responder por varias sedes y áreas (PROPUESTO)', '',
        bloque('Antes (V40): una persona, una sola asignación', 'antes', antes_t,
               columnas, pk, fks, unicas, {'encargado': 'retirada'}),
        '',
        bloque('Después (V41): una fila por cada sede y cada área a cargo', 'despues', despues_t,
               c2, p2, f2, u2, {'encargado_sede': 'nueva', 'encargado_area': 'nueva'}),
        '',
        'antes -[hidden]right-> despues',
        '',
        """note bottom of antes_encargado
  La llave primaria es la persona:
  **una sola fila por persona**, y por eso
  una sola sede o una sola área.
  CHK_encargado_asignacion ata el tipo
  a la columna llena.
end note""",
        """note bottom of despues_encargado_sede
  Llave compuesta (persona, sede): la misma
  sede no se repite para una persona.
  Quitar una asignación apaga b_estado_activo;
  volver a darla lo enciende en la misma fila.
end note""",
        """note bottom of despues_encargado_area
  Igual, por (persona, área). El tipo ya no
  es una columna: lo dice la tabla, así que
  los dos CHECK de V40 sobran.
end note""",
        """note as regla
  **Lo que el esquema no comprueba y queda en el servicio**
  Todas las asignaciones activas de una persona son
  del **mismo cliente**: la sede apunta a su cliente y el
  área llega a él por su sede, en dos tablas distintas.
  Puede estar a cargo de una sede y de un área de esa
  misma sede a la vez (decidido por el usuario).
  Sin fecha de inicio: nada la pide ni la muestra.
end note""",
        """note as datos
  **Migración de los datos**
  HEADQUARTER  → encargado_sede (persona, sede, activo)
  SERVICE_AREA → encargado_area (persona, área, activo)
  y después DROP TABLE encargado.
end note""",
        '@enduml', '']
    (SALIDA / 'cambio-v41.puml').write_text('\n'.join(cambio))


main()
