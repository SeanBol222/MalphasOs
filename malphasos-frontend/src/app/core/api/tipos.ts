import { components } from './client.contrato';
import { components as ubicacion } from './location.contrato';
import { components as personas } from './person.contrato';
import { components as equipos } from './equipment.contrato';
import { components as ordenes } from './work-order.contrato';
import { components as reportes } from './reports.contrato';

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

/**
 * El catalogo metrologico: que se puede medir y en que unidades.
 *
 * <p>Se consulta y no se administra, igual que los paises y las ciudades: lo siembra la migracion
 * `V10` y no hay pantalla que lo cree. Cada unidad pertenece a una magnitud, y el servidor rechaza una
 * unidad que no sea de la magnitud declarada -- con un codigo de error propio, porque los dos
 * identificadores son correctos y lo que falla es la combinacion.
 */
export type Magnitud = equipos['schemas']['MagnitudeResponse'];
export type UnidadDeMedida = equipos['schemas']['MeasurementUnitResponse'];

/**
 * Una de las cosas que se verifican en un tipo de equipo.
 *
 * <p><b>Un tipo se verifica en varias magnitudes</b>, y eso cambio el 2026-10-03: un termohigrometro
 * mide temperatura Y humedad relativa, en unidades distintas, en puntos distintos y a veces con
 * modalidades distintas. Antes el tipo declaraba una sola modalidad, una sola cantidad de lecturas y
 * unos puntos con su unidad escrita a mano, de modo que un termohigrometro habia que registrarlo como
 * dos tipos de equipo.
 *
 * <p>La respuesta <b>si trae nombres</b> -- el de la magnitud y el simbolo de la unidad --, al
 * contrario que el resto de este modulo. Es deliberado: la cabecera de una tabla de verificacion dice
 * «Temperatura (°C)», y obligar a la pantalla a cruzar dos catalogos para pintar un encabezado es la
 * friccion que el resto del modulo ya padece.
 */
export type VerificacionDeTipo = equipos['schemas']['TypeVerificationResponse'];
export type NuevaVerificacionDeTipo = equipos['schemas']['TypeVerificationRequest'];
export type ModalidadDeVerificacion = NonNullable<NuevaVerificacionDeTipo['modalidad']>;

/**
 * Los puntos en los que se verifica, y cuantas lecturas se toman en cada uno.
 *
 * <p>Las dos cosas solo existen con una modalidad <b>constante</b>: con patron y equipo variables,
 * cuantas lecturas tomar lo decide el ingeniero en campo y no hay nada constante que declarar. El
 * backend lo impone en el esquema y en el dominio; aqui el formulario lo refleja para no ofrecer lo que
 * sera rechazado.
 *
 * <p><b>Un punto ya no lleva unidad</b>: la declara su verificacion una sola vez y el punto la hereda.
 * Antes habia que teclearla en cada punto, y dos puntos hermanos podian contradecirse.
 */
export type PuntoDeVerificacion = equipos['schemas']['VerificationPointResponse'];
export type NuevoPuntoDeVerificacion = equipos['schemas']['VerificationPointRequest'];
export type CambioDeVerificaciones = equipos['schemas']['DeclareVerificationsRequest'];

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

/** Si a un tipo se le verifica algo. Derivado, igual que en el backend: no hay campo que lo diga. */
export function seVerifica(tipo: TipoDeEquipo | undefined): boolean {
  return !!tipo?.verificaciones?.length;
}

/** Cuantas lecturas pide un reporte completo de este tipo: puntos por cantidad, sumado. */
export function lecturasEsperadas(tipo: TipoDeEquipo | undefined): number {
  return (tipo?.verificaciones ?? []).reduce(
    (total, verificacion) =>
      total + (verificacion.puntos?.length ?? 0) * (verificacion.cantidadDatos ?? 0),
    0,
  );
}

export type Fabricante = equipos['schemas']['ManufacturerResponse'];
export type NuevoFabricante = equipos['schemas']['ManufacturerRequest'];

export type EquipoDeCatalogo = equipos['schemas']['EquipmentResponse'];
export type NuevoEquipoDeCatalogo = equipos['schemas']['EquipmentCreateRequest'];

export type Modelo = equipos['schemas']['ModelResponse'];
export type NuevoModelo = equipos['schemas']['ModelCreateRequest'];
/** La ficha tecnica de un modelo, desde V15: riesgo, caracteristicas y datos electricos. */
export type FichaTecnica = equipos['schemas']['TechnicalSheetRequest'];
export type ClaseDeRiesgo = NonNullable<FichaTecnica['riesgo']>;

/** De menor a mayor riesgo, como las numera el Decreto 4725 de 2005. */
export const CLASES_DE_RIESGO: readonly ClaseDeRiesgo[] = ['I', 'IIA', 'IIB', 'III'] as const;

/** Como se escriben: «IIa» y no «IIA», que es solo como las guarda el enum. */
export const ETIQUETA_DE_RIESGO: Readonly<Record<ClaseDeRiesgo, string>> = {
  I: 'Clase I',
  IIA: 'Clase IIa',
  IIB: 'Clase IIb',
  III: 'Clase III',
};

export type EquipoDeCliente = equipos['schemas']['ClientEquipmentResponse'];
export type NuevoEquipoDeCliente = equipos['schemas']['ClientEquipmentRegisterRequest'];
export type CambioDeEquipoDeCliente = equipos['schemas']['ClientEquipmentUpdateRequest'];

/**
 * La hoja de vida de un equipo instalado: las cuatro secciones que enumera RF-22 —identificacion,
 * tecnica, fabricante y servicio tecnico—. Es un documento compilado y de solo lectura: cada dato se
 * corrige donde vive, no aqui.
 */
export type HojaDeVida = equipos['schemas']['LifeSheetResponse'];

/** Una linea del historial: la cuarta seccion de la hoja de vida, que se escribe sola al cerrar un reporte. */
export type Intervencion = equipos['schemas']['InterventionResponse'];

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

/**
 * Personas: la gente de la casa y la del cliente, con o sin acceso al sistema.
 *
 * <p>(Aqui habia un solo alias de lectura, con el comentario «del modulo de personas solo se lee, y
 * solo para poner nombre a un identificador; la seccion de personas no existe todavia». Fue cierto
 * hasta el <b>2026-10-02</b>.)
 *
 * <p><b>«Persona» no es «usuario».</b> Una persona puede existir sin cuenta —un encargado al que solo
 * se le llama por telefono— y por eso el API tiene cuatro altas distintas: una que crea solo la fila y
 * tres que crean ademas el usuario en Keycloak. Las tres ultimas exigen correo, nombre de usuario y
 * contrasena inicial; la primera, no.
 *
 * <p><b>Y quien puede crear a quien depende de QUE es esa persona</b>, que es la «escalera de
 * usuarios» del backend: la gente de la casa -ingenieros y administradores- exige
 * {@code super.person.write}, y la del cliente -representantes y encargados- {@code person.write}. El
 * prefijo marca lo que {@code admin.full} no concede. Ver [[modelo-de-permisos]].
 */
export type Persona = personas['schemas']['PersonResponse'];
export type NuevaPersonaSinAcceso = personas['schemas']['PersonCreateRequest'];
export type NuevoUsuario = personas['schemas']['PersonRegisterRequest'];
export type CambioDePersona = personas['schemas']['PersonUpdateRequest'];
export type CorreoDePersona = personas['schemas']['EmailPersonResponse'];
export type TelefonoDePersona = personas['schemas']['PhonePersonResponse'];

/** Sale del tipo de la PETICION: en la respuesta es opcional y ese undefined se colaria al cuerpo. */
export type TipoDePersona = NonNullable<NuevaPersonaSinAcceso['tipoPersona']>;

/**
 * Los cinco tipos, de la casa hacia el cliente.
 *
 * <p>{@code SUPER_ADMIN} esta en la lista porque el contrato lo admite, pero <b>no se ofrece al dar de
 * alta</b>: el super usuario se crea a mano en Keycloak y ningun grupo concede su autoridad. Verlo en
 * una ficha es legitimo; poder crearlo desde una pantalla, no.
 */
export const TIPOS_DE_PERSONA: readonly TipoDePersona[] = [
  'SUPER_ADMIN',
  'ADMIN',
  'ENGINEER',
  'MANAGER',
  'CEO_CLIENT',
] as const;

/**
 * Como se nombra cada tipo en pantalla.
 *
 * <p>⚠️ <b>`ENGINEER` y `MANAGER` son dos cosas distintas y se confunden facilisimo.</b> El ingeniero
 * es de BolivarBioingenieria y ejecuta mantenimientos; el encargado es del cliente y responde por una
 * sede o un area —lo que la ERS llama «profesional responsable» y el realm, por un nombre heredado,
 * tambien llama `engineer` en sus autoridades—. Ver el glosario del dominio.
 */
export const ETIQUETA_DE_TIPO_DE_PERSONA: Readonly<Record<TipoDePersona, string>> = {
  SUPER_ADMIN: 'Super usuario',
  ADMIN: 'Administrador',
  ENGINEER: 'Ingeniero',
  MANAGER: 'Encargado del cliente',
  CEO_CLIENT: 'Representante del cliente',
};

/**
 * Si dar de alta o tocar a alguien de este tipo exige el escalon de arriba.
 *
 * <p>Es la regla del backend reflejada, no una invencion de la pantalla: crear, editar o retirar a la
 * gente de la casa exige {@code super.person.write}. Se usa para no ofrecer botones que el servidor
 * va a rechazar con un 403.
 */
export function exigeEscalonDeArriba(tipo: TipoDePersona | undefined): boolean {
  return tipo === 'ADMIN' || tipo === 'ENGINEER' || tipo === 'SUPER_ADMIN';
}

/**
 * Reportes de servicio: lo que se hizo sobre cada equipo de una orden.
 *
 * <p>Hay <b>uno por equipo de la orden</b>, no uno por orden. Nace en {@code BORRADOR} y vacio, se llena
 * en campo y se cierra; desde ahi ya no cambia, y corregirlo es retirarlo y abrir otro.
 *
 * <p>El reporte <b>no trae cliente, sede ni responsables</b>: se consultan por el identificador de la
 * orden, que es lo que RF-11 llama autocompletar. Copiarlos seria una tercera copia que mantener.
 */
export type ReporteDeServicio = reportes['schemas']['ServiceReportResponse'];
export type LlenarReporte = reportes['schemas']['ServiceReportFillRequest'];
export type LecturaDeVerificacion = reportes['schemas']['VerificationReadingResponse'];
export type NuevaLectura = reportes['schemas']['VerificationReadingRequest'];

export type EstadoDeReporte = NonNullable<ReporteDeServicio['estado']>;

/**
 * Como queda el equipo tras la intervencion.
 *
 * <p>⚠️ <b>Estas tres palabras no salen de la ERS</b>, que enumera «resultado» entre los campos del
 * reporte sin decir que valores admite. Son una propuesta del backend, y estan marcadas como tal en su
 * migracion: cambiarlas mientras no haya datos es una linea.
 */
export type ResultadoDeServicio = NonNullable<LlenarReporte['resultado']>;

/** Los dos estados, en el orden en que ocurren. */
export const ESTADOS_DE_REPORTE: readonly EstadoDeReporte[] = ['BORRADOR', 'FINALIZADO'] as const;

export const ETIQUETA_DE_ESTADO_DE_REPORTE: Readonly<Record<EstadoDeReporte, string>> = {
  BORRADOR: 'Borrador',
  FINALIZADO: 'Finalizado',
};

export const RESULTADOS_DE_SERVICIO: readonly ResultadoDeServicio[] = [
  'OPERATIVO',
  'OPERATIVO_CON_RESTRICCIONES',
  'FUERA_DE_SERVICIO',
] as const;

export const ETIQUETA_DE_RESULTADO: Readonly<Record<ResultadoDeServicio, string>> = {
  OPERATIVO: 'Operativo',
  OPERATIVO_CON_RESTRICCIONES: 'Operativo con restricciones',
  FUERA_DE_SERVICIO: 'Fuera de servicio',
};

/**
 * Si un reporte se puede cerrar con lo que tiene escrito.
 *
 * <p>Es la misma pareja que exige el backend —procedimientos y resultado—, y esta aqui para poder
 * desactivar el boton en vez de dejar que el servidor responda 409 a algo que la pantalla ofrecio. <b>No
 * cubre la verificacion metrologica</b>, que el servidor comprueba contra el tipo del equipo y la
 * pantalla no puede saber sin consultarlo.
 */
export function sePuedeCerrar(reporte: ReporteDeServicio | undefined): boolean {
  return (
    !!reporte &&
    reporte.estado === 'BORRADOR' &&
    !!reporte.estadoActivo &&
    !!reporte.procedimientos?.trim() &&
    !!reporte.resultado
  );
}
