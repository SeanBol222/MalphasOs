"""Genera los tres diagramas PlantUML del plan del recibido por orden a partir del esquema vivo (V41).

Uso, desde Documentation/DatabaseDesign/V42-recibido-por-orden:
    python3 fuente/generar.py fuente/plantuml
    java -jar <plantuml.jar> -tsvg -o ../.. fuente/plantuml/*.puml
    for f in *.svg; do rsvg-convert -f pdf -o "${f%.svg}.pdf" "$f"; done

Los .puml van a fuente/plantuml/ y los .svg y .pdf quedan arriba, solos (ordenado el 2026-10-27).

Los volcados se sacaron de la base de desarrollo en V41, el 2026-10-27, con las mismas consultas que
los de V41-encargados: information_schema.columns y pg_constraint de public.

Escribe:
  esquema-actual.puml   la base de hoy, completa
  cambio-v42.puml       solo lo que toca la migracion, antes y despues
  esquema-con-v42.puml  la base completa como quedaria
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
                          'sede', 'area_servicio', 'encargado_sede', 'encargado_area'],
    'equipment — equipos y catálogo': ['tipo_equipo', 'marca', 'fabricante', 'equipo', 'modelo',
                                       'equipo_cliente', 'magnitud', 'unidad_medida',
                                       'verificacion_tipo_equipo', 'punto_verificacion',
                                       'protocolo', 'protocolo_tipo_equipo', 'equipo_patron',
                                       'intervencion'],
    'work-order — órdenes de trabajo': ['orden_trabajo', 'orden_trabajo_equipo', 'recibido_orden'],
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
skinparam class<<cambia>> {
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


RECIBE = ['n_recibe_nombre', 'k_recibe_cedula', 'n_recibe_cargo', 'k_huella_firma_recibe',
          'f_firma_recibe']


def cambiar(columnas, pk, fks, unicas):
    """Aplica V42: el recibido sale del reporte y pasa a la orden, entera o por area."""
    columnas = {t: list(c) for t, c in columnas.items()}
    pk, fks, unicas = dict(pk), list(fks), {t: list(u) for t, u in unicas.items()}

    columnas['reporte_servicio'] = [c for c in columnas['reporte_servicio'] if c[0] not in RECIBE]
    fks = [f for f in fks if f[1] != 'FK_reporte_servicio_firma_recibe']

    unicas.setdefault('orden_trabajo', []).append('UQ_orden_trabajo_identidad_con_sede')
    unicas.setdefault('area_servicio', []).append('UQ_area_servicio_identidad_con_sede')

    columnas['recibido_orden'] = [
        ('k_id_recibido', 'uuid', False),
        ('k_id_orden_trabajo', 'uuid', False),
        ('k_id_sede', 'uuid', False),
        ('k_id_area_servicio', 'uuid', True),
        ('n_nombre', 'varchar(120)', False),
        ('k_cedula', 'varchar(20)', True),
        ('n_cargo', 'varchar(80)', True),
        ('k_huella_firma', 'varchar(64)', True),
        ('f_firma', 'timestamp', True),
        ('b_estado_activo', 'boolean', False),
    ]
    pk['recibido_orden'] = ['k_id_recibido']
    fks += [
        ('recibido_orden', 'FK_recibido_orden_orden_de_la_sede', ['k_id_orden_trabajo', 'k_id_sede'],
         'orden_trabajo'),
        ('recibido_orden', 'FK_recibido_orden_area_de_la_sede', ['k_id_area_servicio', 'k_id_sede'],
         'area_servicio'),
        ('recibido_orden', 'FK_recibido_orden_firma', ['k_huella_firma'], 'imagen_documento'),
    ]
    unicas['recibido_orden'] = ['UQ_recibido_orden_alcance_activo']
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
    if 'recibido_orden' in columnas or 'encargado_sede' not in columnas:
        raise SystemExit('El volcado no es de V41')

    leyenda = """legend right
  **Cómo leerlo**
  Punto negro: columna obligatoria (NOT NULL)
  «PK» llave primaria · «FK» foránea
  Línea punteada con nombre: restricción única o de exclusión
  Pata de gallo hacia la tabla que apunta; doble raya: foránea
  obligatoria; círculo: anulable. Las compuestas llevan sus columnas
  Índices únicos parciales: ver esquema-bd-malphasos en SecondBrain
endlegend"""

    actual = completo('MalphasOS — esquema actual (V41, 37 tablas)', 'esquema-actual',
                      columnas, pk, fks, unicas, {'reporte_servicio': 'cambia'},
                      leyenda.replace('endlegend',
                                      '  <color:#AE1800>rojo</color>: tabla de la que V42 saca columnas\nendlegend'))
    (SALIDA / 'esquema-actual.puml').write_text(actual)

    c2, p2, f2, u2 = cambiar(columnas, pk, fks, unicas)
    despues = completo('MalphasOS — esquema con V42 (PROPUESTO, 38 tablas)', 'esquema-con-v42',
                       c2, p2, f2, u2, {'recibido_orden': 'nueva'},
                       leyenda.replace('endlegend',
                                       '  <color:#1F6B35>verde</color>: tabla nueva de V42\nendlegend'))
    (SALIDA / 'esquema-con-v42.puml').write_text(despues)

    vecinas = ['orden_trabajo', 'orden_trabajo_equipo', 'sede', 'area_servicio', 'imagen_documento',
               'reporte_servicio']
    antes_t = vecinas
    despues_t = vecinas + ['recibido_orden']

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
        '@startuml cambio-v42', ESTILO,
        'title V42 — se firma el recibido por sede o por área, no por reporte (PROPUESTO)', '',
        bloque('Antes (V41): cada reporte guarda quién lo recibió y su firma', 'antes', antes_t,
               columnas, pk, fks, unicas, {'reporte_servicio': 'cambia'}),
        '',
        bloque('Después (V42): el recibido es de la orden, entera o por área', 'despues', despues_t,
               c2, p2, f2, u2, {'recibido_orden': 'nueva'}),
        '',
        'antes -[hidden]right-> despues',
        '',
        """note bottom of antes_reporte_servicio
  Cinco columnas de quien recibe en cada
  reporte: n_recibe_nombre, k_recibe_cedula,
  n_recibe_cargo, k_huella_firma_recibe y
  f_firma_recibe. Se firma reporte por reporte;
  la casilla de la orden solo copia la firma a
  los reportes que ya existen.
end note""",
        """note bottom of despues_recibido_orden
  Sin área: el recibido de toda la sede, es decir,
  de la orden entera. Con área: el de esa área.
  Una fila activa por alcance:
  UQ (orden, área) NULLS NOT DISTINCT.
  Las dos foráneas compuestas atan el área a la
  sede de la orden. Firma y fecha van juntas, y
  una firma no se cambia ni se retira.
end note""",
        """note as regla
  **Cómo toma su recibido cada equipo**
  Primero el de su área, si lo hay; si no, el de la
  sede. Sirve también para los reportes que se abran
  después de firmar: el recibido no se copia, se lee.
  Ya no se firma un reporte suelto (decidido por el
  usuario), y el reporte solo muestra el que le toca.
end note""",
        """note as datos
  **Migración de los datos**
  Cada reporte activo con recibido pasa a la orden:
  sin área si todos los de la orden coinciden, por
  área si coinciden dentro de cada área. Si dos no
  coinciden en la misma área, la migración se
  detiene y dice cuáles. Después se quitan las cinco
  columnas, sus dos CHECK y su foránea.
end note""",
        '@enduml', '']
    (SALIDA / 'cambio-v42.puml').write_text('\n'.join(cambio))


main()
