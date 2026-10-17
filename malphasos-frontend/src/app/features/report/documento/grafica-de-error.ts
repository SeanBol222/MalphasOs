/**
 * La gráfica de la página 2 del reporte (2026-10-16): el error de cada lectura —equipo menos patrón—
 * frente a la banda de tolerancia. Es la que el usuario eligió entre tres —error con banda, líneas de
 * patrón y equipo, y barras—, sobre la idea de los informes reales, que dibujan patrón y equipo como
 * dos líneas casi encimadas: con la banda se ve de un vistazo qué lectura se salió y por cuánto.
 *
 * <p>Una función pura que devuelve la geometría ya calculada, para que la plantilla solo pinte y las
 * pruebas puedan comprobar dónde cae cada punto sin montar nada.
 */

/** El lienzo, en unidades del viewBox: el eje vertical en x = 48 y la zona útil de y = 10 a y = 130. */
export const LIENZO = {
  ancho: 360,
  alto: 140,
  izquierda: 48,
  derecha: 352,
  arriba: 10,
  abajo: 130,
} as const;

export interface Punto {
  readonly x: number;
  readonly y: number;
  /** Nulo si la lectura no tiene tolerancia: no se sabe, y se pinta neutro. */
  readonly conforme: boolean | null;
}

export interface Marca {
  readonly y: number;
  readonly texto: string;
}

export interface GraficaDeError {
  /** La banda de tolerancia; nula si no hay tolerancia que dibujar. */
  readonly banda: { readonly y: number; readonly alto: number } | null;
  readonly ceroY: number;
  readonly puntos: readonly Punto[];
  /** Las marcas del eje vertical: +tolerancia, 0 y −tolerancia, o el mayor error si no hay tolerancia. */
  readonly marcas: readonly Marca[];
}

const FORMATO = new Intl.NumberFormat('es-CO', { maximumFractionDigits: 4 });

/** «+0,5», «−0,5», «0». Con el signo menos de verdad, no el guion. */
export function conSigno(valor: number): string {
  if (valor === 0) {
    return '0';
  }

  return `${valor > 0 ? '+' : '−'}${FORMATO.format(Math.abs(valor))}`;
}

/**
 * La geometría de la gráfica.
 *
 * <p>La escala es simétrica alrededor de cero y deja aire: llega a la mayor de 1,6 veces la tolerancia
 * o 1,25 veces el mayor error, de modo que la banda nunca toca el borde y un punto fuera de ella se ve
 * fuera. Las lecturas se reparten a lo ancho en el orden en que llegan.
 */
export function graficaDeError(
  lecturas: readonly { readonly error: number; readonly conforme: boolean | null }[],
  tolerancia: number | null,
): GraficaDeError {
  const { izquierda, derecha, arriba, abajo } = LIENZO;
  const mayorError = Math.max(0, ...lecturas.map((lectura) => Math.abs(lectura.error)));
  const tope = Math.max((tolerancia ?? 0) * 1.6, mayorError * 1.25) || 1;
  const y = (valor: number) => arriba + ((tope - valor) / (2 * tope)) * (abajo - arriba);
  const paso = (derecha - izquierda) / Math.max(lecturas.length, 1);

  const marcas: Marca[] =
    tolerancia !== null
      ? [
          { y: y(tolerancia), texto: conSigno(tolerancia) },
          { y: y(0), texto: '0' },
          { y: y(-tolerancia), texto: conSigno(-tolerancia) },
        ]
      : [
          { y: y(mayorError), texto: conSigno(mayorError) },
          { y: y(0), texto: '0' },
          { y: y(-mayorError), texto: conSigno(-mayorError) },
        ];

  return {
    banda: tolerancia !== null ? { y: y(tolerancia), alto: y(-tolerancia) - y(tolerancia) } : null,
    ceroY: y(0),
    puntos: lecturas.map((lectura, indice) => ({
      x: izquierda + (indice + 0.5) * paso,
      y: y(lectura.error),
      conforme: lectura.conforme,
    })),
    // Sin tolerancia y sin error, las tres marcas serian el mismo cero.
    marcas: marcas.filter(
      (marca, i) => marcas.findIndex((otra) => otra.texto === marca.texto) === i,
    ),
  };
}
