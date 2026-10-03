/**
 * Traduce los codigos de error del backend a frases que una persona lea, un catalogo por modulo.
 *
 * <p>Se llamaba {@code catalogo-client.ts} y dejo de ser cierto el 2026-09-26, al entrar los de
 * ubicaciones y los diez de equipos: un nombre que miente sobre lo que contiene es peor que uno
 * largo.
 *
 * <p>Lo primero son los del modulo de clientes.
 *
 * <p>Es RNF-17, y el backend ya hizo su mitad: emite codigos estables y <b>nunca comparte uno de
 * "no existe" con uno de "datos invalidos"</b>. Fundirlos aqui en un "ha ocurrido un error" tiraria
 * a la basura esa decision.
 *
 * <p>El tono lo fija el manual de marca: <b>directo e instructivo</b>, sin disculpas ni
 * exclamaciones. Dice que paso y que hacer.
 */
export const CATALOGO_CLIENT: Readonly<Record<string, string>> = {
  ERR_CLIENT_001: 'Ese cliente no existe o fue retirado.',
  ERR_CLIENT_002: 'Esa sede no existe o fue cerrada.',
  ERR_CLIENT_003: 'Esa área de servicio no existe o fue cerrada.',
  ERR_CLIENT_004: 'Ese encargado no existe o fue retirado.',
  ERR_CLIENT_005: 'Revise los datos del formulario.',
  ERR_CLIENT_006: 'Esa ciudad no existe.',
  ERR_CLIENT_007: 'Esa persona no existe o fue retirada.',
};

/**
 * Y los del modulo de ubicaciones, que el frontend consume para los selectores.
 *
 * <p>Va en el mismo mapa y no en otro archivo porque {@code traducirError} recibe un codigo, no un
 * modulo: los prefijos ya los separan. Fundir los dos catalogos en un mensaje unico seria tirar la
 * decision del backend de no compartir nunca un codigo de «no existe» con uno de «datos invalidos».
 */
export const CATALOGO_LOCATION: Readonly<Record<string, string>> = {
  ERR_LOCATION_001: 'Ese país no existe.',
  ERR_LOCATION_002: 'Esa ciudad no existe.',
  ERR_LOCATION_003: 'Revise los datos de la ubicación.',
};

/**
 * Y los del modulo de equipos, que son diez.
 *
 * <p><b>Seis de ellos dicen «no existe» de seis cosas distintas</b>, y por eso no se funden: al
 * registrar un equipo se manejan cinco referencias a la vez —marca, tipo, equipo, fabricante,
 * modelo—, y «no existe» sin decir cual obligaria a probar una por una.
 *
 * <p>El ultimo, {@code ERR_EQUIPMENT_010}, no es un «no existe» ni un «dato invalido»: es una regla
 * de negocio. Un equipo no se traslada al area de otro cliente.
 */
export const CATALOGO_EQUIPMENT: Readonly<Record<string, string>> = {
  ERR_EQUIPMENT_001: 'Ese fabricante no existe o fue retirado.',
  ERR_EQUIPMENT_002: 'Esa marca no existe o fue retirada.',
  ERR_EQUIPMENT_003: 'Ese tipo de equipo no existe o fue retirado.',
  ERR_EQUIPMENT_004: 'Esa combinación de tipo y marca no existe en el catálogo.',
  ERR_EQUIPMENT_005: 'Ese modelo no existe o fue retirado.',
  ERR_EQUIPMENT_006: 'Ese equipo no existe o fue dado de baja.',
  ERR_EQUIPMENT_007: 'Revise los datos del equipo.',
  ERR_EQUIPMENT_008: 'Ese país no existe.',
  ERR_EQUIPMENT_009: 'Esa área de servicio no existe o fue cerrada.',
  ERR_EQUIPMENT_010: 'Un equipo no se puede trasladar al área de otro cliente.',
};

/**
 * Y los del modulo de ordenes de trabajo.
 *
 * <p>El tercero es el que mas se va a ver y el que mas cuesta redactar: «el estado no lo permite» no
 * dice nada por si solo, asi que el mensaje nombra las transiciones que existen. Una orden ya ejecutada
 * no se puede volver a iniciar, y eso es una regla del dominio, no un fallo del usuario.
 */
export const CATALOGO_WORK_ORDER: Readonly<Record<string, string>> = {
  ERR_WORK_ORDER_001: 'Esa orden de trabajo no existe o fue anulada.',
  ERR_WORK_ORDER_002: 'Revise los datos de la orden.',
  ERR_WORK_ORDER_003:
    'El estado de la orden no permite esa acción. Una orden se inicia estando creada y se ejecuta estando en ejecución.',
  ERR_WORK_ORDER_004: 'Ese cliente no existe o fue retirado.',
  ERR_WORK_ORDER_005: 'Esa sede no existe o fue cerrada.',
  ERR_WORK_ORDER_006: 'Esa área de servicio no existe o fue cerrada.',
  ERR_WORK_ORDER_007: 'Ese equipo no existe o fue dado de baja.',
  ERR_WORK_ORDER_008: 'Esa persona no existe o fue retirada.',
};

/**
 * Y los del modulo de reportes de servicio.
 *
 * <p>El tercero cubre tres situaciones distintas que tienen en comun no ser culpa de lo escrito: la orden
 * no ha empezado, el reporte ya esta cerrado, o la verificacion esta a medias. El mensaje las nombra las
 * tres porque el codigo no las distingue, y «el estado no lo permite» a secas no dice nada.
 *
 * <p>Los tres ultimos hablan de eslabones del catalogo que <b>el usuario nunca nombra</b>: el servidor los
 * recorre para averiguar como se verifica el equipo. Si aparecen, no es que se haya elegido mal algo, es
 * que la cadena del catalogo esta rota — y el mensaje tiene que decir eso y no «revise los datos».
 */
export const CATALOGO_SERVICE_REPORT: Readonly<Record<string, string>> = {
  ERR_SERVICE_REPORT_001: 'Ese reporte no existe o fue retirado.',
  ERR_SERVICE_REPORT_002: 'Revise los datos del reporte.',
  ERR_SERVICE_REPORT_003:
    'Ahora no se puede: la orden tiene que haber empezado, un reporte cerrado ya no se toca, y para cerrarlo hace falta la verificación completa.',
  ERR_SERVICE_REPORT_004: 'Esa orden de trabajo no existe o fue anulada.',
  ERR_SERVICE_REPORT_005: 'Ese equipo no existe o fue dado de baja.',
  ERR_SERVICE_REPORT_006: 'El modelo de ese equipo no existe o fue retirado.',
  ERR_SERVICE_REPORT_007: 'El equipo del catálogo al que pertenece no existe o fue retirado.',
  ERR_SERVICE_REPORT_008: 'El tipo de equipo no existe o fue retirado: sin él no se sabe cómo verificarlo.',
};

/**
 * Y los del modulo de personas, que son los unicos que hablan de OTRO sistema.
 *
 * <p>Cuatro de los seis son de Keycloak, y eso es lo que los hace distintos de todos los demas
 * catalogos: no dicen que el dato este mal, dicen que <b>el proveedor de identidad</b> no pudo hacer su
 * parte. Un alta de ingeniero toca dos sistemas sin transaccion que los envuelva, asi que el mensaje
 * tiene que distinguir «ese nombre de usuario ya existe» —que se arregla cambiandolo— de «no se pudo
 * hablar con Keycloak» —que no se arregla tocando el formulario—.
 */
export const CATALOGO_PERSON: Readonly<Record<string, string>> = {
  ERR_PERSON_001: 'Esa persona no existe o fue retirada.',
  ERR_PERSON_002: 'Revise los datos de la persona.',
  ERR_KEYCLOAK_001: 'Ese nombre de usuario ya existe en el sistema de acceso. Elija otro.',
  ERR_KEYCLOAK_002:
    'El sistema de acceso rechazó los datos de la cuenta. Revise el nombre de usuario, el correo y la contraseña.',
  ERR_KEYCLOAK_003:
    'El servidor no tiene permiso para crear cuentas en el sistema de acceso. Es un problema de configuración, no de los datos.',
  ERR_KEYCLOAK_004:
    'No se pudo contactar con el sistema de acceso. La persona no se dio de alta; vuelva a intentarlo.',
};

/** Lo que se dice cuando no hay nada mejor que decir. */
export const MENSAJE_DE_RESPALDO = 'No se pudo completar la operación. Intente de nuevo.';
