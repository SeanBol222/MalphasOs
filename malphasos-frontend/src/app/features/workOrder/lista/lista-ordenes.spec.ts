import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { proveerApiSimulado } from '../../../../testing/entorno';
import { proveerSesionFalsa } from '../../../../testing/keycloak-falso';
import { asentar, responderA } from '../../../../testing/pantalla';
import {
  CLIENTES,
  ID_CLIENTE,
  ID_INGENIERO,
  ID_SEDE,
  orden,
  PERSONAS,
  SEDE,
  URL_CLIENTES,
  URL_ORDENES,
  URL_PERSONAS,
  urlSede,
} from '../../../../testing/ordenes';
import { URL_REPORTES } from '../../../../testing/reportes';
import { ListaOrdenes } from './lista-ordenes';

const URL_AVANCE = `${URL_REPORTES}/progress`;
const TODAS = ['work-order.read', 'work-order.write', 'person.read', 'report.read'];

/** Dos equipos, para que el avance tenga de qué contar. */
const DOS_EQUIPOS = [
  { idEquipoCliente: 'ec1', idReporteServicio: 'r1' },
  { idEquipoCliente: 'ec2', idReporteServicio: 'r2' },
];

describe('Listado de órdenes de trabajo', () => {
  let fixture: ComponentFixture<ListaOrdenes>;
  let http: HttpTestingController;

  function montar(autoridades: readonly string[] = TODAS): void {
    TestBed.configureTestingModule({
      providers: [
        ...proveerApiSimulado(),
        ...proveerSesionFalsa({ autoridades }),
        provideRouter([]),
      ],
    });
    fixture = TestBed.createComponent(ListaOrdenes);
    http = TestBed.inject(HttpTestingController);
  }

  beforeEach(() => montar());
  afterEach(() => http.verify());

  const raiz = () => fixture.nativeElement as HTMLElement;
  const texto = () => raiz().textContent ?? '';
  const filas = () => [...raiz().querySelectorAll('tbody tr')];
  const celda = (fila: Element, atributo: string) =>
    (fila.querySelector(`[${atributo}]`)?.textContent ?? '').replace(/\s+/g, ' ').trim();

  async function abrir(ordenes: object[], avance: object[] = []): Promise<void> {
    fixture.detectChanges();
    await responderA(fixture, http, URL_ORDENES, ordenes);
    http.match(URL_CLIENTES).forEach((p) => p.flush(CLIENTES));
    http.match(URL_PERSONAS).forEach((p) => p.flush(PERSONAS));
    http.match(URL_AVANCE).forEach((p) => p.flush(avance));
    await asentar(fixture);
    http.match(urlSede(ID_SEDE)).forEach((p) => p.flush(SEDE));
    await asentar(fixture);
  }

  function pulsar(etiqueta: string): void {
    [...raiz().querySelectorAll('button')]
      .find((b) => (b.textContent ?? '').trim().startsWith(etiqueta))!
      .click();
    fixture.detectChanges();
  }

  function elegir(id: string, valor: string): void {
    const selector = raiz().querySelector<HTMLSelectElement>(`#${id}`)!;
    selector.value = valor;
    selector.dispatchEvent(new Event('change'));
    fixture.detectChanges();
  }

  it('pinta cada orden con la fecha, el día, el cliente y la sede en palabras', async () => {
    await abrir([orden()]);

    const fila = filas()[0].textContent ?? '';
    expect(fila).toContain('15 oct 2026');
    expect(fila).toContain('jueves');
    expect(fila).toContain('Hospital Central');
    expect(fila).toContain('Sede Norte');
    expect(fila).toContain('Preventivo');
    expect(fila).toContain('Trimestral');
  });

  it('abre en «Por hacer»: las creadas y las que están en ejecución, de la más próxima a la más lejana', async () => {
    await abrir([
      orden({ id: 'o3', fechaMantenimiento: '2026-10-20' }),
      orden({ id: 'o1', fechaMantenimiento: '2026-10-10', estadoEjecucion: 'EN_EJECUCION' }),
      orden({ id: 'o2', fechaMantenimiento: '2026-10-01', estadoEjecucion: 'EJECUTADA' }),
      orden({ id: 'o4', fechaMantenimiento: '2026-10-05', estadoActivo: false }),
    ]);

    expect(filas().map((f) => f.getAttribute('data-orden'))).toEqual(['o1', 'o3']);
    expect(texto()).toContain('Por hacer · 2');
    expect(texto()).toContain('Ejecutadas · 1');
    expect(texto()).toContain('Anuladas · 1');
    expect(texto()).toContain('Todas · 4');
  });

  it('las hechas van de la más reciente hacia atrás', async () => {
    await abrir([
      orden({ id: 'o1', fechaMantenimiento: '2026-09-01', estadoEjecucion: 'EJECUTADA' }),
      orden({ id: 'o2', fechaMantenimiento: '2026-10-01', estadoEjecucion: 'EJECUTADA' }),
    ]);

    pulsar('Ejecutadas');

    expect(filas().map((f) => f.getAttribute('data-orden'))).toEqual(['o2', 'o1']);
  });

  it('una anulada solo sale en «Anuladas» y en «Todas», y dice que está anulada', async () => {
    await abrir([orden({ id: 'o1', estadoActivo: false, estadoEjecucion: 'EN_EJECUCION' })]);

    expect(filas()).toHaveLength(0);
    expect(texto()).toContain('No hay órdenes que coincidan');

    pulsar('Anuladas');
    expect(celda(filas()[0], 'data-estado')).toBe('Anulada');
  });

  it('una anulada que ya se había ejecutado cuenta como anulada, no como ejecutada', async () => {
    await abrir([orden({ id: 'o1', estadoActivo: false, estadoEjecucion: 'EJECUTADA' })]);

    expect(texto()).toContain('Ejecutadas · 0');
    expect(texto()).toContain('Anuladas · 1');
  });

  it('nunca dice más cerrados que equipos: un equipo quitado de la orden no cuenta', async () => {
    await abrir(
      [orden({ id: 'o1', estadoEjecucion: 'EN_EJECUCION', equipos: DOS_EQUIPOS })],
      [{ idOrdenTrabajo: 'o1', cerrados: 3, abiertos: 0 }],
    );

    expect(filas()[0].querySelector('[data-reportes]')!.textContent).toContain('2 de 2 cerrados');
  });

  it('el avance cuenta los cerrados contra los equipos de la orden', async () => {
    await abrir(
      [orden({ id: 'o1', estadoEjecucion: 'EN_EJECUCION', equipos: DOS_EQUIPOS })],
      [{ idOrdenTrabajo: 'o1', cerrados: 1, abiertos: 1 }],
    );

    expect(celda(filas()[0], 'data-reportes')).toBe('1 de 2 cerrados');
    const barra = filas()[0].querySelector<HTMLElement>('[data-reportes] .bg-ink')!;
    expect(barra.style.width).toBe('50%');
  });

  it('una orden en ejecución sin reportes es cero de N, no «sin datos»', async () => {
    await abrir([orden({ id: 'o1', estadoEjecucion: 'EN_EJECUCION', equipos: DOS_EQUIPOS })]);

    expect(celda(filas()[0], 'data-reportes')).toBe('0 de 2 cerrados');
  });

  it('una creada dice que no ha empezado, y una sin equipos lo dice con peso', async () => {
    await abrir([
      orden({ id: 'o1', fechaMantenimiento: '2026-10-01', equipos: DOS_EQUIPOS }),
      orden({ id: 'o2', fechaMantenimiento: '2026-10-02' }),
    ]);

    expect(celda(filas()[0], 'data-reportes')).toBe('Sin empezar · 2 equipos');
    expect(filas()[0].querySelector('[data-reportes] .bg-ink')).toBeNull();
    expect(celda(filas()[1], 'data-reportes')).toBe('Sin equipos');
    expect(filas()[1].querySelector('[data-reportes] span')!.className).toContain('font-bold');
  });

  it('el estado se distingue por peso tipográfico y no por color', async () => {
    await abrir([
      orden({ id: 'o1', fechaMantenimiento: '2026-10-01', estadoEjecucion: 'EN_EJECUCION' }),
      orden({ id: 'o2', fechaMantenimiento: '2026-10-02' }),
    ]);

    const [enEjecucion, creada] = filas().map((f) => f.querySelector('[data-estado]')!);
    expect(enEjecucion.textContent).toContain('En ejecución');
    expect(enEjecucion.className).toContain('font-extrabold');
    expect(creada.textContent).toContain('Creada');
    expect(creada.className).toContain('font-bold');
    expect(filas().every((f) => !f.innerHTML.includes('text-accent'))).toBe(true);
  });

  it('dice quién la hace, y «Sin asignar» pesa', async () => {
    await abrir([
      orden({ id: 'o1', fechaMantenimiento: '2026-10-01', idIngeniero: ID_INGENIERO }),
      orden({ id: 'o2', fechaMantenimiento: '2026-10-02' }),
    ]);

    expect(celda(filas()[0], 'data-ingeniero')).toBe('Ana Ruiz');
    expect(filas()[0].querySelector('[data-ingeniero]')!.className).not.toContain('font-bold');
    expect(celda(filas()[1], 'data-ingeniero')).toBe('Sin asignar');
    expect(filas()[1].querySelector('[data-ingeniero]')!.className).toContain('font-bold');
  });

  it('filtra por ingeniero, y «Sin asignar» es un valor del filtro', async () => {
    await abrir([
      orden({ id: 'o1', fechaMantenimiento: '2026-10-01', idIngeniero: ID_INGENIERO }),
      orden({ id: 'o2', fechaMantenimiento: '2026-10-02' }),
    ]);

    elegir('filtroIngeniero', ID_INGENIERO);
    expect(filas().map((f) => f.getAttribute('data-orden'))).toEqual(['o1']);
    // Los recuentos siguen al filtro: dicen lo que se veria al pulsar cada boton.
    expect(texto()).toContain('Por hacer · 1');

    elegir('filtroIngeniero', 'sin-asignar');
    expect(filas().map((f) => f.getAttribute('data-orden'))).toEqual(['o2']);
  });

  it('dos ingenieros con el mismo nombre se distinguen por la cédula en el filtro', async () => {
    const homonimo = { ...PERSONAS[0], identificador: 'p9', cedula: '999' };
    fixture.detectChanges();
    await responderA(fixture, http, URL_ORDENES, [
      orden({ id: 'o1', fechaMantenimiento: '2026-10-01', idIngeniero: ID_INGENIERO }),
      orden({ id: 'o2', fechaMantenimiento: '2026-10-02', idIngeniero: 'p9' }),
    ]);
    http.match(URL_CLIENTES).forEach((p) => p.flush(CLIENTES));
    http.match(URL_PERSONAS).forEach((p) => p.flush([...PERSONAS, homonimo]));
    http.match(URL_AVANCE).forEach((p) => p.flush([]));
    await asentar(fixture);
    http.match(urlSede(ID_SEDE)).forEach((p) => p.flush(SEDE));
    await asentar(fixture);

    const opciones = [...raiz().querySelectorAll('#filtroIngeniero option')].map((o) =>
      (o.textContent ?? '').trim(),
    );
    expect(opciones).toEqual(['Todos', 'Sin asignar', 'Ana Ruiz · 123', 'Ana Ruiz · 999']);
  });

  it('filtra por cliente, y solo ofrece los que tienen órdenes', async () => {
    await abrir([
      orden({ id: 'o1', fechaMantenimiento: '2026-10-01' }),
      { ...orden({ id: 'o2', fechaMantenimiento: '2026-10-02' }), idCliente: 'otro' },
    ]);

    const opciones = [...raiz().querySelectorAll('#filtroCliente option')].map((o) =>
      (o.textContent ?? '').trim(),
    );
    // Por nombre; un cliente que no se pudo resolver se ofrece igual, para poder filtrar su orden.
    expect(opciones).toEqual(['Todos', 'Hospital Central', 'No disponible']);

    elegir('filtroCliente', ID_CLIENTE);
    expect(filas().map((f) => f.getAttribute('data-orden'))).toEqual(['o1']);
  });

  it('busca por cliente o por sede, sin tildes ni mayúsculas', async () => {
    await abrir([orden({ id: 'o1' })]);
    const buscador = raiz().querySelector<HTMLInputElement>('#buscarOrdenes')!;

    buscador.value = 'sede norte';
    buscador.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    expect(filas()).toHaveLength(1);

    buscador.value = 'clinica';
    buscador.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    expect(filas()).toHaveLength(0);
  });

  it('sin person.read no pide personas ni pinta la columna del ingeniero', async () => {
    TestBed.resetTestingModule();
    montar(['work-order.read', 'report.read']);
    fixture.detectChanges();
    // Se comprueba antes de responder nada: el ayudante drena las listas y lo ocultaria.
    expect(http.match(URL_PERSONAS)).toHaveLength(0);
    await abrir([orden()]);

    expect(raiz().querySelector('[data-ingeniero]')).toBeNull();
    expect(raiz().querySelector('#filtroIngeniero')).toBeNull();
  });

  it('sin report.read no pide el avance', async () => {
    TestBed.resetTestingModule();
    montar(['work-order.read']);
    fixture.detectChanges();

    expect(http.match(URL_AVANCE)).toHaveLength(0);
    await abrir([orden()]);
  });

  it('sin órdenes explica cómo se programa una', async () => {
    await abrir([]);

    expect(raiz().querySelector('table')).toBeNull();
    expect(texto()).toContain('se programa para un cliente y una de sus sedes');
  });

  it('sin work-order.write no se ofrece programar', async () => {
    TestBed.resetTestingModule();
    montar(['work-order.read']);
    await abrir([orden()]);

    expect([...raiz().querySelectorAll('a')].map((a) => a.textContent?.trim())).not.toContain(
      'Programar orden',
    );
  });

  describe('Lo que falta por aprobar (V54)', () => {
    const URL_POR_APROBAR = `${URL_ORDENES}/pending-approval`;
    const EJECUTADAS = [
      orden({ id: 'o1', fechaMantenimiento: '2026-11-03', estadoEjecucion: 'EJECUTADA' }),
      orden({ id: 'o2', fechaMantenimiento: '2026-11-04', estadoEjecucion: 'EJECUTADA' }),
      orden({ id: 'o3', fechaMantenimiento: '2026-11-05' }),
    ];

    async function abrirConAprobacion(pendientes: object[]): Promise<void> {
      TestBed.resetTestingModule();
      montar([...TODAS, 'work-order.approve']);
      fixture.detectChanges();
      await responderA(fixture, http, URL_POR_APROBAR, pendientes);
      await abrir(EJECUTADAS);
    }

    it('«Por aprobar» filtra las ejecutadas que el servidor da por pendientes', async () => {
      await abrirConAprobacion([EJECUTADAS[1]]);

      expect(texto()).toContain('Por aprobar · 1');
      pulsar('Por aprobar');
      expect(filas().map((f) => f.getAttribute('data-orden'))).toEqual(['o2']);
    });

    it('una ejecutada dice si está aprobada, y lo pendiente pesa', async () => {
      await abrirConAprobacion([EJECUTADAS[1]]);
      pulsar('Ejecutadas');

      const [o2, o1] = filas();
      expect(o2.getAttribute('data-orden')).toBe('o2');
      expect(celda(o2, 'data-aprobacion')).toBe('Por aprobar');
      expect(o2.querySelector('[data-aprobacion]')!.className).toContain('font-bold');
      expect(celda(o1, 'data-aprobacion')).toBe('Aprobada');
      expect(o1.querySelector('[data-aprobacion]')!.className).not.toContain('font-bold');
    });

    it('las del historial, ejecutadas antes de la aprobación, ni cuentan por aprobar ni se dicen aprobadas', async () => {
      const vieja = orden({
        id: 'vieja',
        fechaMantenimiento: '2026-10-01',
        estadoEjecucion: 'EJECUTADA',
      });
      TestBed.resetTestingModule();
      montar([...TODAS, 'work-order.approve']);
      fixture.detectChanges();
      await responderA(fixture, http, URL_POR_APROBAR, [EJECUTADAS[1], vieja]);
      await abrir([...EJECUTADAS, vieja]);

      expect(texto()).toContain('Por aprobar · 1');
      pulsar('Ejecutadas');
      const historica = filas().find((f) => f.getAttribute('data-orden') === 'vieja')!;
      expect(historica.querySelector('[data-aprobacion]')).toBeNull();
    });

    it('una orden sin ejecutar no dice nada de aprobación', async () => {
      await abrirConAprobacion([]);

      expect(filas().map((f) => f.getAttribute('data-orden'))).toEqual(['o3']);
      expect(filas()[0].querySelector('[data-aprobacion]')).toBeNull();
    });

    it('si no se pudo saber qué falta, no da ninguna por aprobada', async () => {
      TestBed.resetTestingModule();
      montar([...TODAS, 'work-order.approve']);
      fixture.detectChanges();
      http
        .expectOne(URL_POR_APROBAR)
        .flush(null, { status: 500, statusText: 'Internal Server Error' });
      await abrir(EJECUTADAS);
      pulsar('Ejecutadas');

      expect(raiz().querySelector('[data-aprobacion]')).toBeNull();
    });

    it('sin work-order.approve no hay filtro ni se pregunta', async () => {
      await abrir(EJECUTADAS);

      http.expectNone(URL_POR_APROBAR);
      expect(texto()).not.toContain('Por aprobar');
      pulsar('Ejecutadas');
      expect(raiz().querySelector('[data-aprobacion]')).toBeNull();
    });
  });

  describe('Cada columna se ordena, en los dos sentidos (2026-10-27)', () => {
    const ids = () => filas().map((f) => f.getAttribute('data-orden'));
    const cabecera = (columna: string) =>
      raiz().querySelector<HTMLButtonElement>(`[data-ordenar="${columna}"]`)!;
    const ordenarPor = (columna: string) => {
      cabecera(columna).click();
      fixture.detectChanges();
    };
    const ariaSort = (columna: string) =>
      cabecera(columna).closest('th')!.getAttribute('aria-sort');

    it('la fecha se invierte al pulsarla, y la flecha y aria-sort lo dicen', async () => {
      await abrir([
        orden({ id: 'o1', fechaMantenimiento: '2026-10-10' }),
        orden({ id: 'o2', fechaMantenimiento: '2026-10-20' }),
      ]);
      // Sin pulsar nada, lo que falta va de lo más próximo a lo más lejano.
      expect(ids()).toEqual(['o1', 'o2']);
      expect(ariaSort('fecha')).toBe('ascending');
      expect(cabecera('fecha').textContent).toContain('▲');

      ordenarPor('fecha');

      expect(ids()).toEqual(['o2', 'o1']);
      expect(ariaSort('fecha')).toBe('descending');
      expect(cabecera('fecha').textContent).toContain('▼');
      expect(ariaSort('estado')).toBe('none');
      expect(cabecera('estado').textContent).toContain('↕');
    });

    it('el estado va de lo que pide trabajo a lo anulado, y al revés', async () => {
      await abrir([
        orden({ id: 'anulada', fechaMantenimiento: '2026-10-01', estadoActivo: false }),
        orden({ id: 'hecha', fechaMantenimiento: '2026-10-02', estadoEjecucion: 'EJECUTADA' }),
        orden({ id: 'creada', fechaMantenimiento: '2026-10-03' }),
        orden({
          id: 'en-curso',
          fechaMantenimiento: '2026-10-04',
          estadoEjecucion: 'EN_EJECUCION',
        }),
      ]);
      pulsar('Todas');

      ordenarPor('estado');
      expect(ids()).toEqual(['creada', 'en-curso', 'hecha', 'anulada']);

      ordenarPor('estado');
      expect(ids()).toEqual(['anulada', 'hecha', 'en-curso', 'creada']);
    });

    it('el ingeniero, de la A a la Z, con «Sin asignar» al final', async () => {
      await abrir([
        orden({ id: 'sin', fechaMantenimiento: '2026-10-01' }),
        orden({ id: 'con', fechaMantenimiento: '2026-10-02', idIngeniero: ID_INGENIERO }),
      ]);

      ordenarPor('ingeniero');
      expect(ids()).toEqual(['con', 'sin']);

      ordenarPor('ingeniero');
      expect(ids()).toEqual(['sin', 'con']);
    });

    it('los reportes, del menor avance al mayor', async () => {
      await abrir(
        [
          orden({
            id: 'mitad',
            fechaMantenimiento: '2026-10-01',
            estadoEjecucion: 'EN_EJECUCION',
            equipos: DOS_EQUIPOS,
          }),
          orden({
            id: 'nada',
            fechaMantenimiento: '2026-10-02',
            estadoEjecucion: 'EN_EJECUCION',
            equipos: DOS_EQUIPOS,
          }),
          orden({
            id: 'todo',
            fechaMantenimiento: '2026-10-03',
            estadoEjecucion: 'EN_EJECUCION',
            equipos: DOS_EQUIPOS,
          }),
        ],
        [
          { idOrdenTrabajo: 'mitad', cerrados: 1 },
          { idOrdenTrabajo: 'todo', cerrados: 2 },
        ],
      );

      ordenarPor('reportes');
      expect(ids()).toEqual(['nada', 'mitad', 'todo']);
    });

    it('a igualdad, por fecha: dos del mismo cliente salen de la más antigua a la más reciente', async () => {
      await abrir([
        orden({ id: 'tarde', fechaMantenimiento: '2026-10-20' }),
        orden({ id: 'pronto', fechaMantenimiento: '2026-10-10' }),
      ]);

      ordenarPor('cliente');
      expect(ids()).toEqual(['pronto', 'tarde']);
      // Invertir el cliente no invierte el desempate.
      ordenarPor('cliente');
      expect(ids()).toEqual(['pronto', 'tarde']);
    });

    it('el orden elegido se mantiene al cambiar de filtro', async () => {
      await abrir([
        orden({ id: 'o1', fechaMantenimiento: '2026-09-01', estadoEjecucion: 'EJECUTADA' }),
        orden({ id: 'o2', fechaMantenimiento: '2026-10-01', estadoEjecucion: 'EJECUTADA' }),
        // Una por hacer, para que la tabla de «Por hacer» tenga cabeceras que pulsar.
        orden({ id: 'o3', fechaMantenimiento: '2026-11-01' }),
      ]);
      ordenarPor('fecha');
      ordenarPor('fecha');
      expect(ariaSort('fecha')).toBe('ascending');

      pulsar('Ejecutadas');
      expect(ids()).toEqual(['o1', 'o2']);
    });
  });
});
