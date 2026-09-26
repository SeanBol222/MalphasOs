/**
 * Traduce los codigos de error del modulo de clientes a frases que una persona lea.
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

/** Lo que se dice cuando no hay nada mejor que decir. */
export const MENSAJE_DE_RESPALDO = 'No se pudo completar la operación. Intente de nuevo.';
