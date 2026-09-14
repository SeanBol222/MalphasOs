import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';

/**
 * El sistema de diseño, contrastado contra el Manual de Marca.
 *
 * <p>Es el equivalente de {@code RealmAuthorityContractTest} del backend: un
 * invariante estructural, no un caso. Allí se comparaba el vocabulario del
 * código con el del realm; aquí se comparan los tokens con lo que el manual
 * declara, y sus contrastes con lo que WCAG AA exige.
 *
 * <p><b>Esta prueba habría encontrado sola el defecto del botón.</b> El manual
 * describe la acción principal como «relleno acento»; con el acento de marca
 * la etiqueta da 3,76:1 y no alcanza AA. Se descubrió midiendo a mano al
 * escribir la declaración de diseño. A partir de aquí lo dice la batería.
 */

const TOKENS = leerTokens();

/** Los hexadecimales que el Manual de Marca v1.0 declara, apartado 05 — Color. */
const MANUAL = {
  ink: '#201e1d',
  paper: '#f3f2f2',
  'brand-black': '#2d2b2b',
  accent: '#ec3013',
  'accent-700': '#ae1800',
} as const;

/** Umbrales de WCAG 2.1 AA. */
const AA_TEXTO = 4.5;
const AA_NO_TEXTO = 3;

describe('Sistema de diseño', () => {
  describe('Los tokens dicen lo que el manual de marca dice', () => {
    it.each(Object.entries(MANUAL))('%s vale %s', (nombre, hex) => {
      expect(TOKENS[`--color-${nombre}`]).toBe(hex);
    });

    it('el paso 600 del acento y el acento de marca son el mismo color', () => {
      // No son dos decisiones: el acento ES un paso de su propia escala.
      expect(TOKENS['--color-accent-600']).toBe(TOKENS['--color-accent']);
    });

    it('el paso 900 de la neutra y el negro de marca son el mismo color', () => {
      expect(TOKENS['--color-neutral-900']).toBe(TOKENS['--color-brand-black']);
    });
  });

  describe('Contraste, que es lo que el manual no podía comprobar solo', () => {
    it('el texto sobre papel cumple AA con holgura', () => {
      expect(contraste(TOKENS['--color-ink'], TOKENS['--color-paper'])).toBeGreaterThanOrEqual(
        AA_TEXTO,
      );
    });

    it('el texto en acento cumple AA usando el paso 700, que es para lo que existe', () => {
      expect(
        contraste(TOKENS['--color-accent-700'], TOKENS['--color-paper']),
      ).toBeGreaterThanOrEqual(AA_TEXTO);
    });

    it('la etiqueta de la accion principal cumple AA sobre el relleno del paso 700', () => {
      // El caso que el manual no nombra. Es el control mas repetido de la
      // aplicacion: si esto se rompe, la aplicacion entera deja de ser AA.
      expect(contraste(TOKENS['--color-paper'], TOKENS['--color-accent-700'])).toBeGreaterThanOrEqual(
        AA_TEXTO,
      );
    });

    it('el acento de marca sirve para bordes y foco, que es donde el manual lo pone', () => {
      // Umbral de elemento no textual: 3,0.
      expect(contraste(TOKENS['--color-accent'], TOKENS['--color-paper'])).toBeGreaterThanOrEqual(
        AA_NO_TEXTO,
      );
    });

    it('y NO sirve para texto, que es la razon de que el paso 700 exista', () => {
      // Prueba a la inversa, y es deliberada: fija el *motivo* de una regla,
      // no solo la regla. Si alguien aclarara u oscureciera el acento de marca
      // hasta que pasara AA, esta prueba caeria avisando de que la separacion
      // entre accent y accent-700 ya no tiene razon de ser.
      expect(contraste(TOKENS['--color-accent'], TOKENS['--color-paper'])).toBeLessThan(AA_TEXTO);
    });
  });

  describe('Forma', () => {
    it('el espaciado base son 8 px, de modo que la escala no puede expresar 4', () => {
      // El manual manda multiplos de 8. Con la base en 0.5rem, p-1 son 8 px:
      // los 4 px dejan de ser escribibles con la escala.
      expect(TOKENS['--spacing']).toBe('0.5rem');
    });

    it('el area tactil minima son 44 px', () => {
      expect(TOKENS['--spacing-tactil']).toBe('2.75rem');
    });

    it('los radios de Tailwind quedan anulados: el manual exige radio cero', () => {
      const css = leerHojaDeEstilos();
      expect(css).toMatch(/--radius-\*:\s*initial/);
    });

    it('la familia tipografica es Archivo, con las sustitutas que el manual nombra', () => {
      const familia = TOKENS['--font-sans'];
      expect(familia).toContain('Archivo');
      expect(familia.indexOf('Helvetica Neue')).toBeLessThan(familia.indexOf('Arial'));
    });
  });
});

// ---------------------------------------------------------------------------

function leerHojaDeEstilos(): string {
  // Desde la raiz del proyecto y no desde import.meta.url: la prueba se
  // empaqueta antes de correr, y ahi esa URL apunta al bundle, no al fuente.
  return readFileSync(resolve(process.cwd(), 'src/styles.css'), 'utf8');
}

/** Los pares `--token: valor;` del bloque `@theme` de la hoja de estilos. */
function leerTokens(): Record<string, string> {
  const css = leerHojaDeEstilos();
  const bloque = css.slice(css.indexOf('@theme'), css.indexOf('@layer base'));
  const tokens: Record<string, string> = {};

  for (const [, nombre, valor] of bloque.matchAll(/(--[\w-]+):\s*([^;]+);/g)) {
    tokens[nombre] = valor.trim();
  }

  return tokens;
}

/** Relación de contraste de WCAG 2.1 entre dos colores hexadecimales. */
function contraste(a: string, b: string): number {
  const [alta, baja] = [luminancia(a), luminancia(b)].sort((x, y) => y - x);

  return (alta + 0.05) / (baja + 0.05);
}

/** Luminancia relativa, según la definición de WCAG 2.1. */
function luminancia(hex: string): number {
  const canales = [1, 3, 5].map((i) => parseInt(hex.slice(i, i + 2), 16) / 255);
  const [r, g, b] = canales.map((c) => (c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4));

  return 0.2126 * r + 0.7152 * g + 0.0722 * b;
}
