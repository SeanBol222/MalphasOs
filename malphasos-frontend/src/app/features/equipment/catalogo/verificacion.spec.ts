import { Component, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';
import { proveerApiSimulado } from '../../../../testing/entorno';
import { asentar, responderA } from '../../../../testing/pantalla';
import {
  ID_CELSIUS,
  ID_MMHG,
  ID_PRESION,
  ID_TEMPERATURA,
  MAGNITUDES,
  UNIDADES,
  URL_MAGNITUDES,
  URL_UNIDADES_DE,
} from '../../../../testing/catalogo';
import { VerificacionEnCurso } from './panel-de-verificacion';
import { ConfiguracionDeVerificacion, Verificacion } from './verificacion';

@Component({
  selector: 'app-anfitrion-verificacion',
  imports: [Verificacion],
  template: `<app-verificacion [inicial]="inicial()" (cambio)="ultima.set($event)" />`,
})
class Anfitrion {
  readonly inicial = signal<readonly VerificacionEnCurso[] | null>(null);
  readonly ultima = signal<ConfiguracionDeVerificacion | null>(null);
}

describe('Qué se verifica en un tipo de equipo', () => {
  let fixture: ComponentFixture<Anfitrion>;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [...proveerApiSimulado()] });
    fixture = TestBed.createComponent(Anfitrion);
    http = TestBed.inject(HttpTestingController);
  });

  const raiz = () => fixture.nativeElement as HTMLElement;
  const texto = () => raiz().textContent ?? '';
  const ultima = () => fixture.componentInstance.ultima();

  /** Abre el bloque con lo que ya estuviera guardado y responde al catálogo que pida. */
  async function abrirCon(inicial: readonly VerificacionEnCurso[] | null): Promise<void> {
    fixture.componentInstance.inicial.set(inicial);
    fixture.detectChanges();
    await responderA(fixture, http, URL_MAGNITUDES, MAGNITUDES);
    await responderAUnidades();
  }

  /**
   * Responde a las consultas de unidades que haya.
   *
   * <p>Salen después de las magnitudes, porque la consulta depende de la elegida: responderlas antes
   * sería responder a algo que todavía no existe. Y `http.match` consume, así que preguntar si una
   * existe y responderla tienen que ser la misma operación.
   */
  async function responderAUnidades(): Promise<void> {
    for (const magnitudId of Object.keys(UNIDADES)) {
      for (const peticion of http.match(URL_UNIDADES_DE(magnitudId))) {
        peticion.flush(UNIDADES[magnitudId]);
      }
    }

    await asentar(fixture);
  }

  function escribir(selector: string, valor: string): void {
    const control = raiz().querySelector<HTMLInputElement | HTMLSelectElement>(selector)!;
    control.value = valor;
    control.dispatchEvent(new Event(control.tagName === 'SELECT' ? 'change' : 'input'));
    fixture.detectChanges();
  }

  /** Una verificación de presión en mmHg, patrón constante, 3 lecturas, un punto a 100. */
  const presion: VerificacionEnCurso = {
    magnitudId: ID_PRESION,
    unidadId: ID_MMHG,
    modalidad: 'PATRON_CONSTANTE',
    cantidadDatos: 3,
    puntos: [100],
    tolerancia: null,
    valida: true,
  };

  it('sin verificaciones no hay paneles, y es válido: así se dice que no se verifica nada', async () => {
    await abrirCon(null);

    expect(raiz().querySelector<HTMLInputElement>('#cuantasVerificaciones')!.value).toBe('0');
    expect(raiz().querySelectorAll('app-panel-de-verificacion').length).toBe(0);
    expect(ultima()?.verificaciones).toEqual([]);
    expect(ultima()?.valida).toBe(true);
  });

  it('el contador despliega tantos paneles como se le pida', async () => {
    // Es lo que el usuario pidio: decir cuantas cosas se le miden y que aparezca un panel por cada
    // una. Un termohigrometro son dos.
    await abrirCon(null);

    escribir('#cuantasVerificaciones', '2');
    await asentar(fixture);

    expect(raiz().querySelectorAll('app-panel-de-verificacion').length).toBe(2);
    expect(texto()).toContain('Verificación 1');
    expect(texto()).toContain('Verificación 2');
  });

  it('bajar el contador quita los últimos paneles', async () => {
    await abrirCon(null);
    escribir('#cuantasVerificaciones', '2');
    await asentar(fixture);

    escribir('#cuantasVerificaciones', '1');
    await asentar(fixture);

    expect(raiz().querySelectorAll('app-panel-de-verificacion').length).toBe(1);
  });

  it('lo ya guardado se ve puesto al abrir, panel por panel', async () => {
    await abrirCon([presion]);

    expect(raiz().querySelector<HTMLSelectElement>('#magnitud-1')!.value).toBe(ID_PRESION);
    expect(raiz().querySelector<HTMLSelectElement>('#unidad-1')!.value).toBe(ID_MMHG);
    expect(raiz().querySelector<HTMLSelectElement>('#modalidad-1')!.value).toBe('PATRON_CONSTANTE');
    expect(raiz().querySelector<HTMLInputElement>('#cantidad-1')!.value).toBe('3');
    expect(raiz().querySelector<HTMLInputElement>('#cuantos-1')!.value).toBe('1');
    expect(raiz().querySelector<HTMLInputElement>('#valor-1-0')!.value).toBe('100');
  });

  it('la unidad solo ofrece las de la magnitud elegida', async () => {
    // No es comodidad: el esquema ata el par (magnitud, unidad) con una foranea compuesta, de modo
    // que ofrecer °C para una verificacion de presion seria ofrecer un 409.
    await abrirCon([presion]);

    const unidades = [...raiz().querySelectorAll('#unidad-1 option')].map((o) =>
      o.textContent?.trim(),
    );

    expect(unidades.join(' ')).toContain('mmHg');
    expect(unidades.join(' ')).toContain('kPa');
    expect(unidades.join(' ')).not.toContain('°C');
  });

  it('cambiar de magnitud suelta la unidad, que ya no es de esa magnitud', async () => {
    await abrirCon([presion]);

    escribir('#magnitud-1', ID_TEMPERATURA);
    await responderAUnidades();

    // Si se conservara, el panel quedaria en el unico estado que el servidor devuelve con un 409.
    expect(raiz().querySelector<HTMLSelectElement>('#unidad-1')!.value).toBe('');
    expect(ultima()?.valida).toBe(false);
  });

  it('una magnitud ya usada no se ofrece en el otro panel', async () => {
    // Un tipo de equipo se verifica una sola vez en cada magnitud: el servidor lo rechaza y el
    // esquema lo impide con un indice unico. La pantalla refleja la regla en vez de descubrirla.
    await abrirCon([presion]);
    escribir('#cuantasVerificaciones', '2');
    await asentar(fixture);
    await responderAUnidades();

    const delSegundo = [...raiz().querySelectorAll('#magnitud-2 option')].map((o) =>
      o.textContent?.trim(),
    );

    expect(delSegundo.join(' ')).toContain('Temperatura');
    expect(delSegundo.join(' ')).not.toContain('Presión');
  });

  it('el contador de puntos despliega una casilla de valor por punto, y solo el valor', async () => {
    // Era la queja concreta: la unidad se escribia en cada punto. Ahora esta arriba, una sola vez.
    await abrirCon([presion]);

    escribir('#cuantos-1', '3');
    await asentar(fixture);

    expect(raiz().querySelector('#valor-1-0')).toBeTruthy();
    expect(raiz().querySelector('#valor-1-1')).toBeTruthy();
    expect(raiz().querySelector('#valor-1-2')).toBeTruthy();
    // No hay ningun control de unidad por punto: la unidad es de la verificacion.
    expect(raiz().querySelector('#punto-unidad-0')).toBeNull();
  });

  it('dos magnitudes se traducen a dos verificaciones, cada una con su unidad', async () => {
    await abrirCon([
      presion,
      {
        magnitudId: ID_TEMPERATURA,
        unidadId: ID_CELSIUS,
        modalidad: 'PATRON_EQUIPO_VARIABLE',
        cantidadDatos: null,
        puntos: [],
        tolerancia: null,
        valida: true,
      },
    ]);

    expect(ultima()?.verificaciones.length).toBe(2);
    expect(ultima()?.verificaciones[0].unidadId).toBe(ID_MMHG);
    expect(ultima()?.verificaciones[0].puntos).toEqual([{ valor: 100 }]);
    // Con patron y equipo variables no viajan ni cantidad ni puntos: el servidor los rechaza.
    expect(ultima()?.verificaciones[1].cantidadDatos).toBeUndefined();
    expect(ultima()?.verificaciones[1].puntos).toEqual([]);
    expect(ultima()?.valida).toBe(true);
  });

  it('un dato incoherente que venga del servidor no se reenvía', async () => {
    // Es alcanzable de verdad: si una fila tuviera puntos con la modalidad variable, la pantalla los
    // pintaria y, sin este recorte, los mandaria de vuelta para que el backend los rechace.
    await abrirCon([
      {
        magnitudId: ID_TEMPERATURA,
        unidadId: ID_CELSIUS,
        modalidad: 'PATRON_EQUIPO_VARIABLE',
        cantidadDatos: 7,
        puntos: [100],
        tolerancia: null,
        valida: true,
      },
    ]);

    expect(ultima()?.verificaciones[0].cantidadDatos).toBeUndefined();
    expect(ultima()?.verificaciones[0].puntos).toEqual([]);
    expect(ultima()?.valida).toBe(true);
  });

  it('una modalidad constante sin puntos no se da por válida', async () => {
    await abrirCon([{ ...presion, puntos: [] }]);

    // El padre mira esto para no enviar: el backend lo rechazaria con un 400 que nadie sabria leer.
    expect(ultima()?.valida).toBe(false);
  });

  it('una verificación sin magnitud no se da por válida', async () => {
    await abrirCon(null);
    escribir('#cuantasVerificaciones', '1');
    await asentar(fixture);

    expect(ultima()?.valida).toBe(false);
  });

  it('el mismo valor dos veces en la misma verificación se avisa', async () => {
    await abrirCon([presion]);
    escribir('#cuantos-1', '2');
    await asentar(fixture);
    escribir('#valor-1-1', '100');
    await asentar(fixture);

    expect(texto()).toContain('declarado dos veces');
    expect(ultima()?.valida).toBe(false);
  });

  it('el mismo valor en dos magnitudes distintas no es un repetido', async () => {
    // 100 mmHg y 100 °C no son el mismo punto. Con el modelo anterior los puntos colgaban del
    // aparato entero y solo los distinguia la unidad escrita a mano.
    await abrirCon([
      presion,
      {
        magnitudId: ID_TEMPERATURA,
        unidadId: ID_CELSIUS,
        modalidad: 'EQUIPO_CONSTANTE',
        cantidadDatos: 1,
        puntos: [100],
        tolerancia: null,
        valida: true,
      },
    ]);

    expect(texto()).not.toContain('declarado dos veces');
    expect(ultima()?.valida).toBe(true);
  });

  describe('La tolerancia (2026-10-14)', () => {
    it('se escribe en la unidad elegida, y lo dice', async () => {
      await abrirCon([presion]);

      expect(raiz().querySelector('label[for="tolerancia-1"]')!.textContent).toContain('en mmHg');
    });

    it('es opcional: en blanco el panel vale y no viaja', async () => {
      await abrirCon([presion]);

      expect(ultima()?.valida).toBe(true);
      expect(ultima()?.verificaciones[0].tolerancia).toBeUndefined();
    });

    it('escrita, viaja como número', async () => {
      await abrirCon([presion]);
      escribir('#tolerancia-1', '0.5');
      await asentar(fixture);

      expect(ultima()?.verificaciones[0].tolerancia).toBe(0.5);
      expect(ultima()?.valida).toBe(true);
    });

    it('la guardada se ve puesta al abrir y se conserva', async () => {
      // Reconfigurar otra cosa no puede quitarla en silencio.
      await abrirCon([{ ...presion, tolerancia: 2 }]);

      expect(raiz().querySelector<HTMLInputElement>('#tolerancia-1')!.value).toBe('2');
      expect(ultima()?.verificaciones[0].tolerancia).toBe(2);
    });

    it('cero, negativa o con cinco decimales no vale, y se dice', async () => {
      await abrirCon([presion]);

      for (const valor of ['0', '-1', '0.00001']) {
        escribir('#tolerancia-1', valor);
        await asentar(fixture);

        expect(ultima()?.valida, valor).toBe(false);
        expect(texto(), valor).toContain('mayor que cero y llevar como mucho 4 decimales');
      }
    });

    it('los ceros de la derecha no cuentan como decimales, igual que en el servidor', async () => {
      await abrirCon([presion]);
      escribir('#tolerancia-1', '0.50000');
      await asentar(fixture);

      expect(ultima()?.valida).toBe(true);
      expect(ultima()?.verificaciones[0].tolerancia).toBe(0.5);
    });

    it('vale también con patrón y equipo variables, porque también hay un patrón', async () => {
      await abrirCon([
        {
          magnitudId: ID_TEMPERATURA,
          unidadId: ID_CELSIUS,
          modalidad: 'PATRON_EQUIPO_VARIABLE',
          cantidadDatos: null,
          puntos: [],
          tolerancia: 0.3,
          valida: true,
        },
      ]);

      expect(ultima()?.verificaciones[0].tolerancia).toBe(0.3);
    });
  });

  describe('Lo que WCAG exige y no se ve', () => {
    it('cada campo lleva su etiqueta asociada, también los valores', async () => {
      await abrirCon([presion]);

      for (const id of [
        'cuantasVerificaciones',
        'magnitud-1',
        'unidad-1',
        'modalidad-1',
        'cantidad-1',
        'cuantos-1',
        'valor-1-0',
        'tolerancia-1',
      ]) {
        expect(raiz().querySelector(`label[for="${id}"]`)).toBeTruthy();
      }
    });

    it('los controles respetan el área táctil mínima', async () => {
      await abrirCon([presion]);

      for (const control of raiz().querySelectorAll('input, select, button')) {
        expect(control.className).toContain('min-h-tactil');
      }
    });
  });
});
