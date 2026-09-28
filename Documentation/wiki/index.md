# Índice — Wiki de la documentación de MalphasOS

Catálogo de contenido. Ver [[CONVENCIONES.md]] para las convenciones. ⭐ marca la nota más importante de su categoría.

## La regla central

- [[regla-implementado-vs-previsto]] ⭐ — No se documenta como existente algo que no está implementado. Los macros `\estadoImplementado`/`\estadoPrevisto`/`\noVerificable` y cómo se aplican.

## Proyecto

- [[datos-del-proyecto]] — Cliente, siglas, duración, presupuesto, fases, autor, institución.
- [[objetivos-y-criterios-de-exito]] ⭐ — OBJ-01 a OBJ-07 y CE-01 a CE-10, con qué mide a qué.
- [[interesados-riesgos-hitos]] — Los 8 interesados, 10 riesgos y 5 hitos del documento de constitución.

## Requisitos

- [[priorizacion-moscow]] — Las cuatro listas del apartado 3.5 de la ERS, con sus 18 códigos inexistentes y 10 requisitos reales sin clasificar.
- [[rf-ordenes-trabajo]] — RF-01 a RF-07 (3.2.1). **Seis implementados** entre el 2026-09-13 y el 2026-09-27, y **RF-04 como desviación consciente**. (Decía «previstos en su totalidad»: cierto hasta el 2026-09-13.)
- [[rf-clientes]] — RF-08 (3.2.2). El único de su categoría; implementado.
- [[rf-reportes-mantenimiento]] — RF-09 a RF-17 (3.2.3). **RF-09, RF-11 y RF-15 implementados** entre el 27 y el 28 de septiembre de 2026; RF-13 espera los protocolos de RF-14 y RF-17 la firma. (Decía «previstos en su totalidad»: cierto hasta el 2026-09-27.)
- [[rf-firma-digital]] — RF-18, RF-21 (3.2.4). Previstos.
- [[rf-hojas-vida]] — RF-22 a RF-27 (3.2.5). Dos implementados, dos previstos.
- [[rf-inventario]] — RF-36, RF-37 (3.2.6). Previstos, Won't Have.
- [[rf-alertas-calibracion]] — RF-40, RF-41 (3.2.7). Previstos, Won't Have.
- [[rf-modulo-comercial]] — RF-45, RF-47 (3.2.8). Previstos.
- [[rf-usuarios-seguridad]] — RF-49 a RF-53 (3.2.9). Los cinco implementados, ninguno con objetivo que lo justifique.
- [[requisitos-no-funcionales]] ⭐ — Los 23 RNF de 3.3 y 3.8. Solo uno implementado (RNF-23, JWT).
- [[requisitos-de-dominio]] — RD-01 a RD-07 (sin RD-02). Los seis sin enunciado propio.
- [[estado-de-implementacion]] ⭐ — La cifra agregada (**17**/31, 1/23) con su evidencia, cómo se verificó y **por qué estuvo tres semanas caducada en 8**.

## Glosario

- [[glosario-dominio]] — Sede, área de servicio, encargado, orden de trabajo, hoja de vida, INVIMA, verificación metrológica, equipo/modelo/unidad.
- [[correspondencia-terminologica]] ⭐ — ERS vs. código vs. realm de Keycloak. El caso "profesional responsable"/`Manager`/`engineer`.

## Documentos oficiales

- [[ers-ieee830]] — La Especificación de Requisitos de Software. Estructura completa, apartado por apartado.
- [[documento-constitucion]] — El Project Charter. Objetivos, criterios, interesados, riesgos, Gantt.
- [[plan-gestion-alcance]] — Cómo se define, descompone, verifica y controla el alcance de MSO.
- [[declaracion-diseno-frontend]] ⭐ — Cómo se construye la interfaz: Angular, Tailwind, accesibilidad AA, offline aplazado. Y los tres costes que declara.
- [[manual-de-marca]] ⭐ — **La autoridad del sistema visual.** Logotipo, color, tipografía, retícula y tono de voz.
- [[matriz-de-trazabilidad]] ⭐ — El documento más nuevo y el que centraliza casi todos los defectos conocidos.
- [[matriz-de-interesados]] — Análisis de interesados (Poder vs. Interés), expectativas, trazabilidad y riesgos.

## Forma: cómo se escribe

- [[convenciones-latex]] — Macros propios, portada, márgenes, tablas, énfasis, formatos de código.
- [[paleta-de-colores]] — Los hexadecimales del cuerpo del documento y del design system ampliado.
- [[herramientas-y-compilacion]] — `latexmk`, el JAR de PlantUML, el rodeo por SVG, el `.gitignore`, el devcontainer.

## Defectos conocidos

- [[defectos-conocidos-de-la-ers]] ⭐ — Referencias rotas, huecos de trazabilidad, incoherencias de prioridad y de rendimiento, RF-49 autodependiente, restos de maquetación.
- [[diagramas-de-casos-de-uso]] — Los diez `.puml`, cuáles están en la ERS y cuáles dibujan módulos inexistentes.

---

**27 notas de contenido** más `CONVENCIONES.md` y `log.md` · última actualización 2026-09-28 · ver [[log.md]] para el historial.
