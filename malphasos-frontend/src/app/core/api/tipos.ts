import { components } from './client.contrato';
import { components as ubicacion } from './location.contrato';
import { components as personas } from './person.contrato';
import { components as equipos } from './equipment.contrato';
import { components as ordenes } from './work-order.contrato';

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

/**
 * El catalogo de equipos, que son cinco piezas y no una.
 *
 * <p>Registrar un equipo de un cliente exige una cadena entera: una <b>marca</b> y un <b>tipo</b> se
 * combinan en un <b>equipo</b> del catalogo; ese equipo y un <b>fabricante</b> se combinan en un
 * <b>modelo</b>; y es el modelo lo que se instala en un area como <b>equipo del cliente</b>. Ninguna
 * de esas respuestas trae nombres, solo identificadores: las pantallas los resuelven cruzando listas.
 */
export type Marca = equipos['schemas']['BrandResponse'];
export type NuevoNombre = equipos['schemas']['NamedRequest'];

export type TipoDeEquipo = equipos['schemas']['EquipmentTypeResponse'];
export type NuevoTipoDeEquipo = equipos['schemas']['EquipmentTypeCreateRequest'];
export type CambioDeTipoDeEquipo = equipos['schemas']['EquipmentTypeUpdateRequest'];
export type ModalidadDeVerificacion = NonNullable<NuevoTipoDeEquipo['modalidadVerificacion']>;

/**
 * Los puntos en los que se verifica un tipo de equipo, y cuantas lecturas se toman en cada uno.
 *
 * <p>Las dos cosas solo existen con una modalidad <b>constante</b>: con patron y equipo variables, cuantas
 * lecturas tomar lo decide el ingeniero en campo y no hay nada constante que declarar. El backend lo
 * impone en el esquema y en el dominio; aqui el formulario lo refleja para no ofrecer lo que sera
 * rechazado.
 */
export type PuntoDeVerificacion = equipos['schemas']['VerificationPointResponse'];
export type NuevoPuntoDeVerificacion = equipos['schemas']['VerificationPointRequest'];
export type CambioDeModalidad = equipos['schemas']['VerificationModeRequest'];

/** Las dos modalidades que mantienen algo constante, que son las que piden cantidad y puntos. */
export const MODALIDADES_CONSTANTES: readonly ModalidadDeVerificacion[] = [
  'PATRON_CONSTANTE',
  'EQUIPO_CONSTANTE',
] as const;

/** Si una modalidad exige cantidad de lecturas y al menos un punto. */
export function mantieneAlgoConstante(
  modalidad: ModalidadDeVerificacion | '' | undefined,
): boolean {
  return !!modalidad && MODALIDADES_CONSTANTES.includes(modalidad);
}

export type Fabricante = equipos['schemas']['ManufacturerResponse'];
export type NuevoFabricante = equipos['schemas']['ManufacturerRequest'];

export type EquipoDeCatalogo = equipos['schemas']['EquipmentResponse'];
export type NuevoEquipoDeCatalogo = equipos['schemas']['EquipmentCreateRequest'];

export type Modelo = equipos['schemas']['ModelResponse'];
export type NuevoModelo = equipos['schemas']['ModelCreateRequest'];

export type EquipoDeCliente = equipos['schemas']['ClientEquipmentResponse'];
export type NuevoEquipoDeCliente = equipos['schemas']['ClientEquipmentRegisterRequest'];
export type CambioDeEquipoDeCliente = equipos['schemas']['ClientEquipmentUpdateRequest'];

/** Las tres modalidades de verificacion, en el orden en que el esquema las declara. */
export const MODALIDADES_DE_VERIFICACION: readonly ModalidadDeVerificacion[] = [
  'PATRON_CONSTANTE',
  'EQUIPO_CONSTANTE',
  'PATRON_EQUIPO_VARIABLE',
] as const;

/**
 * Como se nombra cada modalidad en pantalla.
 *
 * <p>Los codigos describen <b>que se mantiene constante durante la verificacion</b>, y eso no se
 * deduce del nombre: se explica aqui una vez en lugar de esperar que cada pantalla lo adivine.
 */
export const ETIQUETA_DE_MODALIDAD: Readonly<Record<ModalidadDeVerificacion, string>> = {
  PATRON_CONSTANTE: 'Patrón constante',
  EQUIPO_CONSTANTE: 'Equipo constante',
  PATRON_EQUIPO_VARIABLE: 'Patrón y equipo variables',
};

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

/**
 * Ordenes de trabajo: lo que se programa, con que equipos y en que estado va.
 *
 * <p>Una orden nace <b>CREADA</b>, pasa a <b>EN_EJECUCION</b> cuando el ingeniero empieza y a
 * <b>EJECUTADA</b> cuando termina. Las tres transiciones son operaciones distintas en el API, no un
 * campo que se edita: el backend comprueba en cada una que el estado la permita.
 */
export type OrdenDeTrabajo = ordenes['schemas']['WorkOrderResponse'];
export type NuevaOrdenDeTrabajo = ordenes['schemas']['WorkOrderScheduleRequest'];
export type EquipoDeOrden = ordenes['schemas']['WorkOrderEquipmentResponse'];

export type EstadoDeEjecucion = NonNullable<OrdenDeTrabajo['estadoEjecucion']>;
export type Periodicidad = NonNullable<NuevaOrdenDeTrabajo['periodicidad']>;
export type TipoDeServicio = NonNullable<NuevaOrdenDeTrabajo['tipoServicio']>;

/** Los estados, en el orden en que ocurren. */
export const ESTADOS_DE_EJECUCION: readonly EstadoDeEjecucion[] = [
  'CREADA',
  'EN_EJECUCION',
  'EJECUTADA',
] as const;

export const ETIQUETA_DE_ESTADO: Readonly<Record<EstadoDeEjecucion, string>> = {
  CREADA: 'Creada',
  EN_EJECUCION: 'En ejecución',
  EJECUTADA: 'Ejecutada',
};

export const PERIODICIDADES: readonly Periodicidad[] = [
  'MENSUAL',
  'TRIMESTRAL',
  'SEMESTRAL',
  'ANUAL',
] as const;

export const ETIQUETA_DE_PERIODICIDAD: Readonly<Record<Periodicidad, string>> = {
  MENSUAL: 'Mensual',
  TRIMESTRAL: 'Trimestral',
  SEMESTRAL: 'Semestral',
  ANUAL: 'Anual',
};

export const TIPOS_DE_SERVICIO: readonly TipoDeServicio[] = [
  'PREVENTIVO',
  'CORRECTIVO',
  'CALIBRACION',
] as const;

export const ETIQUETA_DE_TIPO_DE_SERVICIO: Readonly<Record<TipoDeServicio, string>> = {
  PREVENTIVO: 'Preventivo',
  CORRECTIVO: 'Correctivo',
  CALIBRACION: 'Calibración',
};
