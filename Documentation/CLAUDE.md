# CLAUDE.md — Documentación de MalphasOS

## Propósito

Este directorio contiene la documentación formal del proyecto **MalphasOS**, sistema de gestión de clientes y de mantenimientos preventivos de equipos biomédicos para BolívarBioingeniería LTDA. El documento central es la Especificación de Requisitos de Software bajo IEEE 830; los demás documentos se apoyan en ella.

## La regla que gobierna todo lo que se escribe aquí

**No se documenta como existente algo que no está implementado.**

La ERS heredada describe módulos completos que el sistema no tiene —órdenes de trabajo, firma digital, reportes, inventario, alertas de calibración, módulo comercial—. Antes de afirmar que MalphasOS hace algo, hay que comprobarlo en `malphasos/`. Si no está, se marca como **previsto** o **fuera del alcance actual**, con una fórmula consistente en todo el documento.

El apartado 3.2 de la ERS ya aplica esta regla requisito por requisito con dos macros propios:

```latex
\newcommand{\estadoImplementado}{\textcolor{mainRed}{\textbf{[IMPLEMENTADO]}}}
\newcommand{\estadoPrevisto}{\textcolor{mainGray}{\textbf{[PREVISTO]}}}
```

Cada requisito cierra con un punto *Estado en la implementación actual* que cita la evidencia: la ruta del API, la tabla del esquema o el agregado que lo respalda. De los **31 requisitos funcionales, 8 están implementados y 23 son previstos**. Un documento que promete lo que el sistema no hace engaña a quien confía en él.

## Datos del proyecto

- **Proyecto**: MalphasOS v1.0 — siglas **MSO** en el plan de gestión del alcance
- **Cliente**: BolívarBioingeniería LTDA
- **Duración estimada**: 6 meses (26 semanas teóricas)
- **Presupuesto**: COP $15.000.000
- **Fases**: MVP (1.5 meses) + Sistema Completo (6 meses)
- **Autor**: Sean Sebastian Bolivar Calderon (20231020135)
- **Institución**: Universidad Distrital Francisco José de Caldas
- **Asignatura**: Calidad de Software

## Estado del proyecto

**El backend está construido**, no en planificación. Cuatro módulos de dominio completos de esquema a REST:

| Módulo | Estado |
|---|---|
| `bootstrap` | Configuración transversal, seguridad, OpenAPI, manejo de excepciones |
| `shared/domain/events` | Contrato de eventos de dominio + despachador in-process |
| `person` | Completo, más `PersonCommunicationPort` publicado hacia otros módulos |
| `location` | Completo: esquema, dominio, aplicación, persistencia, REST |
| `client` | Completo: cuatro agregados (cliente, sede, área de servicio, encargado) |
| `equipment` | Primera tanda completa: seis agregados. **Falta la segunda tanda**: verificaciones técnicas y datos metrológicos |

Migraciones `V1__baseline` a `V5__equipment_catalog`. Batería en 339 pruebas en verde al 2026-09-02 (cifra registrada en el `CLAUDE.md` de la raíz y en el wiki; no se ha vuelto a medir desde el worktree de documentación).

**La migración del backend está cerrada** salvo la segunda tanda de `equipment`. Lo pendiente del producto es frontend y opcionales.

La tabla de estado por módulo del `CLAUDE.md` de la raíz es la fuente de verdad y se mantiene al día. **Léela del disco antes de afirmar nada sobre el estado**: la copia que carga la sesión puede venir de un árbol anterior a los cambios en curso.

## Dónde se trabaja: worktrees separados

La documentación vive en un **worktree propio** sobre la rama `MalphasOS-Documentation`:

| Árbol | Rama | Contenido |
|---|---|---|
| `.../BolivarBioIngenieria/MalphasOS` | `main` | El código, en `malphasos/` |
| `.../BolivarBioIngenieria/MalphasOS-Documentation` | `MalphasOS-Documentation` | La documentación, en `Documentation/` |

Ambos comparten el mismo `.git`. Git impide que una rama esté activa en los dos árboles a la vez: si un comando falla diciendo que una rama ya está en uso, es esto y está bien que ocurra — cambia de rama, no fuerces.

**`Documentation/` es propiedad exclusiva de la documentación.** `malphasos/` y `SecondBrain/` son de solo lectura, incluso la copia que se ve dentro del worktree de documentación. Si al documentar aparece un defecto del código o una afirmación falsa del wiki, **no se arregla desde aquí**: se reporta.

## Estructura de carpetas

```
Documentation/
├── CLAUDE.md                              ← Este archivo
├── .gitignore                             ← Ignora productos de compilación LaTeX
├── .devcontainer/devcontainer.json        ← Entorno VS Code (TeX Live + PlantUML)
├── dockerfile.devcontainer                ← Imagen base del devcontainer
├── IEEE830/
│   ├── IEEE830.tex                        ← ERS (1862 líneas)
│   ├── IEEE830.pdf                        ← 44 páginas
│   └── use_cases/<caso>/<caso>.{puml,svg,png,pdf}
├── ScopeManagementPlan/
│   ├── PlanDeGestionDelAlcance.tex        ← 298 líneas
│   └── PlanDeGestionDelAlcance.pdf        ← 6 páginas
├── constitutionDocument/
│   ├── constitutionDocument.tex           ← 482 líneas
│   ├── constitutionDocument.pdf           ← 12 páginas
│   └── Images/                            ← Logo, escudo UD, casos de uso heredados
└── raw/
    └── IEEE830.pdf                        ← Versión previa de la ERS (42 pp.), solo referencia
```

## Documentos existentes

### 1. Especificación de Requisitos de Software (ERS) — IEEE 830

- **Ubicación**: `IEEE830/IEEE830.tex`
- **Páginas**: 44
- **Contenido**: introducción y alcance, exclusiones explícitas (apartado 1.2.2), descripción general, requisitos funcionales por dominio (apartado 3.2), requisitos no funcionales (3.3), casos de uso con diagramas.
- **Rasgo distintivo**: el apartado 3.2 está contrastado contra el código requisito por requisito con `\estadoImplementado` y `\estadoPrevisto`.

Los 8 requisitos implementados son RF-08 (crear cliente), RF-22 y RF-24 (hoja de vida de equipo), y RF-49 a RF-53 (login, rol, y alta, modificación y baja de usuarios). Los 23 restantes cubren órdenes de trabajo, reportes, firma digital, historial de intervenciones, inventario, alertas de calibración y módulo comercial.

### 2. Plan de Gestión del Alcance

- **Ubicación**: `ScopeManagementPlan/PlanDeGestionDelAlcance.tex`
- **Páginas**: 6
- **Siglas del proyecto**: MSO
- **Contenido**: cómo se define, descompone, verifica y controla el alcance. Toma la ERS como enunciado del alcance y sus 31 requisitos como descomposición.
- **Nota**: declara abiertamente que MSO **no** mantiene un diccionario de la EDT como documento aparte; el enunciado de cada requisito hace esa función.

### 3. Documento de Constitución (Project Charter)

- **Ubicación**: `constitutionDocument/constitutionDocument.tex`
- **Páginas**: 12 (reducido de 21 originales)
- **Contenido**: descripción del proyecto y producto, objetivos (1 general + 7 específicos), 10 criterios de éxito, 5 categorías de requisitos de aprobación, finalidad y justificación, entregables, 8 interesados, 10 riesgos, duración, hitos y Gantt de 26 semanas, presupuesto y roles.

**Decisiones de diseño aplicadas:**
- Mantener todas las secciones IEEE 830, ninguna eliminada
- Reducir contenido a la mitad (de 974 a 482 líneas LaTeX)
- Gantt integrado con `pgfgantt`, sin imagen externa
- Colores tomados del design system

## Diagramas de casos de uso

Los **diez fuentes `.puml`** están en `IEEE830/use_cases/`, cada uno en su subcarpeta junto a su `.svg`, `.pdf` y —salvo `malphasos_general`— su `.png`:

`alertas_calibracion`, `clientes`, `firma_digital`, `hojas_vida`, `inventario`, `malphasos_general`, `mantenimientos`, `modulo_comercial`, `ordenes_trabajo`, `usuarios_seguridad`.

Durante un tiempo se creyó que estos fuentes se habían perdido. No era cierto: estaban en una rama vieja y se rescataron. **Los diagramas se pueden regenerar.**

La ERS incluye ocho de ellos; `clientes` y `malphasos_general` están compilados pero todavía sin `\includegraphics` en el `.tex`.

## Cómo trabajar con estos documentos

### Compilación LaTeX

Desde el directorio del documento:

```bash
cd Documentation/IEEE830 && latexmk -pdf IEEE830.tex
```

`latexmk` resuelve por sí solo las pasadas que hagan falta para el índice, las tablas `longtable` y las referencias cruzadas. No usar `pdflatex` a mano.

**Un documento que no compila no se commitea.**

### Regeneración de diagramas

`plantuml` no está en el PATH; se invoca el JAR. La exportación directa a PDF necesita Batik y falla, así que se pasa por SVG:

```bash
java -jar /home/sean-omarchy/.vscode/extensions/jebbs.plantuml-2.18.1/plantuml.jar -tsvg <archivo>.puml
rsvg-convert -f pdf -o <salida>.pdf <archivo>.svg
```

Tras tocar un diagrama, recompilar el documento y comprobar que el PDF lo incluye.

### Qué se versiona y qué no

`Documentation/.gitignore` ignora `.aux`, `.log`, `.out`, `.toc`, `.lof`, `.lot`, `.synctex.gz`, `.fls`, `.fdb_latexmk`, `.bbl`, `.blg`, y los directorios `out/` y `__WorkspaceFolder__/` que deja la extensión de PlantUML.

**Los `.pdf` sí se versionan**: son el entregable, y se leen sin tener LaTeX instalado. La consecuencia es que cada recompilación los marca como modificados aunque no cambie nada visible. Conviene revisarlo antes de añadirlos al índice.

### Versionado Git

Micro-commits: una preocupación por commit, una rama por preocupación, siempre desde la rama de documentación actualizada. **El usuario revisa el diff antes de cada commit.**

```bash
git checkout -b docs/<tema>
git add Documentation/<ruta-concreta>
git commit
```

Nombres de rama por familia de documento:

- `docs/ieee830-<tema>` — cambios a la ERS
- `docs/manual-<tema>` — manuales
- `docs/diagramas-<tema>` — diagramas PlantUML

**Nunca `git add -A`.** Otras sesiones trabajan en el mismo repositorio sobre `malphasos/`; añadir solo los archivos propios de `Documentation/`.

**No se mergea a `main`.** La rama se deja hecha y se reporta; el usuario revisa antes de integrar. No tiene excepción.

**Nunca añadir atribución a los commits.** Ni `Co-Authored-By`, ni `Claude-Session`, ni "Generated with". Todo se atribuye al usuario.

**Mensajes de commit**: asunto en inglés siguiendo Conventional Commits (`docs(ieee830): ...`), cuerpo en español explicando **por qué**, no qué. Si una sección cambió porque el código la desmintió, decirlo.

### Edición

- **Editor**: VS Code + LaTeX Workshop. El `.devcontainer` de esta carpeta trae TeX Live, Java, Graphviz y `librsvg2-bin` ya instalados.
- **Validación**: compilar y revisar el PDF después de cada cambio.

## Convenciones de documentación

### LaTeX

Un documento nuevo debe verse como hermano de la ERS y del plan de alcance. Preámbulo de referencia (`IEEE830.tex`, `PlanDeGestionDelAlcance.tex`):

```latex
\documentclass[12pt,a4paper]{article}
\usepackage[margin=2.5cm]{geometry}
\usepackage{graphicx}
\usepackage{xcolor}
\usepackage{fancyhdr}
\usepackage{array}
\usepackage{longtable}
\usepackage{hyperref}
\usepackage{pgfgantt}   % solo si el documento lleva cronograma
```

**Márgenes y tipografía**:
- Tamaño: 12pt, A4
- Márgenes: 2.5 cm en todos lados
- Tipo de letra: Computer Modern estándar
- Espaciado: 6pt entre párrafos, sin sangría

**Macros propios en lugar de los de `article`**. La ERS y el plan de alcance titulan con `\sectiontitle` y `\subsectiontitle`, que componen la sección sin numerar y la inscriben en el índice a mano:

```latex
\newcommand{\sectiontitle}[1]{\section*{#1}\addcontentsline{toc}{section}{#1}}
\newcommand{\subsectiontitle}[1]{\subsection*{#1}\addcontentsline{toc}{subsection}{#1}}
```

La numeración va escrita dentro del título (`3.2.2. Gestión de Clientes`). **No usar `\section` ni `\subsection` directamente** en estos documentos.

Otro macro útil: `\ruta{}`, que compone rutas de API y de archivo con `\nolinkurl` en vez de `\texttt`, porque `\texttt` es indivisible y una ruta larga se sale de la caja.

**Formatos de referencia**:
- Requisitos: `RF-01`, `RD-03`, `RNF-07`
- Objetivos: `OBJ-01` a `OBJ-07`
- Criterios de éxito: `CE-01` a `CE-10`
- Riesgos: `R-01` a `R-10`
- Hitos: `H-00` a `H-04`

### Tablas

- Usar `longtable` para tablas multi-página
- Ancho mínimo de columnas: 2 cm (excepto ID)
- Encabezados en negrita
- Alineación izquierda para texto, centro para números e identificadores

### Énfasis

- **Negrita** (`\textbf{}`): conceptos clave, decisiones importantes
- *Cursiva* (`\textit{}`): términos técnicos de primera mención, notas
- `Monoespaciado`: códigos, referencias de archivos, comandos

### Números y formato

- Porcentajes: `40\,\%`, `99\,\%`
- Desigualdades: `$\geq$`, `$\leq$`, `$<$`, `$>$`
- Separador decimal español: `1{,}5` (no 1.5)
- Miles: `15.000.000` con puntos

### Referencias cruzadas

- Usar `\label{}` y `\ref{}` para figuras y tablas
- Formato: `Ver Tabla~\ref{tab:criterios}` o `Figura~\ref{fig:gantt}`

### Terminología

La ERS usa un vocabulario fijado —**sede**, **área de servicio**, **encargado**, **orden de trabajo**— y no se introducen sinónimos. Cuando el código llama a algo de otra forma (`Headquarter`, `ServiceArea`, `Manager`), la documentación manda para el lector y el código manda para la verdad: se explica la correspondencia en lugar de elegir a ciegas.

Idioma: español (Colombia). Evitar anglicismos innecesarios.

## Paleta de colores del proyecto

**Colores del cuerpo del documento** (ERS y plan de alcance):

| Color | Hex | Uso | Rol |
|-------|-----|-----|-----|
| `mainRed` | #EC3013 | Filete del encabezado, marca [IMPLEMENTADO] | Primary |
| `mainGray` | #6b6866 | Texto del encabezado, marca [PREVISTO] | Text atenuado |
| `mainWhite` | #F3F2F2 | Fondos y resaltes | Background |

**Paleta ampliada del design system** (Gantt del Project Charter, diagramas, énfasis):

| Color | Hex | Uso | Rol |
|-------|-----|-----|-----|
| Rojo principal | #EC3013 | Inicio, crítico | Primary |
| Naranja | #ef6853 | Desarrollo temprano | Secondary |
| Coral | #ff9783 | Desarrollo medio | Accent 1 |
| Rojo medio | #ff563c | Desarrollo avanzado | Accent 2 |
| Gris oscuro | #201E1D | Texto, fondo | Text |
| Gris claro | #F3F2F2 | Fondo, resalte | Background |

El Gantt del charter extiende esa gama hacia rojos progresivamente más oscuros (`#e15b47`, `#dd2b0f`, `#C91E10`, `#B51A0D`, `#9E1609`, `#8B1407`, `#7A1206`, `#6B0F05`, `#5C0D04`), uno por módulo, para que el orden del color siga el orden del cronograma.

**Uso en documentos**:
- Gantt: asignar color por fase o módulo
- Títulos enfatizados: rojo principal
- Información secundaria: grises
- Diagramas: usar la paleta completa en orden

## Cronograma de 26 semanas

El Gantt de `constitutionDocument.pdf` es teórico y se divide así:

| Período | Duración | Contenido |
|---------|----------|-----------|
| Semana 1 | 1 sem | Landing Page empresa |
| Semanas 2-7 | 6 sem | Fase 1: MVP (Auth, Clientes, OT, Reportes, Equipos) |
| Semanas 8-26 | 19 sem | Fase 2: Sistema completo (Firma, Hojas de vida, Inventario, Alertas, Comercial, PDF/Excel, Capacitación) |

**Nota importante**: estos plazos son **relativos al inicio formal del proyecto**. Las fechas absolutas deben definirse en el Plan de Proyecto.

## Documentos pendientes (roadmap)

### Plan de Proyecto
- **Objetivo**: desglose de actividades y recursos
- **Contenido**: EDT, cronograma con fechas absolutas, asignación de recursos, dependencias y camino crítico, hitos de seguimiento mensual, presupuesto detallado por componente

### Diseño de Arquitectura del Sistema
- **Objetivo**: arquitectura técnica del sistema
- **Componentes**: diagrama general, módulos funcionales y técnicos, flujo de datos, stack tecnológico, patrones de diseño, escalabilidad
- **Nota**: existe material de sobra para escribirlo — arquitectura hexagonal por módulo, agregados con eventos de dominio, y las decisiones registradas en `SecondBrain/wiki/malphasos/decisiones-tecnicas-malphasos.md`

### Especificación Técnica Detallada
- **API REST**: endpoints, métodos, payloads. Los cuatro módulos ya publican su grupo OpenAPI en `/v3/api-docs/<grupo>`
- **Base de datos**: modelo entidad-relación, tablas, restricciones. Migraciones `V1` a `V5`
- **Interfaz**: mockups, flujos de usuario, wireframes
- **Seguridad**: autenticación, autorización por roles, cifrado
- **Integraciones**: firma digital, captura de fotos, exportación PDF/Excel — **todas previstas, ninguna implementada**

### Plan de Pruebas
- **Pruebas funcionales**: casos de uso por módulo
- **Pruebas de aceptación**: criterios por requisito
- **Pruebas de rendimiento**: tiempos de respuesta, carga concurrente
- **Pruebas de seguridad**: autenticación, inyección, XSS
- **Matriz de trazabilidad**: RF → casos de prueba

### Manuales de Usuario
- **Por perfil**: Administrador, Ingeniero Técnico, SuperUsuario
- **Contenido**: guías paso a paso, capturas, resolución de problemas
- **Advertencia**: no hay frontend. Un manual de usuario escrito hoy documentaría un sistema que el usuario final no puede ver

### Documentación Técnica para desarrolladores
- **Contenido**: preparación del entorno, estructura de carpetas del código, convenciones, cómo ejecutar las pruebas, despliegue en producción

### Acta de cierre del proyecto
- Al final del proyecto

## Checklist de documentación

- [x] Documento de Constitución (Project Charter)
- [x] Especificación de Requisitos de Software (ERS) — IEEE 830
- [x] Plan de Gestión del Alcance
- [ ] Plan de Proyecto
- [ ] Diseño de Arquitectura
- [ ] Especificación Técnica Detallada
- [ ] Plan de Pruebas
- [ ] Manuales de Usuario (Admin, Ingeniero, SuperUsuario)
- [ ] Documentación Técnica para desarrolladores
- [ ] Acta de cierre del proyecto

## Mantener consistencia

**Cada documento debe tener**: portada con título, autor, fecha y versión; índice (`\tableofcontents`); encabezado y pie estandarizados; referencias cruzadas internas.

**Antes de commitear**: compilar sin errores, verificar el índice, revisar `\ref{}` y `\label{}`, comprobar los enlaces internos y pasar el corrector ortográfico.

**Jerarquía de carpetas**: una carpeta por documento, con su `.tex` y su `.pdf` en la raíz de esa carpeta; `Images/` para imágenes; `use_cases/` para diagramas PlantUML.

**Números de versión**: cada documento incluye versión con formato `v1.0`, `v1.1`, `v2.0`.

**Trazabilidad IEEE 830**: RF → objetivos → criterios de éxito → plan de pruebas.

## Comunicación con interesados

**Al cliente (BolívarBioingeniería)**: Project Charter en versión ejecutiva, ERS en 2-3 sesiones, Plan de Proyecto con cronograma y presupuesto, mockups en sesión de revisión. **Al presentar la ERS, dejar claro qué está construido y qué no**: las marcas del apartado 3.2 existen precisamente para esa conversación.

**Al equipo de desarrollo**: Especificación Técnica completa, Plan de Pruebas con criterios de aceptación, Documentación Técnica.

**A usuarios finales**: manuales por rol y capacitación.

**Confidencialidad**: estos documentos contienen información de BolívarBioingeniería. No compartir fuera del equipo autorizado.

## Contactos y referencias

- **Cliente**: BolívarBioingeniería LTDA (Gerente — Por definir)
- **Director del Proyecto**: Sean Sebastian Bolivar Calderon
- **Institución**: Universidad Distrital Francisco José de Caldas
- **Asignatura**: Calidad de Software

---

**Última actualización**: 5 de septiembre de 2026
**Versión**: 2.0
**Estado**: Backend construido y cerrado salvo la segunda tanda de `equipment`. Tres documentos formales entregados; 8 de 31 requisitos funcionales con código detrás.
