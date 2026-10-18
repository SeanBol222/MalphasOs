# Cambios de MalphasOS

Lo que trae cada versión, para quien usa y despliega MalphasOS. El detalle de cada decisión y de cómo se
construyó está en `SecondBrain/`; los requisitos, en la ERS de `Documentation/`.

## [1.0.0] — 2026-10-17

La primera versión para producción: la gestión de clientes, equipos y mantenimientos de una empresa de
servicio biomédico, desde la orden de trabajo hasta el informe firmado en PDF. Cubre **24 de los 31
requisitos funcionales** de la ERS; lo que queda fuera está al final.

### Clientes y ubicaciones

- Clientes con su NIT, su sigla y sus contactos; sedes con su ciudad; áreas de servicio y sus encargados.
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
  intervenciones, que se escribe solo al cerrar cada reporte. Numerada por cliente e imprimible.

### Órdenes de trabajo

- Una orden directa por cliente, sede y tipo de servicio, con sus equipos elegidos por área y su
  ingeniero responsable.
- Se inicia y se ejecuta; **no se ejecuta con reportes abiertos**.
- La agenda del inicio, por semana o por mes, con «solo mis órdenes».

### Reportes de servicio

- Un reporte por equipo de la orden, que lee de la orden lo que no le corresponde escribir.
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
  permisos separa al SuperUsuario del administrador.

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
- La hoja de vida se imprime desde el navegador; el PDF del servidor es el del informe del reporte.
