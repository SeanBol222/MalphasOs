---
name: declaracion-diseno-frontend
description: La Declaracion de Diseno del Frontend: que decide sobre plataforma, arquitectura, sistema visual, accesibilidad, offline y pruebas, y que deja abierto a proposito
tags: [documentos, frontend, decisiones, forma]
fuente: "Documentation/FrontendDesign/DeclaracionDeDisenoFrontend.tex"
estado: vigente
updated: 2026-09-13
---

# Declaración de Diseño del Frontend

**Qué es.** El documento que decide cómo se construye la interfaz de MalphasOS: plataforma, arquitectura, sistema visual, accesibilidad, comportamiento en movilidad y estrategia de pruebas. Dieciséis páginas, once apartados.

**Qué no es.** No introduce ningún requisito nuevo: decide **cómo** se satisfacen los que la ERS ya escribió. No es un manual de estilo —eso es [[manual-de-marca]]— ni una lista de pantallas.

**Por qué se escribió.** El backend está terminado en sus cinco módulos y no existe una línea de frontend. Cuatro requisitos de órdenes de trabajo —RF-03, RF-04, RF-06 y RF-07— están abiertos **únicamente porque no hay formulario**, y seis requisitos no funcionales enteros —RNF-12 a RNF-17— no tienen relación alguna con el backend.

## Las decisiones, en una tabla

| Ámbito | Decisión |
|---|---|
| Framework | **Angular**. Versión no fijada: se anota al crear el proyecto |
| Estilos | **Tailwind**, con la paleta del manual de marca como tokens |
| Componentes | **spartan/ui** sobre Angular CDK: el código vive en el repositorio |
| Datos | **TanStack Query**, aislado tras un servicio inyectable por módulo |
| Formularios | Reactivos de Angular |
| Contrato | **Cliente TypeScript generado desde OpenAPI**, y versionado |
| Estructura | Por módulo de negocio, **con los nombres del backend** |
| Accesibilidad | **WCAG 2.1 AA**, verificado con analizador automático en la batería |
| Movilidad | Web responsiva y táctil. **Instalación y offline aplazados**, no descartados |
| Pruebas | Unitarias, integración contra API simulada, y extremo a extremo en login y crear orden |
| Primer bloque | Rebanada vertical: arranque, autenticación y flujo de órdenes de trabajo |

## Las tres decisiones que tienen un coste escrito

El documento declara que **una decisión sin coste escrito se relee más tarde como si no lo hubiera tenido**, y aplica esa regla a sí mismo.

1. **Angular descarta un componente reutilizable.** El starter de autenticación del proyecto original —`keycloak.ts`, `AuthProvider`, `PrivateRoute`, `apiFetch`— estaba clasificado como directamente reutilizable y es React. Hay que reescribirlo contra `keycloak-angular`. **Es la primera vez que el proyecto desecha algo marcado como reutilizable**, y se hizo eligiendo la coherencia estructural con el backend hexagonal.

2. **TanStack Query se distribuye como experimental** en su adaptador de Angular. Se acota haciendo que ningún componente lo use directamente: vive dentro del servicio de cada módulo, de modo que un cambio de su API toca esos servicios y no las pantallas.

3. **El acento de la marca no alcanza AA como relleno de botón.** Ver abajo.

## El hallazgo de contraste

El manual de marca ya traía su propia regla: `#EC3013` no se usa para texto de párrafo sobre papel, y para texto en acento va `#AE1800`. Medido, la regla funciona.

**Lo que el manual no nombra es el botón.** Describe la acción principal como «relleno acento»; con `#EC3013` de relleno y la etiqueta en papel el contraste es **3,76:1**, por debajo del 4,5:1 que exige AA. El control más repetido de la aplicación no alcanzaría el nivel que el propio documento declara.

**La resolución no corrige el manual: extiende su regla a un caso que no nombraba.** El relleno usa el paso 700, `#AE1800`, con etiqueta en papel: 6,41:1. El acento se queda donde el manual lo pone para bordes, foco e iconografía de alerta, donde el umbral es 3,0 y sí cumple.

## Por qué el offline se aplazó, y qué queda decidido

La aplicación **tendrá** instalación y consulta sin conexión; se aplazan, no se descartan.

**Aplazarlas no incumple nada**: el apartado 2.1 de la ERS pide acceso desde el teléfono **sin instalar aplicaciones nativas**, y una página responsiva en el navegador lo cumple de la forma más literal posible. Ningún requisito pide instalación ni uso sin conexión.

**La escritura sin conexión sí tiene un bloqueo real, comprobado sobre el código**: no hay claves de idempotencia ni bloqueo optimista —ninguna de las seis migraciones tiene columna de versión—, de modo que un reintento crearía órdenes duplicadas. Y reintroduciría la mentira histórica que el módulo de órdenes se diseñó para impedir: un área congelada que el dispositivo tenía en caché puede no ser la real.

**Decisión ya tomada para cuando se retome**: el cliente generaría el identificador de la orden y el backend lo aceptaría en lugar de generarlo. Queda escrito para que quien lo retome no vuelva a decidirlo, y para que conste que **el backend dejaría de estar cerrado**.

## Dos extensiones propias sobre el manual de marca

Señaladas como tales en el documento, porque el manual manda:

- **Retícula en teléfono.** El manual fija 12 columnas y márgenes de 48 px pensando en escritorio. En anchos de teléfono se reducen a 4 columnas y 16 px, conservando medianil y la escala de 8.
- **Relleno de la acción principal** con el paso 700, por el contraste de arriba.

## Qué deja abierto a propósito

La versión exacta del framework, que se fija al crear el proyecto; el diseño de cada pantalla; la *landing page*, que es otro producto y queda fuera con su porqué escrito; los módulos cuyo backend no existe; y **cuándo** entran la instalación y el uso sin conexión, decididos en forma pero no en fecha.

## Notas relacionadas

[[manual-de-marca]] · [[paleta-de-colores]] · [[requisitos-no-funcionales]] · [[rf-ordenes-trabajo]] · [[estado-de-implementacion]] · [[convenciones-latex]]
