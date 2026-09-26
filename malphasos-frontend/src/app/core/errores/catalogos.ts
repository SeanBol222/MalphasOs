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

/** Lo que se dice cuando no hay nada mejor que decir. */
export const MENSAJE_DE_RESPALDO = 'No se pudo completar la operación. Intente de nuevo.';
