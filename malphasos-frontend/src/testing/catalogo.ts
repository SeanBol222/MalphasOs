import { ComponentFixture } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';
import { asentar } from './pantalla';

/**
 * Las cinco listas del catalogo de equipos, que casi ninguna pantalla pide de una en una.
 *
 * <p>Ninguna respuesta del modulo trae nombres, solo identificadores, de modo que una pantalla que
 * quiera pintar «tensiometro Welch Allyn · Medtronic» necesita cruzar cuatro listas. Responder a las
 * cinco en cada prueba a mano seria copiar cinco lineas por caso.
 */

const API = 'http://localhost:8081/v1/api';

export const URL_MARCAS = `${API}/brands`;
export const URL_TIPOS = `${API}/equipment-types`;
export const URL_FABRICANTES = `${API}/manufacturers`;
export const URL_EQUIPOS = `${API}/equipments`;
export const URL_MODELOS = `${API}/models`;

/** Un catalogo de ejemplo, encadenado como el de verdad: marca y tipo → equipo → modelo. */
export const ID_MARCA = 'm1';
export const ID_TIPO = 't1';
export const ID_FABRICANTE = 'f1';
export const ID_EQUIPO = 'e1';
export const ID_MODELO = 'mo1';

export const MARCAS = [
  { id: ID_MARCA, nombre: 'Welch Allyn', estadoActivo: true },
  { id: 'm2', nombre: 'Marca retirada', estadoActivo: false },
];

export const TIPOS = [
  {
    id: ID_TIPO,
    nombre: 'Tensiómetro',
    tecnologiaPredominante: 'Electrónica',
    definicionTecnica: 'Mide presión arterial',
    recomendacionesCuidado: 'No golpear',
    modalidadVerificacion: 'PATRON_CONSTANTE',
    verificable: true,
    estadoActivo: true,
  },
];

export const FABRICANTES = [
  { id: ID_FABRICANTE, nombre: 'Medtronic', idPais: 'co', estadoActivo: true },
];

export const EQUIPOS = [
  { id: ID_EQUIPO, idTipoEquipo: ID_TIPO, idMarca: ID_MARCA, estadoActivo: true },
];

export const MODELOS = [
  { id: ID_MODELO, idEquipo: ID_EQUIPO, idFabricante: ID_FABRICANTE, invima: 'INV-1', estadoActivo: true },
];

/** Responde a las consultas del catalogo que la pantalla haya lanzado, en cualquier orden. */
export async function responderAlCatalogo(
  fixture: ComponentFixture<unknown>,
  http: HttpTestingController,
  datos: {
    marcas?: object;
    tipos?: object;
    fabricantes?: object;
    equipos?: object;
    modelos?: object;
  } = {},
): Promise<void> {
  const pares: [string, object][] = [
    [URL_MARCAS, datos.marcas ?? MARCAS],
    [URL_TIPOS, datos.tipos ?? TIPOS],
    [URL_FABRICANTES, datos.fabricantes ?? FABRICANTES],
    [URL_EQUIPOS, datos.equipos ?? EQUIPOS],
    [URL_MODELOS, datos.modelos ?? MODELOS],
  ];

  // Primero se deja que salgan todas las consultas, y despues se responden las que haya.
  //
  // El orden importa y el motivo no es obvio: http.match() CONSUME las peticiones que casan, asi que
  // no se puede usar para preguntar si una existe. Preguntar y responder tienen que ser la misma
  // operacion, y cada componente pide solo las piezas que necesita, no las cinco.
  fixture.detectChanges();
  await asentar(fixture);

  for (const [url, cuerpo] of pares) {
    for (const peticion of http.match(url)) {
      peticion.flush(cuerpo);
    }
  }

  await asentar(fixture);
}
