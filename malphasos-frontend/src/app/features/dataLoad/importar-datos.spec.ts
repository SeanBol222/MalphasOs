import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';
import { proveerApiSimulado } from '../../../testing/entorno';
import { asentar } from '../../../testing/pantalla';
import { InformeDeCarga } from '../../core/api/tipos';
import { ImportarDatos, nombreDeSeccion, porArchivo } from './importar-datos';

const URL = 'http://localhost:8081/v1/api/data-loads';

/** Un informe inventado: la carga real no entra en las pruebas. */
const VALIDA: InformeDeCarga = {
  valida: true,
  errores: [],
  avisos: [{ mensaje: '2 equipos entran sin serie y recibirán una provisional' }],
  resumen: [
    { seccion: 'tiposEquipo', nuevos: 3, existentes: 0 },
    { seccion: 'ordenes', nuevos: 4, existentes: 1 },
  ],
};
const CON_ERRORES: InformeDeCarga = {
  valida: false,
  errores: [
    {
      archivo: '2-cliente.json',
      ruta: '/equipos/0/modelo',
      mensaje: 'La ref «mod-1» no está declarada en ningún archivo de esta carga',
    },
  ],
  avisos: [],
  resumen: [],
};

const json = (nombre: string) => new File(['{}'], nombre, { type: 'application/json' });

/** Importar datos (2026-11-08): validar sin escribir y aplicar, todo o nada, solo el SuperUsuario. */
describe('Importar datos', () => {
  let fixture: ComponentFixture<ImportarDatos>;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: proveerApiSimulado() });
    fixture = TestBed.createComponent(ImportarDatos);
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });

  afterEach(() => http.verify());

  const raiz = () => fixture.nativeElement as HTMLElement;
  const boton = (texto: string) =>
    [...raiz().querySelectorAll('button')].find((b) => b.textContent?.trim() === texto) as
      HTMLButtonElement | undefined;
  const texto = (selector: string) => raiz().querySelector(selector)?.textContent?.trim() ?? '';

  async function elegir(...nombres: (string | File)[]): Promise<File[]> {
    const archivos = nombres.map((n) => (typeof n === 'string' ? json(n) : n));
    const entrada = raiz().querySelector<HTMLInputElement>('#importar-archivo')!;
    Object.defineProperty(entrada, 'files', { value: archivos, configurable: true });
    entrada.dispatchEvent(new Event('change'));
    await asentar(fixture);
    return archivos;
  }

  async function validar(informe: InformeDeCarga): Promise<void> {
    boton('Validar')!.click();
    await asentar(fixture);
    http.expectOne({ method: 'POST', url: `${URL}/validation` }).flush(informe);
    await asentar(fixture);
  }

  it('sin archivos no se puede validar, y no hay informe ni nada que aplicar', () => {
    expect(boton('Validar')!.disabled).toBe(true);
    expect(raiz().querySelector('[data-informe]')).toBeNull();
    expect(boton('Aplicar')).toBeUndefined();
  });

  it('valida los archivos elegidos y lee el informe: veredicto, avisos y resumen por sección', async () => {
    await elegir('1-catalogo.json', '2-cliente.json');
    expect(texto('[data-archivos]')).toContain('2-cliente.json');

    boton('Validar')!.click();
    await asentar(fixture);
    const peticion = http.expectOne({ method: 'POST', url: `${URL}/validation` });
    expect((peticion.request.body as FormData).getAll('archivos')).toHaveLength(2);
    peticion.flush(VALIDA);
    await asentar(fixture);

    expect(texto('[data-veredicto]')).toBe('La carga es válida: se puede aplicar.');
    expect(texto('[data-avisos]')).toContain('La carga entera');
    expect(texto('[data-resumen]')).toContain('Tipos de equipo');
    expect(texto('[data-resumen]')).toContain('Órdenes de trabajo');
    expect(boton('Aplicar')).toBeDefined();
  });

  it('con errores dice dónde está cada uno y no ofrece aplicar', async () => {
    await elegir('2-cliente.json');
    await validar(CON_ERRORES);

    expect(texto('[data-veredicto]')).toBe('La carga tiene errores: no se puede aplicar.');
    expect(texto('[data-errores] [data-grupo]')).toBe('2-cliente.json (1)');
    expect(texto('[data-errores]')).toContain('/equipos/0/modelo');
    expect(boton('Aplicar')).toBeUndefined();
  });

  it('aplicar pide confirmación, y al confirmar aplica y lo dice', async () => {
    await elegir('1-catalogo.json');
    await validar(VALIDA);

    boton('Aplicar')!.click();
    await asentar(fixture);
    expect(raiz().querySelector('[data-confirmacion]')).not.toBeNull();
    http.expectNone({ method: 'POST', url: URL });

    boton('Confirmar y aplicar')!.click();
    await asentar(fixture);
    http.expectOne({ method: 'POST', url: URL }).flush(VALIDA);
    await asentar(fixture);

    expect(texto('[data-veredicto]')).toBe('Carga aplicada: los datos ya están en MalphasOS.');
    expect(texto('[data-resumen] thead')).toContain('Entraron');
    expect(boton('Aplicar')).toBeUndefined();
  });

  it('cancelar la confirmación no aplica nada', async () => {
    await elegir('1-catalogo.json');
    await validar(VALIDA);

    boton('Aplicar')!.click();
    await asentar(fixture);
    boton('Cancelar')!.click();
    await asentar(fixture);

    http.expectNone({ method: 'POST', url: URL });
    expect(boton('Aplicar')).toBeDefined();
  });

  it('añadir otro archivo tras validar obliga a validar otra vez', async () => {
    await elegir('1-catalogo.json');
    await validar(VALIDA);

    await elegir('2-cliente.json');

    expect(raiz().querySelector('[data-informe]')).toBeNull();
    expect(boton('Aplicar')).toBeUndefined();
  });

  it('un rechazo al aplicar se lee como informe: no escribió nada y dice por qué', async () => {
    await elegir('1-catalogo.json');
    await validar(VALIDA);
    boton('Aplicar')!.click();
    await asentar(fixture);
    boton('Confirmar y aplicar')!.click();
    await asentar(fixture);

    http
      .expectOne({ method: 'POST', url: URL })
      .flush(CON_ERRORES, { status: 422, statusText: 'Unprocessable Content' });
    await asentar(fixture);

    expect(texto('[data-veredicto]')).toBe('MalphasOS rechazó la carga y no escribió nada.');
    expect(texto('[data-errores]')).toContain('/equipos/0/modelo');
    expect(boton('Aplicar')).toBeUndefined();
    expect(raiz().querySelector('[role="alert"]')).toBeNull();
  });

  it('si aplicar falla sin informe, se avisa y vuelve a pedir confirmación antes de reintentar', async () => {
    await elegir('1-catalogo.json');
    await validar(VALIDA);
    boton('Aplicar')!.click();
    await asentar(fixture);
    boton('Confirmar y aplicar')!.click();
    await asentar(fixture);

    http
      .expectOne({ method: 'POST', url: URL })
      .flush(null, { status: 502, statusText: 'Bad Gateway' });
    await asentar(fixture);

    expect(raiz().querySelector('[role="alert"]')).not.toBeNull();
    expect(boton('Confirmar y aplicar')).toBeUndefined();
    expect(boton('Aplicar')).toBeDefined();
  });

  it('añadir suma a lo ya elegido sin quitarlo: el catálogo y el par del cliente viven en carpetas distintas', async () => {
    await elegir('1-catalogo.json');
    await elegir('2-cliente.json', '3-historial.json');

    const nombres = [...raiz().querySelectorAll('[data-archivos] li span')].map((s) =>
      s.textContent?.trim(),
    );
    expect(nombres).toEqual(['1-catalogo.json', '2-cliente.json', '3-historial.json']);
    expect(raiz().querySelector('label[for="importar-archivo"]')?.textContent?.trim()).toBe(
      'Añadir archivos',
    );

    boton('Validar')!.click();
    await asentar(fixture);
    const peticion = http.expectOne({ method: 'POST', url: `${URL}/validation` });
    expect((peticion.request.body as FormData).getAll('archivos')).toHaveLength(3);
    peticion.flush(VALIDA);
    await asentar(fixture);
  });

  it('el mismo archivo elegido dos veces no se repite', async () => {
    const catalogo = json('1-catalogo.json');
    await elegir(catalogo);
    await elegir(catalogo);

    expect(raiz().querySelectorAll('[data-archivos] li')).toHaveLength(1);
  });

  it('quitar un archivo lo saca de la lista, y el informe que había deja de valer', async () => {
    await elegir('1-catalogo.json', '2-cliente.json');
    await validar(VALIDA);

    raiz().querySelector<HTMLButtonElement>('[aria-label="Quitar 2-cliente.json"]')!.click();
    await asentar(fixture);

    expect(texto('[data-archivos]')).not.toContain('2-cliente.json');
    expect(texto('[data-archivos]')).toContain('1-catalogo.json');
    expect(raiz().querySelector('[data-informe]')).toBeNull();
    expect(boton('Aplicar')).toBeUndefined();
  });

  it('quitar el último vuelve a decir que no hay archivos', async () => {
    await elegir('1-catalogo.json');
    raiz().querySelector<HTMLButtonElement>('[aria-label="Quitar 1-catalogo.json"]')!.click();
    await asentar(fixture);

    expect(raiz().querySelector('[data-archivos]')).toBeNull();
    expect(boton('Validar')!.disabled).toBe(true);
  });

  it('el ZIP del conversor se elige como un archivo más, y se valida solo', async () => {
    const zip = new File(['PK'], 'carga.zip', { type: 'application/zip' });
    expect(raiz().querySelector('#importar-archivo')!.getAttribute('accept')).toContain('.zip');
    await elegir(zip);

    expect(boton('Validar')!.disabled).toBe(false);
    await validar(VALIDA);
    expect(texto('[data-veredicto]')).toContain('se puede aplicar');
  });

  it('los errores de una carga entera se agrupan por archivo, cada uno con cuántos tiene', async () => {
    await elegir('carga.zip');
    await validar({
      valida: false,
      errores: [
        { archivo: 'clientes/fana/historial.json', ruta: '/ordenes/0/ingeniero', mensaje: 'a' },
        { archivo: 'clientes/servimed/cliente.json', ruta: '/equipos/3/serie', mensaje: 'b' },
        { archivo: 'clientes/fana/historial.json', ruta: '/ordenes/9/sede', mensaje: 'c' },
        { mensaje: 'd' },
      ],
      avisos: [],
      resumen: [],
    });

    expect(
      [...raiz().querySelectorAll('[data-errores] [data-grupo]')].map((g) => g.textContent?.trim()),
    ).toEqual([
      'clientes/fana/historial.json (2)',
      'clientes/servimed/cliente.json (1)',
      'La carga entera (1)',
    ]);
  });

  it('un fallo que no es un informe se avisa como error', async () => {
    await elegir('1-catalogo.json');
    boton('Validar')!.click();
    await asentar(fixture);
    http
      .expectOne({ method: 'POST', url: `${URL}/validation` })
      .flush({ code: 'ERR' }, { status: 403, statusText: 'Forbidden' });
    await asentar(fixture);

    expect(texto('[role="alert"]')).toBe('No se pudo completar la operación. Intente de nuevo.');
  });
});

describe('Cómo se nombra cada cosa del informe', () => {
  it('una sección conocida por su nombre, y una desconocida tal cual', () => {
    expect(nombreDeSeccion('tiposEquipo')).toBe('Tipos de equipo');
    expect(nombreDeSeccion('facturas')).toBe('facturas');
  });

  it('los problemas se agrupan por archivo en el orden en que aparece cada uno; sin archivo, los de la carga', () => {
    const grupos = porArchivo([
      { archivo: 'b.json', mensaje: '1' },
      { mensaje: '2' },
      { archivo: 'a.json', mensaje: '3' },
      { archivo: 'b.json', mensaje: '4' },
    ]);

    expect(grupos.map((g) => [g.archivo, g.problemas.map((p) => p.mensaje)])).toEqual([
      ['b.json', ['1', '4']],
      [undefined, ['2']],
      ['a.json', ['3']],
    ]);
    expect(porArchivo(undefined)).toEqual([]);
  });
});
