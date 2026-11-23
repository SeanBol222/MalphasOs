# Cambios de MalphasOS

Lo que trae cada versión, para quien usa y despliega MalphasOS. El detalle de cada decisión y de cómo se
construyó está en `SecondBrain/`; los requisitos, en la ERS de `Documentation/`.

## [1.2.0] — 2026-11-22

Los patrones que lleva cada visita, un catálogo pensado para cientos de filas después de la carga de
datos, una campanita con lo que pide atención, y la aplicación usable en una ventana estrecha.

### Patrones de medición

- **Cada verificación dice con qué patrones se hace**, en Catálogo › Tipos › «Patrones», y el reporte solo
  ofrece esos. Los que ya se habían usado en reportes quedan admitidos al actualizar, y una carga de datos
  hace lo mismo con los suyos.
- **La orden elige los patrones que lleva a la visita**, por tipo de equipo —las pesas de 5 kg para unos
  equipos y las de 20 kg para otros—, y cada reporte de ese tipo los recibe. Si el catálogo admite un solo
  patrón vigente para un tipo, la orden ya lo trae puesto. Se cambian hasta ejecutarla, y lo que el
  ingeniero ponga a mano en un reporte no se toca. **Los tipos sin verificación metrológica no aparecen**:
  no tienen contra qué medirse.

### Catálogo

- **Un buscador en cada lista** —equipos del catálogo, tipos, modelos, marcas, fabricantes, protocolos y
  patrones—, sin tildes ni mayúsculas, y tablas que se ordenan desde la cabecera.
- **Un modelo se crea eligiendo tipo y después marca**, escribiendo o desplegando la lista, y la
  combinación se crea si falta. **El alta de un tipo trae sus marcas y modelos**, en dos columnas.
- Los formularios de alta se abren con un botón; retirar algo pregunta antes; un fabricante se renombra
  desde su fila, como una marca.
- **En un teléfono**, pestañas que se deslizan y tablas como tarjetas.

### Avisos

- **Una campanita** al pie de la barra lateral —arriba en un teléfono— con los **patrones vencidos o que
  vencen en 30 días** y las **órdenes ejecutadas por aprobar**, según lo que cada quien puede hacer, y el
  número de los que no ha leído. Un aviso desaparece solo cuando se resuelve lo que lo causa.

### Personas

- **Una persona retirada se puede reactivar** desde su ficha: vuelve, y si tenía cuenta, entra con la
  contraseña de antes.

### Pantallas

- **En una ventana estrecha**, con la barra lateral abierta, nada se sale de la pantalla: el reporte pasa
  a tarjetas, y los listados —órdenes, equipos, clientes, personas y el catálogo— se desplazan de lado
  dentro de su recuadro, con sus filtros en una sola fila.
- En el reporte, **recuadros que se ponen oscuros** en vez de circulitos para el estado al recibir, el
  resultado y el alcance de la firma.
- Las áreas de una sede se pliegan, y los equipos de una orden van en orden alfabético.

### Documentos

- **Las firmas del informe al pie de su última página**, y en la hoja de vida, líneas de historial en
  blanco hasta las firmas para anotar a mano. El logo, más pequeño.

### Actualizar desde la 1.1.0

- **Respaldar la base antes.** El backend aplica dos migraciones al arrancar: `V59` guarda lo que dice
  cada reporte en un solo documento y **conserva solo lo vigente** —lo que se había retirado dentro de un
  reporte, como una lectura corregida o un accesorio quitado, no se migra; ningún informe lo imprimía—, y `V60` añade los patrones de cada verificación y de cada orden.
- **Una variable nueva, `APP_APPROVAL_SINCE`**: desde qué día una orden ejecutada espera la aprobación del
  supervisor. Lo ejecutado antes —el historial que trajo una carga de datos— no la espera. Se pone en
  `.env.prod` con una fecha posterior a la última orden cargada; vacía, todo el historial sale «por
  aprobar». Ver `DESPLIEGUE.md`.

## [1.1.0] — 2026-11-08

Traer los datos de la aplicación que la empresa usaba antes, con su historial, sin pasarlos a mano; la
foto de cada equipo en su hoja de vida; y desplegar en la red local de una oficina, sin dominio, que es
como se instaló la 1.0.0 por primera vez.

### Importar datos de otro sistema

- **Un formato publicado**, `malphasos-carga/1`, en `contracts/carga/`: el esquema, un ejemplo inventado
  de cada archivo y la guía. MalphasOS no sabe nada del sistema de origen; el conversor vive a su lado.
  Son el catálogo de la empresa con sus ingenieros —y la firma de cada uno—, cada cliente con sus
  equipos, y el historial de cada cliente, **todos en una sola carga**: sueltos o en **un ZIP**, que es
  lo que conviene que entregue el conversor.
- **Administración › Importar datos**, solo para el SuperUsuario: se suben los archivos —se van
  añadiendo, aunque estén en carpetas distintas, y se quita el que se eligió mal—, se **validan
  sin escribir nada** y el informe agrupa los errores y avisos por archivo, con su ruta, y dice cuántos
  registros entran y cuántos ya estaban. Se aplica tras una validación limpia, **todo o nada**.
- **Lo que ya existe se reconoce y no se toca**, por su llave natural: un cliente que se carga después
  se sube con el catálogo otra vez. **Un historial no se carga dos veces.** Y la validación rechaza lo
  que la base rechazaría al aplicar: dos equipos con el mismo modelo y la misma serie, uno que ya es de
  otro cliente, y dos reportes del mismo equipo en una orden.
- **El historial entra ejecutado y cerrado**: cada orden y cada informe con su documento, la firma
  del ingeniero y **el certificado del patrón que estaba vigente el día del servicio**, no el de hoy. La
  firma puede estar guardada en su cuenta o venir en la carga; la guardada manda. La carga real de
  dieciséis clientes —3827 informes— se aplica en menos de un minuto.

### Equipos

- **Cada equipo tiene su foto en la hoja de vida**: se pone desde la propia hoja de vida —se elige, en el
  teléfono con la cámara; se arrastra; o se pega con Ctrl+V—, se encuadra moviéndola y acercándola en un
  recuadro cuadrado, y se guarda en PNG de 400 px, de modo que una foto ocupa unos 60 a 180 KB. La ponen
  quienes editan equipos: los ingenieros y los administradores. El PDF de la hoja de vida la imprime.
- **Un equipo se registra aunque no traiga serie**: recibe una provisional, `P:` y el comienzo de su
  identificador, que se reemplaza una vez por la de la placa desde la ficha del área.
- **El voltaje del modelo admite decimales**: un equipo a pila es de 1,5 V.

### Permisos

- **Un administrador ya no ve las pantallas del SuperUsuario**, como el alta de ingenieros y
  administradores, que le respondían «sin permiso». La pantalla y el servidor aplican ahora la misma
  regla.

### Despliegue

- **En la red local, sin dominio**: un `Caddyfile` para entrar por la IP del servidor, con el certificado
  de la autoridad propia de Caddy, y los puertos configurables para un servidor que ya tiene otro proxy en
  el 80 y el 443. Se elige en `.env.prod`; cómo, y cómo confiar en el certificado desde cada equipo, en
  `DESPLIEGUE.md`.
- **El guion del primer SuperUsuario** pasa el nombre a minúsculas: con mayúsculas, dejaba el usuario
  creado sin contraseña y sin rol.

### Actualizar desde la 1.0.0

- El backend aplica dos migraciones al arrancar: el voltaje decimal y la tabla de las fotos. No hay que
  hacer nada con los datos: ningún equipo tiene foto hasta que se le pone.
- **Si en el servidor se editaron a mano `docker-compose.prod.yaml` o `docker/caddy/Caddyfile`** —para
  entrar por IP o cambiar los puertos—, esos cambios pasan ahora a `.env.prod`. Antes del `git pull` se
  devuelven los dos archivos a como estaban (`git checkout -- docker-compose.prod.yaml
  docker/caddy/Caddyfile`) y después se ponen en `.env.prod` las variables de la red local. La dirección
  no cambia, así que el realm ya importado sigue valiendo.

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
