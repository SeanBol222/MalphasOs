import { components } from './client.contrato';

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
export type TipoIdentificacion = NuevoCliente['tipoIdentificacion'];

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
