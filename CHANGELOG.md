# Cambios de MalphasOS

Lo que trae cada versión, para quien usa y despliega MalphasOS. El detalle de cada decisión y de cómo se
construyó está en `SecondBrain/`; los requisitos, en la ERS de `Documentation/`.

## [1.0.0] — 2026-10-17

La primera versión para producción: la gestión de clientes, equipos y mantenimientos de una empresa de
servicio biomédico, desde la orden de trabajo hasta el informe firmado en PDF. Cubre **24 de los 31
requisitos funcionales** de la ERS; lo que queda fuera está al final.

### Clientes y ubicaciones

- Clientes con su NIT, su sigla y sus contactos; sedes con su ciudad; áreas de servicio y sus encargados.
  **Un encargado puede estar a cargo de varias sedes y áreas** de un mismo cliente, y se le releva de
  cada una por separado. La migración `V41` lleva los encargados que ya existían a la forma nueva, y la
  ficha de la sede pone al frente a alguien ya registrado, sumándolo a lo que tiene.
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
  intervenciones, que se escribe solo al cerrar cada reporte. Numerada por cliente, y **en PDF generado
  por el servidor**, como el informe, con el número de página en cada hoja.

### Órdenes de trabajo

- Una orden directa por cliente, sede y tipo de servicio, con sus equipos elegidos por área —cada uno
  con su tipo y su marca, no solo la serie— y su ingeniero responsable.
- Se inicia y se ejecuta; **no se ejecuta con reportes abiertos**, y si falta el ingeniero o los equipos
  para iniciarla, la pantalla dice cuál de los dos.
- La agenda del inicio, por semana o por mes, con «solo mis órdenes».

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
- **Quien recibe el servicio firma en la pantalla**, antes o después de cerrar el reporte, y con una
  casilla la misma firma vale para los demás reportes de la orden que aún no la tengan.
- **El informe en PDF lo genera el servidor**, con el formato de la empresa, las dos firmas, la gráfica
  de error y un QR a su versión digital. La pantalla lo muestra y lo exporta.

### La empresa y las personas

- Los datos de la empresa que presta el servicio —membrete, logo, garantía, códigos de formato— se editan
  desde la aplicación; MalphasOS no lleva los de ninguna en su código.
- Ingenieros, administradores, representantes de cliente y encargados. Los que tienen cuenta la reciben
  en Keycloak en el mismo paso.
- **Cada quien ve lo suyo**: un representante de cliente solo ve los datos de su cliente, y la escalera de
  permisos separa al SuperUsuario del administrador. No ve el catálogo de la empresa ni «Nuevo
  cliente», y si todavía no representa a ninguno, la pantalla le dice que espere a que se lo asignen.
  Las fichas de su cliente y de sus sedes son de solo lectura: sin editar, retirar ni cerrar, sin abrir
  sedes ni áreas y sin tocar el contacto.
- **El representante se nombra desde la ficha del cliente**, y es lo que decide qué clientes ve; el
  alta entrada desde la ficha lo nombra al terminar. **Desde «Personas» el alta también deja elegir**,
  sin obligar —el cliente se busca escribiendo, y solo se admite uno que exista—: el cliente del
  representante, y el cliente, la sede y el área del encargado. Un
  representante puede quedar además **encargado** de una sede de su cliente o de un área suya,
  eligiéndola en el mismo formulario.
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
