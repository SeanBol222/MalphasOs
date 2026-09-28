---
name: arquitectura-frontend-malphasos
description: Como se construye el frontend de MalphasOS -Angular, por modulo de negocio, cliente generado desde OpenAPI- y por que cada pieza es el espejo de una del backend
tags: [frontend, arquitectura, angular, "describe:malphasos"]
source: Documentation/FrontendDesign/DeclaracionDeDisenoFrontend.tex
estado: estable
updated: 2026-09-28
---

# Arquitectura del frontend de MalphasOS

**Esta nota dice cómo se escribe código de frontend aquí.** Las decisiones y su porqué están en el documento oficial, `Documentation/wiki/documentos/declaracion-diseno-frontend.md` en `Documentation/`; esta nota es la versión operativa para quien va a construir. El sistema visual tiene nota aparte: [[sistema-de-diseno-malphasos]].

**Estado al 2026-09-28**: el proyecto existe, habla con el API desde un navegador y cubre **clientes, equipos con su catálogo, órdenes de trabajo y reportes de servicio**, con **368** pruebas. (Esta línea decía «decidido y sin escribir, no existe todavía el proyecto»: cierto hasta el **2026-09-13**.)

## El stack, y dónde vive

| Pieza | Elección |
|---|---|
| Framework | **Angular**. La versión se fija al crear el proyecto, no de memoria |
| Estilos | **Tailwind** con los tokens del manual de marca |
| Componentes | **spartan/ui** sobre Angular CDK |
| Datos de servidor | **TanStack Query**, adaptador de Angular |
| Formularios | Reactivos de Angular |
| Autenticación | `keycloak-angular` |
| Contrato | Cliente TypeScript **generado desde OpenAPI** y versionado |
| Pruebas | Unitarias · integración contra API simulada · extremo a extremo en login y crear orden |

**Vive en `malphasos-frontend/`**, hermano de `malphasos/`. Es una decisión de esta nota, no del documento oficial: el `CLAUDE.md` de la raíz decía que el código vivía **exclusivamente** en `malphasos/`, y esa frase se corrigió el mismo día porque describía un proyecto sin frontend.

## Por qué Angular: el espejo del backend

La razón de la elección no fue popularidad sino **correspondencia estructural**. Cada pieza del frontend tiene su equivalente exacto en el backend hexagonal, y eso permite razonar las dos mitades con el mismo vocabulario:

| En el backend | En el frontend |
|---|---|
| Puerto de entrada (`WorkOrderServicePort`) | Servicio inyectable del módulo (`WorkOrderApiService`) |
| `@PreAuthorize("hasAuthority('work-order.write')")` | Guard de ruta con la misma autoridad |
| Catálogo de errores por módulo | Catálogo de traducción por módulo |
| Módulo hexagonal (`workorder/`) | Carpeta de feature (`features/workOrder/`) |

**Lo que costó, y está escrito para que no se olvide**: el starter de autenticación del proyecto original estaba clasificado como `reusable:alta` y es React. Angular lo descarta. Es la **primera vez que el proyecto desecha algo marcado como reutilizable**. Ver la corrección en [[integracion-keycloak-frontend]].

## Estructura

```
malphasos-frontend/src/app/
  core/       sesion, interceptores, traduccion de errores
  shared/     componentes de interfaz, utilidades
  features/
    client/   equipment/   person/   location/   workOrder/
```

**Por módulo de negocio, con los nombres del backend**, y no por tipo técnico. Dos razones:

- El frontend del original está organizado en `pages/`, `services/` y `auth/`, y [[arquitectura-frontend]] dice explícitamente que ahí **no hay convención que copiar**.
- La matriz de trazabilidad relaciona requisitos con código. Si `features/workOrder/` se llama igual que el paquete del backend, la relación se lee sin traducir.

## Las capas dentro de un módulo, y la regla que las sostiene

| Capa | Responsabilidad |
|---|---|
| Cliente generado | Tipos y llamadas, producidos desde OpenAPI. **No se edita a mano** |
| Servicio del módulo | Encapsula TanStack Query y el cliente generado. Única puerta al servidor |
| Componentes | Presentación e interacción |
| Modelo de vista | Traduce entre lo que el API devuelve y lo que la pantalla necesita, cuando no coinciden |

> **Un componente nunca llama al API directamente.** Siempre pasa por el servicio de su módulo.

**Un servicio por agregado, no uno por módulo** — precisado el 2026-09-26 al construir la sección de clientes. Esta nota decía «servicio del módulo», y con cuatro agregados dentro de `client` —cliente, sede, área de servicio y encargado— eso habría sido el archivo más grande del frontend. El backend tampoco lo hace así: separa en `application/services/<agregado>/`. Lo que la regla protege —que TanStack Query no se escape a las pantallas— se cumple igual.

### La navegación tiene un solo origen, y desde el 2026-09-26 tiene dos niveles

`NAVEGACION` es la única fuente: **el menú y las rutas se derivan de la misma lista**, de modo que no puede haber un destino en el menú que no exista ni una página que el menú no ofrezca. Lo fija una prueba, y es el equivalente de `RestAuthorizationCoverageTest` del backend.

Una entrada puede tener **hijas**, y entonces cambia de naturaleza: **no tiene página propia**. `catalogo` no es una pantalla, es el sitio donde están sus cinco piezas, así que su ruta redirige a la primera y el menú la pinta como un **botón que despliega**, no como un enlace.

| Dónde | Qué hay |
|---|---|
| Cabecera | `Catálogo ▾` despliega las cinco piezas, cada una con su dirección |
| Dentro de la sección | Una subnavegación con las mismas cinco, para saltar sin volver a la cabecera |
| URL | `/catalogo/fabricantes` — **se puede enlazar, recargar y volver con el botón de atrás** |

**Rutas y no pestañas con estado interno**, y las tres cosas de esa última fila son la razón. Además, cada pieza se convierte en **su propio trozo diferido**: quien entra a corregir una marca ya no descarga los modelos.

**El desplegable se abre al pulsar, no al pasar el ratón.** Con el ratón por encima no se puede navegar con el teclado y en un teléfono no hay ratón. Es un `button` con `aria-expanded` y `aria-controls`: sin lo segundo, un lector de pantalla se entera de que algo se abrió pero no de qué.

### Las claves de caché son jerárquicas, y eso es lo que evita el defecto clásico

`['clientes'] → ['clientes', id] → ['clientes', id, 'sedes'] → ['sedes', id] → ['sedes', id, 'areas']`.

TanStack Query invalida **por prefijo**, así que una escritura sobre un cliente alcanza sola a su ficha y a su lista de sedes. Sin eso hay que acordarse de invalidar cada pantalla que mira el mismo dato, y el día que alguien lo olvide el síntoma es «creé algo y no aparece hasta recargar». Está fijado por pruebas: cada escritura comprueba que **sale una segunda consulta**, y quitar la invalidación las pone rojas.

Lo que **no** se invalida también es una decisión: renombrar un área no toca la rama del cliente, porque la lista de sedes de un cliente no dice nada de sus áreas.

Esa regla no es estética: **es lo que acota el riesgo de TanStack Query**, cuyo adaptador de Angular se distribuye con el sufijo `experimental` en el nombre del paquete. Si su API cambia entre versiones, lo que se toca son los servicios de módulo y no cada pantalla.

## Autenticación y autorización

El patrón del original se conserva aunque su código no: instancia única, inicio de sesión al arrancar, refresco del token antes de que expire, cierre de sesión si el refresco falla. Flujo de código de autorización con **PKCE**, que es el correcto para un cliente público.

Dos precisiones que evitan un malentendido peligroso:

- **El frontend no autoriza: oculta.** Esconder un botón mejora la experiencia; el permiso lo comprueba el servidor en cada llamada. Un frontend que «protege» una operación no protege nada.
- **El vocabulario de autoridades no se duplica a mano.** Sale del token y se contrasta con el del backend. Dos listas escritas por separado se desincronizan, y este proyecto ya tiene precedentes documentados de eso.
- **Y desde el 2026-09-26 hay prueba de que los literales de las rutas existen.** Ocurrió lo que la advertencia anterior anunciaba: las rutas de las sedes se escribieron con `headquarter.read` y `headquarter.write`, que **no existen** —las sedes las protege `client.*`—. Nada fallaba: el guard mandaba a `/sin-permiso` y la pantalla quedaba inalcanzable para todo el mundo, incluido el administrador. Una prueba lee ahora `app.routes.ts`, extrae cada `requiereAutoridad('…')` y exige que el realm conceda ese rol; otra comprueba que la lista no esté vacía, para que no pase en vacío. Es el equivalente de `RestAuthorizationCoverageTest` en el otro extremo de la línea.

**Ocultar exige saber qué se puede**, y eso se lee con `Sesion.puede(...)` **como señal calculada**, no como booleano: las autoridades se releen en cada evento de Keycloak —renovación de token incluida—, y un valor calculado en el constructor se quedaría con el de entonces.

**Lo que un grupo no puede leer no se disimula.** El grupo `clients` no tiene `location.read` ni `person.read`, de modo que para esos usuarios el catálogo de países y la lista de personas responden 403. La pantalla distingue entonces **«no tiene» de «no se pudo consultar»**: decir «sin país» de un cliente que sí lo tiene es afirmar algo falso, y es lo que hacía la primera versión.

Las 19 autoridades y qué protege cada una, en [[modelo-de-permisos]].

## El contrato se genera, no se escribe

Los tipos y las firmas salen de los documentos OpenAPI que el backend publica por módulo. **Si el backend renombra un campo, el frontend deja de compilar.** Escribirlos a mano convierte ese mismo cambio en un fallo en ejecución, o en ninguno.

**Lo generado se versiona.** Así el proyecto compila sin backend levantado y los cambios del contrato aparecen como diferencias revisables.

Encaja con lo que el backend ya hace: tres de sus grupos de OpenAPI —`client`, `equipment` y `work-order`— tienen prueba que verifica que el grupo contiene sus recursos. Ver [[openapi-swagger]].

## Traducción de errores

Un catálogo por módulo traduce cada código a un mensaje en español, con uno genérico de respaldo.

El backend se tomó el trabajo de mantener **tres familias distintas** —«no existe», «datos inválidos» y «conflicto de estado»— y nunca comparte códigos entre ellas. **Si el frontend las funde en «ha ocurrido un error», tira a la basura esa decisión.** Se presentan distinto: lo que no existe dice qué referencia falló; lo inválido señala el campo; el conflicto dice qué impide la operación **ahora**.

La redacción sigue el tono del manual de marca — ver [[sistema-de-diseno-malphasos]].

## Pruebas

Mismo listón que el backend, que llega a este punto con **810** pruebas y la costumbre de verificar por mutación. El frontend va por **368**, contadas el 2026-09-28. (Decía **154** el 2026-09-26 y antes 624 del backend sin citar las del frontend, que entonces no existían.)

| Nivel | Qué cubre |
|---|---|
| Unitarias | Validación de formularios, guards, traducción de errores, lógica de los servicios |
| Integración | La pantalla completa contra un API simulado, **con los códigos de error reales** |
| Extremo a extremo | Login y crear una orden, contra el sistema real |
| Accesibilidad | Analizador automático sobre cada pantalla, dentro de las de integración |

**El API simulado responde con los códigos reales.** Simular un error genérico probaría que la pantalla muestra algo, no que muestra lo correcto.

### Zoneless: `whenStable()` no se puede esperar con una petición en vuelo

Encontrado el 2026-09-26, y costó una tanda de pruebas que **agotaban su tiempo en vez de fallar**: la aplicación es zoneless y una petición HTTP sin responder cuenta como **tarea pendiente**, así que `await fixture.whenStable()` mientras hay una en vuelo no termina nunca. Se espera por **tics vacíos** —`setTimeout(0)` más `detectChanges()`— hasta que la señal llega al DOM, y por tics y no por milisegundos porque un retardo fijo es una carrera lenta.

Los ayudantes viven en `src/testing/pantalla.ts` —`asentar`, `responderA`, `atenderRefresco`— en lugar de copiarse en cada pantalla, que es lo que estaba a punto de pasar. Los del catálogo de equipos, en `src/testing/catalogo.ts`, y ahí está escrita otra trampa: **`http.match()` consume las peticiones que casan**, así que no sirve para preguntar si una existe — preguntar y responder tienen que ser la misma operación.

### Un formulario reactivo no es una señal

Un `computed()` que lea `formulario.getRawValue()` **no vuelve a calcularse nunca**: no tiene de qué depender, y con `OnPush` se queda con el primer valor para siempre. Los métodos llamados desde la plantilla sí se reevalúan en cada ciclo; los calculados, no. Donde hace falta reaccionar a lo que se escribe —desplegables encadenados, avisos que dependen de lo elegido— el valor entra como señal con `toSignal(formulario.valueChanges)`. Encontrado el 2026-09-26 escribiendo el panel que crea un modelo.

### La invalidación de caché no siempre se espera

TanStack aguarda la promesa que devuelve `onSuccess` antes de resolver la mutación. En un servicio normal eso es lo correcto: cuando la mutación termina, la lista ya está fresca. **En una cadena de cinco altas seguidas es un freno**: cada paso esperaba la recarga completa del catálogo que no necesitaba. En `CatalogoApi` la invalidación se lanza sin esperarla, y lleva escrito el porqué. Se descubrió porque la prueba del panel **se colgaba en el segundo paso en vez de fallar**.

### Un control añadido a un formulario desactivado nace activo

Encontrado el 2026-09-28 en la tabla de verificación de un reporte, y **el defecto era invisible desde el código**: el formulario se desactiva cuando el reporte está cerrado, pero las casillas se crean más tarde —cuando llega el tipo del equipo, cuatro consultas después— y un control que se añade a un formulario ya desactivado **no hereda ese estado**. La tabla de un reporte cerrado se podía teclear; el servidor lo habría rechazado, pero la pantalla estaba ofreciendo algo que no existe.

La regla que queda: cuando el contenido de un formulario se construye desde datos asíncronos, **el estado activo/desactivado se aplica también al reconstruirlo**, no solo cuando cambia la condición. Lo encontró la prueba del caso no editable, que es de las que parecen tontas hasta que pasa esto.

### Un fallo en `afterEach` se lee como ochenta y seis

También del 2026-09-28, y es la trampa de diagnóstico más caras de esta sesión. Una prueba dejó **una petición sin responder**; el `http.verify()` del `afterEach` lanzó, y esa excepción **impidió a Angular desmontar el TestBed**. Las once pruebas siguientes fallaron con «el módulo ya está instanciado», los trabajadores del corredor se contaminaron entre sí y el recuento llegó a **86 fallos en archivos que nadie había tocado** —clientes, armazón— con recuentos distintos en cada ejecución.

Dos cosas que conviene llevarse:

- **Un solo fallo de limpieza se disfraza de regresión general.** Ante decenas de fallos en archivos no tocados, buscar el **primero por orden de ejecución** en vez de leer el total: aquí el resto era humo.
- **La causa era drenar las peticiones en una sola pasada.** La recarga que provoca invalidar la caché no sale en el mismo tic que la escritura, así que un único `http.match()` encuentra la lista vacía y la petición llega después. Se drena en vueltas, sin salir en la primera vacía.

### Montar el TestBed dentro de la prueba solo funciona una vez

El entorno de pruebas de Angular reinicia el TestBed en un `beforeEach` propio, de modo que **la primera prueba monta bien y la segunda encuentra el módulo ya instanciado**. Montar va en el `beforeEach` del archivo; para cambiar de autoridades en un caso concreto, `TestBed.resetTestingModule()` y volver a montar, que es lo que hacen las pantallas con permisos distintos.

## Por dónde se empieza

**Rebanada vertical: arranque de la aplicación, autenticación y el flujo completo de órdenes de trabajo.** Atraviesa sesión, datos, formularios, errores y accesibilidad de una vez, de modo que una decisión equivocada aparece en la primera semana y no en la quinta.

> **Lo que se hizo de verdad, y por qué el orden cambió.** Tras el arranque y la autenticación entró **clientes** y no órdenes de trabajo, por decisión del usuario el 2026-09-13, y se **cerró entera** el 2026-09-26: ficha, edición, baja, contactos, sedes, áreas de servicio y encargados. No fue un desvío: sin sedes ni áreas no hay dónde registrar un equipo, y **una orden de trabajo solo puede tocar equipos de áreas de su propia sede**, de modo que el formulario de órdenes —que es lo que cierra sus cuatro RF de pantalla— no tenía contra qué construirse. La rebanada vertical ya se había atravesado con la primera versión de clientes.

El formulario de una orden arrastra consigo el selector de cliente, el de sede, la selección múltiple de áreas y la de equipos — piezas que los demás módulos reutilizarán.

**Cierra cuatro requisitos** que hoy están abiertos sólo por falta de formulario: RF-03, RF-04, RF-06 y RF-07. Ver [[hoja-de-ruta-producto]].

> **Cerró tres de los cuatro el 2026-09-27**, y RF-04 quedó como desviación consciente. Y el **2026-09-28 entraron los reportes de servicio**, en cuatro tandas: la ficha del reporte con los cinco campos de RF-15, la tabla de verificación, el historial de un equipo y el bloque que autocompleta desde la orden —que es lo que cerró **RF-11**—. El detalle de cada decisión está en [[dominio-reporte-servicio]]; lo que vale para cualquier pantalla está arriba, en las tres lecciones nuevas de pruebas.

## Lo que está decidido y aplazado

**Instalación en el dispositivo y consulta sin conexión**: la aplicación las tendrá, no entran ahora. Aplazarlas no incumple nada —la ERS pide acceso desde el teléfono **sin instalar nada nativo**, y una web responsiva lo cumple literalmente—.

**La escritura sin conexión es otra cosa y tiene un bloqueo real**: no hay claves de idempotencia ni bloqueo optimista en el backend, comprobado sobre las seis migraciones. Un reintento duplicaría órdenes. Si se retoma, la decisión ya tomada es que **el cliente genere el identificador de la orden**, y entonces el backend deja de estar cerrado.

## Notas relacionadas

[[sistema-de-diseno-malphasos]] · [[arquitectura-frontend]] · [[integracion-keycloak-frontend]] · [[hoja-de-ruta-producto]] · [[dominio-reporte-servicio]] · [[modelo-de-permisos]] · [[openapi-swagger]] · [[patron-catalogo-errores-por-contexto]] · [[arquitectura-hexagonal]] · [[seguridad-keycloak-backend]]
