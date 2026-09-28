import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';
import { proveerApiSimulado } from '../../../../testing/entorno';
import { proveerSesionFalsa } from '../../../../testing/keycloak-falso';
import { asentar } from '../../../../testing/pantalla';
import { EQUIPOS, ID_EQUIPO } from '../../../../testing/ordenes';
import {
  EQUIPOS as EQUIPOS_DE_CATALOGO,
  ID_TIPO,
  MODELOS,
  TIPOS,
  URL_EQUIPOS as URL_EQUIPOS_DE_CATALOGO,
  URL_MODELOS,
  URL_TIPOS,
} from '../../../../testing/catalogo';
import { ID_REPORTE, urlVerificacion } from '../../../../testing/reportes';
import { LecturaDeVerificacion } from '../../../core/api/tipos';
import { TablaDeVerificacion } from './tabla-de-verificacion';

const URL_EQUIPOS_DE_CLIENTE = 'http://localhost:8081/v1/api/client-equipments';

/** El punto que el tipo de ejemplo declara: 100 mmHg, con tres lecturas. */
const ID_PUNTO = 'pv1';

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
    expect(texto()).toContain('100 mmHg');
    expect(texto()).toContain('3 lecturas por punto');
  });

  it('prellena lo que ya se midió', async () => {
    await abrir([
      { id: 'd1', idPuntoVerificacion: ID_PUNTO, secuencia: 2, valorPatron: 100, valorEquipo: 99.4, unidad: 'mmHg' },
    ]);

    expect(patron(1).value).toBe('100');
    expect(equipo(1).value).toBe('99.4');
    // Las otras dos siguen vacías: la tabla no se inventa lo que nadie midió.
    expect(patron(0).value).toBe('');
  });

  it('guardar manda el punto y el número de cada lectura, y no la unidad', async () => {
    // La unidad la pone el servidor desde el punto. Mandarla permitiría escribir «°C» en un punto
    // medido en mmHg, y el reporte saldría impreso con una unidad que nadie midió.
    await abrir();
    escribir(patron(0), '100');
    escribir(equipo(0), '101.2');

    pulsar('Guardar verificación');
    await asentar(fixture);

    const peticion = http.expectOne(urlVerificacion(ID_REPORTE));
    expect(peticion.request.method).toBe('PATCH');
    expect(peticion.request.body.lecturas).toEqual([
      {
        idPuntoVerificacion: ID_PUNTO,
        secuencia: 1,
        valorPatron: 100,
        valorEquipo: 101.2,
        unidadSinPunto: undefined,
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
    await abrir([], [{ ...TIPOS[0], modalidadVerificacion: undefined, cantidadDatos: undefined, puntosVerificacion: [], verificable: false }]);

    expect(texto()).toContain('no se les hace verificación metrológica');
    expect(filas()).toHaveLength(0);
  });

  it('con patrón y equipo variables no hay puntos, la unidad se escribe y se añaden lecturas', async () => {
    await abrir([], [
      {
        ...TIPOS[0],
        modalidadVerificacion: 'PATRON_EQUIPO_VARIABLE',
        cantidadDatos: undefined,
        puntosVerificacion: [],
      },
    ]);

    expect(filas()).toHaveLength(1);
    expect(texto()).toContain('Patrón y equipo variables');
    // Sin puntos no hay cantidad fijada: cuantas tomar lo decide quien mide.
    expect(texto()).not.toContain('lecturas por punto');
    expect(raiz().querySelector('#unidad-0')).not.toBeNull();

    pulsar('Añadir lectura');
    expect(filas()).toHaveLength(2);
  });

  it('con modalidad variable la lectura viaja sin punto y con su unidad', async () => {
    await abrir([], [
      {
        ...TIPOS[0],
        modalidadVerificacion: 'PATRON_EQUIPO_VARIABLE',
        cantidadDatos: undefined,
        puntosVerificacion: [],
      },
    ]);
    escribir(patron(0), '1');
    escribir(equipo(0), '1.1');
    escribir(raiz().querySelector<HTMLInputElement>('#unidad-0')!, 'mA');

    pulsar('Guardar verificación');
    await asentar(fixture);

    const peticion = http.expectOne(urlVerificacion(ID_REPORTE));
    expect(peticion.request.body.lecturas).toEqual([
      {
        idPuntoVerificacion: undefined,
        secuencia: 1,
        valorPatron: 1,
        valorEquipo: 1.1,
        unidadSinPunto: 'mA',
      },
    ]);
    peticion.flush({ id: ID_REPORTE, lecturas: [] });
    await asentar(fixture);
  });

  it('un reporte que no se puede editar enseña las lecturas y no deja tocarlas', async () => {
    fixture.componentRef.setInput('editable', false);
    await abrir([
      { id: 'd1', idPuntoVerificacion: ID_PUNTO, secuencia: 1, valorPatron: 100, valorEquipo: 101, unidad: 'mmHg' },
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
});
