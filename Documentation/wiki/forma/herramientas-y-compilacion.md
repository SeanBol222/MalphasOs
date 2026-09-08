---
name: herramientas-y-compilacion
description: Como se compila cada documento, como se regeneran los diagramas PlantUML, que ignora el .gitignore, y la discrepancia del devcontainer
tags: [forma, herramientas, compilacion, plantuml]
fuente: "Documentation/.gitignore, Documentation/.devcontainer/devcontainer.json, Documentation/dockerfile.devcontainer"
estado: vigente
updated: 2026-09-05
---

# Herramientas y compilación

## Compilar un documento LaTeX

Desde el directorio del documento:

```bash
cd Documentation/IEEE830 && latexmk -pdf IEEE830.tex
```

`latexmk` resuelve por sí solo las pasadas que hagan falta para el índice, las tablas `longtable` y las referencias cruzadas. **No usar `pdflatex` a mano** — una sola pasada deja el índice y las referencias desactualizados. **Un documento que no compila no se commitea.**

## Regenerar un diagrama PlantUML

`plantuml` no está en el `PATH` de la máquina de desarrollo (sí lo está dentro del devcontainer, que instala un wrapper — ver más abajo). Se invoca el JAR directamente:

```bash
java -jar /home/sean-omarchy/.vscode/extensions/jebbs.plantuml-2.18.1/plantuml.jar -tsvg <archivo>.puml
rsvg-convert -f pdf -o <salida>.pdf <archivo>.svg
```

**El rodeo por SVG es obligatorio**: la exportación directa `-tpdf` del JAR necesita la librería Batik y falla en este entorno. Tras tocar un diagrama, hay que recompilar el documento que lo incluye y comprobar que el PDF resultante lo muestra — un `\includegraphics` a un archivo que no se regeneró queda con la versión vieja sin que LaTeX avise.

Los diez diagramas fuente (`.puml`) viven en `IEEE830/use_cases/<caso>/`, cada uno junto a su `.svg`, `.pdf` y (salvo `malphasos_general`) su `.png`. Ver [[diagramas-de-casos-de-uso]] para la lista completa y cuáles están incluidos en la ERS.

## Qué versiona el `.gitignore` y qué no

`Documentation/.gitignore` ignora los productos de compilación de LaTeX (`*.aux`, `*.log`, `*.out`, `*.toc`, `*.lof`, `*.lot`, `*.synctex.gz`, `*.fdb_latexmk`, `*.fls`, `*.bbl`, `*.blg`, `texput.log`) y las salidas que la extensión de PlantUML de VS Code deja fuera del directorio del diagrama cuando no se configura la ruta de exportación (`out/`, `__WorkspaceFolder__/`).

**Los `.pdf` sí se versionan**: son el entregable, y se leen sin tener LaTeX instalado. Consecuencia práctica: cada recompilación local los marca como modificados en `git status` aunque el contenido visible no cambie (metadatos internos del PDF, fecha de compilación). Conviene revisar el diff binario o el propio PDF antes de añadirlo al índice, no darlo por hecho.

## El devcontainer

`.devcontainer/devcontainer.json` + `dockerfile.devcontainer` — imagen base `texlive/texlive:latest` con Java, Graphviz, `librsvg2-bin`, ImageMagick, Ghostscript y Poppler ya instalados, más un PlantUML 1.2024.7 descargado en `/opt/plantuml/plantuml.jar` con un wrapper en `/usr/local/bin/plantuml`. Dentro del devcontainer, a diferencia de la máquina de desarrollo, **`plantuml` sí está en el `PATH`**.

**Discrepancia sin resolver**: `devcontainer.json` configura las recetas de LaTeX Workshop (`latex-workshop.latex.recipes`) con `pdflatex` y `pdflatex × 2`, no con `latexmk`, mientras que esta misma guía —y el `CLAUDE.md` que la origina— indica compilar con `latexmk -pdf`. Quien use el devcontainer con la receta por omisión de VS Code obtendrá un PDF con el índice y las referencias cruzadas desactualizados tras un solo cambio, exactamente el problema que `latexmk` existe para evitar. No se ha corregido el `devcontainer.json` porque no es responsabilidad de esta wiki tocar la configuración; se deja registrado como defecto conocido.

## Notas relacionadas

[[diagramas-de-casos-de-uso]] · [[defectos-conocidos-de-la-ers]] · [[convenciones-latex]]
