#!/usr/bin/env bash
# Regenera todos los diagramas de esta carpeta y los deja ordenados (2026-10-27).
#
# Uso, desde Documentation/DatabaseDesign/refactor-3fn:
#     bash fuente/dibujar.sh
#
# Necesita java, el plantuml.jar de la extension de VS Code y rsvg-convert. Los .puml van a
# fuente/plantuml/; los .svg y .pdf, a una carpeta por esquema:
#     1-hoy/              la base en V41
#     2-final/            la propuesta final, tras la refactorizacion a 3FN (V42 a V50)
# y en cada una: vista-general, completo y modulos/<modulo>.
set -euo pipefail
cd "$(dirname "$0")/.."
JAR=/home/sean-omarchy/.vscode/extensions/jebbs.plantuml-2.18.1/plantuml.jar

rm -rf fuente/plantuml 1-hoy 2-refactorizado 3-final 2-final
mkdir -p fuente/plantuml
python3 fuente/generar.py fuente/plantuml
java -jar "$JAR" -tsvg fuente/plantuml/*.puml

python3 - <<'PY'
import os, shutil, subprocess
from pathlib import Path
carpetas = {'esquema-actual': '1-hoy', 'actual': '1-hoy',
            'esquema-final': '2-final', 'final': '2-final'}
for svg in sorted(Path('fuente/plantuml').glob('*.svg')):
    nombre = svg.stem
    if nombre.startswith('esquema-'):
        destino = Path(carpetas[nombre]) / 'completo'
    else:
        prefijo, parte = nombre.split('-', 1)
        destino = (Path(carpetas[prefijo]) / 'vista-general' if parte == 'general'
                   else Path(carpetas[prefijo]) / 'modulos' / parte)
    destino.parent.mkdir(parents=True, exist_ok=True)
    shutil.move(svg, destino.with_suffix('.svg'))
    subprocess.run(['rsvg-convert', '-f', 'pdf', '-o', destino.with_suffix('.pdf'),
                    destino.with_suffix('.svg')], check=True)
PY
