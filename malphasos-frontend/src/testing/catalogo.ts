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
export const URL_MAGNITUDES = `${API}/magnitudes`;

/** Un catalogo de ejemplo, encadenado como el de verdad: marca y tipo → equipo → modelo. */
export const ID_MARCA = 'm1';
export const ID_TIPO = 't1';
export const ID_FABRICANTE = 'f1';
export const ID_EQUIPO = 'e1';
export const ID_MODELO = 'mo1';

/**
 * El catalogo metrologico, que no se administra: lo siembra la migracion `V10`.
 *
 * <p>Dos magnitudes a proposito, porque el caso que forzo el modelo es el termohigrometro: un aparato
 * que mide temperatura Y humedad relativa. Con una sola magnitud de prueba, la regla de «una magnitud
 * no se declara dos veces» no se podria ejercer.
 */
export const ID_PRESION = 'mag-presion';
export const ID_TEMPERATURA = 'mag-temperatura';
export const ID_MMHG = 'uni-mmhg';
export const ID_KPA = 'uni-kpa';
export const ID_CELSIUS = 'uni-celsius';

export const MAGNITUDES = [
  { id: ID_PRESION, codigo: 'presion', nombre: 'Presión' },
  { id: ID_TEMPERATURA, codigo: 'temperatura', nombre: 'Temperatura' },
];

export const UNIDADES: Readonly<Record<string, { id: string; simbolo: string; nombre: string }[]>> = {
  [ID_PRESION]: [
    { id: ID_MMHG, simbolo: 'mmHg', nombre: 'milímetro de mercurio' },
    { id: ID_KPA, simbolo: 'kPa', nombre: 'kilopascal' },
  ],
  [ID_TEMPERATURA]: [{ id: ID_CELSIUS, simbolo: '°C', nombre: 'grado Celsius' }],
};

export const URL_UNIDADES_DE = (magnitudId: string) => `${API}/magnitudes/${magnitudId}/units`;

/** La verificacion que trae el tipo de ejemplo: presion en mmHg, patron constante, 3 lecturas. */
export const ID_VERIFICACION = 'ver1';

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
    // Desde el 2026-10-03 lo que se verifica cuelga de un nivel propio: un tipo declara una
    // verificacion por magnitud, con su unidad, su modalidad, sus lecturas por punto y sus valores.
    // Antes eran tres campos sueltos del tipo, y eso daba por supuesto que un aparato mide una cosa.
    verificaciones: [
      {
        id: ID_VERIFICACION,
        magnitudId: ID_PRESION,
        magnitud: 'Presión',
        unidadId: ID_MMHG,
        unidad: 'mmHg',
        unidadNombre: 'milímetro de mercurio',
        modalidad: 'PATRON_CONSTANTE',
        cantidadDatos: 3,
        puntos: [{ id: 'pv1', valor: 100 }],
      },
    ],
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
    magnitudes?: object;
  } = {},
): Promise<void> {
  const pares: [string, object][] = [
    [URL_MARCAS, datos.marcas ?? MARCAS],
    [URL_TIPOS, datos.tipos ?? TIPOS],
    [URL_FABRICANTES, datos.fabricantes ?? FABRICANTES],
    [URL_EQUIPOS, datos.equipos ?? EQUIPOS],
    [URL_MODELOS, datos.modelos ?? MODELOS],
    [URL_MAGNITUDES, datos.magnitudes ?? MAGNITUDES],
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

  // Las unidades salen DESPUES de las magnitudes, porque la consulta depende de la elegida: se
  // responden en una segunda pasada o no habrian existido todavia.
  for (const magnitudId of Object.keys(UNIDADES)) {
    for (const peticion of http.match(URL_UNIDADES_DE(magnitudId))) {
      peticion.flush(UNIDADES[magnitudId]);
    }
  }

  await asentar(fixture);
}
