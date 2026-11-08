import { components } from './client.contrato';
import { components as ubicacion } from './location.contrato';
import { components as personas } from './person.contrato';
import { components as equipos } from './equipment.contrato';
import { components as ordenes } from './work-order.contrato';
import { components as reportes } from './reports.contrato';
import { components as compania } from './company.contrato';
import { components as carga } from './data-load.contrato';

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
export type ResumenDeSede = components['schemas']['HeadquarterSummaryResponse'];
export type NuevaSede = components['schemas']['HeadquarterCreateRequest'];
export type CambioDeSede = components['schemas']['HeadquarterUpdateRequest'];

export type AreaDeServicio = components['schemas']['ServiceAreaResponse'];
export type NuevaArea = components['schemas']['ServiceAreaCreateRequest'];
/** Renombrar un area reutiliza el cuerpo del alta: el backend no declara un tipo aparte. */
export type CambioDeArea = NuevaArea;

/**
 * Quien supervisa a un cliente, con su cargo (V53, 2026-11-01): tiene cuenta, ve lo de ese cliente y
 * aprueba sus ordenes. Sustituye a los encargados de sede y de area y al representante legal.
 */
export type SupervisorDelCliente = components['schemas']['ClientSupervisorResponse'];

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

/**
 * Los protocolos de mantenimiento preventivo, del 2026-10-10: una actividad reutilizable que se asigna
 * a los tipos de equipo con una prioridad. La asignacion se manda entera y en orden; la prioridad la
 * deriva el servidor de la posicion.
 */
export type Protocolo = equipos['schemas']['ProtocolResponse'];
export type NuevoProtocolo = equipos['schemas']['ProtocolRequest'];
export type Patron = equipos['schemas']['ReferenceStandardResponse'];
export type NuevoPatron = equipos['schemas']['ReferenceStandardRequest'];
export type ProtocoloAsignado = equipos['schemas']['AssignedProtocolResponse'];
export type AsignacionDeProtocolos = equipos['schemas']['AssignProtocolsRequest'];
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
/** La foto de un equipo (V58, 2026-11-08): un PNG como data: URI y cuándo se subió. */
export type FotoDeEquipo = equipos['schemas']['EquipmentPhotoResponse'];
export type ResumenDeEquipos = equipos['schemas']['EquipmentOverviewResponse'];
export type EquiposDeArea = equipos['schemas']['EquipmentAreaSummaryResponse'];
export type ProtocolosDeTipo = equipos['schemas']['ProtocolsOfTypeResponse'];
export type FilaDeResumenDeEquipos = equipos['schemas']['EquipmentOverviewRow'];

/** Lo que se le pide al listado de equipos. Los campos ausentes no filtran. */
export interface ConsultaDeEquipos {
  readonly q?: string;
  readonly idCliente?: string;
  readonly idTipoEquipo?: string;
  readonly resultado?: ResultadoDeServicio;
  readonly dadosDeBaja?: boolean;
  readonly pagina: number;
  readonly tamano: number;
}

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
/** La aprobación de una orden ejecutada por el supervisor de su cliente (V54): lo que permite facturarla. */
export type AprobacionDeOrden = ordenes['schemas']['WorkOrderApprovalResponse'];

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
 * <p><b>Desde V53 toda persona tiene cuenta</b>: el API tiene tres altas, y las tres crean ademas el
 * usuario en Keycloak, con correo, nombre de usuario y contrasena inicial. Hasta entonces habia una
 * cuarta, sin cuenta, para los encargados.
 *
 * <p><b>Y quien puede crear a quien depende de QUE es esa persona</b>, que es la «escalera de
 * usuarios» del backend: la gente de la casa -ingenieros y administradores- exige
 * {@code super.person.write}, y la del cliente -sus supervisores- {@code person.write}. El
 * prefijo marca lo que {@code admin.full} no concede. Ver [[modelo-de-permisos]].
 */
export type Persona = personas['schemas']['PersonResponse'];
export type NuevoUsuario = personas['schemas']['PersonRegisterRequest'];
export type CambioDePersona = personas['schemas']['PersonUpdateRequest'];
export type CorreoDePersona = personas['schemas']['EmailPersonResponse'];
export type TelefonoDePersona = personas['schemas']['PhonePersonResponse'];
/** Si la persona tiene cuenta y con qué usuario entra (2026-11-02). */
export type CuentaDePersona = personas['schemas']['PersonAccountResponse'];
export type AccesoNuevo = personas['schemas']['AccountGrantRequest'];

/** Sale del tipo de la PETICION: en la respuesta es opcional y ese undefined se colaria al cuerpo. */
export type TipoDePersona = NonNullable<CambioDePersona['tipoPersona']>;

/**
 * Los cuatro tipos, de la casa hacia el cliente.
 *
 * <p>{@code SUPER_ADMIN} esta en la lista porque el contrato lo admite, pero <b>no se ofrece al dar de
 * alta</b>: el super usuario se crea a mano en Keycloak y ningun grupo concede su autoridad. Verlo en
 * una ficha es legitimo; poder crearlo desde una pantalla, no.
 */
export const TIPOS_DE_PERSONA: readonly TipoDePersona[] = [
  'SUPER_ADMIN',
  'ADMIN',
  'ENGINEER',
  'SUPERVISOR',
] as const;

/**
 * Como se nombra cada tipo en pantalla.
 *
 * <p>El ingeniero es de la empresa que presta el servicio y ejecuta mantenimientos; el supervisor es
 * del cliente, revisa y aprueba. Hasta V53 la gente del cliente era el encargado (`MANAGER`) y el
 * representante (`CEO_CLIENT`).
 */
export const ETIQUETA_DE_TIPO_DE_PERSONA: Readonly<Record<TipoDePersona, string>> = {
  SUPER_ADMIN: 'Super usuario',
  ADMIN: 'Administrador',
  ENGINEER: 'Ingeniero',
  SUPERVISOR: 'Supervisor del cliente',
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

/**
 * Un paso del protocolo copiado en el reporte, del 2026-10-10: se marca hecho o no aplica, y «no
 * aplica» exige nota. Ver `ServiceReport` en el backend.
 */
export type PasoDeReporte = reportes['schemas']['ReportStepResponse'];
export type EstadoDePaso = NonNullable<PasoDeReporte['estado']>;
export type MarcaDePaso = reportes['schemas']['MarkReportStepRequest'];
export type LlenarReporte = reportes['schemas']['ServiceReportFillRequest'];
export type LecturaDeVerificacion = reportes['schemas']['VerificationReadingResponse'];
export type NuevaLectura = reportes['schemas']['VerificationReadingRequest'];

/**
 * Un patrón contra el que se tomaron las lecturas de una verificación, copiado como estaba el día que
 * se eligió (V27, 2026-10-14): renovar su certificado en el catálogo no cambia lo que el reporte dice.
 * Desde V30 (2026-10-16) son varios por verificación y llevan su ficha.
 */
export type PatronUsado = reportes['schemas']['UsedReferenceStandardResponse'];

/**
 * Lo que el informe imprime y no es del reporte: la orden, el cliente, el equipo, cómo se verifica, la
 * empresa y el ingeniero (2026-10-17). Congelado al cerrar; en un borrador, compuesto en vivo.
 */
export type InformeDeReporte = reportes['schemas']['ReportDocumentResponse'];

/** Cómo llegó el equipo y a quién se entregó (V31, 2026-10-16). */
export type RegistroDeIngreso = reportes['schemas']['RecordIntakeRequest'];
export type AccesorioDelIngreso = reportes['schemas']['IntakeAccessoryRequest'];
export type QuienRecibe = reportes['schemas']['RecordHandoverRequest'];

/**
 * Quien recibe firma el recibido (V39, 2026-10-17): quién es, la firma como PNG en data: URI y, con el
 * alcance de RF-21, si vale también para los del área o los de la orden que aún no tengan.
 */
export type FirmaDeRecibido = reportes['schemas']['SignReceiptRequest'];
/** RF-21: a cuántos reportes de la orden va una firma de quien recibe. */
export type AlcanceDeFirma = NonNullable<FirmaDeRecibido['alcance']>;
export type VerificacionAlIngreso = reportes['schemas']['IntakeCheckResponse'];
export type AccesorioDeReporte = reportes['schemas']['ReportAccessoryResponse'];
export type EstadoAlIngreso = NonNullable<RegistroDeIngreso['fisico']>;

/** Las tres columnas del informe impreso, en su orden. */
export const ESTADOS_AL_INGRESO: readonly EstadoAlIngreso[] = [
  'CONFORME',
  'NO_CONFORME',
  'NO_APLICA',
] as const;

export const ETIQUETA_DE_ESTADO_AL_INGRESO: Readonly<Record<EstadoAlIngreso, string>> = {
  CONFORME: 'Conforme',
  NO_CONFORME: 'No conforme',
  NO_APLICA: 'No aplica',
};

/** Cuántos equipos de una orden tienen su reporte cerrado y cuántos en borrador (2026-10-16). */
export type AvanceDeReportes = reportes['schemas']['WorkOrderReportProgressResponse'];

/**
 * Si el certificado del patrón ya no vale ese día. El último día todavía vale, como en el servidor:
 * vence al terminar. Las dos fechas son «2026-10-14», así que se comparan como texto.
 */
export function patronVencido(patron: PatronUsado | null | undefined, hoy: string): boolean {
  return !!patron?.vencimiento && patron.vencimiento < hoy;
}

/**
 * Las verificaciones de las que el reporte tiene lecturas y ningún patrón: sin él, esas lecturas no
 * dicen contra qué se compararon y el servidor no deja cerrar (V30).
 */
export function verificacionesSinPatron(reporte: ReporteDeServicio | undefined): string[] {
  const conPatron = new Set((reporte?.patrones ?? []).map((usado) => usado.idVerificacion));
  const medidas = new Set((reporte?.lecturas ?? []).map((lectura) => lectura.idVerificacion!));

  return [...medidas].filter((verificacion) => !conPatron.has(verificacion));
}

/** Si algún patrón del reporte tiene el certificado vencido ese día: el informe los imprime todos. */
export function algunPatronVencido(reporte: ReporteDeServicio | undefined, hoy: string): boolean {
  return (reporte?.patrones ?? []).some((usado) => patronVencido(usado, hoy));
}

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
export function sePuedeCerrar(reporte: ReporteDeServicio | undefined, hoy?: string): boolean {
  if (!reporte || reporte.estado !== 'BORRADOR' || !reporte.estadoActivo || !reporte.resultado) {
    return false;
  }

  // Desde el 2026-10-16, como en el servidor: todo reporte exige las dos verificaciones al ingreso.
  if (!reporte.ingresoFisico || !reporte.ingresoOperativo) {
    return false;
  }

  // Un correctivo exigia ademas la falla y el diagnostico, del 2026-10-14 al 2026-10-27: el usuario
  // los quito, en el servidor y aqui. El cierre es el mismo para los dos tipos de servicio.

  // Desde el 2026-10-14, como en el servidor: cada verificacion con lecturas exige un patron -por
  // verificacion desde V30-, y ninguno vencido el dia del cierre. Sin `hoy` no se juzga la vigencia:
  // quien llama sin fecha no puede saberla.
  if (verificacionesSinPatron(reporte).length || (hoy && algunPatronVencido(reporte, hoy))) {
    return false;
  }

  // Desde el 2026-10-10, como en el servidor: con pasos del protocolo se exigen todos marcados y el
  // texto es trabajo adicional, opcional; sin pasos el texto sigue siendo lo que cuenta que se hizo.
  const pasos = reporte.pasos ?? [];

  return pasos.length
    ? pasos.every((paso) => paso.estado !== 'PENDIENTE')
    : !!reporte.procedimientos?.trim();
}

/**
 * La empresa que presta el servicio (2026-10-17): el membrete de los documentos, que un administrador
 * edita. El logo viaja como data: URI.
 */
export type Empresa = compania['schemas']['ServiceCompanyResponse'];

/** La firma de quien llama, que copian los informes que cierra (V38, 2026-10-17). */
export type MiFirma = personas['schemas']['PersonSignatureResponse'];
export type DatosDeEmpresa = compania['schemas']['ServiceCompanyRequest'];

/**
 * El informe de una carga de datos (2026-11-08): lo que impide aplicarla, lo que conviene leer y, por
 * sección, cuántos registros entran y cuántos ya estaban. Es la misma forma al validar y al aplicar, y
 * también la del 422 de una carga rechazada.
 */
export type InformeDeCarga = carga['schemas']['DataLoadReportResponse'];
export type ProblemaDeCarga = carga['schemas']['DataLoadProblemResponse'];
export type SeccionDeCarga = carga['schemas']['DataLoadSectionResponse'];
