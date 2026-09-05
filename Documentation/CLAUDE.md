# CLAUDE.md — Documentación de MalphasOS

## Propósito

Este directorio contiene la documentación integral del proyecto **MalphasOS**, sistema de gestión de mantenimiento de equipos biomédicos para BolívarBioingeniería LTDA. Todos los documentos siguen estándares IEEE 830 (Especificación de Requisitos) con énfasis en claridad, concisión y completitud.

## Estado del proyecto

- **Proyecto**: MalphasOS v1.0
- **Cliente**: BolívarBioingeniería LTDA
- **Duración estimada**: 6 meses (26 semanas teóricas)
- **Presupuesto**: COP $15.000.000
- **Fases**: MVP (1.5 meses) + Sistema Completo (6 meses)
- **Autor**: Sean Sebastian Bolivar Calderon (20231020135)
- **Institución**: Universidad Distrital Francisco José de Caldas
- **Asignatura**: Calidad de Software

## Estructura de carpetas

```
Documentation/
├── CLAUDE.md                          ← Este archivo (guía general)
├── constitutionDocument/
│   ├── constitutionDocument.tex       ← Fuente LaTeX (482 líneas, condensado)
│   ├── constitutionDocument.pdf       ← PDF compilado (12 páginas)
│   ├── Images/
│   │   └── malphasos-stacked.png     ← Logo del proyecto
│   └── [archivos auxiliares LaTeX]
└── [Próximas carpetas de documentos]
```

## Documentos existentes

### 1. **Documento de Constitución (Project Charter)** 
- **Ubicación**: `constitutionDocument/constitutionDocument.tex`
- **Formato**: LaTeX + PDF compilado
- **Páginas**: 12 (reducido de 21 originales)
- **Contenido**: 
  - Descripción del proyecto y producto
  - Objetivos (1 general + 7 específicos)
  - Criterios de éxito (10 criterios)
  - Requisitos de aprobación (5 categorías)
  - Finalidad y justificación
  - Entregables (proyecto, Fase 1, Fase 2, soporte)
  - Interesados principales (8)
  - Riesgos identificados (10)
  - Duración, hitos y Gantt de 26 semanas
  - Presupuesto y roles (Sponsor, Director)

**Decisiones de diseño aplicadas:**
- ✅ Mantener TODAS las secciones IEEE 830 (ninguna eliminada)
- ✅ Reducir contenido en 50% (de 974 a 482 líneas LaTeX)
- ✅ Gantt integrado con pgfgantt en LaTeX
- ✅ Colores extraídos del design system
- ✅ Tablas optimizadas sin perder información

## Próximos documentos (Roadmap)

### 2. **Especificación de Requisitos de Software (ERS)** — IEEE 830
- **Objetivo**: Definir funcionalmente RF, RD, RNF del sistema
- **Estructura esperada**:
  - Descripción general del sistema
  - Requisitos funcionales (RF-01 a RF-NN)
  - Requisitos de dominio (RD-01 a RD-NN)
  - Requisitos no funcionales (RNF-01 a RNF-NN)
  - Priorización MoSCoW (Must, Should, Could, Won't)
  - Matriz de trazabilidad (RF → Objetivos → Criterios)
  - Casos de uso por módulo
  - Supuestos y restricciones

### 3. **Plan de Proyecto**
- **Objetivo**: Desglose de actividades y recursos
- **Contenido**:
  - Estructura desglosada del trabajo (WBS)
  - Timeline absoluta (fechas específicas)
  - Asignación de recursos
  - Dependencias y camino crítico
  - Hitos de seguimiento mensual
  - Presupuesto detallado por componente

### 4. **Diseño de Arquitectura del Sistema**
- **Objetivo**: Arquitectura técnica del sistema
- **Componentes**:
  - Diagrama de arquitectura general
  - Módulos funcionales y técnicos
  - Flujo de datos entre componentes
  - Stack tecnológico (Backend, Frontend, BD)
  - Patrones de diseño utilizados
  - Consideraciones de escalabilidad

### 5. **Especificación Técnica Detallada**
- **API REST**: Endpoints, métodos, payloads
- **Base de datos**: Modelo entidad-relación, tablas, constraints
- **Interfaz**: Mockups, flujos de usuario, wireframes
- **Seguridad**: Autenticación (JWT), autorización por roles, encriptación
- **Integraciones**: Firma digital, captura de fotos, exportación PDF/Excel

### 6. **Plan de Pruebas**
- **Pruebas funcionales**: Casos de uso por módulo
- **Pruebas de aceptación**: Criterios de acepta por requisito
- **Pruebas de rendimiento**: Tiempos de respuesta, carga concurrente
- **Pruebas de seguridad**: Validación de autenticación, inyección, XSS
- **Matriz de trazabilidad**: RF → Casos de prueba

### 7. **Manual de Usuario**
- **Versiones por perfil**:
  - Administrador (gestión clientes, órdenes, configuración)
  - Ingeniero Técnico (registro en campo, firmas)
  - SuperUsuario (creación de usuarios, permisos)
- **Contenido**: Guías paso a paso, screenshots, troubleshooting

### 8. **Documentación Técnica**
- **Para desarrolladores**:
  - Setup del entorno de desarrollo
  - Estructura de carpetas del código
  - Convenciones de código
  - Cómo ejecutar tests
  - Deployment en producción

## Convenciones de documentación

### LaTeX

**Paquetes estándar** (usar en todos los documentos):
```latex
\usepackage[utf8]{inputenc}
\usepackage[T1]{fontenc}
\usepackage{geometry}
\usepackage{longtable}
\usepackage{array}
\usepackage{fancyhdr}
\usepackage{graphicx}
\usepackage{float}
\usepackage{pgfgantt}  % Para cronogramas/diagramas
\usepackage{xcolor}    % Para colores personalizados
```

**Márgenes y tipografía**:
- Tamaño: 12pt, A4
- Márgenes: 2.5cm en todos lados
- Tipo de letra: Computer Modern estándar
- Espaciado: 6pt entre párrafos, sin sangría

**Formatos de referencia**:
- Requisitos: `RF-01`, `RD-03`, `RNF-07`
- Objetivos: `OBJ-01` a `OBJ-07`
- Criterios: `CE-01` a `CE-10`
- Riesgos: `R-01` a `R-10`
- Hitos: `H-00` a `H-04`

### Títulos de secciones

- Nivel 1 (`\section`): Mayúsculas, sin numeración especial
- Nivel 2 (`\subsection`): Solo primera letra mayúscula
- Nivel 3 (`\subsubsection`): Raro en estos docs, evitar exceso de profundidad

### Tablas

- Usar `longtable` para tablas multi-página
- Ancho mínimo columnas: 2cm (excepto ID)
- Encabezados en **bold**
- Alineación izquierda para texto, centro para números/IDs

### Énfasis

- **Bold** (`\textbf{}`): Conceptos clave, decisiones importantes
- *Cursiva* (`\textit{}`): Términos técnicos de primera mención, notas
- `Monoespaciado`: Códigos, referencias de archivos, comandos

### Números y formato

- Porcentajes: `40\,\%`, `99\,\%`
- Desigualdades: `$\geq$`, `$\leq$`, `$<$`, `$>$`
- Separador decimal español: `1{,}5` (no 1.5)
- Miles (si aplica): `15.000.000` con puntos

### Referencias cruzadas

- Usar `\label{}` y `\ref{}` para figuras y tablas
- Formato: `Ver Tabla~\ref{tab:criterios}` o `Figura~\ref{fig:gantt}`

## Paleta de colores del proyecto

Extraída del design system: `https://claude.ai/code/artifact/0030621c-968c-4862-a38c-17eef1ff1d96`

**Colores principales** (para Gantt, diagramas, énfasis):

| Color | Hex | Uso | Rol |
|-------|-----|-----|-----|
| Rojo principal | #EC3013 | Inicio, crítico | Primary |
| Naranja | #ef6853 | Desarrollo temprano | Secondary |
| Coral | #ff9783 | Desarrollo medio | Accent 1 |
| Rojo medio | #ff563c | Desarrollo avanzado | Accent 2 |
| Gris oscuro | #201E1D | Texto, fondo | Text |
| Gris claro | #F3F2F2 | Fondo, resalte | Background |

**Uso en documentos**:
- Gantt: Asignar color por fase/módulo
- Títulos enfatizados: Rojo principal
- Información secundaria: Grises
- Diagramas: Usar paleta completa ordenada

## Cronograma de 26 semanas

El Gantt en `constitutionDocument.pdf` es teórico y se divide así:

| Período | Duración | Contenido |
|---------|----------|-----------|
| Semana 1 | 1 sem | Landing Page empresa |
| Semanas 2-7 | 6 sem | Fase 1: MVP (Auth, Clientes, OT, Reportes, Equipos) |
| Semanas 8-26 | 19 sem | Fase 2: Sistema completo (Firma, Hojas de vida, Inventario, Alertas, Comercial, PDF/Excel, Capacitación) |

**Nota importante**: Estos plazos son **relativos al inicio formal del proyecto**. Las fechas absolutas deben definirse en el Plan de Proyecto.

## Cómo trabajar con estos documentos

### Compilación LaTeX

```bash
cd /path/to/document/
pdflatex nombrearchivo.tex
```

**Para tablas multi-página**, puede necesitar compilar dos veces:
```bash
pdflatex nombrearchivo.tex && pdflatex nombrearchivo.tex
```

### Edición

- **Editor recomendado**: VS Code + LaTeX Workshop
- **Alternativa**: Overleaf (cloud, sin setup local)
- **Validación**: Revisar PDF compilado después de cambios

### Versionado Git

```bash
git add Documentation/
git commit -m "docs: descripción del cambio"
git push origin branch-name
```

**Ramas de documentación sugeridas**:
- `docs/project-charter` — Cambios al Project Charter
- `docs/requirements` — Cambios a ERS
- `docs/architecture` — Cambios a diseño
- `docs/technical-spec` — Cambios a especificación técnica

### Mantener consistencia

1. **Cada documento debe tener**:
   - Portada con título, autor, fecha, versión
   - Índice de contenido (`\tableofcontents`)
   - Encabezado y pie de página estandarizados
   - Referencias cruzadas internas

2. **Antes de publicar**:
   - Compilar sin errores
   - Verificar índice automático
   - Revisar referencias (`\ref{}`, `\label{}`)
   - Probar links internos (si usa PDF interactivo)
   - Chequear ortografía (herramienta externa)

3. **Mantener jerarquía de carpetas**:
   - `./Images/` para todas las imágenes
   - `./templates/` para templates LaTeX reutilizables
   - Documentos principales en raíz de carpeta respectiva

## Comunicación con stakeholders

### Documentos para presentar

1. **Al cliente (BolívarBioingeniería)**:
   - Project Charter (resumido, versión ejecutiva)
   - ERS (detallado, en 2-3 sesiones)
   - Plan de Proyecto (con timeline y presupuesto)
   - Mockups/Diseño (en sesión de revisión)

2. **Al equipo de desarrollo**:
   - Especificación Técnica (completa)
   - Plan de Pruebas (criterios de aceptación)
   - Documentación Técnica (setup, convenciones)

3. **A usuarios finales**:
   - Manuales de Usuario (por rol)
   - Capacitación (sesión presencial o videoguía)

## Checklist de documentación completa

- [ ] Project Charter (✅ completado)
- [ ] Especificación de Requisitos (ERS)
- [ ] Plan de Proyecto
- [ ] Diseño de Arquitectura
- [ ] Especificación Técnica Detallada
- [ ] Plan de Pruebas
- [ ] Manuales de Usuario (Admin, Ingeniero, SuperUser)
- [ ] Documentación Técnica para desarrolladores
- [ ] Acta de cierre del proyecto (al final)

## Notas importantes

1. **IEEE 830 compliance**: Todos los documentos deben mantener trazabilidad: RF → Objetivos → Criterios de éxito → Plan de pruebas.

2. **Idioma**: Español (Colombia). Evitar anglicismos innecesarios.

3. **Números de versión**: Cada documento debe incluir versión. Formato: `v1.0`, `v1.1`, `v2.0`.

4. **Actualización**: Revisar documentos cada mes durante ejecución. Marcar cambios con fecha y autor.

5. **Confidencialidad**: Estos documentos contienen información sensible de BolívarBioingeniería. No compartir fuera del equipo autorizado.

## Contactos y referencias

- **Cliente**: BolívarBioingeniería LTDA (Gerente — Por definir)
- **Director del Proyecto**: Sean Sebastian Bolivar Calderon
- **Institución**: Universidad Distrital Francisco José de Caldas
- **Asignatura**: Calidad de Software

---

**Última actualización**: 1 de septiembre de 2026  
**Versión**: 1.0  
**Estado**: Proyecto en fase de planificación
