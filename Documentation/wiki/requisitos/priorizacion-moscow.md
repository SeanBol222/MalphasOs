---
name: priorizacion-moscow
description: Las cuatro listas del apartado 3.5 de la ERS, con sus 18 codigos inexistentes y los 10 requisitos reales que quedaron sin clasificar
tags: [requisitos, moscow, priorizacion, defecto-conocido]
fuente: "Documentation/IEEE830/IEEE830.tex, apartado 3.5"
estado: defecto-conocido
updated: 2026-09-05
---

# Priorización MoSCoW (apartado 3.5 de la ERS)

La priorización se hizo con la gerente de BolívarBioingeniería LTDA para definir el alcance del MVP. Cuatro listas de códigos, en prosa antes de las listas explícitas:

- **Must Have**: RF-01, RF-02, RF-03, RF-04, RF-05, RF-07, RF-08, RF-09, **RF-10**, RF-15, RD-03, RD-04, RF-49, RF-50, RF-51, RF-52, RF-53
- **Should Have**: RD-01, RF-06, RNF-06, RF-11, RF-13, RD-05, RNF-12, RNF-13, RNF-14, RNF-16, RNF-17, RF-31, RF-32, RF-33, RF-34, RNF-23
- **Could Have**: RNF-01, RNF-03, RNF-04, RF-17, RNF-08, RF-22, **RF-23**, RF-24, **RF-25**, RF-26, RF-27, RD-06, RNF-15, RNF-20, RNF-21
- **Won't Have**: RNF-10, **RF-28, RF-29, RF-30**, RNF-11, **RF-35**, RF-36, RF-37, RNF-18, **RF-38, RF-39**, RD-07, RF-40, RF-41, **RF-42, RF-43**, RNF-19

(En negrita, los códigos que no existen como requisito en ningún otro apartado — ver más abajo y [[defectos-conocidos-de-la-ers]].)

## El listado cita 18 códigos `RF-` que no existen en el apartado 3.2

De los 49 códigos `RF-` distintos que la ERS menciona en todo el texto, solo 31 tienen sección propia con nombre, descripción y criterios de aceptación en 3.2. Los otros 18 aparecen aquí, en el apartado 3.5, o como dependencia de un requisito real, y nunca se definen: RF-10, RF-16, RF-20, RF-23, RF-25, RF-28, RF-29, RF-30, RF-31, RF-32, RF-33, RF-34, RF-35, RF-38, RF-39, RF-42, RF-43, RF-46. El patrón: se concentran en las categorías que la propia ERS marca fuera del MVP inicial (fotografías, inventario, alertas, cotizaciones). Detalle completo en [[defectos-conocidos-de-la-ers]].

## Diez requisitos reales sin clasificación MoSCoW

Cinco funcionales con sección propia en 3.2 no aparecen en ninguna de las cuatro listas: **RF-14, RF-18, RF-21, RF-45, RF-47**. El caso de RF-18/RF-21 (firma digital) es el más llamativo: el párrafo de prosa de Won't Have menciona explícitamente "la firma digital unificada", pero ninguno de los dos códigos figura en la lista real de esa categoría — la narrativa y la lista de códigos se contradicen. Ver [[rf-firma-digital]].

Cinco no funcionales tampoco: **RNF-02, RNF-05, RNF-07, RNF-09, RNF-22**.

En total, 10 de los 54 requisitos funcionales y no funcionales reales —casi uno de cada cinco— carecen de la clasificación que el apartado 3.5 dice haber aplicado a todos. En cambio, **los seis códigos de dominio, sin nombre ni enunciado, sí tienen todos prioridad asignada**: la única propiedad que la ERS les reconoce es, precisamente, la que sobra sin un enunciado que priorizar.

## Cómo leer esto al escribir sobre priorización

Si se cita la lista MoSCoW de una categoría, conviene filtrar los códigos inexistentes en vez de reproducirlos como si fueran requisitos reales — repetir el error no ayuda a un lector nuevo. Las notas `rf-*` de esta wiki ya hacen ese filtrado en su columna MoSCoW.

## Notas relacionadas

[[defectos-conocidos-de-la-ers]] ⭐ · [[requisitos-de-dominio]] · [[rf-firma-digital]] · [[estado-de-implementacion]]
