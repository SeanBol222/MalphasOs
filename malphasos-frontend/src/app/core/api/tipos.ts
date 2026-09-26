import { components } from './client.contrato';
import { components as ubicacion } from './location.contrato';
import { components as personas } from './person.contrato';

/**
 * Nombres legibles para lo que el contrato genera.
 *
 * <p>El archivo generado no se edita nunca: se regenera con {@code npm run contrato} desde
 * {@code contracts/openapi/}, que el backend publica. Este modulo es la unica capa que lo toca, de
 * modo que el resto del codigo no arrastra {@code components["schemas"][...]} por todas partes.
 *
 * <p><b>Los campos de respuesta llegan opcionales</b> porque asi los declara el contrato: springdoc
 * no marca nada como requerido salvo que se le diga. No se "arreglan" aqui a proposito — afirmar que
 * un campo siempre viene cuando el contrato no lo garantiza es exactamente el desfase silencioso que
 * generar los tipos existe para evitar.
 */
export type Cliente = components['schemas']['ClientResponse'];
export type NuevoCliente = components['schemas']['ClientCreateRequest'];
export type CambioDeCliente = components['schemas']['ClientUpdateRequest'];
export type TipoIdentificacion = NuevoCliente['tipoIdentificacion'];

export type Contacto = components['schemas']['ContactResponse'];
export type NuevoContacto = components['schemas']['ContactRequest'];

export type Sede = components['schemas']['HeadquarterResponse'];
export type NuevaSede = components['schemas']['HeadquarterCreateRequest'];
export type CambioDeSede = components['schemas']['HeadquarterUpdateRequest'];

export type AreaDeServicio = components['schemas']['ServiceAreaResponse'];
export type NuevaArea = components['schemas']['ServiceAreaCreateRequest'];
/** Renombrar un area reutiliza el cuerpo del alta: el backend no declara un tipo aparte. */
export type CambioDeArea = NuevaArea;

export type Encargado = components['schemas']['ManagerResponse'];
export type NuevoEncargado = components['schemas']['ManagerRegisterRequest'];
/**
 * Sale del tipo de la PETICION y no del de la respuesta.
 *
 * <p>En la respuesta el campo es opcional —springdoc no marca nada como requerido— y ese
 * {@code undefined} se cuela hasta el cuerpo que se manda. Del lado de la peticion el contrato si lo
 * exige, que es lo que este tipo tiene que decir.
 */
export type TipoDeAsignacion = NonNullable<NuevoEncargado['tipo']>;

/**
 * Los dos modulos que el frontend consume hoy publican su contrato por separado, y por eso hay dos
 * archivos generados. Se importan con nombres distintos a proposito: fundirlos en uno solo haria
 * que un cambio del esquema de un modulo pudiera romper el otro sin que se viera de donde viene.
 */
export type Pais = ubicacion['schemas']['CountryResponse'];
export type Ciudad = ubicacion['schemas']['CityResponse'];

/**
 * Del modulo de personas solo se lee, y solo para poner nombre a un identificador.
 *
 * <p>Un encargado que el API devuelve es {@code idPersona} y nada mas: sin esto, la lista de
 * encargados de una sede seria una columna de UUID. La seccion de personas no existe todavia.
 */
export type Persona = personas['schemas']['PersonResponse'];

/** Los tipos de documento que el contrato admite, en el orden en que se ofrecen. */
export const TIPOS_DE_IDENTIFICACION: readonly TipoIdentificacion[] = [
  'NIT_JURIDICO',
  'NIT_NATURAL',
  'CC',
  'CE',
] as const;

/** Como se nombra cada tipo en pantalla. El contrato da el codigo, no la etiqueta. */
export const ETIQUETA_DE_IDENTIFICACION: Readonly<Record<TipoIdentificacion, string>> = {
  NIT_JURIDICO: 'NIT jurídico',
  NIT_NATURAL: 'NIT natural',
  CC: 'Cédula de ciudadanía',
  CE: 'Cédula de extranjería',
};
