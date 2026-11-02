# Cambios de MalphasOS

Lo que trae cada versión, para quien usa y despliega MalphasOS. El detalle de cada decisión y de cómo se
construyó está en `SecondBrain/`; los requisitos, en la ERS de `Documentation/`.

## [1.0.0] — 2026-11-02

La primera versión para producción: la gestión de clientes, equipos y mantenimientos de una empresa de
servicio biomédico, desde la orden de trabajo hasta el informe firmado en PDF. Cubre **24 de los 31
requisitos funcionales** de la ERS; lo que queda fuera está al final.

### Clientes y ubicaciones

- Clientes con su NIT, su sigla y sus contactos; sedes con su ciudad, y sus áreas de servicio.
- **Los supervisores del cliente**: la gente del cliente que entra a ver lo suyo y aprueba sus órdenes.
  Se nombran desde la ficha del cliente, cada uno con su cargo, y una persona puede supervisar a varios
  clientes. Son quienes firman como responsables en la hoja de vida y en el informe.
- Los 249 países y las 1.350 ciudades —los municipios de Colombia y la capital de cada país— vienen
  sembrados, con un buscador que no distingue tildes ni mayúsculas.

### Equipos

- El catálogo en cinco piezas —tipo, marca, fabricante, equipo del catálogo y modelo—, y el alta de un
  equipo del cliente eligiendo tipo, marca y modelo, con el modelo creable desde el propio formulario.
- Cómo se verifica cada tipo: una o varias magnitudes, con su unidad, sus puntos, cuántas lecturas por
  punto y su tolerancia.
- Los protocolos de mantenimiento preventivo, asignados a cada tipo con su prioridad.
- El catálogo de equipos patrón, con su certificado y su vencimiento.
- **La hoja de vida de cada equipo**: identificación, ficha técnica, protocolo e historial de
  intervenciones, que sale de sus reportes cerrados: corregir uno con un reporte sustituto corrige la
  hoja. Numerada por cliente, y **en PDF generado
  por el servidor**, como el informe, con el número de página en cada hoja.

### Órdenes de trabajo

- Una orden directa por cliente, sede y tipo de servicio, con sus equipos elegidos por área —cada uno
  con su tipo y su marca, no solo la serie— y su ingeniero responsable.
- Se inicia y se ejecuta; **no se ejecuta con reportes abiertos**, y si falta el ingeniero o los equipos
  para iniciarla, la pantalla dice cuál de los dos.
- La agenda del inicio, por semana o por mes, con «solo mis órdenes».
- El listado de órdenes se ordena por cualquier columna, pulsando su cabecera, en los dos sentidos, y
  filtra las que están **por aprobar**.
- **Una orden ejecutada se congela**: cliente, sede, ingeniero y equipos quedan como eran ese día.
- **La aprobación del supervisor del cliente**: con la orden ejecutada, el supervisor la aprueba desde
  su propia cuenta con su firma guardada, o en sitio, dibujando su firma en la sesión del ingeniero o
  del administrador —y con la misma firma puede recibir los equipos—. Aprobada, la orden queda lista
  para facturar, y quien aprobó sale con su cargo, el día y su firma en el informe, el resumen y el
  cronograma. Sustituir después un reporte retira la aprobación.
- **Los documentos de la orden en un ZIP**: los informes de todos sus equipos, el resumen de la orden y,
  si es periódica, el cronograma de los próximos mantenimientos, en PDF.

### Reportes de servicio

- Un reporte por equipo de la orden, que lee de la orden lo que no le corresponde escribir.
- **Menos pasos**: la recepción del equipo, la verificación y a quién se entrega se guardan solas al
  cambiarlas, sin botón —firmar sigue siendo un botón—; con
  patrón constante, la casilla del patrón ya trae el valor del punto; y un correctivo se cierra sin
  exigir la falla ni el diagnóstico.
- El ingreso del equipo, sus accesorios, los pasos del protocolo de un preventivo, la falla, el
  diagnóstico, la causa y los repuestos de un correctivo, y la verificación metrológica contra los
  patrones, con el error y la conformidad de cada lectura.
- Cerrar exige lo que el informe necesita —el ingreso, el resultado, la verificación completa y patrones
  vigentes— y **congela el informe**: corregir después el equipo, el cliente o la empresa no cambia un
  informe ya entregado.

### Firma digital y PDF

- **La firma del ingeniero vive en su cuenta**: la dibuja una vez en la ventana que abre su nombre, al
  pie de la barra lateral, y cada informe que cierra la copia. Esa ventana reúne también sus datos y
  cerrar sesión.
- **Quien recibe el servicio firma en la pantalla**, antes o después de cerrar el reporte, y la misma
  firma puede valer para ese reporte, para los de su área o para toda la orden. Quien recibe puede ser
  alguien de turno, con su nombre escrito, o uno de los supervisores del cliente, elegido de la lista.
- **El informe en PDF lo genera el servidor**, con el formato de la empresa, las dos firmas, la gráfica
  de error y un QR a su versión digital. La pantalla lo muestra y lo exporta.

### La empresa y las personas

- Los datos de la empresa que presta el servicio —membrete, logo, garantía, códigos de formato— se editan
  desde la aplicación; MalphasOS no lleva los de ninguna en su código. **Su ciudad se elige del catálogo**,
  con el mismo buscador que las sedes.
- Ingenieros, administradores y supervisores del cliente, **todos con cuenta**, que reciben en Keycloak
  en el mismo paso del alta. A una ficha que no la tenga se le da desde su ficha, con «Dar acceso».
- **Cada quien ve lo suyo**: un supervisor solo ve los datos de los clientes que supervisa, y la escalera
  de permisos separa al SuperUsuario del administrador. No ve el catálogo de la empresa ni «Nuevo
  cliente», y si todavía no supervisa a ninguno, la pantalla le dice que espere a que se lo asignen.
  Las fichas de su cliente y de sus sedes son de solo lectura, y las de sus órdenes y reportes nombran
  a quienes trabajan en ellos sin darle acceso a las demás personas del sistema.
- **El supervisor se nombra desde la ficha del cliente**, y es lo que decide qué clientes ve; el alta
  entrada desde la ficha lo nombra al terminar, y desde «Personas» el alta también deja elegir su
  cliente. Solo una persona de tipo supervisor se nombra: un ingeniero aprobaría su propio trabajo.
- **El correo de la cuenta**: uno de los correos de la ficha es el de Keycloak —con él también se
  entra—. Cambiarlo o corregirlo en la aplicación cambia la cuenta, y no se retira sin elegir otro. La
  consola de cuenta de Keycloak ya no deja a nadie cambiarse el correo ni el nombre por fuera.
- **Contraseñas**: la del alta es temporal y se cambia al entrar por primera vez; cada quien cambia la
  suya desde la ventana de su nombre. Si alguien la olvida, quien
  puede editarlo le pone una temporal desde «Personas», y al entrar con ella tendrá que cambiarla. La
  recuperación por correo («¿Olvidó su contraseña?») queda para cuando haya servidor de correo.

### Despliegue

- `docker-compose.prod.yaml`: un solo dominio con HTTPS automático detrás de Caddy, que es el único
  servicio que publica puertos; Keycloak en modo de producción, con un realm sin usuarios ni secretos
  escritos; y un respaldo diario de las dos bases con su restauración probada. Cómo instalarlo, en
  `DESPLIEGUE.md`.
- El frontend se configura al arrancar: la misma imagen sirve en cualquier dominio.

### Lo que no trae

- **Inventario, alertas de calibración y el módulo comercial** (RF-36, RF-37, RF-40, RF-41, RF-45 y
  RF-47): siguen previstos.
- **RF-04**, elegir las áreas antes de ver sus equipos: la pantalla muestra todas las de la sede a la vez.
  Es una desviación consciente.
- La exportación a Excel que menciona la ERS en su apartado 2.2, que no tiene requisito propio.
- El uso sin conexión, la foto en la hoja de vida y las alertas de vencimiento de los patrones.
