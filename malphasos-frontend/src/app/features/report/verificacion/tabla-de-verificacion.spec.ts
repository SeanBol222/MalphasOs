import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';
import { proveerApiSimulado } from '../../../../testing/entorno';
import { proveerSesionFalsa } from '../../../../testing/keycloak-falso';
import { asentar } from '../../../../testing/pantalla';
import { EQUIPOS, ID_EQUIPO } from '../../../../testing/ordenes';
import {
  EQUIPOS as EQUIPOS_DE_CATALOGO,
  ID_CELSIUS,
  ID_TEMPERATURA,
  ID_TIPO,
  ID_PATRON,
  ID_VERIFICACION,
  MODELOS,
  TIPOS,
  URL_EQUIPOS as URL_EQUIPOS_DE_CATALOGO,
  URL_MODELOS,
  URL_PATRONES,
  PATRONES,
  URL_TIPOS,
} from '../../../../testing/catalogo';
import { ID_REPORTE, URL_REPORTES, urlVerificacion } from '../../../../testing/reportes';
import { LecturaDeVerificacion } from '../../../core/api/tipos';
import { TablaDeVerificacion } from './tabla-de-verificacion';

const URL_EQUIPOS_DE_CLIENTE = 'http://localhost:8081/v1/api/client-equipments';

/** El punto que la verificacion del tipo de ejemplo declara: 100 mmHg, con tres lecturas. */
const ID_PUNTO = 'pv1';

/** La misma verificacion con patron y equipo variables: sin cantidad y sin puntos. */
const VARIABLE = {
  id: ID_VERIFICACION,
  magnitudId: 'mag-presion',
  magnitud: 'Presión',
  unidadId: 'uni-mmhg',
  unidad: 'mmHg',
  unidadNombre: 'milímetro de mercurio',
  modalidad: 'PATRON_EQUIPO_VARIABLE',
  puntos: [],
};

/** Una segunda verificacion, de temperatura: es lo que hace de un tipo un termohigrometro. */
const ID_VERIFICACION_TEMPERATURA = 'ver2';
const DE_TEMPERATURA = {
  id: ID_VERIFICACION_TEMPERATURA,
  magnitudId: ID_TEMPERATURA,
  magnitud: 'Temperatura',
  unidadId: ID_CELSIUS,
  unidad: '°C',
  unidadNombre: 'grado Celsius',
  modalidad: 'EQUIPO_CONSTANTE',
  cantidadDatos: 1,
  puntos: [{ id: 'pv2', valor: 37 }],
};

describe('Tabla de verificación de un reporte', () => {
  let fixture: ComponentFixture<TablaDeVerificacion>;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [...proveerApiSimulado(), ...proveerSesionFalsa({ autoridades: ['report.write'] })],
    });
    fixture = TestBed.createComponent(TablaDeVerificacion);
    http = TestBed.inject(HttpTestingController);
    fixture.componentRef.setInput('idReporte', ID_REPORTE);
    fixture.componentRef.setInput('idEquipoCliente', ID_EQUIPO);
    fixture.componentRef.setInput('editable', true);
  });

  afterEach(() => http.verify());

  const raiz = () => fixture.nativeElement as HTMLElement;
  const texto = () => raiz().textContent ?? '';
  const filas = () => [...raiz().querySelectorAll('tbody tr')];
  const patron = (i: number) => raiz().querySelector<HTMLInputElement>(`#patron-${i}`)!;
  const equipo = (i: number) => raiz().querySelector<HTMLInputElement>(`#equipo-${i}`)!;

  /**
   * Responde a los cuatro eslabones de la cadena del catálogo.
   *
   * <p>Cuatro consultas para saber cómo se verifica un equipo: unidad → modelo → equipo del catálogo →
   * tipo. No es un exceso de esta pantalla, es la forma de la cadena — el backend camina la misma.
   */
  async function abrir(
    lecturas: readonly LecturaDeVerificacion[] = [],
    tipos: readonly object[] = TIPOS,
  ): Promise<void> {
    fixture.componentRef.setInput('lecturas', lecturas);
    fixture.detectChanges();
    await asentar(fixture);

    http.match(URL_EQUIPOS_DE_CLIENTE).forEach((p) => p.flush(EQUIPOS));
    http.match(URL_MODELOS).forEach((p) => p.flush(MODELOS));
    http.match(URL_EQUIPOS_DE_CATALOGO).forEach((p) => p.flush(EQUIPOS_DE_CATALOGO));
    http.match(URL_TIPOS).forEach((p) => p.flush(tipos));
    http.match(URL_PATRONES).forEach((p) => p.flush(PATRONES));
    await asentar(fixture);
  }

  function escribir(control: HTMLInputElement, valor: string): void {
    control.value = valor;
    control.dispatchEvent(new Event('input'));
    fixture.detectChanges();
  }

  function pulsar(etiqueta: string): void {
    const boton = [...raiz().querySelectorAll('button')].find((b) =>
      (b.textContent ?? '').includes(etiqueta),
    );

    if (!boton) {
      throw new Error(`No hay ningun boton que diga "${etiqueta}"`);
    }

    boton.click();
    fixture.detectChanges();
  }

  it('la tabla la dicta el tipo: un punto por tres lecturas son tres casillas', async () => {
    // Ni una más ni una menos. Una lectura en un punto que el tipo no declara, o la número cuatro
    // donde se piden tres, las rechaza el servidor: la pantalla no las ofrece.
    await abrir();

    expect(filas()).toHaveLength(3);
    // La unidad va en la cabecera del bloque, una sola vez, no repetida en cada fila.
    expect(texto()).toContain('Presión (mmHg)');
    expect(texto()).toContain('3 lecturas por punto');
  });

  it('prellena lo que ya se midió', async () => {
    await abrir([
      {
        id: 'd1',
        idVerificacion: ID_VERIFICACION,
        idPuntoVerificacion: ID_PUNTO,
        secuencia: 2,
        valorPatron: 100,
        valorEquipo: 99.4,
        unidad: 'mmHg',
      },
    ]);

    expect(patron(1).value).toBe('100');
    expect(equipo(1).value).toBe('99.4');
    // Las otras dos siguen vacías: la tabla no se inventa lo que nadie midió.
    expect(patron(0).value).toBe('');
  });

  it('guardar manda la verificacion, el punto y el número de cada lectura, y no la unidad', async () => {
    // La unidad la pone el servidor desde la verificacion. Mandarla permitiría escribir «°C» en una
    // verificacion medida en mmHg, y el reporte saldría impreso con una unidad que nadie midió.
    await abrir();
    escribir(patron(0), '100');
    escribir(equipo(0), '101.2');

    pulsar('Guardar verificación');
    await asentar(fixture);

    const peticion = http.expectOne(urlVerificacion(ID_REPORTE));
    expect(peticion.request.method).toBe('PATCH');
    expect(peticion.request.body.lecturas).toEqual([
      {
        idVerificacion: ID_VERIFICACION,
        idPuntoVerificacion: ID_PUNTO,
        secuencia: 1,
        valorPatron: 100,
        valorEquipo: 101.2,
      },
    ]);
    peticion.flush({ id: ID_REPORTE, lecturas: [] });
    await asentar(fixture);
  });

  it('una casilla a medias no se manda: el servidor exige las dos lecturas', async () => {
    await abrir();
    escribir(patron(0), '100');
    escribir(equipo(1), '99');

    const guardar = [...raiz().querySelectorAll('button')].find((b) =>
      (b.textContent ?? '').includes('Guardar verificación'),
    )!;
    expect(guardar.disabled).toBe(true);
  });

  it('sin ninguna casilla completa no hay nada que guardar', async () => {
    await abrir();

    const guardar = [...raiz().querySelectorAll('button')].find((b) =>
      (b.textContent ?? '').includes('Guardar verificación'),
    )!;
    expect(guardar.disabled).toBe(true);
  });

  it('un tipo que no se verifica no tiene tabla, y lo dice con su nombre', async () => {
    await abrir([], [{ ...TIPOS[0], verificaciones: [], verificable: false }]);

    expect(texto()).toContain('no se les hace verificación metrológica');
    expect(filas()).toHaveLength(0);
  });

  it('con patrón y equipo variables no hay puntos, no se escribe unidad, y se añaden lecturas', async () => {
    await abrir([], [{ ...TIPOS[0], verificaciones: [VARIABLE] }]);

    expect(filas()).toHaveLength(1);
    expect(texto()).toContain('Patrón y equipo variables');
    // Sin puntos no hay cantidad fijada: cuantas tomar lo decide quien mide.
    expect(texto()).not.toContain('lecturas por punto');
    // Y la unidad YA NO SE TECLEA ni en este caso: la declara la verificacion. Era el unico sitio
    // donde venia de fuera, y permitia imprimir una unidad que nadie midio.
    expect(raiz().querySelector('#unidad-0')).toBeNull();
    expect(texto()).toContain('Presión (mmHg)');

    pulsar('Añadir lectura');
    expect(filas()).toHaveLength(2);
  });

  it('un termohigrometro tiene una tabla por magnitud, cada una con su unidad', async () => {
    // Es el caso que forzo el cambio del 2026-10-03: antes habia una tabla y una modalidad para todo
    // el aparato, de modo que esto habia que registrarlo como dos tipos de equipo.
    await abrir([], [{ ...TIPOS[0], verificaciones: [...TIPOS[0].verificaciones, DE_TEMPERATURA] }]);

    expect(texto()).toContain('Presión (mmHg)');
    expect(texto()).toContain('Temperatura (°C)');
    // 1 punto x 3 lecturas de presion + 1 punto x 1 lectura de temperatura.
    expect(filas()).toHaveLength(4);
    expect(raiz().querySelectorAll('table')).toHaveLength(2);
  });

  it('las lecturas de una magnitud no se cuelan en la tabla de la otra', async () => {
    // Con dos verificaciones variables, las dos dejan el punto nulo: sin casar por verificacion, la
    // lectura de presion habria prellenado la casilla de temperatura.
    await abrir(
      [
        {
          id: 'd1',
          idVerificacion: ID_VERIFICACION,
          secuencia: 1,
          valorPatron: 1,
          valorEquipo: 1.1,
          unidad: 'mmHg',
        },
      ],
      [{ ...TIPOS[0], verificaciones: [VARIABLE, { ...DE_TEMPERATURA, modalidad: 'PATRON_EQUIPO_VARIABLE', cantidadDatos: undefined, puntos: [] }] }],
    );

    expect(patron(0).value).toBe('1');
    // La de temperatura sigue vacia: no es la misma medida aunque las dos vayan sin punto.
    expect(patron(1).value).toBe('');
  });

  it('con modalidad variable la lectura viaja sin punto, pero con su verificacion', async () => {
    // La verificacion es obligatoria justamente aqui: sin punto, es lo unico que dice que se midio.
    await abrir([], [{ ...TIPOS[0], verificaciones: [VARIABLE] }]);
    escribir(patron(0), '1');
    escribir(equipo(0), '1.1');

    pulsar('Guardar verificación');
    await asentar(fixture);

    const peticion = http.expectOne(urlVerificacion(ID_REPORTE));
    expect(peticion.request.body.lecturas).toEqual([
      {
        idVerificacion: ID_VERIFICACION,
        idPuntoVerificacion: undefined,
        secuencia: 1,
        valorPatron: 1,
        valorEquipo: 1.1,
      },
    ]);
    peticion.flush({ id: ID_REPORTE, lecturas: [] });
    await asentar(fixture);
  });

  it('un reporte que no se puede editar enseña las lecturas y no deja tocarlas', async () => {
    fixture.componentRef.setInput('editable', false);
    await abrir([
      {
        id: 'd1',
        idVerificacion: ID_VERIFICACION,
        idPuntoVerificacion: ID_PUNTO,
        secuencia: 1,
        valorPatron: 100,
        valorEquipo: 101,
        unidad: 'mmHg',
      },
    ]);

    expect(patron(0).value).toBe('100');
    expect(patron(0).disabled).toBe(true);
    expect(
      [...raiz().querySelectorAll('button')].map((b) => (b.textContent ?? '').trim()),
    ).toEqual([]);
  });

  it('si la cadena del catálogo no llega, lo dice sin confundirlo con «no se verifica»', async () => {
    // Son dos cosas distintas: que el tipo no exija verificación, y que no se haya podido averiguar
    // cuál es su tipo. La segunda es un fallo que hay que ver, no una característica del equipo.
    await abrir([], []);

    expect(texto()).toContain('No se pudo averiguar el tipo');
    expect(texto()).not.toContain('no se les hace verificación');
  });

  it('no consulta el tipo dos veces por casilla: las cuatro listas van en caché', async () => {
    await abrir();

    // Cuatro consultas para toda la tabla, no cuatro por fila. Si alguna se pidiera por casilla, esto
    // encontraria peticiones pendientes y el verify() del final fallaria.
    expect(http.match(URL_TIPOS)).toHaveLength(0);
    expect(http.match(URL_MODELOS)).toHaveLength(0);
  });

  describe('Los patrones de cada verificación (V30), el error y la conformidad (2026-10-14)', () => {
    /** El tipo de ejemplo con tolerancia: ±0,5 mmHg. */
    const conTolerancia = (tolerancia: number | undefined) => [
      { ...TIPOS[0], verificaciones: [{ ...TIPOS[0].verificaciones[0], tolerancia }] },
    ];

    /** Un patrón puesto en una verificación, como lo copió el reporte. */
    const usado = (idVerificacion: string, extra: object = {}) => ({
      idVerificacion,
      id: ID_PATRON,
      nombre: 'Juego de pesas clase F1',
      marca: 'Kern',
      serie: 'KF1-2024-118',
      certificado: 'CAL-2026-0412',
      vencimiento: '2099-04-30',
      ...extra,
    });
    const VENCIDO = {
      id: 'pat2',
      nombre: 'Termómetro vencido',
      marca: undefined,
      serie: 'T-9',
      certificado: 'CAL-2010-1',
      vencimiento: '2000-01-31',
    };
    const RUTA_PATRONES = `${URL_REPORTES}/${ID_REPORTE}/verifications/${ID_VERIFICACION}/reference-standards`;

    const desplegable = (idVerificacion = ID_VERIFICACION) =>
      raiz().querySelector<HTMLSelectElement>(`#patron-nuevo-${idVerificacion}`);
    const opciones = (idVerificacion = ID_VERIFICACION) =>
      [...(desplegable(idVerificacion)?.querySelectorAll('option') ?? [])].map((o) =>
        (o.textContent ?? '').trim(),
      );
    const bloque = (idVerificacion = ID_VERIFICACION) =>
      (raiz().querySelector(`[data-patrones="${idVerificacion}"]`)?.textContent ?? '').replace(/\s+/g, ' ');
    const celda = (atributo: string, fila: number) =>
      (filas()[fila].querySelector(`[${atributo}]`)?.textContent ?? '').trim();

    it('ofrece solo los patrones vigentes y sin retirar', async () => {
      await abrir();

      expect(opciones()).toEqual([
        'Elija un patrón vigente',
        'Juego de pesas clase F1 · serie KF1-2024-118',
      ]);
    });

    it('elegir uno lo pone en su verificación al momento, y el desplegable vuelve a empezar', async () => {
      await abrir();

      desplegable()!.value = ID_PATRON;
      desplegable()!.dispatchEvent(new Event('change'));
      await asentar(fixture);

      const peticion = http.expectOne(RUTA_PATRONES);
      expect(peticion.request.method).toBe('POST');
      expect(peticion.request.body).toEqual({ idEquipoPatron: ID_PATRON });
      expect(desplegable()!.value).toBe('');
      peticion.flush({ id: ID_REPORTE, lecturas: [], patrones: [usado(ID_VERIFICACION)] });
      await asentar(fixture);
    });

    it('el que ya está en la verificación no se ofrece otra vez', async () => {
      fixture.componentRef.setInput('patrones', [usado(ID_VERIFICACION)]);
      await abrir();

      expect(opciones()).toEqual(['No quedan patrones vigentes por añadir']);
    });

    it('el puesto se ve con su certificado y su vencimiento, y quitarlo va por su ruta', async () => {
      fixture.componentRef.setInput('patrones', [usado(ID_VERIFICACION)]);
      await abrir();

      expect(bloque()).toContain('Juego de pesas clase F1 · Kern · serie KF1-2024-118');
      expect(bloque()).toContain('certificado CAL-2026-0412 · vence el 30 abr 2099');
      expect(texto()).not.toContain('vencido el reporte no se puede cerrar');

      [...raiz().querySelectorAll('button')]
        .find((b) => b.getAttribute('aria-label') === 'Quitar Juego de pesas clase F1 de Presión')!
        .click();
      await asentar(fixture);

      const peticion = http.expectOne(`${RUTA_PATRONES}/${ID_PATRON}`);
      expect(peticion.request.method).toBe('DELETE');
      peticion.flush(null, { status: 204, statusText: 'No Content' });
      await asentar(fixture);
    });

    it('uno vencido se ve vencido, y avisa de que así no se cierra', async () => {
      fixture.componentRef.setInput('patrones', [usado(ID_VERIFICACION, VENCIDO)]);
      await abrir();

      expect(bloque()).toContain('venció el');
      expect(texto()).toContain(
        'Con el certificado de Termómetro vencido vencido el reporte no se puede cerrar',
      );
    });

    it('con lecturas guardadas y sin patrón avisa de que hace falta uno; sin lecturas, no', async () => {
      const lecturas = [
        {
          id: 'd1',
          idVerificacion: ID_VERIFICACION,
          idPuntoVerificacion: ID_PUNTO,
          secuencia: 1,
          valorPatron: 100,
          valorEquipo: 100.3,
          unidad: 'mmHg',
        },
      ];
      await abrir(lecturas);
      expect(bloque()).toContain('necesitan al menos un patrón');

      fixture.componentRef.setInput('lecturas', []);
      await asentar(fixture);
      expect(bloque()).not.toContain('necesitan al menos un patrón');
    });

    it('cada verificación tiene sus propios patrones', async () => {
      const verificacion = TIPOS[0].verificaciones[0];
      const conDos = [
        {
          ...TIPOS[0],
          verificaciones: [
            verificacion,
            { ...verificacion, id: 'ver2', magnitud: 'Temperatura', unidad: '°C' },
          ],
        },
      ];
      fixture.componentRef.setInput('patrones', [usado('ver2')]);
      await abrir([], conDos);

      // Lo puesto, no lo ofrecido: el desplegable de la primera sí lo ofrece, porque ella no lo tiene.
      const puestosEn = (id: string) =>
        raiz().querySelectorAll(`[data-patrones="${id}"] [data-patron-usado]`).length;
      expect(puestosEn(ID_VERIFICACION)).toBe(0);
      expect(puestosEn('ver2')).toBe(1);
      expect(opciones()).toContain('Juego de pesas clase F1 · serie KF1-2024-118');
      expect(bloque('ver2')).toContain('Patrones de Temperatura');
      expect(bloque('ver2')).toContain('Juego de pesas clase F1');
      expect(opciones('ver2')).toEqual(['No quedan patrones vigentes por añadir']);
    });

    it('dice el error y si es conforme mientras se escribe, con el borde dentro', async () => {
      // 50,7 − 50,2 en coma flotante es 0,5000000000000071: con una comparacion ingenua el borde
      // saldria «No». El servidor cuenta en decimal y diria «Sí».
      await abrir([], conTolerancia(0.5));
      expect(texto()).toContain('tolerancia ±0,5 mmHg');

      escribir(patron(0), '50.2');
      escribir(equipo(0), '50.7');
      expect(celda('data-error', 0)).toBe('+0,5');
      expect(celda('data-conforme', 0)).toBe('Sí');

      escribir(equipo(0), '50.7001');
      expect(celda('data-conforme', 0)).toBe('No');

      escribir(equipo(0), '49.6');
      expect(celda('data-error', 0)).toBe('-0,6');
      expect(celda('data-conforme', 0)).toBe('No');
    });

    it('sin los dos valores no hay error, y sin tolerancia no se dice si es conforme', async () => {
      await abrir([], conTolerancia(undefined));
      expect(texto()).toContain('sin tolerancia');

      escribir(patron(0), '100');
      expect(celda('data-error', 0)).toBe('—');

      escribir(equipo(0), '140');
      expect(celda('data-error', 0)).toBe('+40');
      expect(celda('data-conforme', 0)).toBe('—');
    });

    it('un reporte cerrado se juzga con la tolerancia guardada en la lectura, no con la de hoy', async () => {
      // El tipo dice ±0,5 hoy, pero la lectura se tomo con ±0,2: con 0,3 de error fue no conforme y lo
      // sigue siendo.
      fixture.componentRef.setInput('editable', false);
      await abrir(
        [
          {
            id: 'd1',
            idVerificacion: ID_VERIFICACION,
            idPuntoVerificacion: ID_PUNTO,
            secuencia: 1,
            valorPatron: 100,
            valorEquipo: 100.3,
            unidad: 'mmHg',
            tolerancia: 0.2,
          },
        ],
        conTolerancia(0.5),
      );

      expect(celda('data-conforme', 0)).toBe('No');
      expect(desplegable()).toBeNull();
      expect(raiz().querySelector('[aria-label^="Quitar"]')).toBeNull();
      expect(texto()).toContain('No se registró contra qué patrón se midió');
    });
  });
});
