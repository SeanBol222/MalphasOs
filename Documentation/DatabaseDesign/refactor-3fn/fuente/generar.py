"""Genera los diagramas PlantUML de la refactorizacion del esquema a 3FN, a partir del esquema vivo (V41).

Uso, desde Documentation/DatabaseDesign/refactor-3fn:
    bash fuente/dibujar.sh

que llama a este guion, dibuja y deja cada diagrama en su carpeta. Ver la cabecera de dibujar.sh.

Los volcados son los de V41, del 2026-10-27, los mismos que los de V42-recibido-por-orden. Lo que se
cambia y por que esta en ../analisis-2026-10-27.md.

Escribe:
  esquema-actual.puml        la base de hoy, con lo que cambia en rojo
  esquema-final.puml         la base despues de la refactorizacion, V42 a V52 (PROPUESTO)
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
    'company — empresa y documentos': ['empresa', 'telefono_empresa', 'imagen_documento'],
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


CAMBIAN = ['persona', 'correo_persona', 'telefono_persona', 'firma_persona', 'empresa',
           'orden_trabajo', 'verificacion_tipo_equipo', 'reporte_servicio', 'sede', 'unidad_medida',
           'intervencion', 'orden_trabajo_equipo', 'patron_verificacion_reporte', 'dato_verificacion',
           'paso_reporte']


def renombrar(columnas, fks, tabla, viejo, nuevo):
    columnas[tabla] = [(nuevo if c == viejo else c, t, n) for c, t, n in columnas[tabla]]
    return [(t, n, [nuevo if (t == tabla and c == viejo) else c for c in cs], d) for t, n, cs, d in fks]


def cambiar(columnas, pk, fks, unicas):
    """La refactorizacion del analisis del 2026-10-27: A1-A4, B1-B3, B5, B6, C1-C5 y sin el puente."""
    columnas = {t: list(c) for t, c in columnas.items()}
    pk, fks, unicas = dict(pk), list(fks), {t: list(u) for t, u in unicas.items()}

    def quitar_columnas(tabla, nombres):
        columnas[tabla] = [c for c in columnas[tabla] if c[0] not in nombres]

    def quitar_fk(nombre):
        nonlocal fks
        fks = [f for f in fks if f[1] != nombre]

    # C5: los nombres que enganan.
    fks = renombrar(columnas, fks, 'orden_trabajo', 'k_identificador', 'k_id_ingeniero')
    fks = renombrar(columnas, fks, 'reporte_servicio', 't_finalizado', 'f_finalizado')
    fks = renombrar(columnas, fks, 'firma_persona', 'k_id_persona', 'k_identificador')
    pk['firma_persona'] = ['k_identificador']

    # C3: el dueno de un correo o un telefono de persona es obligatorio.
    for tabla in ('correo_persona', 'telefono_persona'):
        columnas[tabla] = [(c, t, False if c == 'k_identificador' else n) for c, t, n in columnas[tabla]]

    # A1: los telefonos de la empresa, en su tabla. C2: su ciudad, del catalogo.
    # C1: el logo, por huella.
    quitar_columnas('empresa', ['t_telefonos', 'n_ciudad', 'b_logo', 't_logo_tipo'])
    columnas['empresa'] += [('k_id_ciudad', 'uuid', True), ('k_huella_logo', 'varchar(64)', True)]
    fks += [('empresa', 'FK_empresa_ciudad', ['k_id_ciudad'], 'ciudad'),
            ('empresa', 'FK_empresa_logo', ['k_huella_logo'], 'imagen_documento')]
    # Construida en V43 como lista de valores: la llave es (empresa, posicion), sin identificador ni
    # borrado logico, porque el formulario la reescribe entera.
    columnas['telefono_empresa'] = [('k_id_empresa', 'uuid', False),
                                    ('i_orden', 'integer', False),
                                    ('n_telefono_empresa', 'varchar(30)', False)]
    pk['telefono_empresa'] = ['k_id_empresa', 'i_orden']
    fks += [('telefono_empresa', 'FK_telefono_empresa_empresa', ['k_id_empresa'], 'empresa')]

    # C1: la firma de una persona, por huella.
    quitar_columnas('firma_persona', ['b_imagen'])
    columnas['firma_persona'].insert(1, ('k_huella', 'varchar(64)', False))
    fks += [('firma_persona', 'FK_firma_persona_imagen', ['k_huella'], 'imagen_documento')]

    # A2: sin segundo tipo de persona: es encargado quien tiene asignaciones.
    quitar_columnas('persona', ['t_segundo_tipo_persona'])

    # A3: la orden llega a su cliente por la sede.
    quitar_columnas('orden_trabajo', ['k_id_cliente'])
    quitar_fk('FK_orden_trabajo_cliente')
    quitar_fk('FK_orden_trabajo_sede_del_cliente')
    fks += [('orden_trabajo', 'FK_orden_trabajo_sede', ['k_id_sede'], 'sede')]
    unicas['sede'] = [u for u in unicas.get('sede', []) if u != 'UQ_sede_identidad_con_cliente']

    # A4: la verificacion llega a su magnitud por la unidad.
    quitar_columnas('verificacion_tipo_equipo', ['k_id_magnitud'])
    quitar_fk('FK_verificacion_tipo_equipo_unidad_de_su_magnitud')
    fks += [('verificacion_tipo_equipo', 'FK_verificacion_tipo_equipo_unidad', ['k_id_unidad_medida'],
             'unidad_medida')]
    unicas['unidad_medida'] = [u for u in unicas.get('unidad_medida', [])
                               if u != 'UQ_unidad_medida_por_magnitud']

    # B6 y el puente: sin orden_trabajo_equipo. Un equipo esta en una orden si tiene un reporte activo
    # en ella, y el reporte apunta a la orden y al equipo por separado. Su area -B6- no se guarda: un
    # equipo en una orden sin ejecutar no se traslada, y al ejecutarla la orden se congela en su
    # documento, con el area de cada equipo.
    del columnas['orden_trabajo_equipo'], pk['orden_trabajo_equipo']
    unicas.pop('orden_trabajo_equipo', None)
    fks = [f for f in fks if f[0] != 'orden_trabajo_equipo' and f[3] != 'orden_trabajo_equipo']
    fks += [('reporte_servicio', 'FK_reporte_servicio_orden', ['k_id_orden_trabajo'], 'orden_trabajo'),
            ('reporte_servicio', 'FK_reporte_servicio_equipo_cliente', ['k_id_equipo_cliente'],
             'equipo_cliente')]
    columnas['orden_trabajo'] += [('t_documento', 'jsonb', True),
                                  ('b_documento_tardio', 'boolean', False)]

    # B1, B2 y B3: un reporte abierto lee en vivo, y uno cerrado lee su documento congelado.
    quitar_columnas('patron_verificacion_reporte',
                    ['n_nombre', 'n_marca', 'k_serie', 'n_certificado', 'f_vencimiento', 'n_modelo',
                     'k_codigo_interno', 't_rango', 't_resolucion', 'f_calibracion',
                     'n_empresa_calibradora'])
    quitar_columnas('dato_verificacion', ['n_unidad', 'd_tolerancia'])
    # Y su posicion, que tambien era copia de la prioridad del tipo: un reporte no repite protocolo.
    quitar_columnas('paso_reporte', ['n_nombre', 't_descripcion', 't_indicacion', 'i_orden'])
    unicas['paso_reporte'] = [u for u in unicas.get('paso_reporte', []) if u != 'UQ_paso_reporte_orden']
    unicas['paso_reporte'].append('UQ_paso_reporte_protocolo')

    # B5: sin intervencion: la hoja de vida lee el documento de los reportes cerrados.
    for tabla in ('intervencion',):
        del columnas[tabla], pk[tabla]
        unicas.pop(tabla, None)
    fks = [f for f in fks if f[0] != 'intervencion' and f[3] != 'intervencion']

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



ESPACIO = """\
skinparam nodesep 70
skinparam ranksep 90
skinparam padding 4
skinparam defaultFontSize 15
"""


def slug(modulo):
    return modulo.split(' ')[0]


def vista_general(titulo, nombre, columnas, fks, estereotipos):
    """Solo los nombres de las tablas, por modulo, y sus relaciones: la forma de un vistazo."""
    partes = [f'@startuml {nombre}', ESTILO, ESPACIO, 'left to right direction',
              f'title {titulo}', '']
    tablas = set(columnas)
    for modulo, miembros in MODULOS.items():
        presentes = [t for t in miembros if t in tablas]
        if not presentes:
            continue
        partes.append(f'package "{modulo}" {{')
        for t in presentes:
            marca = estereotipos.get(t, '')
            partes.append(f'  entity "{t}" as {t}' + (f' <<{marca}>>' if marca else ''))
        partes.append('}')
    vistas = set()
    for tabla, _, _, destino in fks:
        if tabla in tablas and destino in tablas and tabla != destino and (tabla, destino) not in vistas:
            vistas.add((tabla, destino))
            partes.append(f'{tabla} }}o--|| {destino}')
    partes += ['hide members', '@enduml', '']
    return '\n'.join(partes)


def por_modulo(titulo, nombre, modulo, columnas, pk, fks, unicas, estereotipos):
    """Las tablas de un modulo con sus columnas, y las de fuera a las que apuntan solo con su nombre."""
    propias = [t for t in MODULOS[modulo] if t in columnas]
    vecinas = sorted({d for t, _, _, d in fks if t in propias and d not in propias}
                     | {t for t, _, _, d in fks if d in propias and t not in propias})
    partes = [f'@startuml {nombre}', ESTILO, ESPACIO, f'title {titulo}', '']
    partes.append(f'package "{modulo}" {{')
    for t in propias:
        partes.append(entidad(t, columnas, pk, fks, unicas, estereotipos.get(t, '')))
    partes.append('}')
    for t in vecinas:
        partes.append(f'entity "{t}" as {t} <<otro modulo>> {{\n}}')
    tablas = set(propias) | set(vecinas)
    partes += relaciones([f for f in fks if f[0] in propias or f[3] in propias], columnas, tablas)
    partes += ['skinparam class<<otro modulo>> {', '  BackgroundColor #EDEBEA', '  BorderColor #9A9795',
               '  FontColor #6B6866', '}', '@enduml', '']
    return '\n'.join(partes)


def legibles(prefijo, etiqueta, columnas, pk, fks, unicas, estereotipos):
    """Escribe la vista general y una por modulo, con el prefijo dado."""
    (SALIDA / f'{prefijo}-general.puml').write_text(
        vista_general(f'{etiqueta} — vista general', f'{prefijo}-general', columnas, fks, estereotipos))
    for modulo in MODULOS:
        if not any(t in columnas for t in MODULOS[modulo]):
            continue
        nombre = f'{prefijo}-{slug(modulo)}'
        (SALIDA / f'{nombre}.puml').write_text(
            por_modulo(f'{etiqueta} — {modulo}', nombre, modulo, columnas, pk, fks, unicas,
                       estereotipos))



AMPLIO = """\
skinparam nodesep 110
skinparam ranksep 140
skinparam padding 6
skinparam defaultFontSize 15
skinparam packageStyle rectangle
"""


def main():
    columnas, pk, fks, unicas = leer()
    if 'intervencion' not in columnas or 'encargado_sede' not in columnas:
        raise SystemExit('El volcado no es de V41')

    leyenda = """legend right
  **Cómo leerlo**
  Punto negro: columna obligatoria (NOT NULL)
  «PK» llave primaria · «FK» foránea
  Línea punteada con nombre: restricción única o de exclusión
  Pata de gallo hacia la tabla que apunta; doble raya: foránea
  obligatoria; círculo: anulable. Las compuestas llevan sus columnas
  Índices únicos parciales y foráneas sin índice: ver el análisis
endlegend"""

    marcas = {t: 'cambia' for t in CAMBIAN}
    marcas['intervencion'] = 'retirada'
    marcas['orden_trabajo_equipo'] = 'retirada'
    actual = completo('MalphasOS — esquema actual (V41, 37 tablas)', 'esquema-actual',
                      columnas, pk, fks, unicas, marcas,
                      leyenda.replace('endlegend',
                                      '  <color:#AE1800>rojo</color>: tabla que la refactorización cambia o retira\nendlegend'))
    (SALIDA / 'esquema-actual.puml').write_text(actual)

    # La propuesta final, V42 a V52. El recibido no cambia el esquema: se queda en el reporte (V39), y
    # firmar varios a la vez -toda la orden o un area- es cosa del servicio y de la pantalla.
    c2, p2, f2, u2 = cambiar(columnas, pk, fks, unicas)
    marcas_finales = {t: 'cambia' for t in CAMBIAN if t in c2}
    marcas_finales['telefono_empresa'] = 'nueva'
    final = completo('MalphasOS — propuesta final, V42 a V52 (PROPUESTO, 36 tablas)', 'esquema-final',
                     c2, p2, f2, u2, marcas_finales,
                     leyenda.replace('endlegend',
                                     '  <color:#AE1800>rojo</color>: tabla cambiada · <color:#1F6B35>verde</color>: nueva\nendlegend'))
    final = final.replace(ESTILO, ESTILO + AMPLIO, 1).replace('skinparam linetype ortho\n', '', 1)
    (SALIDA / 'esquema-final.puml').write_text(final)

    # Las vistas legibles, pedidas por el usuario: el diagrama entero no se lee.
    legibles('actual', 'Esquema actual (V41)', columnas, pk, fks, unicas, marcas)
    legibles('final', 'Propuesta final (PROPUESTO)', c2, p2, f2, u2, marcas_finales)


main()
